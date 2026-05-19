import os
from sqlalchemy import text
from database import engine
from dotenv import load_dotenv
import models

load_dotenv()

def fix_database():
    print("Starting Database Fix...")
    
    with engine.connect() as conn:
        # 1. Add is_verified to users table if it doesn't exist
        print("Checking for 'is_verified' column in 'users' table...")
        try:
            conn.execute(text("ALTER TABLE users ADD COLUMN is_verified BOOLEAN DEFAULT FALSE;"))
            conn.commit()
            print("Added 'is_verified' column successfully.")
        except Exception as e:
            print(f"Checking error: {e}")
            if "already exists" in str(e):
                print("Column 'is_verified' already exists.")
            else:
                print(f"Error adding column: {e}")
        
        # 2. Ensure all other tables (like verification_codes and notifications) are created
        print("Creating any missing tables...")
        models.Base.metadata.create_all(bind=engine)
        print("Tables check complete.")

if __name__ == "__main__":
    fix_database()
