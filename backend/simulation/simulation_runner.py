import requests
import time
import random
from datetime import datetime
from simulation import generate_person_state, generate_device_events, family, house

# Config
BACKEND_URL = "http://127.0.0.1:8000"
HOUSE_ID = 1  # Assume we are simulating House #1
SIM_SPEED = 2 # Seconds between updates

def run_realtime_simulation():
    print(f"🚀 Starting Real-time Simulation for House {HOUSE_ID}...")
    print(f"Connecting to {BACKEND_URL}")
    
    while True:
        current_time = datetime.now()
        
        # 1. Simulate Environment (Temperature)
        # Simple seasonal logic: Night is cooler, Day is hotter
        hour = current_time.hour
        base_temp = 20 if (hour < 7 or hour > 20) else 28
        temp = base_temp + random.randint(-2, 5)
        weather = "Clear" if temp < 30 else "Hot"
        
        try:
            # Push Environment to Backend
            env_res = requests.post(
                f"{BACKEND_URL}/environment/push",
                params={"house_id": HOUSE_ID, "temp": temp, "weather_desc": weather}
            )
            print(f"[{current_time.strftime('%H:%M:%S')}] Temp: {temp}°C | Server: {env_res.json().get('message')}")
            
            # 2. Simulate Person Behavior & Device States
            all_device_events = []
            for person in family:
                room, activity = generate_person_state(person, current_time)
                events = generate_device_events(person["name"], room, activity, current_time)
                for e_room, device, action in events:
                    all_device_events.append({"room": e_room, "device": device, "action": action})
            
            # Sync Device States to Backend
            if all_device_events:
                requests.post(
                    f"{BACKEND_URL}/simulation/sync-devices",
                    params={"house_id": HOUSE_ID},
                    json=all_device_events
                )
                
        except Exception as e:
            print(f"❌ Error connecting to backend: {e}")
            
        time.sleep(SIM_SPEED)

if __name__ == "__main__":
    run_realtime_simulation()
