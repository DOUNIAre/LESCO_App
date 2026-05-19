"""
pretrain.py — Behavioural-Cloning pre-training for the PPO Smart-Home Agent.

Reads the full-year historical dataset, converts each (timestamp, device,
action, weather) row into a 9-dim state vector + normalised target action,
then calls PPOAgent.learn_from_batch() to imprint human habits.

Usage:
    # From the project root, with venv activated:
    python -m ai.pretrain                          # default settings
    python -m ai.pretrain --epochs 20 --batch 2048 # custom
"""

import argparse
import os
import re
import sys
import time

import numpy as np

# Ensure project root is importable
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from ai.ppo_agent import PPOAgent
from ai.config import (
    DATASET_PATH,
    PRETRAINED_MODEL_PATH,
    DEVICE_PARAMS,
    DEVICE_ACTION_MAP,
    DATASET_DEVICE_TYPE_MAP,
    PARAMETER_RANGES,
    COMFORT_TARGET,
    OBS_DIM_BASE,
)

# ── Constants ─────────────────────────────────────────────────────────
DEFAULT_EPOCHS = 15
DEFAULT_BATCH = 1024
OBS_DIM = OBS_DIM_BASE + 2 + 1   # 6 + 2 (device state) + 1 (rule baseline) = 9

# Temperature regex: matches strings like "23°C", "20°C", "23C"
_TEMP_RE = re.compile(r"(\d+)\s*[°]?\s*C", re.IGNORECASE)


# ── Helpers ───────────────────────────────────────────────────────────

def _parse_temperature(action_str: str) -> float | None:
    """Extract numeric temperature from strings like '23°C'. Returns None on failure."""
    m = _TEMP_RE.search(action_str)
    if m:
        return float(m.group(1))
    return None


def _normalize_temp(temp_c: float) -> float:
    """Normalise a temperature (°C) to [0, 1] over the configured range."""
    pmin = PARAMETER_RANGES["temperature"]["min"]
    pmax = PARAMETER_RANGES["temperature"]["max"]
    return float(np.clip((temp_c - pmin) / (pmax - pmin), 0.0, 1.0))


def _rule_baseline(hour: float, device_type: str, parameter_type: str) -> float:
    """
    Lightweight replica of SmartHomeEnv._rule_baseline / PPORecommender logic.
    Returns a normalised [0, 1] baseline value.
    """
    is_night = hour < 6 or hour > 22

    if parameter_type == "on_off":
        return 0.0 if is_night else 1.0

    if parameter_type == "temperature":
        target = COMFORT_TARGET
        pmin = PARAMETER_RANGES["temperature"]["min"]
        pmax = PARAMETER_RANGES["temperature"]["max"]
        return float(np.clip((target - pmin) / (pmax - pmin), 0, 1))

    if parameter_type == "duration":
        return 60.0 / 240.0

    return 0.5


def _action_to_target(action_str: str, device_type: str, parameter_type: str) -> float | None:
    """
    Convert a raw action string from the dataset into a normalised [0, 1] target.

    Returns None when the action string is not relevant for the given parameter_type
    (e.g. a temperature string paired with the on_off parameter is handled separately).
    """
    action_str = str(action_str).strip()

    # --- Try static map first (ON, OFF, HIGH, MEDIUM, LOW) ---
    if action_str in DEVICE_ACTION_MAP:
        mapped = DEVICE_ACTION_MAP[action_str]
        if parameter_type in mapped:
            return mapped[parameter_type]
        # For on_off parameter with HIGH/MEDIUM/LOW → device is ON
        if parameter_type == "on_off" and any(k in mapped for k in ("temperature",)):
            return mapped.get("on_off", 1.0)
        return mapped.get(parameter_type)

    # --- Try parsing as a temperature string (e.g. "23°C") ---
    temp = _parse_temperature(action_str)
    if temp is not None:
        if parameter_type == "temperature":
            return _normalize_temp(temp)
        if parameter_type == "on_off":
            return 1.0   # A temperature setting implies the device is ON
        return None

    return None


# ── Data loading ──────────────────────────────────────────────────────

def load_dataset(csv_path: str):
    """
    Load and preprocess the CSV dataset using only the stdlib + numpy.
    Returns a list of dicts, one per device-action row (NaN devices skipped).
    """
    import csv
    rows = []
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            device = row.get("device", "").strip()
            if not device:
                continue  # skip activity-only rows

            action = row.get("action", "").strip()
            if not action:
                continue

            # Parse timestamp
            ts = row["timestamp"].strip()
            # "2025-01-01 00:00:00" → hour, weekday
            from datetime import datetime
            dt = datetime.strptime(ts, "%Y-%m-%d %H:%M:%S")

            rows.append({
                "hour": dt.hour + dt.minute / 60.0,
                "weekday": dt.weekday(),
                "device": device,
                "action": action,
                "tavg": float(row.get("tavg", 22) or 22),
                "prcp": float(row.get("prcp", 0) or 0),
            })
    return rows


