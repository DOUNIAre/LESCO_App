"""
main.py  —  LESCO Smart Home System — FastAPI Backend
Clean, production-ready implementation with:
  - JWT Authentication
  - Room-Assignment-based device authority
  - Rule-based safety checks
  - Weighted Median conflict resolution
  - PPO AI recommendation loop
"""

import os
os.environ["OPENBLAS_NUM_THREADS"] = "1"
os.environ["MKL_NUM_THREADS"] = "1"
os.environ["OMP_NUM_THREADS"] = "1"

from fastapi import FastAPI, Depends, HTTPException, BackgroundTasks
from sqlalchemy.orm import Session
from typing import List
import random
import string
import sys

# Internal imports
import models
import schemas
import security
from database import engine, get_db
from services import resolver, rules, weather, mail_service
from fastapi.security import OAuth2PasswordRequestForm

# AI imports
from ai.adapters.backend_adapter import SQLAlchemyAdapter
from ai.ppo_recommender import PPORecommender

# Initialize DB tables
models.Base.metadata.create_all(bind=engine)

from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="LESCO Smart Home API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Per-house recommender cache: {house_id: PPORecommender}
_recommenders: dict = {}

def _get_recommender(house_id: int, db: Session) -> PPORecommender:
    """Get or create a cached PPORecommender for a house."""
    if house_id not in _recommenders:
        adapter = SQLAlchemyAdapter(db)
        _recommenders[house_id] = PPORecommender(adapter, house_id)
    return _recommenders[house_id]


# ── Root ─────────────────────────────────────────────────────────────────────

@app.get("/")
def read_root():
    return {"message": "LESCO Smart Home API is Online", "status": "Running", "docs": "/docs"}


# ── Helpers ──────────────────────────────────────────────────────────────────

def save_weather_to_db(db: Session, house_id: int, temp: int, weather_desc: str):
    new_env = models.Environment(house_id=house_id, temperature=int(temp), weather=weather_desc)
    db.add(new_env)
    db.commit()
    db.refresh(new_env)
    return new_env


# ── AUTH ─────────────────────────────────────────────────────────────────────

@app.post("/login")
def login(form_data: OAuth2PasswordRequestForm = Depends(), db: Session = Depends(get_db)):
    user = db.query(models.User).filter(models.User.email == form_data.username).first()
    if not user or not security.verify_password(form_data.password, user.password):
        raise HTTPException(status_code=401, detail="Invalid Email or Password")

    access_token = security.create_access_token(data={"user_id": user.id})
    user_out = {
        "id": user.id,
        "name": user.name,
        "email": user.email,
        "memberships": [{"house_id": m.house_id, "role": m.role} for m in user.memberships],
    }
    return {"access_token": access_token, "token_type": "bearer", "user": user_out}


@app.get("/users/me/", response_model=schemas.UserOut)
def get_my_profile(current_user: models.User = Depends(security.get_current_user)):
    return current_user


@app.put("/users/me/", response_model=schemas.UserOut)
def update_my_profile(
    payload: schemas.UserUpdate,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    if payload.name is not None:
        current_user.name = payload.name
    if payload.email is not None:
        if payload.email != current_user.email:
            existing = db.query(models.User).filter(models.User.email == payload.email).first()
            if existing:
                raise HTTPException(status_code=400, detail="Email already registered")
            current_user.email = payload.email
    db.commit()
    db.refresh(current_user)
    return current_user


# ── USER & HOUSE MANAGEMENT ──────────────────────────────────────────────────

@app.post("/users/", response_model=schemas.UserOut)
async def create_user(
    user: schemas.UserCreate, 
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db)
):
    try:
        if db.query(models.User).filter(models.User.email == user.email).first():
            raise HTTPException(status_code=400, detail="Email already registered")
        
        new_user = models.User(
            name=user.name,
            email=user.email,
            password=security.get_password_hash(user.password)
        )
        db.add(new_user)
        db.commit()
        db.refresh(new_user)

        # Generate and send verification code
        code = "".join(random.choices(string.digits, k=6))
        db.add(models.VerificationCode(email=user.email, code=code))
        db.commit()
        
        print(f"DEBUG SIGNUP VERIFICATION: Email={user.email}, Code={code}")
        
        # Send email in the background
        try:
            background_tasks.add_task(mail_service.send_verification_email, user.email, code)
        except Exception as mail_err:
            print(f"Background task setup failed: {mail_err}")

        return new_user
    except HTTPException:
        db.rollback()
        raise
    except Exception as e:
        db.rollback()
        print(f"CRITICAL ERROR IN SIGNUP: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/users/verify/")
