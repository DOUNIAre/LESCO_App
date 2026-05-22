from sqlalchemy import Column, Integer, String, ForeignKey, Boolean, DateTime, Enum, Float
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base
import datetime

Base = declarative_base()

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String)
    email = Column(String, unique=True, index=True)
    password = Column(String) 
    is_verified = Column(Boolean, default=False)
    
    # Relationships: One user can have many preferences, memberships,...
    memberships = relationship("Membership", back_populates="user")
    preferences = relationship("UserPreference", back_populates="user")

class VerificationCode(Base):
    __tablename__ = "verification_codes"

    id = Column(Integer, primary_key=True, index=True)
    email = Column(String, index=True)
    code = Column(String)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

class House(Base):
    __tablename__ = "houses"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String)
    invite_code = Column(String, unique=True)

    # Relationships
    members = relationship("Membership", back_populates="house")
    rooms = relationship("Room", back_populates="house")

class Membership(Base):
    __tablename__ = "memberships"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"))
    house_id = Column(Integer, ForeignKey("houses.id"))
    role = Column(String) # e.g., 'owner', 'member'

    user = relationship("User", back_populates="memberships")
    house = relationship("House", back_populates="members")

class Room(Base):
    __tablename__ = "rooms"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String)
    house_id = Column(Integer, ForeignKey("houses.id"))
    room_type = Column(String) # "shared" or "personal"

    house = relationship("House", back_populates="rooms")
    devices = relationship("SmartDevice", back_populates="room")

class SmartDevice(Base):
    __tablename__ = "smart_devices"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String)
    device_type = Column(String) # e.g., "AC", "Light", "Heater"
    status = Column(Boolean, default=False) # True = ON, False = OFF
    value = Column(Integer, default=0) # e.g., 22 degrees or 70% brightness
    room_id = Column(Integer, ForeignKey("rooms.id"))
    created_by = Column(Integer, ForeignKey("users.id"), nullable=True)
    
    room = relationship("Room", back_populates="devices")
    creator = relationship("User")

class UserPreference(Base):
    __tablename__ = "user_preferences"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"))
    category = Column(String) # e.g., "TEMPERATURE"
    value = Column(Integer)   # e.g., 22
    time = Column(DateTime, default=datetime.datetime.utcnow)
    context = Column(String)  # e.g., "AWAY", "HOME", "SLEEP"

    user = relationship("User", back_populates="preferences")

class Environment(Base):
    __tablename__ = "environment"
    id = Column(Integer, primary_key=True, index=True)
    house_id = Column(Integer, ForeignKey("houses.id"))
    temperature = Column(Integer)
    weather = Column(String) # e.g., "Sunny", "Rainy" 
    timestamp = Column(DateTime, default=datetime.datetime.utcnow)

class Recommendation(Base):
    __tablename__ = "recommendations"
    id = Column(Integer, primary_key=True, index=True)
    house_id = Column(Integer, ForeignKey("houses.id"))
    device_id = Column(Integer, ForeignKey("smart_devices.id"))
    content = Column(String) # The text: "Turn off AC to save energy"
    proposed_value = Column(Integer)
    confidence_score = Column(Float)
    reason = Column(String)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    # Links for the feedback loop
    feedback = relationship("UserFeedback", back_populates="recommendation")

class UserFeedback(Base):
    __tablename__ = "user_feedback"
    id = Column(Integer, primary_key=True, index=True)
    recommendation_id = Column(Integer, ForeignKey("recommendations.id"))
    user_id = Column(Integer, ForeignKey("users.id"))
    response = Column(Boolean) # True = Accepted, False = Denied
    timestamp = Column(DateTime, default=datetime.datetime.utcnow)

    recommendation = relationship("Recommendation", back_populates="feedback")

class Notification(Base):
    __tablename__ = "notifications"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"))
    message = Column(String)
    is_read = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

class DeviceActionHistory(Base):
    __tablename__ = "device_action_history"

    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(Integer, ForeignKey("smart_devices.id"))
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True) # Null if AI did it
    
    action_type = Column(String) # e.g., "TOGGLE", "SET_VALUE" 
    new_value = Column(Integer)  # The value it was changed TO
    
    # Very important: How did this happen? "MANUAL" or "AI_SUGGESTION"
    origin = Column(String, default="MANUAL") 
    
    timestamp = Column(DateTime, default=datetime.datetime.utcnow)

    # Relationships
    device = relationship("SmartDevice")
    user = relationship("User")

class Rule(Base):
    __tablename__ = "rules"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String)
    condition_device_type = Column(String) # e.g., "AC"
    forbidden_device_type = Column(String) # e.g., "Heater"
    priority = Column(Integer, default=1) # 1 = Highest, 3 = Lowest

class RoomAssignment(Base):
    __tablename__ = "room_assignments"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"))
    room_id = Column(Integer, ForeignKey("rooms.id"))
    
    

    user = relationship("User")
    room = relationship("Room")
