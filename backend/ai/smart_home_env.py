"""
SmartHomeEnv — Lightweight Gym-style simulated environment for PPO training.

Mirrors the 9-dim state vector used by PPORecommender._build_state():
  [hour_norm, weekday_norm, is_weekend, is_night,
   outdoor_temp_norm, is_raining,
   device_status, device_value_norm,
   rule_baseline_norm]

Reward combines comfort maintenance, energy cost, and a simulated
user-approval signal derived from how close the action is to an
"ideal" that depends on context (time, temperature, occupancy).
"""

import numpy as np
from typing import Optional, Tuple
from .config import REWARD_WEIGHTS, COMFORT_TARGET, PARAMETER_RANGES


class SmartHomeEnv:
    """
    A single-device simulated smart-home environment.

    Each episode spans a 24-hour day (144 steps × 10-min intervals).
    The agent outputs a normalised [0, 1] action which maps to the
    parameter range of the chosen device type.

    Args:
        device_type: One of "AC", "Heater", "Light", "TV", "Appliance".
        parameter_type: One of "on_off", "temperature", "duration".
        seed: Optional RNG seed for reproducibility.
    """

    STEPS_PER_EPISODE = 144          # 24 h ÷ 10 min
    MINUTES_PER_STEP = 10

    def __init__(
        self,
        device_type: str = "AC",
        parameter_type: str = "temperature",
        seed: Optional[int] = None,
    ):
        self.device_type = device_type
        self.parameter_type = parameter_type
        self.obs_dim = 9                      # matches PPORecommender
        self.action_dim = 1

        self.rng = np.random.default_rng(seed)

        # --- Mutable episode state ---
        self._step_idx: int = 0
        self._hour: float = 0.0
        self._outdoor_temp: float = 22.0
        self._is_raining: bool = False
        self._device_on: bool = False
        self._device_value: float = 0.0       # normalised [0, 1]

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def reset(self) -> np.ndarray:
        """Reset the environment to a random starting hour and return obs."""
        self._step_idx = 0

        # Randomise starting conditions
        self._hour = self.rng.uniform(0, 24)
        self._outdoor_temp = self.rng.uniform(5, 38)
        self._is_raining = bool(self.rng.random() < 0.25)
        self._device_on = bool(self.rng.random() < 0.5)
        self._device_value = self.rng.uniform(0, 1)

        return self._observe()

    def step(self, action: float) -> Tuple[np.ndarray, float, bool, dict]:
        """
        Execute one time-step.

        Args:
            action: Normalised value in [0, 1].

        Returns:
            (next_obs, reward, done, info)
        """
        action = float(np.clip(action, 0.0, 1.0))

        # --- Compute reward BEFORE advancing clock ---
        reward, info = self._compute_reward(action)

        # --- Advance simulation ---
        self._step_idx += 1
        self._hour = (self._hour + self.MINUTES_PER_STEP / 60.0) % 24.0

        # Drift outdoor temperature realistically
        self._outdoor_temp += self.rng.normal(0, 0.3)
        self._outdoor_temp = float(np.clip(self._outdoor_temp, -5, 45))

        # Small chance rain changes
        if self.rng.random() < 0.02:
            self._is_raining = not self._is_raining

        # Update device state from agent action
        if self.parameter_type == "on_off":
            self._device_on = action >= 0.5
        else:
            self._device_on = True
        self._device_value = action

        done = self._step_idx >= self.STEPS_PER_EPISODE
        return self._observe(), reward, done, info

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _observe(self) -> np.ndarray:
        """Build the 9-dim observation vector."""
        hour_norm = self._hour / 24.0
        weekday = int(self._hour * 7 / 24) % 7      # pseudo-weekday from hour
        weekday_norm = weekday / 7.0
        is_weekend = 1.0 if weekday >= 5 else 0.0
        is_night = 1.0 if (self._hour < 6 or self._hour > 22) else 0.0

        outdoor_temp_norm = self._outdoor_temp / 40.0
        rain_flag = 1.0 if self._is_raining else 0.0

        device_status = 1.0 if self._device_on else 0.0
        device_val_norm = self._device_value

        rule_baseline_norm = self._rule_baseline()

        return np.array([
            hour_norm, weekday_norm, is_weekend, is_night,
            outdoor_temp_norm, rain_flag,
            device_status, device_val_norm,
            rule_baseline_norm,
        ], dtype=np.float32)

    def _rule_baseline(self) -> float:
        """Simple heuristic baseline (normalised to [0, 1])."""
        if self.parameter_type == "on_off":
            # On during occupied hours, off at night
            return 0.0 if (self._hour < 6 or self._hour > 22) else 1.0

        if self.parameter_type == "temperature":
            # Target comfort temp, normalised to [16, 28] range
            target = COMFORT_TARGET
            pmin = PARAMETER_RANGES["temperature"]["min"]
            pmax = PARAMETER_RANGES["temperature"]["max"]
            return float(np.clip((target - pmin) / (pmax - pmin), 0, 1))

        if self.parameter_type == "duration":
            # 60 min default, normalised to [0, 240]
            return 60.0 / 240.0

        return 0.5

    def _compute_reward(self, action: float) -> Tuple[float, dict]:
        """
        Reward signal combining comfort, energy, and simulated approval.

        Returns:
            (scalar_reward, info_dict)
        """
        w = REWARD_WEIGHTS

        # --- 1. Comfort reward ---
        ideal = self._ideal_action()
        comfort_error = abs(action - ideal)
        comfort_reward = 1.0 - comfort_error          # best = 1.0

        # --- 2. Energy penalty ---
        # Higher action values → more energy usage
        energy_penalty = action * 0.5                  # [0, 0.5]

        # Night bonus: reward turning things off
        is_night = self._hour < 6 or self._hour > 22
        if is_night and action < 0.2:
            comfort_reward += 0.3                      # bonus for night setback

        # --- 3. Simulated user approval ---
        # Probability of approval scales with how close action is to ideal
        approval_prob = max(0.0, 1.0 - 2.0 * comfort_error)
        approved = self.rng.random() < approval_prob
        approval_signal = w["approval_bonus"] if approved else -w["rejection_penalty"]

        # --- Combine ---
        reward = (
            w["comfort"] * comfort_reward
            - w["energy"] * energy_penalty
            + 0.3 * approval_signal                    # scale approval influence
        )

        info = {
            "comfort_reward": comfort_reward,
            "energy_penalty": energy_penalty,
            "approved": approved,
            "ideal_action": ideal,
        }
        return float(reward), info

    def _ideal_action(self) -> float:
        """
        Context-dependent ideal action (normalised [0, 1]).
        This is what a 'perfect' agent would output given current conditions.
        """
        is_night = self._hour < 6 or self._hour > 22
        is_occupied = not is_night                      # simplified

        if self.parameter_type == "on_off":
            if is_night:
                return 0.0
            return 1.0 if is_occupied else 0.0

        if self.parameter_type == "temperature":
            # Ideal = comfort target, but shift if it's very hot/cold outside
            pmin = PARAMETER_RANGES["temperature"]["min"]
            pmax = PARAMETER_RANGES["temperature"]["max"]
            target = COMFORT_TARGET

            if self._outdoor_temp > 30:
                target = max(pmin, COMFORT_TARGET - 1)    # cool more aggressively
            elif self._outdoor_temp < 10:
                target = min(pmax, COMFORT_TARGET + 1)    # heat more aggressively

            if is_night:
                target = max(pmin, target - 2)            # night setback

            return float(np.clip((target - pmin) / (pmax - pmin), 0, 1))

        if self.parameter_type == "duration":
            if is_night:
                return 0.0
            return 60.0 / 240.0                           # 60 min default

        return 0.5