"""
tune_ppo.py — Bayesian Hyperparameter Optimisation for the PPO Agent.

Uses Optuna (TPE sampler) to search over PPO hyperparameters by training
the agent in a simulated smart-home environment and maximising mean
episode reward.

Usage:
    # From the project root, with venv activated:
    python -m ai.tune_ppo                         # 50 trials, 100 episodes each
    python -m ai.tune_ppo --n-trials 3 --n-episodes 10   # quick smoke test
    python -m ai.tune_ppo --apply                 # also update config.py with best params
"""

import argparse
import json
import os
import sys
import time

import numpy as np
import optuna
from optuna.pruners import MedianPruner
from optuna.samplers import TPESampler

# Ensure project root is on sys.path so `ai.*` imports work with `-m`
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from ai.ppo_agent import PPOAgent
from ai.smart_home_env import SmartHomeEnv
from ai.config import PPO_CONFIG


# ── Defaults ──────────────────────────────────────────────────────────
DEFAULT_N_TRIALS = 50
DEFAULT_N_EPISODES = 100
DEFAULT_EVAL_WINDOW = 20        # average reward over last N episodes
DEFAULT_SEED = 42
DEVICE_SCENARIOS = [
    ("AC", "temperature"),
    ("Light", "on_off"),
    ("Appliance", "duration"),
]


# ── Objective ─────────────────────────────────────────────────────────

def objective(
    trial: optuna.Trial,
    n_episodes: int,
    eval_window: int,
    seed: int,
) -> float:
    """
    Single Optuna trial: sample hyperparams → train PPO → return mean reward.
    """
    # --- Sample hyperparameters ---
    cfg = {
        "lr":            trial.suggest_float("lr", 1e-5, 1e-3, log=True),
        "gamma":         trial.suggest_float("gamma", 0.9, 0.999),
        "gae_lambda":    trial.suggest_float("gae_lambda", 0.8, 1.0),
        "clip_epsilon":  trial.suggest_float("clip_epsilon", 0.1, 0.3),
        "value_coef":    trial.suggest_float("value_coef", 0.1, 1.0),
        "entropy_coef":  trial.suggest_float("entropy_coef", 0.001, 0.1, log=True),
        "max_grad_norm": trial.suggest_float("max_grad_norm", 0.3, 1.0),
        "ppo_epochs":    trial.suggest_int("ppo_epochs", 2, 10),
        "batch_size":    trial.suggest_categorical("batch_size", [32, 64, 128, 256]),
        # carry over non-tuned keys
        "buffer_size":   PPO_CONFIG["buffer_size"],
    }

    obs_dim = 9  # SmartHomeEnv observation dimension

    # --- Train across device scenarios ---
    all_episode_rewards: list[float] = []

    for dev_type, param_type in DEVICE_SCENARIOS:
        env = SmartHomeEnv(device_type=dev_type, parameter_type=param_type, seed=seed)
        agent = PPOAgent(obs_dim=obs_dim, action_dim=1, config_override=cfg)

        for ep in range(n_episodes):
            obs = env.reset()
            episode_reward = 0.0
            done = False

            while not done:
                action, log_prob, value = agent.select_action_for_training(obs)
                next_obs, reward, done, info = env.step(action)

                agent.add_transition(
                    state=obs,
                    action=action,
                    reward=reward,
                    done=float(done),
                    log_prob=log_prob,
                    value=value,
                )

                episode_reward += reward
                obs = next_obs

                # Trigger PPO update when buffer is ready
                if len(agent.buffer) >= agent.batch_size:
                    agent.update()

            all_episode_rewards.append(episode_reward)

        # Report intermediate value for pruning (after each scenario)
        intermediate = float(np.mean(all_episode_rewards[-eval_window:]))
        trial.report(intermediate, len(all_episode_rewards))
        if trial.should_prune():
            raise optuna.TrialPruned()

    # --- Final metric: mean reward over last eval_window episodes ---
    mean_reward = float(np.mean(all_episode_rewards[-eval_window:]))
    return mean_reward


# ── Config file updater ───────────────────────────────────────────────

def apply_best_to_config(best_params: dict) -> None:
    """Overwrite PPO_CONFIG values in config.py with the best found params."""
    config_path = os.path.join(os.path.dirname(__file__), "config.py")
    with open(config_path, "r") as f:
        content = f.read()

    # Map Optuna param names → config.py key format
    replacements = {
        "lr": best_params["lr"],
        "gamma": best_params["gamma"],
        "clip_epsilon": best_params["clip_epsilon"],
        "value_coef": best_params["value_coef"],
        "entropy_coef": best_params["entropy_coef"],
        "max_grad_norm": best_params["max_grad_norm"],
        "ppo_epochs": best_params["ppo_epochs"],
        "batch_size": best_params["batch_size"],
    }

    import re
    for key, value in replacements.items():
        # Match `"key": <old_value>,` pattern in the PPO_CONFIG dict
        pattern = rf'("{key}":\s*)[^,\n]+'
        if isinstance(value, float):
            replacement = rf"\g<1>{value:.6g}"
        else:
            replacement = rf"\g<1>{value}"
        content = re.sub(pattern, replacement, content)

    # Also update gae_lambda_hvac with the tuned gae_lambda
    gae_val = best_params["gae_lambda"]
    content = re.sub(
        r'("gae_lambda_hvac":\s*)[^,\n]+',
        rf"\g<1>{gae_val:.6g}",
        content,
    )

    with open(config_path, "w") as f:
        f.write(content)

    print(f"\n[OK] config.py updated with best parameters.")