def build_training_data(rows: list) -> dict:
    """
    Convert raw rows into grouped (states, targets) arrays.

    Returns:
        {
            ("Light", "on_off"): {"states": np.array, "targets": np.array},
            ("AC", "on_off"): ...,
            ("AC", "temperature"): ...,
            ...
        }
    """
    # Collect per (device_type, parameter_type) lists
    groups: dict[tuple, dict] = {}

    for row in rows:
        device_name = row["device"]
        device_type = DATASET_DEVICE_TYPE_MAP.get(device_name)
        if device_type is None:
            continue

        params = DEVICE_PARAMS.get(device_type, ["on_off"])

        for param_type in params:
            target = _action_to_target(row["action"], device_type, param_type)
            if target is None:
                continue

            # Build 9-dim state vector
            hour = row["hour"]
            weekday = row["weekday"]
            tavg = row["tavg"]
            prcp = row["prcp"]

            state = np.array([
                hour / 24.0,                               # hour_norm
                weekday / 7.0,                              # weekday_norm
                1.0 if weekday >= 5 else 0.0,               # is_weekend
                1.0 if (hour < 6 or hour > 22) else 0.0,    # is_night
                tavg / 40.0,                                # outdoor_temp_norm
                float(prcp),                                # is_raining (already 0/1)
                1.0 if target > 0 else 0.0,                 # device_status (inferred)
                target,                                     # device_value_norm
                _rule_baseline(hour, device_type, param_type),  # rule_baseline_norm
            ], dtype=np.float32)

            key = (device_type, param_type)
            if key not in groups:
                groups[key] = {"states": [], "targets": []}
            groups[key]["states"].append(state)
            groups[key]["targets"].append(target)

    # Convert lists → numpy arrays
    for key in groups:
        groups[key]["states"] = np.array(groups[key]["states"], dtype=np.float32)
        groups[key]["targets"] = np.array(groups[key]["targets"], dtype=np.float32)

    return groups


# ── Pre-training loop ─────────────────────────────────────────────────

def pretrain(
    csv_path: str = DATASET_PATH,
    save_path: str = PRETRAINED_MODEL_PATH,
    epochs: int = DEFAULT_EPOCHS,
    batch_size: int = DEFAULT_BATCH,
):
    """Run the full pre-training pipeline."""
    print("=" * 60)
    print("  PPO Behavioural-Cloning Pre-Training")
    print("=" * 60)
    print(f"  Dataset:    {csv_path}")
    print(f"  Save to:    {save_path}")
    print(f"  Epochs:     {epochs}")
    print(f"  Batch size: {batch_size}")
    print("=" * 60)

    # --- 1. Load data ---
    t0 = time.time()
    print("\n[1/4] Loading dataset ...")
    rows = load_dataset(csv_path)
    print(f"       -> {len(rows):,} device-action rows loaded ({time.time() - t0:.1f}s)")

    # --- 2. Build training data ---
    print("\n[2/4] Building state vectors + targets ...")
    t1 = time.time()
    groups = build_training_data(rows)
    total_samples = sum(len(g["targets"]) for g in groups.values())
    print(f"       -> {total_samples:,} training samples across {len(groups)} groups ({time.time() - t1:.1f}s)")
    for key, g in sorted(groups.items()):
        print(f"         {key[0]:20s} / {key[1]:12s}  ->  {len(g['targets']):>7,} samples")

    # --- 3. Train ---
    print(f"\n[3/4] Training ({epochs} epochs per batch of {batch_size}) ...")
    agent = PPOAgent(obs_dim=OBS_DIM, action_dim=1)
    t2 = time.time()

    all_losses = []
    for key in sorted(groups.keys()):
        g = groups[key]
        states = g["states"]
        targets = g["targets"]
        n = len(targets)
        device_type, param_type = key

        group_losses = []
        # Process in chunks to manage memory
        n_chunks = max(1, (n + batch_size - 1) // batch_size)
        for i in range(0, n, batch_size):
            s_batch = states[i : i + batch_size]
            t_batch = targets[i : i + batch_size]
            loss = agent.learn_from_batch(s_batch, t_batch, epochs=epochs)
            group_losses.append(loss)

        avg_loss = np.mean(group_losses)
        all_losses.append(avg_loss)
        print(f"       {device_type:20s} / {param_type:12s}  ->  loss {avg_loss:.6f}  ({n_chunks} chunks)")

    elapsed = time.time() - t2
    overall_loss = np.mean(all_losses)
    print(f"\n       Overall avg loss: {overall_loss:.6f}  ({elapsed:.1f}s)")

    # --- 4. Save ---
    print(f"\n[4/4] Saving pre-trained model to {save_path} ...")
    agent.save(save_path)
    total_elapsed = time.time() - t0
    print(f"\n{'=' * 60}")
    print(f"  [OK] Pre-training complete in {total_elapsed:.1f}s")
    print(f"  Model saved to: {save_path}")
    print(f"  Total samples:  {total_samples:,}")
    print(f"  Final loss:     {overall_loss:.6f}")
    print(f"{'=' * 60}")

    return agent, overall_loss


# ── CLI ───────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Pre-train the PPO agent on historical smart-home data (Behavioural Cloning)."
    )
    parser.add_argument("--dataset", type=str, default=DATASET_PATH,
                        help=f"Path to CSV dataset (default: {DATASET_PATH})")
    parser.add_argument("--output", type=str, default=PRETRAINED_MODEL_PATH,
                        help=f"Path to save the model (default: {PRETRAINED_MODEL_PATH})")
    parser.add_argument("--epochs", type=int, default=DEFAULT_EPOCHS,
                        help=f"BC epochs per batch (default: {DEFAULT_EPOCHS})")
    parser.add_argument("--batch", type=int, default=DEFAULT_BATCH,
                        help=f"Batch size (default: {DEFAULT_BATCH})")
    args = parser.parse_args()

    pretrain(
        csv_path=args.dataset,
        save_path=args.output,
        epochs=args.epochs,
        batch_size=args.batch,
    )


if __name__ == "__main__":
    main()