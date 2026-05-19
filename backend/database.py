import os
import urllib.parse
from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

# 1. Load the .env file
load_dotenv()

# 2. Get the variables from the .env
# We use .get() to avoid errors if the variable is missing
password = os.getenv("DB_PASSWORD")
user = os.getenv("DB_USER")
host = os.getenv("DB_HOST")
port = os.getenv("DB_PORT")
db_name = os.getenv("DB_NAME")

# 3. Check if we actually found the password
if password is None:
    raise ValueError("Could not find DB_PASSWORD in .env file. Check the file location!")

# 4. Safely encode the '@' symbol to '%40'
safe_password = urllib.parse.quote_plus(password)

# 5. Build the final URL
SQLALCHEMY_DATABASE_URL = f"postgresql://{user}:{safe_password}@{host}:{port}/{db_name}"

# Standard SQLAlchemy setup
engine = create_engine(SQLALCHEMY_DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()