# ── Main ──────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Bayesian hyperparameter tuning for the PPO smart-home agent."
    )
    parser.add_argument("--n-trials", type=int, default=DEFAULT_N_TRIALS,
                        help=f"Number of Optuna trials (default {DEFAULT_N_TRIALS})")
    parser.add_argument("--n-episodes", type=int, default=DEFAULT_N_EPISODES,
                        help=f"Episodes per scenario per trial (default {DEFAULT_N_EPISODES})")
    parser.add_argument("--eval-window", type=int, default=DEFAULT_EVAL_WINDOW,
                        help=f"Episodes to average for final metric (default {DEFAULT_EVAL_WINDOW})")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED,
                        help=f"RNG seed (default {DEFAULT_SEED})")
    parser.add_argument("--apply", action="store_true",
                        help="Write best params back to config.py")
    parser.add_argument("--output", type=str, default="best_ppo_config.json",
                        help="Path to save best config JSON (default best_ppo_config.json)")
    args = parser.parse_args()

    print("=" * 60)
    print("  PPO Bayesian Hyperparameter Tuning (Optuna TPE)")
    print("=" * 60)
    print(f"  Trials:      {args.n_trials}")
    print(f"  Episodes:    {args.n_episodes} per scenario per trial")
    print(f"  Scenarios:   {len(DEVICE_SCENARIOS)} ({', '.join(f'{d}/{p}' for d, p in DEVICE_SCENARIOS)})")
    print(f"  Eval window: last {args.eval_window} episodes")
    print(f"  Seed:        {args.seed}")
    print("=" * 60)

    # Suppress Optuna's verbose default logging
    optuna.logging.set_verbosity(optuna.logging.WARNING)

    sampler = TPESampler(seed=args.seed)
    pruner = MedianPruner(n_startup_trials=5, n_warmup_steps=1)
    study = optuna.create_study(
        direction="maximize",
        sampler=sampler,
        pruner=pruner,
        study_name="ppo_smart_home_hpo",
    )

    start_time = time.time()

    study.optimize(
        lambda trial: objective(
            trial,
            n_episodes=args.n_episodes,
            eval_window=args.eval_window,
            seed=args.seed,
        ),
        n_trials=args.n_trials,
        show_progress_bar=True,
    )

    elapsed = time.time() - start_time

    # --- Results ---
    print("\n" + "=" * 60)
    print("  RESULTS")
    print("=" * 60)
    print(f"  Best trial:  #{study.best_trial.number}")
    print(f"  Best reward: {study.best_value:.4f}")
    print(f"  Time:        {elapsed:.1f}s ({elapsed / 60:.1f} min)")
    print()
    print("  Best hyperparameters:")
    for k, v in study.best_params.items():
        current = PPO_CONFIG.get(k, "N/A")
        if isinstance(v, float):
            print(f"    {k:20s}  {v:<12.6g}  (was {current})")
        else:
            print(f"    {k:20s}  {str(v):<12s}  (was {current})")
    print("=" * 60)

    # --- Save best config ---
    output_path = os.path.join(PROJECT_ROOT, args.output)
    best_config = {**PPO_CONFIG, **study.best_params}
    # Rename gae_lambda → gae_lambda_hvac for config compatibility
    if "gae_lambda" in best_config:
        best_config["gae_lambda_hvac"] = best_config.pop("gae_lambda")
    with open(output_path, "w") as f:
        json.dump(best_config, f, indent=2, default=str)
    print(f"\n[SAVED] Best config saved to: {output_path}")

    # --- Optionally apply to config.py ---
    if args.apply:
        apply_best_to_config(study.best_params)

    # --- Trial summary table ---
    print(f"\n[TOP 5] Trial summary:")
    trials_sorted = sorted(study.trials, key=lambda t: t.value if t.value is not None else float("-inf"), reverse=True)
    print(f"  {'#':>4s}  {'Reward':>10s}  {'lr':>10s}  {'gamma':>8s}  {'clip_e':>8s}  {'ent_coef':>10s}  {'epochs':>6s}  {'batch':>5s}")
    for t in trials_sorted[:5]:
        if t.value is None:
            continue
        p = t.params
        print(
            f"  {t.number:4d}  {t.value:10.4f}"
            f"  {p['lr']:10.2e}  {p['gamma']:8.4f}  {p['clip_epsilon']:8.4f}"
            f"  {p['entropy_coef']:10.2e}  {p['ppo_epochs']:6d}  {p['batch_size']:5d}"
        )

    return study


if __name__ == "__main__":
    main()