def verify_email(email: str, code: str, db: Session = Depends(get_db)):
    v_code = db.query(models.VerificationCode).filter(
        models.VerificationCode.email == email,
        models.VerificationCode.code == code
    ).first()
    
    if not v_code:
        raise HTTPException(status_code=400, detail="Invalid verification code")
    
    user = db.query(models.User).filter(models.User.email == email).first()
    if user:
        user.is_verified = True
        db.delete(v_code)
        db.commit()
    return {"message": "Email verified successfully"}


@app.post("/houses/", response_model=schemas.HouseOut)
def create_house(
    house: schemas.HouseCreate,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    invite_code = "".join(random.choices(string.ascii_uppercase + string.digits, k=6))
    new_house = models.House(name=house.name, invite_code=invite_code)
    db.add(new_house)
    db.commit()
    db.refresh(new_house)

    # Make the creator an owner
    membership = models.Membership(user_id=current_user.id, house_id=new_house.id, role="owner")
    db.add(membership)
    db.commit()
    return new_house


@app.post("/houses/join/")
def join_house(request: schemas.HouseJoin, db: Session = Depends(get_db)):
    house = db.query(models.House).filter(models.House.invite_code == request.invite_code).first()
    if not house:
        raise HTTPException(status_code=404, detail="Invalid invite code")

    existing = db.query(models.Membership).filter(
        models.Membership.user_id == request.user_id,
        models.Membership.house_id == house.id
    ).first()
    if existing:
        return {"message": "Already a member", "house_id": house.id}

    membership = models.Membership(user_id=request.user_id, house_id=house.id, role="member")
    db.add(membership)
    db.commit()
    return {"message": f"Joined house '{house.name}'", "house_id": house.id}


@app.get("/houses/{house_id}/members", response_model=List[schemas.HouseMemberOut])
def get_house_members(
    house_id: int,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    # Verify current user belongs to the house
    user_mem = db.query(models.Membership).filter(
        models.Membership.user_id == current_user.id,
        models.Membership.house_id == house_id
    ).first()
    if not user_mem:
        raise HTTPException(status_code=403, detail="Not a member of this house")

    # Fetch all members
    memberships = db.query(models.Membership).filter(
        models.Membership.house_id == house_id
    ).all()

    out = []
    for m in memberships:
        user = db.query(models.User).filter(models.User.id == m.user_id).first()
        if user:
            out.append({
                "id": user.id,
                "name": user.name,
                "email": user.email,
                "role": m.role
            })
    return out


# ── ROOMS ────────────────────────────────────────────────────────────────────

@app.post("/houses/{house_id}/rooms/", response_model=schemas.RoomOut)
def create_room(
    house_id: int,
    room: schemas.RoomCreate,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    house = db.query(models.House).filter(models.House.id == house_id).first()
    if not house:
        raise HTTPException(status_code=404, detail="House not found")
    new_room = models.Room(name=room.name, house_id=house_id, room_type=room.room_type)
    db.add(new_room)
    db.commit()
    db.refresh(new_room)

    # Auto-assign the creator to the room
    assignment = models.RoomAssignment(user_id=current_user.id, room_id=new_room.id)
    db.add(assignment)
    db.commit()
    return new_room


@app.get("/houses/{house_id}/rooms/", response_model=List[schemas.RoomOut])
def get_rooms(
    house_id: int, 
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    # Check if user is owner
    membership = db.query(models.Membership).filter(
        models.Membership.user_id == current_user.id,
        models.Membership.house_id == house_id
    ).first()
    is_owner = (membership and membership.role == "owner")
    
    if is_owner:
        return db.query(models.Room).filter(models.Room.house_id == house_id).all()
    else:
        assignments = db.query(models.RoomAssignment).filter(
            models.RoomAssignment.user_id == current_user.id
        ).all()
        assigned_room_ids = {a.room_id for a in assignments}
        return db.query(models.Room).filter(
            models.Room.house_id == house_id,
            (models.Room.id.in_(assigned_room_ids) | (models.Room.room_type == "shared"))
        ).all()




# ── DEVICES ──────────────────────────────────────────────────────────────────

@app.post("/rooms/{room_id}/devices/", response_model=schemas.DeviceOut)
def add_device(
    room_id: int,
    device: schemas.DeviceCreate,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    room = db.query(models.Room).filter(models.Room.id == room_id).first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
        
    membership = db.query(models.Membership).filter(
        models.Membership.user_id == current_user.id,
        models.Membership.house_id == room.house_id
    ).first()
    if not membership:
        raise HTTPException(status_code=403, detail="Access Denied: You are not a member of this house.")
        
    is_owner = membership.role == "owner"
    if not is_owner:
        if room.room_type.lower() != "shared":
            assignment = db.query(models.RoomAssignment).filter(
                models.RoomAssignment.user_id == current_user.id,
                models.RoomAssignment.room_id == room.id
            ).first()
            if not assignment:
                raise HTTPException(status_code=403, detail="Access Denied: You cannot add devices to personal rooms you are not assigned to.")

    new_device = models.SmartDevice(
        name=device.name,
        device_type=device.device_type,
        room_id=room_id,
        status=False,
        value=0
    )
    db.add(new_device)
    db.commit()
    db.refresh(new_device)
    return new_device


@app.get("/rooms/{room_id}/devices/", response_model=List[schemas.DeviceOut])
def get_devices(
    room_id: int, 
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    return db.query(models.SmartDevice).filter(models.SmartDevice.room_id == room_id).all()
@app.get("/houses/{house_id}/devices/", response_model=List[schemas.DeviceOut])
def get_all_house_devices(
    house_id: int, 
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    membership = db.query(models.Membership).filter(
        models.Membership.user_id == current_user.id,
        models.Membership.house_id == house_id
    ).first()
    is_owner = (membership and membership.role == "owner")

    if is_owner:
        devices = db.query(models.SmartDevice).join(models.Room).filter(
            models.Room.house_id == house_id
        ).all()
    else:
        assignments = db.query(models.RoomAssignment).filter(
            models.RoomAssignment.user_id == current_user.id
        ).all()
        assigned_room_ids = {a.room_id for a in assignments}
        devices = db.query(models.SmartDevice).join(models.Room).filter(
            models.Room.house_id == house_id,
            (models.Room.id.in_(assigned_room_ids) | (models.Room.room_type == "shared"))
        ).all()
    return devices


# ── CONTROL & LOGIC ──────────────────────────────────────────────────────────

@app.post("/devices/{device_id}/toggle")
def toggle_device(
    device_id: int,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    device = db.query(models.SmartDevice).filter(models.SmartDevice.id == device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    # Authority check: user must be assigned to the device's room (or be the owner of the house)
    room = db.query(models.Room).filter(models.Room.id == device.room_id).first()
    is_owner = False
    if room:
        membership = db.query(models.Membership).filter(
            models.Membership.user_id == current_user.id,
            models.Membership.house_id == room.house_id,
            models.Membership.role == "owner"
        ).first()
        is_owner = membership is not None

    if not is_owner:
        if room and room.room_type == "shared":
            # Any member of the house can control devices in a shared room
            membership = db.query(models.Membership).filter(
                models.Membership.user_id == current_user.id,
                models.Membership.house_id == room.house_id
            ).first()
            if not membership:
                raise HTTPException(status_code=403, detail="Access Denied: You are not a member of this house.")
        else:
            assignment = db.query(models.RoomAssignment).filter(
                models.RoomAssignment.user_id == current_user.id,
                models.RoomAssignment.room_id == device.room_id
            ).first()
            if not assignment:
                raise HTTPException(status_code=403, detail="Access Denied: You are not assigned to this personal room.")

    # Conflict detection: if >1 user in room, trigger resolver
    user_count = db.query(models.RoomAssignment).filter(
        models.RoomAssignment.room_id == device.room_id
    ).count()
    if user_count > 1:
        return {
            "status": "conflict_detected",
            "message": f"There are {user_count} users in this room. Use 'Apply Logic' to resolve."
        }

    # Safety rule check
    is_safe, msg = rules.check_all_rules(db, device.room_id, device.device_type, not device.status)
    if not is_safe:
        raise HTTPException(status_code=400, detail=msg)

    # Execute & log
    device.status = not device.status
    history = models.DeviceActionHistory(
        device_id=device.id, user_id=current_user.id,
        action_type="TOGGLE", new_value=1 if device.status else 0, origin="MANUAL"
    )
    db.add(history)
    db.commit()
    return {"status": "success", "new_status": device.status}


@app.post("/devices/{device_id}/value")
def set_device_value(
    device_id: int,
    payload: dict,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    device = db.query(models.SmartDevice).filter(models.SmartDevice.id == device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    room = db.query(models.Room).filter(models.Room.id == device.room_id).first()
    is_owner = False
    if room:
        membership = db.query(models.Membership).filter(
            models.Membership.user_id == current_user.id,
            models.Membership.house_id == room.house_id,
            models.Membership.role == "owner"
        ).first()
        is_owner = membership is not None

    if not is_owner:
        if room and room.room_type == "shared":
            membership = db.query(models.Membership).filter(
                models.Membership.user_id == current_user.id,
                models.Membership.house_id == room.house_id
            ).first()
            if not membership:
                raise HTTPException(status_code=403, detail="Access Denied: You are not a member of this house.")
        else:
            assignment = db.query(models.RoomAssignment).filter(
                models.RoomAssignment.user_id == current_user.id,
                models.RoomAssignment.room_id == device.room_id
            ).first()
            if not assignment:
                raise HTTPException(status_code=403, detail="Access Denied: You are not assigned to this personal room.")

    # Conflict detection: if >1 user in room, trigger resolver
    user_count = db.query(models.RoomAssignment).filter(
        models.RoomAssignment.room_id == device.room_id
    ).count()
    if user_count > 1:
        return {
            "status": "conflict_detected",
            "message": f"There are {user_count} users in this room. Use 'Apply Logic' to resolve."
        }

    val = int(payload.get("value", 0))

    # Safety rule check
    is_safe, msg = rules.check_all_rules(db, device.room_id, device.device_type, device.status)
    if not is_safe:
        raise HTTPException(status_code=400, detail=msg)

    # Execute & log
    device.value = val
    history = models.DeviceActionHistory(
        device_id=device.id, user_id=current_user.id,
        action_type="SET_VALUE", new_value=val, origin="MANUAL"
    )
    db.add(history)
    db.commit()
    return {"status": "success", "new_value": device.value}


@app.get("/rooms/{room_id}/apply-logic/{category}")
def apply_conflict_resolution(
    room_id: int,
    category: str,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    room = db.query(models.Room).filter(models.Room.id == room_id).first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")

    final_value = resolver.resolve_conflicts(db, room_id, category)
    if final_value is None:
        return {"message": "No preferences found for this category"}

    # Apply resolved value to all matching devices in the room (no owner_id check)
    devices = db.query(models.SmartDevice).filter(
        models.SmartDevice.room_id == room_id,
        models.SmartDevice.device_type == category
    ).all()

    for d in devices:
        d.value = int(final_value)
        if category in ["LIGHT", "AC", "HEATER", "FAN"]:
            d.status = True if final_value > 0 else False

    assignments = db.query(models.RoomAssignment).filter(models.RoomAssignment.room_id == room_id).all()
    assigned_user_ids = [a.user_id for a in assignments]

    # Notify all room members about the resolution
    for uid in assigned_user_ids:
        notif = models.Notification(
            user_id=uid,
            message=f"Conflict resolved in room {room.name}: {category} set to {final_value}"
        )
        db.add(notif)

    db.commit()
    return {"resolved_value": final_value, "devices_updated": len(devices)}


# ── AI & ENVIRONMENT ─────────────────────────────────────────────────────────

@app.get("/houses/{house_id}/recommendation/", response_model=schemas.RecommendationOut)
def get_ai_recommendation(
    house_id: int,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    # 1. Fetch live weather and save snapshot
    online_data = weather.fetch_online_weather()
    temp = online_data["temp"] if online_data["success"] else 25
    desc = online_data["weather"] if online_data["success"] else "Clear"
    save_weather_to_db(db, house_id, temp, desc)

    # 2. Get user role and live devices (filtered by room assignment for non-owners)
    membership = db.query(models.Membership).filter(
        models.Membership.user_id == current_user.id,
        models.Membership.house_id == house_id
    ).first()
    is_owner = (membership and membership.role == "owner")

    rooms = db.query(models.Room).filter(models.Room.house_id == house_id).all()
    if not is_owner:
        assignments = db.query(models.RoomAssignment).filter(
            models.RoomAssignment.user_id == current_user.id
        ).all()
        assigned_room_ids = {a.room_id for a in assignments}
        rooms = [r for r in rooms if r.id in assigned_room_ids or r.room_type.lower() == "shared"]

    all_devices = []
    for room in rooms:
        all_devices.extend(room.devices)

    # 3. Get user preferences
    members = db.query(models.Membership).filter(models.Membership.house_id == house_id).all()
    user_ids = [m.user_id for m in members]
    prefs = db.query(models.UserPreference).filter(
        models.UserPreference.user_id.in_(user_ids)
    ).all()

    # 4. Call PPO recommender
    recommender = _get_recommender(house_id, db)
    ai_result = recommender.generate_recommendation(
        prefs=prefs,
        env_data={"outdoor_temp": temp, "condition": desc},
        devices=all_devices
    )

    # 5. Save recommendation to DB for feedback loop
    # FIX: include device_id from the recommender last_recommendation if available
    last_rec_state = getattr(recommender, 'last_recommendation', None)
    device_id_for_rec = last_rec_state.get('device_id') if last_rec_state else None

    new_rec = models.Recommendation(
        house_id=house_id,
        device_id=device_id_for_rec,
        content=ai_result["recommendation"],
        proposed_value=ai_result["action"],
        confidence_score=ai_result["confidence"],
        reason=ai_result["reason"]
    )
    db.add(new_rec)
    db.commit()
    db.refresh(new_rec)
    return new_rec


@app.post("/feedback/")
def submit_feedback(fb: schemas.FeedbackCreate, db: Session = Depends(get_db)):
    # Find the recommendation to know which house this was for
    rec = db.query(models.Recommendation).filter(
        models.Recommendation.id == fb.recommendation_id
    ).first()
    if not rec:
        raise HTTPException(status_code=404, detail="Recommendation not found")

    # If the recommendation affects a specific device, verify user authority
    if rec.device_id:
        device = db.query(models.SmartDevice).filter(models.SmartDevice.id == rec.device_id).first()
        if device:
            room = db.query(models.Room).filter(models.Room.id == device.room_id).first()
            if room:
                membership = db.query(models.Membership).filter(
                    models.Membership.user_id == fb.user_id,
                    models.Membership.house_id == room.house_id,
                    models.Membership.role == "owner"
                ).first()
                is_owner = membership is not None
                
                if not is_owner:
                    if room.room_type.lower() != "shared":
                        assignment = db.query(models.RoomAssignment).filter(
                            models.RoomAssignment.user_id == fb.user_id,
                            models.RoomAssignment.room_id == room.id
                        ).first()
                        if not assignment:
                            raise HTTPException(
                                status_code=403, 
                                detail="Access Denied: You cannot interact with recommendations for rooms you are not assigned to."
                            )

    # Save feedback record
    new_fb = models.UserFeedback(
        recommendation_id=fb.recommendation_id,
        user_id=fb.user_id,
        response=fb.response
    )
    db.add(new_fb)

    # Feed back into the AI learning loop
    if rec and rec.house_id in _recommenders:
        _recommenders[rec.house_id].update_from_feedback(fb.response)

    # Apply recommendation to the actual device if accepted
    if fb.response and rec and rec.device_id:
        device = db.query(models.SmartDevice).filter(models.SmartDevice.id == rec.device_id).first()
        if device:
            content_lower = rec.content.lower()
            if "turn on" in content_lower:
                device.status = True
            elif "turn off" in content_lower:
                device.status = False
            
            if rec.proposed_value is not None:
                device.value = rec.proposed_value
                if device.device_type in ["AC", "Heater"] and rec.proposed_value > 0:
                    device.status = True
            db.add(device)
            
            history = models.DeviceActionHistory(
                device_id=device.id,
                user_id=fb.user_id,
                action_type="RECOMMENDATION_ACCEPT",
                new_value=device.value if device.device_type in ["AC", "Heater"] else (1 if device.status else 0),
                origin="AI"
            )
            db.add(history)

    # Notify all house members that a recommendation was acted upon
    if rec:
        members = db.query(models.Membership).filter(models.Membership.house_id == rec.house_id).all()
        for m in members:
            msg = f"Recommendation {'Accepted' if fb.response else 'Declined'}: {rec.content}"
            notif = models.Notification(user_id=m.user_id, message=msg)
            db.add(notif)

    db.commit()
    return {"message": "Feedback saved and device action applied"}


# ── PREFERENCES ──────────────────────────────────────────────────────────────

@app.post("/users/preferences/")
def set_preference(
    pref: schemas.PreferenceCreate,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    existing = db.query(models.UserPreference).filter(
        models.UserPreference.user_id == current_user.id,
        models.UserPreference.category == pref.category,
        models.UserPreference.context == pref.context
    ).first()

    if existing:
        existing.value = pref.value
    else:
        new_pref = models.UserPreference(
            user_id=current_user.id,
            category=pref.category,
            value=pref.value,
            context=pref.context
        )
        db.add(new_pref)
        
    db.commit()
    return {"message": "Preference saved"}


@app.get("/users/preferences/", response_model=List[schemas.PreferenceOut])
def get_preferences(
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    return db.query(models.UserPreference).filter(
        models.UserPreference.user_id == current_user.id
    ).all()



# ── SENSOR SIMULATION ────────────────────────────────────────────────────────
@app.post("/environment/push")
async def push_environment_data(
    house_id: int, 
    temp: int, 
    weather_desc: str, 
    db: Session = Depends(get_db)
):
    # 1. Save sensor data
    new_env = save_weather_to_db(db, house_id, temp, weather_desc)
    
    # 2. Trigger AI logic
    recommender = _get_recommender(house_id, db)
    # Check if a recommendation is needed based on new environment
    new_rec = recommender.check_and_generate_recommendation()
    
    if new_rec:
        # 3. If AI made a recommendation, notify the users
        members = db.query(models.Membership).filter(models.Membership.house_id == house_id).all()
        for member in members:
            db.add(models.Notification(
                user_id=member.user_id,
                message=f"New Energy Recommendation: {new_rec['recommendation']}"
            ))
        db.commit()
        return {"status": "success", "message": "Data saved, AI recommendation generated.", "recommendation": new_rec["recommendation"]}
    
    return {"status": "success", "message": "Data saved, no new recommendation needed."}


# ── SIMULATION SYNC ──────────────────────────────────────────────────────────
@app.post("/simulation/sync-devices")
async def sync_devices(
    house_id: int, 
    device_states: List[dict], # [{"name": "light", "room": "kitchen", "action": "ON"}]
    db: Session = Depends(get_db)
):
    # This helper updates the DB to match the simulation's current state
    for state in device_states:
        room = db.query(models.Room).filter(
            models.Room.house_id == house_id, 
            models.Room.name == state["room"]
        ).first()
        
        if room:
            device = db.query(models.SmartDevice).filter(
                models.SmartDevice.room_id == room.id,
                models.SmartDevice.device_type.ilike(state["device"])
            ).first()
            
            if device:
                device.status = (state["action"] == "ON")
                if "°C" in str(state["action"]):
                    device.value = int(state["action"].replace("°C", ""))
    
    db.commit()
    return {"status": "synced"}


# ── SUMMARY & HISTORY ────────────────────────────────────────────────────────

@app.get("/houses/{house_id}/summary", response_model=schemas.HouseSummary)
def get_house_summary(
    house_id: int, 
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    house = db.query(models.House).filter(models.House.id == house_id).first()
    if not house:
        raise HTTPException(status_code=404, detail="House not found")

    room_summaries = []
    total_savings = 0.0

    for r in db.query(models.Room).filter(models.Room.house_id == house_id).all():
        active_count = db.query(models.SmartDevice).filter(
            models.SmartDevice.room_id == r.id,
            models.SmartDevice.status == True
        ).count()

        room_devices = []
        room_saved = 0.0
        for d in r.devices:
            # Energy saved per device: 0.5 kWh per accepted AI recommendation
            d_saved = db.query(models.UserFeedback).join(models.Recommendation).filter(
                models.Recommendation.device_id == d.id,
                models.UserFeedback.response == True
            ).count() * 0.5
            room_saved += d_saved
            room_devices.append(schemas.DeviceSummary(
                id=d.id,
                name=d.name or d.device_type,
                device_type=d.device_type,
                energy_saved_kwh=d_saved
            ))

        total_savings += room_saved
        room_summaries.append(schemas.RoomSummary(
            id=r.id, name=r.name,
            active_devices_count=active_count,
            energy_saved_kwh=room_saved,
            devices=room_devices
        ))

    return schemas.HouseSummary(
        house_id=house.id, house_name=house.name,
        invite_code=house.invite_code,
        total_energy_saved=total_savings, rooms=room_summaries
    )


@app.get("/houses/{house_id}/history", response_model=List[schemas.HistoryOut])
def get_house_history(
    house_id: int, 
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    # Determine if user is owner
    membership = db.query(models.Membership).filter(
        models.Membership.user_id == current_user.id,
        models.Membership.house_id == house_id
    ).first()

    is_owner = membership and membership.role == "owner"

    base_query = db.query(
        models.DeviceActionHistory,
        models.SmartDevice.name.label("device_name"),
        models.User.name.label("user_name"),
        models.Room.name.label("room_name")
    ).join(models.SmartDevice, models.DeviceActionHistory.device_id == models.SmartDevice.id) \
     .outerjoin(models.User, models.DeviceActionHistory.user_id == models.User.id) \
     .join(models.Room, models.SmartDevice.room_id == models.Room.id) \
     .filter(models.Room.house_id == house_id)

    if not is_owner:
        # Non-owner: only see history for devices in their assigned rooms OR shared rooms
        assigned_room_ids = [
            a.room_id for a in db.query(models.RoomAssignment).filter(
                models.RoomAssignment.user_id == current_user.id
            ).all()
        ]
        base_query = base_query.filter(
            (models.Room.id.in_(assigned_room_ids)) | (models.Room.room_type == "shared")
        )

    results = base_query.order_by(models.DeviceActionHistory.timestamp.desc()).all()

    # Flatten results for Pydantic
    history = []
    for row in results:
        h = row[0]
        h.device_name = row.device_name
        h.user_name = row.user_name
        h.room_name = row.room_name
        history.append(h)

    return history


# ── ROOM ASSIGNMENT ──────────────────────────────────────────────────────────

@app.post("/rooms/{room_id}/assign/{user_id}")
def assign_user_to_room(
    room_id: int, 
    user_id: int, 
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    room = db.query(models.Room).filter(models.Room.id == room_id).first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")

    existing = db.query(models.RoomAssignment).filter(
        models.RoomAssignment.room_id == room_id,
        models.RoomAssignment.user_id == user_id
    ).first()
    if existing:
        return {"message": "User is already assigned to this room"}

    db.add(models.RoomAssignment(room_id=room_id, user_id=user_id))
    db.commit()
    return {"message": f"User {user.name} successfully assigned to {room.name}"}


# ── NOTIFICATIONS ────────────────────────────────────────────────────────────

@app.get("/users/{user_id}/notifications", response_model=List[schemas.NotificationOut])
def get_notifications(
    user_id: int, 
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    if current_user.id != user_id:
        raise HTTPException(status_code=403, detail="Not authorized")
    return db.query(models.Notification).filter(
        models.Notification.user_id == user_id
    ).order_by(models.Notification.created_at.desc()).all()


@app.post("/users/{user_id}/notifications/{notif_id}/read")
def mark_notification_read(
    user_id: int, 
    notif_id: int, 
    db: Session = Depends(get_db),
    current_user: models.User = Depends(security.get_current_user)
):
    if current_user.id != user_id:
        raise HTTPException(status_code=403, detail="Not authorized")
    notif = db.query(models.Notification).filter(
        models.Notification.id == notif_id,
        models.Notification.user_id == user_id
    ).first()
    if not notif:
        raise HTTPException(status_code=404, detail="Notification not found")
    notif.is_read = True
    db.commit()
    return {"message": "Marked as read"}


# ── PASSWORD RESET ────────────────────────────────────────────────────────────

@app.post("/users/forgot-password")
async def forgot_password(
    email: str, 
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db)
):
    user = db.query(models.User).filter(models.User.email == email).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    code = "".join(random.choices(string.digits, k=6))
    db.add(models.VerificationCode(email=email, code=code))
    db.commit()
    
    print(f"DEBUG PASSWORD RESET: Email={email}, Code={code}")
    background_tasks.add_task(mail_service.send_reset_password_email, email, code)
        
    return {"message": f"Verification code sent to {email}"}



@app.post("/users/reset-password")
def reset_password(email: str, code: str, new_password: str, db: Session = Depends(get_db)):
    v_code = db.query(models.VerificationCode).filter(
        models.VerificationCode.email == email,
        models.VerificationCode.code == code
    ).first()
    
    if not v_code:
        raise HTTPException(status_code=400, detail="Invalid reset code")
        
    user = db.query(models.User).filter(models.User.email == email).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
        
    user.password = security.get_password_hash(new_password)
    db.delete(v_code)
    db.commit()
    return {"message": "Password updated successfully"}


@app.get("/weather")
def get_live_weather():
    online_data = weather.fetch_online_weather()
    if online_data.get("success"):
        return {
            "temperature": int(round(online_data["temp"])),
            "condition": online_data["weather"],
            "humidity": 49
        }
    return {
        "temperature": 22,
        "condition": "Clear",
        "humidity": 50
    }