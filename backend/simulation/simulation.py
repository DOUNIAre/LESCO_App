import random
import csv
from datetime import datetime, timedelta


# =========================================================
# HOUSE CONFIGURATION
# =========================================================

house = {
    "rooms": [
        "living_room",
        "kitchen",
        "master_bedroom",
        "kid_bedroom",
        "bathroom",
        "hallway"
    ],

    "devices": {
        "living_room": ["light", "tv", "ac", "heater"],
        "kitchen": ["light", "fridge", "dishwasher"],
        "master_bedroom": ["light", "ac", "heater", "tv"],
        "kid_bedroom": ["light", "ac", "heater"],
        "bathroom": ["light", "washing_machine"],
        "hallway": ["light", "heater"]
    }
}


# =========================================================
# FAMILY CONFIGURATION
# =========================================================

family = [
    {
        "name": "father",
        "wake_time": "07:00",
        "sleep_time": "22:30",
        "work_days": [0, 1, 2, 3, 4, 5],
    },

    {
        "name": "mother",
        "wake_time": "06:30",
        "sleep_time": "22:30",
        "work_days": [0, 1, 2, 3, 4]
    },

    {
        "name": "kid",
        "wake_time": "07:30",
        "sleep_time": "21:30",
        "school_days": [0, 1, 2, 3, 4]
    }
]


# =========================================================
# SIMULATION CONFIGURATION
# =========================================================

START_DATE = datetime(2026, 1, 1, 0, 0)
END_DATE = datetime(2026, 4, 1, 0, 0)

TIME_STEP = timedelta(minutes=30)


# =========================================================
# HELPER FUNCTIONS
# =========================================================

def parse_time(time_string):
    hour, minute = map(int, time_string.split(":"))
    return hour, minute


