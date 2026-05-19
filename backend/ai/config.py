# config.py
import os

# ========== PATHS ==========
_AI_DIR = os.path.dirname(os.path.abspath(__file__))
_PROJECT_ROOT = os.path.dirname(_AI_DIR)

DATASET_PATH = os.path.join(_PROJECT_ROOT, "smarthomedataset25.csv")
PRETRAINED_MODEL_PATH = os.path.join(_AI_DIR, "pretrained_model.pt")

# ========== HYPERPARAMETERS ==========

OBS_DIM_BASE = 6  # Time features (4) + environment features (2)

# Device types and their supported parameters
DEVICE_TYPES = ["AC", "Heater", "Light", "TV", "Appliance"]

DEVICE_PARAMS = {
    "AC": ["on_off", "temperature"],
    "Heater": ["on_off", "temperature"],
    "Light": ["on_off"],
    "TV": ["on_off"],
    "Appliance": ["on_off", "duration"],
    "dishwasher": ["on_off"],
    "washing_machine": ["on_off"],
}

# Maps dataset device names → config device types
DATASET_DEVICE_TYPE_MAP = {
    "light": "Light",
    "tv": "TV",
    "ac": "AC",
    "heater": "Heater",
    "dishwasher": "dishwasher",
    "washing_machine": "washing_machine",
}

# Maps raw action strings from the dataset to normalised [0, 1] values.
# Used by pretrain.py to build target labels.
DEVICE_ACTION_MAP = {
    # --- binary on/off ---
    "ON": {"on_off": 1.0},
    "OFF": {"on_off": 0.0},
    # --- heater intensity (normalised on_off scale) ---
    "HIGH": {"on_off": 1.0, "temperature": 1.0},
    "MEDIUM": {"on_off": 1.0, "temperature": 0.5},
    "LOW": {"on_off": 1.0, "temperature": 0.25},
}

# Parameter value ranges (actual, non-normalized)
PARAMETER_RANGES = {
    "on_off": {"min": 0, "max": 1, "type": "int"},
    "temperature": {"min": 16, "max": 28, "type": "int"},
    "duration": {"min": 0, "max": 240, "type": "int"}  # minutes
}

PPO_CONFIG = {
    "lr": 0.000999043,
    "gamma": 0.905614,
    "gae_lambda_hvac": 0.815411,
    "gae_lambda_lights": 0.5,
    "clip_epsilon": 0.25328,
    "value_coef": 0.483424,
    "entropy_coef": 0.0110056,
    "max_grad_norm": 0.641441,
    "ppo_epochs": 10,
    "batch_size": 32,
    "buffer_size": 2048
}

# Training
WARMUP_STEPS = 1000  # Pure rule-based proposals before RL starts
UPDATE_FREQUENCY = 256
TOTAL_EPISODES = 500
EPISODE_LENGTH_STEPS = 1440  # 24 hours

# User trust levels
TRUST_LEVELS = {
    "new": 0.3,      # First week: ask for everything
    "learning": 0.6, # After 100 approvals: auto-approve common actions
    "trusted": 0.85, # After 500 approvals: minimal questions
    "expert": 0.95   # User can enable full autonomy
}

# Reward weights (HITL version)
REWARD_WEIGHTS = {
    "comfort": 1.0,
    "energy": 0.2,  # Reduced from 0.5 to lessen penalties
    "approval_bonus": 2.0,
    "rejection_penalty": 3.0,
    "modification_penalty": 1.0,
    "timeout_penalty": 0
}

# Temperature limits
MIN_SAFE_TEMP = 5.0
MAX_SAFE_TEMP = 35.0
COMFORT_TARGET = 21.0