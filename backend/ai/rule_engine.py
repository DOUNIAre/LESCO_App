from datetime import datetime
from typing import Dict, Optional
from .config import PARAMETER_RANGES, DEVICE_PARAMS, COMFORT_TARGET


class RuleEngine:
    """
    Generates rule-based baseline recommendations per device-parameter pair.
    Rules provide safe defaults; PPO agent learns to adjust/override these.
    """
    
    def __init__(self):
        self.training_steps = 0
    
    def propose_action(
        self,
        device_id: int,
        device_type: str,
        parameter_type: str,
        state: Dict
    ) -> Dict:
        """
        Generate a rule-based recommendation for a specific device-parameter pair.
        
        Args:
            device_id: Unique device identifier
            device_type: "AC", "Heater", "Light", "TV", "Appliance"
            parameter_type: "on_off", "temperature", "duration"
            state: Current state dict with keys:
                - hour: Current hour (0-23)
                - weekday: Current weekday (0-6, 0=Monday)
                - occupied: Dict[room_id -> bool]
                - motion_detected: Dict[room_id -> bool]
                - indoor_temp_c: Current temperature in celsius
                - device_status: Dict[device_id -> {"on_off": 0|1, "value": int}]
        
        Returns:
            Dict with keys:
                - value: Recommended parameter value (actual, not normalized)
                - confidence: Confidence level (0.0-1.0)
                - reason: Explanation string
        """
        # Validate parameter type for device
        if parameter_type not in DEVICE_PARAMS.get(device_type, []):
            return {
                "value": 0,
                "confidence": 0.0,
                "reason": f"Parameter {parameter_type} not supported for {device_type}"
            }
        
        # Route to appropriate rule function
        if parameter_type == "on_off":
            return self._propose_on_off(device_id, device_type, state)
        elif parameter_type == "temperature":
            return self._propose_temperature(device_id, device_type, state)
        elif parameter_type == "duration":
            return self._propose_duration(device_id, device_type, state)
        
        return {"value": 0, "confidence": 0.0, "reason": "Unknown parameter type"}
    
    def _propose_on_off(self, device_id: int, device_type: str, state: Dict) -> Dict:
        """
        Rule-based ON/OFF recommendation.
        Returns 0 (OFF) or 1 (ON).
        """
        hour = state.get("hour", 12)
        occupied = state.get("occupied", {})
        motion_detected = state.get("motion_detected", {})
        
        # Check if any room is occupied or has recent motion
        any_occupied = any(occupied.values()) if occupied else False
        any_motion = any(motion_detected.values()) if motion_detected else False
        
        # === Light and TV: Presence-based rules ===
        if device_type in ["Light", "TV"]:
            # Morning (7-9 AM): Turn on if occupied or motion
            if 7 <= hour <= 9:
                if any_occupied or any_motion:
                    return {"value": 1, "confidence": 0.75, "reason": "morning_routine_occupied"}
                else:
                    return {"value": 0, "confidence": 0.7, "reason": "morning_routine_unoccupied"}
            
            # Evening (18-23): Turn on if occupied or motion
            elif 18 <= hour <= 23:
                if any_occupied or any_motion:
                    return {"value": 1, "confidence": 0.8, "reason": "evening_occupied"}
                else:
                    return {"value": 0, "confidence": 0.7, "reason": "evening_unoccupied"}
            
            # Night (23-6): Turn off
            elif hour >= 23 or hour < 6:
                return {"value": 0, "confidence": 0.85, "reason": "night_setback"}
            
            # Day (6-18): Turn on if occupied, off if unoccupied
            else:
                if any_occupied or any_motion:
                    return {"value": 1, "confidence": 0.7, "reason": "day_occupied"}
                else:
                    return {"value": 0, "confidence": 0.65, "reason": "day_unoccupied"}
        
        # === AC and Heater: Maintain comfort when occupied ===
        elif device_type in ["AC", "Heater"]:
            # Night setback (23-6): Turn off
            if hour >= 23 or hour < 6:
                return {"value": 0, "confidence": 0.8, "reason": "night_setback"}
            
            # Occupied hours (6-23): Keep on for comfort
            if any_occupied:
                return {"value": 1, "confidence": 0.9, "reason": "comfort_maintenance_occupied"}
            
            # Unoccupied: Turn off for energy saving
            else:
                return {"value": 0, "confidence": 0.85, "reason": "energy_saving_unoccupied"}
        
        # === Appliance: Turn on based on user presence ===
        elif device_type == "Appliance":
            if any_occupied:
                return {"value": 1, "confidence": 0.6, "reason": "user_present"}
            else:
                return {"value": 0, "confidence": 0.8, "reason": "user_absent"}
        
        return {"value": 0, "confidence": 0.5, "reason": "default_off"}
    
    def _propose_temperature(self, device_id: int, device_type: str, state: Dict) -> Dict:
        """
        Rule-based temperature recommendation for AC/Heater.
        Returns temperature in celsius (16-28).
        """
        if device_type not in ["AC", "Heater"]:
            return {"value": COMFORT_TARGET, "confidence": 0.0, "reason": "Temperature not applicable"}
        
        indoor_temp = state.get("indoor_temp_c", COMFORT_TARGET)
        
        # === AC: Cool if temp is high ===
        if device_type == "AC":
            if indoor_temp > 25:
                return {"value": 22, "confidence": 0.85, "reason": "cooling_required"}
            elif indoor_temp > 23:
                return {"value": 23, "confidence": 0.75, "reason": "slight_cooling"}
            else:
                return {"value": COMFORT_TARGET, "confidence": 0.8, "reason": "comfort_target"}
        
        # === Heater: Heat if temp is low ===
        elif device_type == "Heater":
            if indoor_temp < 19:
                return {"value": 22, "confidence": 0.85, "reason": "heating_required"}
            elif indoor_temp < 21:
                return {"value": 21, "confidence": 0.75, "reason": "slight_heating"}
            else:
                return {"value": COMFORT_TARGET, "confidence": 0.8, "reason": "comfort_target"}
        
        return {"value": COMFORT_TARGET, "confidence": 0.5, "reason": "default_comfort_target"}
    
    def _propose_duration(self, device_id: int, device_type: str, state: Dict) -> Dict:
        """
        Rule-based duration recommendation for appliances.
        Returns duration in minutes (0-240).
        """
        if device_type != "Appliance":
            return {"value": 0, "confidence": 0.0, "reason": "Duration not applicable"}
        
        # Default duration for appliances: 60 minutes
        return {"value": 60, "confidence": 0.6, "reason": "default_appliance_duration"}
    
    def increment_training_steps(self):
        """Increment training step counter."""
        self.training_steps += 1