def add_randomness(hour, minute, variance=20):

    offset = random.randint(-variance, variance)

    total_minutes = hour * 60 + minute + offset

    randomized_hour = (total_minutes // 60) % 24
    randomized_minute = total_minutes % 60

    return randomized_hour, randomized_minute


def get_season(month):

    if month in [12, 1, 2]:
        return "winter"

    elif month in [3, 4, 5]:
        return "spring"

    elif month in [6, 7, 8]:
        return "summer"

    else:
        return "autumn"


def is_night(hour):

    return hour >= 19 or hour <= 6


# =========================================================
# PERSON BEHAVIOR ENGINE
# =========================================================

def generate_person_state(person, current_time):

    hour = current_time.hour
    minute = current_time.minute
    weekday = current_time.weekday()

    wake_hour, wake_minute = parse_time(person["wake_time"])
    sleep_hour, sleep_minute = parse_time(person["sleep_time"])

    wake_hour, wake_minute = add_randomness(
        wake_hour,
        wake_minute
    )

    sleep_hour, sleep_minute = add_randomness(
        sleep_hour,
        sleep_minute
    )

    current_total = hour * 60 + minute
    wake_total = wake_hour * 60 + wake_minute
    sleep_total = sleep_hour * 60 + sleep_minute


    # =====================================================
    # SLEEPING
    # =====================================================

    if current_total < wake_total or current_total >= sleep_total:

        if person["name"] == "kid":
            return "kid_bedroom", "sleeping"

        else:
            return "master_bedroom", "sleeping"


    # =====================================================
    # FATHER LOGIC
    # =====================================================

    if person["name"] == "father":

        if weekday in person["work_days"]:

            if 7 <= hour < 8:
                return "kitchen", "breakfast"

            elif 8 <= hour < 17:
                return "outside", "working"

            elif 17 <= hour < 19:
                return "living_room", "relaxing"

            elif 19 <= hour < 20:
                return "kitchen", "having_dinner"

            elif 20 <= hour < 21:
                return "living_room", "watching_tv"

            elif 21 <= hour < 22:
                return "bathroom", "showering"

        else:

            weekend_choices = [
                ("living_room", "watching_tv"),
                ("kitchen", "eating"),
                ("outside", "shopping"),
                ("master_bedroom", "resting")
            ]

            return random.choice(weekend_choices)


    # =====================================================
    # MOTHER LOGIC
    # =====================================================

    elif person["name"] == "mother":

        if weekday in person["work_days"]:

            if 6 <= hour < 8:
                return "kitchen", "cooking"

            elif 8 <= hour < 16:
                return "outside", "working"

            elif 17 <= hour < 19:
                return "kitchen", "cooking"

            elif 19 <= hour < 20:
                return "kitchen", "having_dinner"

            elif 20 <= hour < 21:
                return "living_room", "watching_tv"

            elif 21 <= hour < 22:
                return "bathroom", "showering"

        else:

            weekend_choices = [
                ("kitchen", "cooking"),
                ("living_room", "watching_tv"),
                ("outside", "visiting_family"),
                ("bathroom", "laundry")
            ]

            return random.choice(weekend_choices)


    # =====================================================
    # KID LOGIC
    # =====================================================

    elif person["name"] == "kid":

        if weekday in person["school_days"]:

            if 8 <= hour < 16:
                return "outside", "school"

            elif 16 <= hour < 18:
                return "kid_bedroom", "gaming"

            elif 18 <= hour < 19:
                return "living_room", "watching_tv"

            elif 19 <= hour < 20:
                return "kitchen", "having_dinner"

            elif 20 <= hour < 21:
                return "kid_bedroom", "studying"

        else:

            weekend_choices = [
                ("kid_bedroom", "gaming"),
                ("living_room", "watching_tv"),
                ("outside", "playing_outside")
            ]

            return random.choice(weekend_choices)

    return "living_room", "idle"


# =========================================================
# DEVICE ENGINE
# =========================================================

def generate_device_events(person_name, room, activity, current_time):

    hour = current_time.hour
    month = current_time.month

    season = get_season(month)

    events = []

    if room == "outside":
        return events

    devices = house["devices"].get(room, [])

    # =====================================================
    # LIGHTS
    # =====================================================

    if "light" in devices:

        if is_night(hour):
            events.append((room, "light", "ON"))

        else:

            if random.random() < 0.3:
                events.append((room, "light", "ON"))

            else:
                events.append((room, "light", "OFF"))

    # =====================================================
    # TV
    # =====================================================

    if "tv" in devices:

        if activity == "watching_tv":
            events.append((room, "tv", "ON"))

        else:
            events.append((room, "tv", "OFF"))

    # =====================================================
    # AC
    # =====================================================

    if "ac" in devices:

        ac_preferences = {
            "father": (21, 23),
            "mother": (22, 25),
            "kid": (18, 21)
        }

        min_temp, max_temp = ac_preferences[person_name]

        if season == "summer":
            ac_probability = 0.9

        elif season == "spring":
            ac_probability = 0.4

        elif season == "autumn":
            ac_probability = 0.2

        else:
            ac_probability = 0.05

        if random.random() < ac_probability:

            ac_temperature = random.randint(
                min_temp,
                max_temp
            )

            events.append((
                room,
                "ac",
                f"{ac_temperature}°C"
            ))

        else:

            events.append((
                room,
                "ac",
                "OFF"
            ))

    # =====================================================
    # HEATER
    # =====================================================

    if "heater" in devices:

        heater_preferences = {
            "father": ["MEDIUM", "HIGH"],
            "mother": ["LOW", "MEDIUM"],
            "kid": ["HIGH"]
        }

        preferred_levels = heater_preferences[person_name]

        if season == "winter":
            heater_probability = 0.85

        elif season == "autumn":
            heater_probability = 0.4

        elif season == "spring":
            heater_probability = 0.15

        else:
            heater_probability = 0.02

        if random.random() < heater_probability:

            heater_level = random.choice(
                preferred_levels
            )

            events.append((
                room,
                "heater",
                heater_level
            ))

        else:

            events.append((
                room,
                "heater",
                "OFF"
            ))

    # =====================================================
    # WASHING MACHINE
    # =====================================================

    if "washing_machine" in devices:

        if activity == "laundry":
            events.append((room, "washing_machine", "ON"))

    # =====================================================
    # DISHWASHER
    # =====================================================

    if "dishwasher" in devices:

        if activity == "cooking":

            if random.random() < 0.4:
                events.append((room, "dishwasher", "ON"))

    return events


# =========================================================
# MAIN SIMULATION LOOP
# =========================================================

simulation_data = []

current_time = START_DATE

print("Starting simulation...")

while current_time < END_DATE:

    for person in family:

        person_name = person["name"]

        # =================================================
        # PERSON STATE
        # =================================================

        room, activity = generate_person_state(
            person,
            current_time
        )

        # =================================================
        # DEVICE EVENTS
        # =================================================

        device_events = generate_device_events(
            person_name,
            room,
            activity,
            current_time
        )

        # =================================================
        # SAVE OCCUPANCY DATA
        # =================================================

        simulation_data.append({
            "timestamp": current_time,
            "person": person_name,
            "room": room,
            "activity": activity,
            "device": None,
            "action": None
        })

        # =================================================
        # SAVE DEVICE EVENTS
        # =================================================

        for event_room, device, action in device_events:

            simulation_data.append({
                "timestamp": current_time,
                "person": person_name,
                "room": event_room,
                "activity": activity,
                "device": device,
                "action": action
            })

    current_time += TIME_STEP

print("Simulation finished!")


# =========================================================
# EXPORT TO CSV
# =========================================================

csv_file = "smart_home_dataset.csv"

with open(csv_file, mode="w", newline="", encoding="utf-8") as file:

    writer = csv.DictWriter(
        file,
        fieldnames=[
            "timestamp",
            "person",
            "room",
            "activity",
            "device",
            "action"
        ]
    )

    writer.writeheader()

    for row in simulation_data:
        writer.writerow(row)

print(f"Dataset exported to {csv_file}")


# =========================================================
# DATASET STATS
# =========================================================

print(f"Total records generated: {len(simulation_data)}")


# =========================================================
# SAMPLE OUTPUT
# =========================================================

print("\nFirst 20 rows:\n")

for row in simulation_data[:20]:
    print(row)