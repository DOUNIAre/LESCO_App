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
        for rule in all_rules:
            # Check if this rule applies to the device we are moving
            if rule.condition_device_type == target_device_type:
                # Look if the 'forbidden' device is already ON in that room
                conflict = db.query(models.SmartDevice).filter(
                    models.SmartDevice.room_id == room_id,
                    models.SmartDevice.device_type == rule.forbidden_device_type,
                    models.SmartDevice.status == True
                ).first()

                if conflict:
                    return False, f"Rule '{rule.name}' (Priority {rule.priority}): Cannot turn on {target_device_type} while {rule.forbidden_device_type} is running."

    return True, "Safe"