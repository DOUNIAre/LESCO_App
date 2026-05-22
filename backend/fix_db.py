import os
from sqlalchemy import text
from database import engine
from dotenv import load_dotenv
import models

load_dotenv()

def fix_database():
    print("Starting Database Fix...")
    
    # 1. Add is_verified to users table if it doesn't exist
    print("Checking for 'is_verified' column in 'users' table...")
    try:
        with engine.connect() as conn:
            conn.execute(text("ALTER TABLE users ADD COLUMN is_verified BOOLEAN DEFAULT FALSE;"))
            conn.commit()
        print("Added 'is_verified' column successfully.")
    except Exception as e:
        if "already exists" in str(e):
            print("Column 'is_verified' already exists.")
        else:
            print(f"Error adding column: {e}")

    # 1b. Add created_by to smart_devices table if it doesn't exist
    print("Checking for 'created_by' column in 'smart_devices' table...")
    try:
        with engine.connect() as conn:
            conn.execute(text("ALTER TABLE smart_devices ADD COLUMN created_by INTEGER REFERENCES users(id);"))
            conn.commit()
        print("Added 'created_by' column successfully.")
    except Exception as e:
        if "already exists" in str(e):
            print("Column 'created_by' already exists.")
        else:
            print(f"Error adding column: {e}")
    
    # 2. Ensure all other tables (like verification_codes and notifications) are created
    print("Creating any missing tables...")
    models.Base.metadata.create_all(bind=engine)
    print("Tables check complete.")

    # 3. Seed mutual exclusion rules if empty
    print("Seeding mutual exclusion rules...")
    from database import SessionLocal
    db = SessionLocal()
    try:
        existing_rules = db.query(models.Rule).count()
        if existing_rules == 0:
            rule1 = models.Rule(
                name="HVAC Safety AC",
                condition_device_type="AC",
                forbidden_device_type="HEATER",
                priority=1
            )
            rule2 = models.Rule(
                name="HVAC Safety Heater",
                condition_device_type="HEATER",
                forbidden_device_type="AC",
                priority=1
            )
            db.add(rule1)
            db.add(rule2)
            db.commit()
            print("Successfully seeded mutual exclusion rules!")
        else:
            print(f"Rules table already has {existing_rules} entries.")
    except Exception as e:
        print(f"Error seeding rules: {e}")
    finally:
        db.close()

if __name__ == "__main__":
    fix_database()
