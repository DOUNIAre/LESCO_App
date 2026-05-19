import os
import sys
from unittest.mock import patch
from datetime import datetime

# Ensure project root is importable
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from ai.ppo_recommender import PPORecommender
from ai.adapters.backend_adapter import BackendAdapter

class MockAdapter(BackendAdapter):
    def get_devices(self, house_id):
        return [
            {"id": 1, "device_type": "AC", "status": False, "value": 0, "name": "Main AC"},
            {"id": 2, "device_type": "Heater", "status": False, "value": 0, "name": "Main Heater"},
            {"id": 3, "device_type": "Light", "status": False, "value": 0, "name": "Living Light"}
        ]
    def get_preferences(self, house_id):
        return []
    def get_environment(self, house_id):
        return {"outdoor_temp": 22, "weather": "Clear"}

SCENARIOS = [
    {
        "name": "Hot Summer Afternoon (35C, Clear, 2:00 PM)",
        "time": datetime(2026, 7, 15, 14, 0, 0),
        "env": {"outdoor_temp": 35, "condition": "Clear"}
    },
    {
        "name": "Cold Winter Night (5C, Raining, 3:00 AM)",
        "time": datetime(2026, 1, 15, 3, 0, 0),
        "env": {"outdoor_temp": 5, "condition": "Rain"}
    },
    {
        "name": "Mild Spring Evening (18C, Clear, 8:00 PM)",
        "time": datetime(2026, 4, 15, 20, 0, 0),
        "env": {"outdoor_temp": 18, "condition": "Clear"}
    }
]

def run_simulation():
    print("=" * 60)
    print("SMART HOME AGENT SIMULATION")
    print("=" * 60)
    
    adapter = MockAdapter()
    recommender = PPORecommender(adapter, house_id=1)
    
    for scenario in SCENARIOS:
        print(f"\n{scenario['name']}")
        print("-" * 60)
        
        # Patch datetime to trick the agent into thinking it's the scenario time
        with patch('ai.ppo_recommender.datetime') as mock_datetime:
            mock_datetime.now.return_value = scenario["time"]
            
            # The recommender cycles through device parameters. 
            # We have 3 devices. AC(on/off, temp), Heater(on/off, temp), Light(on_off)
            # That's 5 parameters in the queue. Let's pull 5 recommendations to see all of them.
            
            for _ in range(5):
                rec = recommender.generate_recommendation(
                    prefs=[], 
                    env_data=scenario["env"], 
                    devices=[]
                )
                print(f"[{rec['confidence'] * 100:>4.0f}% Confidence] {rec['recommendation']}")

if __name__ == "__main__":
    run_simulation()