from sqlalchemy.orm import Session
import models

def check_all_rules(db: Session, room_id: int, target_device_type: str, target_status: bool):
    """
    Checks the proposed action against all rules in the database, 
    sorted by priority.
    """
    # 1. Get all rules from the DB sorted by Priority (1, 2, 3...)
    all_rules = db.query(models.Rule).order_by(models.Rule.priority.asc()).all()

    # 2. Only check rules if we are trying to turn a device ON
    if target_status == True:
        # Get all currently active devices in this room to check in Python
        active_devices = db.query(models.SmartDevice).filter(
            models.SmartDevice.room_id == room_id,
            models.SmartDevice.status == True
        ).all()

        for rule in all_rules:
            # Check if this rule applies to the device we are moving (case-insensitive)
            if rule.condition_device_type.upper() == target_device_type.upper():
                # Check if the forbidden device is active in this room
                for dev in active_devices:
                    if dev.device_type.upper() == rule.forbidden_device_type.upper():
                        return False, f"Cannot turn on {target_device_type.upper()} while {rule.forbidden_device_type.upper()} is on."

    return True, "Safe"