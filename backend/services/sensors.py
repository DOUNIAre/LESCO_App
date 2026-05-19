import random
from datetime import datetime

def get_simulated_occupancy(house_id: int) -> dict:
    """
    Simulates real occupancy data for a house since we don't have physical sensors yet.
    Returns a dict mapping room_id (or 'all') to a boolean indicating if motion/occupancy is detected.
    """
    now = datetime.now()
    hour = now.hour

    # Baseline logic: Usually unoccupied during work hours (9-17) and deep sleep (1-5)
    # But we add some random variance to simulate a real home
    base_chance = 0.8  # Default 80% chance someone is home
    
    if 9 <= hour <= 17:
        base_chance = 0.2  # 20% chance someone is home during typical work hours
    elif 1 <= hour <= 5:
        base_chance = 1.0  # 100% chance they are home sleeping

    # Add 10% randomness
    is_occupied = random.random() < base_chance

    return {
        "all": is_occupied
    }

def get_simulated_motion(room_id: int) -> bool:
    """Simulates a localized motion sensor in a specific room."""
    # 30% chance of active motion if we just query blindly
    return random.random() < 0.3
