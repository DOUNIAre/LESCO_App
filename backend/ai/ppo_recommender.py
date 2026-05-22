import os
os.environ["OPENBLAS_NUM_THREADS"] = "1"
os.environ["MKL_NUM_THREADS"] = "1"
os.environ["OMP_NUM_THREADS"] = "1"

from datetime import datetime
import numpy as np
from typing import Dict, List, Tuple, Optional

from .adapters.backend_adapter import BackendAdapter
from .ppo_agent import PPOAgent
from .rule_engine import RuleEngine
from .config import OBS_DIM_BASE, DEVICE_PARAMS, PARAMETER_RANGES, COMFORT_TARGET, PRETRAINED_MODEL_PATH


class PPORecommender:
    """
    Generates parameter-based recommendations via PPO, with rule-based baselines.
    Rotates through device-parameter pairs: Device1 ON/OFF → Device1 Temp → Device2 ON/OFF → ...
    """
    
    def __init__(self, adapter: BackendAdapter, house_id: int):
        self.adapter = adapter
        self.house_id = house_id
        self.rule_engine = RuleEngine()
        
        # Fetch devices and build device-parameter queue
        devices = adapter.get_devices(house_id)
        self.devices = {d["id"]: d for d in devices}  # Dict for easy lookup
        self.device_list = sorted(devices, key=lambda x: x["id"])  # Sorted for consistent order
        
        # Build device-parameter queue: [device1_on_off, device1_temp, device2_on_off, ...]
        self.device_param_queue = []
        for device in self.device_list:
            device_type = device["device_type"]
            params = DEVICE_PARAMS.get(device_type, ["on_off"])
            for param in params:
                self.device_param_queue.append({
                    "device_id": device["id"],
                    "device_type": device_type,
                    "parameter_type": param
                })
        
        self.current_queue_idx = 0  # Rotation pointer
        
        # State dimensions for single parameter with rule baseline
        # Time features (4) + environment (2) + current device state (2) + rule baseline (1)
        self.obs_dim = OBS_DIM_BASE + 2 + 1
        
        # Initialize PPO agent with action_dim=1 (single continuous output)
        self.agent = PPOAgent(obs_dim=self.obs_dim, action_dim=1)
        
        # Track last recommendation for feedback
        self.last_recommendation = None
        
        # Auto-load pre-trained weights if available
        if os.path.isfile(PRETRAINED_MODEL_PATH):
            try:
                self.agent.load(PRETRAINED_MODEL_PATH)
                print(f"[PPORecommender] Loaded pre-trained weights from {PRETRAINED_MODEL_PATH}")
            except Exception as e:
                print(f"[PPORecommender] Warning: could not load pre-trained model: {e}")
    
    def generate_recommendation(self, prefs, env_data: dict, devices: list) -> dict:
        """
        Generate a single device-parameter recommendation.
        Cycles through device-parameter pairs on each call.
        
        Args:
            prefs:    list of UserPreference objects (category, value, context)
            env_data: {"outdoor_temp": int/float, "condition": str}
            devices:  list of SmartDevice objects — if non-empty, used instead
                      of the adapter-initialised device list
        
        Returns:
            Dict matching schemas.RecommendationOut:
                recommendation (str), action (int), confidence (float), reason (str)
        """
        # --- Refresh internal device list if caller supplied devices ------
        if devices:
            self._refresh_devices(devices)
        
        # --- Guard: no devices available at all --------------------------
        if not self.device_param_queue:
            return {
                "recommendation": "No devices registered",
                "action": 0,
                "confidence": 0.0,
                "reason": "No devices found for this house"
            }
        
        # Lookahead: find the first device-parameter pair where action is a change from current status
        chosen_idx = self.current_queue_idx
        for i in range(len(self.device_param_queue)):
            temp_idx = (self.current_queue_idx + i) % len(self.device_param_queue)
            pair = self.device_param_queue[temp_idx]
            d_id = pair["device_id"]
            d_type = pair["device_type"]
            p_type = pair["parameter_type"]
            
            temp_state = self._build_state(d_id, d_type, p_type, env_data)
            norm_act, _, _ = self.agent.propose(temp_state)
            act_act = self.agent.denormalize(norm_act, p_type)
            fin_act = int(act_act)
            
            dev = self.devices.get(d_id, {})
            current_status = 1 if dev.get("status") else 0
            current_val = dev.get("value", 0)
            
            if p_type == "on_off":
                if fin_act != current_status:
                    chosen_idx = temp_idx
                    break
            elif p_type == "temperature":
                if fin_act != current_val:
                    chosen_idx = temp_idx
                    break
            else:
                chosen_idx = temp_idx
                break
                
        self.current_queue_idx = chosen_idx

        # Get chosen device-parameter pair from queue
        current_pair = self.device_param_queue[self.current_queue_idx]
        device_id = current_pair["device_id"]
        device_type = current_pair["device_type"]
        parameter_type = current_pair["parameter_type"]
        
        # Build state (includes rule baseline), using env_data from caller
        state = self._build_state(device_id, device_type, parameter_type, env_data)
        
        # Get PPO agent proposal (normalized [0, 1])
        normalized_action, agent_confidence, log_prob = self.agent.propose(state)
        
        # Add transition to buffer so that feedback can update it!
        import torch
        state_t = torch.FloatTensor(state).unsqueeze(0)
        with torch.no_grad():
            _, _, val_tensor = self.agent.network(state_t)
            value_val = val_tensor.item()
        
        self.agent.add_transition(state, normalized_action, 0.0, False, log_prob, value_val)
        
        # Get rule baseline
        rule_state = self._get_rule_state(env_data)
        rule_recommendation = self.rule_engine.propose_action(
            device_id=device_id,
            device_type=device_type,
            parameter_type=parameter_type,
            state=rule_state
        )
        rule_value = rule_recommendation["value"]
        rule_confidence = rule_recommendation["confidence"]
        
        # Denormalize PPO output to actual parameter range
        actual_action = self.agent.denormalize(normalized_action, parameter_type)
        final_action = int(actual_action)
        
        # Combine confidence scores
        final_confidence = round((agent_confidence + rule_confidence) / 2.0, 2)
        
        # Build human-readable recommendation text
        device_info = self.devices.get(device_id, {})
        device_name = device_info.get("name", device_type) if "name" in device_info else device_type
        
        if parameter_type == "on_off":
            action_text = "Turn ON" if final_action == 1 else "Turn OFF"
            recommendation_text = f"{action_text} {device_name}"
        elif parameter_type == "temperature":
            recommendation_text = f"Set {device_name} temperature to {final_action}°C"
        elif parameter_type == "duration":
            recommendation_text = f"Run {device_name} for {final_action} minutes"
        else:
            recommendation_text = f"Set {device_name} {parameter_type} to {final_action}"
        
        reason_text = f"PPO adjusted from rule baseline ({rule_recommendation['reason']})"
        
        # Store full internal state for the feedback loop
        self.last_recommendation = {
            "device_id": device_id,
            "parameter_type": parameter_type,
            "action": final_action,
            "confidence": final_confidence,
            "reason": reason_text,
            "rule_value": rule_value,
            "agent_value": actual_action,
            "log_prob": log_prob
        }
        
        # Advance rotation pointer
        self.current_queue_idx = (self.current_queue_idx + 1) % len(self.device_param_queue)
        
        # Return schema-compatible dict
        return {
            "recommendation": recommendation_text,
            "action": final_action,
            "confidence": final_confidence,
            "reason": reason_text,
        }
    
    def check_and_generate_recommendation(self) -> Optional[dict]:
        """
        High-level trigger used by the backend.
        Fetches current state and decides if a recommendation is worth showing.
        """
        # 1. Fetch current environment from DB
        env = self.adapter.get_environment(self.house_id)
        if not env:
            return None
            
        env_data = {
            "outdoor_temp": env.temperature,
            "condition": env.weather
        }
        
        # 2. Fetch current devices
        devices = self.adapter.get_devices(self.house_id)
        
        # 3. Generate a proposal
        rec_data = self.generate_recommendation(prefs=[], env_data=env_data, devices=devices)
        
        # 4. Filter: Only save/return if confidence is decent (e.g. > 0.6)
        if rec_data["confidence"] > 0.6:
            # Save to database via adapter
            saved_rec = self.adapter.save_recommendation(
                house_id=self.house_id,
                device_id=self.last_recommendation["device_id"],
                content=rec_data["recommendation"],
                proposed_value=rec_data["action"],
                confidence=rec_data["confidence"],
                reason=rec_data["reason"]
            )
            # Add database ID to the result
            rec_data["id"] = saved_rec.id
            return rec_data
            
        return None

    def update_from_feedback(self, feedback_response: bool):
        """
        Update the agent based on user feedback for the last recommendation.
        
        Args:
            feedback_response: True if user accepted, False if rejected
        
        Returns:
            Dict with update metrics or {"update_done": False}
        """
        reward = 1.0 if feedback_response else -0.5
        
        # Update the latest reward in the buffer
        if len(self.agent.buffer) > 0:
            self.agent.buffer.rewards[-1] += reward
        
        # For instant demo visualization of AI learning in one-click feedback:
        if len(self.agent.buffer) >= 1:
            old_batch_size = self.agent.batch_size
            self.agent.batch_size = len(self.agent.buffer)
            metrics = self.agent.update()
            self.agent.batch_size = old_batch_size
            
            # Save the trained model weights back to disk so they persist!
            try:
                from .config import PRETRAINED_MODEL_PATH
                self.agent.save(PRETRAINED_MODEL_PATH)
                print(f"[PPORecommender] Saved updated weights to {PRETRAINED_MODEL_PATH}")
            except Exception as e:
                print(f"[PPORecommender] Warning: could not save weights: {e}")
            return metrics
        
        return {"update_done": False}
    
    # ------------------------------------------------------------------
    #  Private helpers
    # ------------------------------------------------------------------
    
    def _refresh_devices(self, devices: list):
        """
        Rebuild the internal device lookup and parameter queue from an
        externally-supplied device list (ORM objects or dicts).
        """
        normalised = []
        for d in devices:
            if isinstance(d, dict):
                normalised.append(d)
            else:
                # ORM object — extract fields
                normalised.append({
                    "id": d.id,
                    "device_type": d.device_type,
                    "status": d.status,
                    "value": d.value,
                    "room_id": getattr(d, "room_id", None),
                    "name": getattr(d, "name", d.device_type),
                })
        
        self.devices = {d["id"]: d for d in normalised}
        self.device_list = sorted(normalised, key=lambda x: x["id"])
        
        self.device_param_queue = []
        for device in self.device_list:
            device_type = device["device_type"]
            params = DEVICE_PARAMS.get(device_type, ["on_off"])
            for param in params:
                self.device_param_queue.append({
                    "device_id": device["id"],
                    "device_type": device_type,
                    "parameter_type": param
                })
        
        # Reset pointer if it's now out of bounds
        if self.device_param_queue:
            self.current_queue_idx = self.current_queue_idx % len(self.device_param_queue)
        else:
            self.current_queue_idx = 0
    
    def _build_state(self, device_id: int, device_type: str,
                     parameter_type: str, env_data: dict) -> np.ndarray:
        """
        Build observation state for the current device-parameter pair.
        Includes: time features (4) + environment (2) + current device state (2) + rule baseline (1)
        
        Args:
            device_id: Target device ID
            device_type: Target device type
            parameter_type: Target parameter type
            env_data: {"outdoor_temp": number, "condition": str}
        
        Returns:
            numpy array of shape (obs_dim,)
        """
        now = datetime.now()
        
        # Time features (4)
        state_features = [
            now.hour / 24.0,
            now.weekday() / 7.0,
            1.0 if now.weekday() >= 5 else 0.0,
            1.0 if now.hour < 6 or now.hour > 22 else 0.0,
        ]
        
        # Environment features (2) — sourced from caller
        outdoor_temp = float(env_data.get("outdoor_temp", 22))
        condition = str(env_data.get("condition", "Clear"))
        state_features.append(outdoor_temp / 40.0)  # Normalize
        state_features.append(1.0 if "rain" in condition.lower() else 0.0)
        
        # Current device state (2)
        device = self.devices.get(device_id)
        if device:
            status_val = 1.0 if device["status"] else 0.0
            state_features.append(status_val)
            state_features.append(float(device["value"] or 0) / 100.0)  # Normalize value

        else:
            state_features.extend([0.0, 0.0])
        
        # Rule baseline (normalized to [0, 1]) (1)
        rule_state = self._get_rule_state(env_data)
        rule_recommendation = self.rule_engine.propose_action(
            device_id=device_id,
            device_type=device_type,
            parameter_type=parameter_type,
            state=rule_state
        )
        rule_value = rule_recommendation["value"]
        param_range = PARAMETER_RANGES.get(parameter_type, {"min": 0, "max": 1})
        range_span = param_range["max"] - param_range["min"]
        normalized_rule_value = (rule_value - param_range["min"]) / range_span if range_span else 0.0
        state_features.append(normalized_rule_value)
        
        return np.array(state_features, dtype=np.float32)
    
    def _get_rule_state(self, env_data: dict) -> dict:
        """
        Build state dict for rule engine.
        
        Args:
            env_data: {"outdoor_temp": number, "condition": str}
        
        Returns:
            Dict with keys: hour, weekday, occupied, motion_detected, indoor_temp_c, device_status
        """
        now = datetime.now()
        
        # Use caller-supplied environment data
        indoor_temp = float(env_data.get("outdoor_temp", 21))
        
        # TODO: Fetch actual occupancy and motion data from adapter
        # For now, assume home is occupied if not night hours
        is_occupied = not (now.hour >= 23 or now.hour < 6)
        occupied = {"all": is_occupied}
        motion_detected = {"all": is_occupied}
        
        # Current device status
        device_status = {}
        for device in self.device_list:
            device_status[device["id"]] = {
                "on_off": int(device["status"]),
                "value": int(device["value"])
            }
        
        return {
            "hour": now.hour,
            "weekday": now.weekday(),
            "occupied": occupied,
            "motion_detected": motion_detected,
            "indoor_temp_c": indoor_temp,
            "device_status": device_status
        }