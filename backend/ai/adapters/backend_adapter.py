class BackendAdapter:
    def get_devices(self, house_id: int) -> list:
        raise NotImplementedError

class SQLAlchemyAdapter(BackendAdapter):
    def __init__(self, db_session):
        self.db = db_session

    def get_devices(self, house_id: int) -> list:
        # We need to import models here or at the top
        import models
        rooms = self.db.query(models.Room).filter(models.Room.house_id == house_id).all()
        devices = []
        for r in rooms:
            for d in r.devices:
                devices.append({
                    "id": d.id,
                    "device_type": d.device_type,
                    "status": d.status,
                    "value": d.value,
                    "room_id": r.id,
                    "name": d.name or d.device_type
                })
        return devices
    def get_environment(self, house_id: int):
        import models
        return self.db.query(models.Environment).filter(
            models.Environment.house_id == house_id
        ).order_by(models.Environment.timestamp.desc()).first()

    def save_recommendation(self, house_id, device_id, content, proposed_value, confidence, reason):
        import models
        rec = models.Recommendation(
            house_id=house_id,
            device_id=device_id,
            content=content,
            proposed_value=proposed_value,
            confidence_score=confidence,
            reason=reason
        )
        self.db.add(rec)
        self.db.commit()
        self.db.refresh(rec)
        return rec
