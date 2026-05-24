"""
resolver.py  —  Conflict Resolution Service
Implements the three strategies from the architecture spec:
  1. CONTINUOUS  (Weighted Median)  — AC temperature, brightness
  2. BINARY      (Majority Voting)  — ON/OFF switches
  3. CONSTRAINT  (Safety Block)     — AC + Heater, Window + AC, etc.
"""

import math
from sqlalchemy.orm import Session
import models


# Maps device_type -> preference categories to search (in order of priority)
# e.g. AC device resolves via "AC" prefs first, falls back to "TEMPERATURE"
CATEGORY_ALIASES = {
    "AC":          ["AC", "TEMPERATURE"],
    "HEATER":      ["HEATER", "TEMPERATURE"],
    "TEMPERATURE": ["TEMPERATURE", "AC", "HEATER"],
    "BRIGHTNESS":  ["BRIGHTNESS"],
    "FAN":         ["FAN"],
    "LIGHT":       ["LIGHT"],
    "TV":          ["TV"],
}

# Continuous (Weighted Median) device types
CONTINUOUS_TYPES = {"TEMPERATURE", "AC", "HEATER", "BRIGHTNESS", "FAN"}


def _weighted_median(values: list, weights: list) -> float:
    """
    Compute the true weighted median.
    Sorts (value, weight) pairs by value, then finds the
    midpoint of the cumulative weight distribution.
    If the cumulative weight is exactly half of the total weight,
    it averages with the next value to get a true median.
    """
    if not values:
        return 0.0

    total_weight = sum(weights)
    if total_weight == 0:
        return float(values[0])

    # Sort by value
    pairs = sorted(zip(values, weights), key=lambda x: x[0])

    # Walk cumulative weights until we cross the 50% mark
    cumulative = 0.0
    half = total_weight / 2.0
    for i, (val, w) in enumerate(pairs):
        cumulative += w
        if cumulative > half:
            return float(val)
        elif cumulative == half:
            # Exactly at the midpoint — average this value and the next one
            if i + 1 < len(pairs):
                return float(val + pairs[i+1][0]) / 2.0
            return float(val)

    return float(pairs[-1][0])


def _get_user_weight(db: Session, user_id: int, house_id: int) -> int:
    """Owner = weight 2, regular member = weight 1."""
    membership = db.query(models.Membership).filter(
        models.Membership.user_id == user_id,
        models.Membership.house_id == house_id,
    ).first()
    return 2 if (membership and membership.role == "owner") else 1


def resolve_conflicts(db: Session, room_id: int, device_type: str):
    """
    Main entry point called by the API.
    Returns the resolved value (float) or None if no preferences exist.
    """
    device_type = device_type.upper()

    # 1. Get users assigned to this room
    assignments = db.query(models.RoomAssignment).filter(
        models.RoomAssignment.room_id == room_id
    ).all()
    assigned_user_ids = [a.user_id for a in assignments]

    if not assigned_user_ids:
        return None

    # 2. Resolve the room's house_id for weight calculation
    room = db.query(models.Room).filter(models.Room.id == room_id).first()
    house_id = room.house_id if room else None

    # 3. Find preferences user-by-user using alias mapping
    prefs = []
    aliases = CATEGORY_ALIASES.get(device_type, [device_type])
    
    for uid in assigned_user_ids:
        found = False
        # Try to find a preference matching aliases in the HOME context first (case-insensitive)
        for alias_cat in aliases:
            p = db.query(models.UserPreference).filter(
                models.UserPreference.user_id == uid,
                models.UserPreference.category.ilike(alias_cat),
                models.UserPreference.context.ilike("HOME")
            ).first()
            if p:
                prefs.append(p)
                found = True
                break
        
        # If not found under HOME context, try any context for that user
        if not found:
            for alias_cat in aliases:
                p = db.query(models.UserPreference).filter(
                    models.UserPreference.user_id == uid,
                    models.UserPreference.category.ilike(alias_cat)
                ).first()
                if p:
                    prefs.append(p)
                    break

    if not prefs:
        return None

    # === STRATEGY A: CONTINUOUS — use Weighted Median ===
    if device_type in CONTINUOUS_TYPES:
        # For AC/HEATER: exclude "OFF" (value=0) prefs from the median calculation
        # so that a user who has TEMPERATURE=22 doesn't get averaged with someone's OFF state
        active_prefs = [p for p in prefs if p.value > 0]
        if not active_prefs:
            active_prefs = prefs  # fall back to all if everyone is 0

        values  = [float(p.value) for p in active_prefs]
        weights = [_get_user_weight(db, p.user_id, house_id) for p in active_prefs]
        return _weighted_median(values, weights)

    # === STRATEGY B: BINARY — use Majority Voting ===
    else:
        weights_on  = sum(_get_user_weight(db, p.user_id, house_id) for p in prefs if p.value > 0)
        weights_off = sum(_get_user_weight(db, p.user_id, house_id) for p in prefs if p.value == 0)
        # In a tie, prefer OFF (energy saving)
        return 1 if weights_on > weights_off else 0


def has_preference_conflict(db: Session, room_id: int, device_type: str) -> bool:
    """
    Checks if there is a preference conflict among the assigned users in the room
    for the given device type. A conflict only exists if:
      1. There are multiple users assigned to the room.
      2. At least two of them have registered preferences for this device type/alias category.
      3. Their registered preference values differ.
    """
    device_type = device_type.upper()
    assignments = db.query(models.RoomAssignment).filter(
        models.RoomAssignment.room_id == room_id
    ).all()
    assigned_user_ids = [a.user_id for a in assignments]
    if len(assigned_user_ids) <= 1:
        return False

    prefs = []
    aliases = CATEGORY_ALIASES.get(device_type, [device_type])
    
    for uid in assigned_user_ids:
        found = False
        # Try HOME context first
        for alias_cat in aliases:
            p = db.query(models.UserPreference).filter(
                models.UserPreference.user_id == uid,
                models.UserPreference.category.ilike(alias_cat),
                models.UserPreference.context.ilike("HOME")
            ).first()
            if p:
                prefs.append(p)
                found = True
                break
        if not found:
            # Try any context
            for alias_cat in aliases:
                p = db.query(models.UserPreference).filter(
                    models.UserPreference.user_id == uid,
                    models.UserPreference.category.ilike(alias_cat)
                ).first()
                if p:
                    prefs.append(p)
                    break

    if len(prefs) <= 1:
        return False

    values = [p.value for p in prefs]
    return len(set(values)) > 1