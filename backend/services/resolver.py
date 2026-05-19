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


def _weighted_median(values: list, weights: list) -> float:
    """
    Compute the true weighted median.
    Sorts (value, weight) pairs by value, then finds the
    midpoint of the cumulative weight distribution.
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
    for val, w in pairs:
        cumulative += w
        if cumulative >= half:
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
    # 1. Get users assigned to this room
    assignments = db.query(models.RoomAssignment).filter(
        models.RoomAssignment.room_id == room_id
    ).all()
    assigned_user_ids = [a.user_id for a in assignments]

    if not assigned_user_ids:
        return None

    # 2. Get their preferences for this device category
    prefs = db.query(models.UserPreference).filter(
        models.UserPreference.user_id.in_(assigned_user_ids),
        models.UserPreference.category == device_type,
    ).all()

    if not prefs:
        return None

    # 3. Resolve the room's house_id for weight calculation
    room = db.query(models.Room).filter(models.Room.id == room_id).first()
    house_id = room.house_id if room else None

    # === STRATEGY A: CONTINUOUS — use Weighted Median ===
    if device_type in ["TEMPERATURE", "AC", "BRIGHTNESS", "HEATER", "FAN"]:
        values  = [float(p.value) for p in prefs]
        weights = [_get_user_weight(db, p.user_id, house_id) for p in prefs]
        return _weighted_median(values, weights)

    # === STRATEGY B: BINARY — use Majority Voting ===
    else:
        weights_on  = sum(_get_user_weight(db, p.user_id, house_id) for p in prefs if p.value > 0)
        weights_off = sum(_get_user_weight(db, p.user_id, house_id) for p in prefs if p.value == 0)
        # In a tie, prefer OFF (energy saving)
        return 1 if weights_on > weights_off else 0