#  Smart Home Recommendation System Backend

![FastAPI](https://img.shields.io/badge/FastAPI-005571?style=for-the-badge&logo=fastapi)
![Python](https://img.shields.io/badge/python-3670A0?style=for-the-badge&logo=python&logoColor=ffdd54)
![PostgreSQL](https://img.shields.io/badge/postgres-%23316192.svg?style=for-the-badge&logo=postgresql&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-black?style=for-the-badge&logo=JSON%20web%20tokens)

This is the central engine for the **Smart Home**. It is a high-performance, secure, multi-user system designed to manage smart devices, resolve environmental conflicts through weighted logic, and provide an automated AI-based recommendation loop for energy efficiency.

##  Core Features

###  Multi-User Conflict Resolution
- **Weighted Average Strategy:** Automatically resolves temperature and brightness conflicts by prioritizing the House Owner's preferences (Weight: 2) over members (Weight: 1).
- **Majority Voting:** Handles binary decisions (ON/OFF) based on active user consensus.
- **Ownership Logic:** Supports personal device overrides where specific users retain exclusive control.

###  Safety & Reliability
- **Safety Constraints:** Rule-based layer prevents conflicting operations (e.g., blocking AC activation if the Heater is already running).
- **Audit Logs:** Full history tracking of every device state change, recording the user, timestamp, and origin (Manual vs. AI).

###  AI & Energy Optimization
- **AI Feedback Loop:** Integrated support for Reinforcement Learning (PPO) models via user "Accept/Deny" feedback tracking.
- **Virtual Sensors:** Automated outdoor context retrieval using the **OpenWeatherMap API**.
- **Energy Analytics:** Real-time calculation of kWh saved per room based on AI-optimized settings.

###  Security
- **Authentication:** JWT (JSON Web Tokens) for secure mobile-to-backend communication.
- **Encryption:** Password hashing using `bcrypt`.
- **Authorization:** Dependency injection layers to ensure users only control devices within their own houses.

---

##  Tech Stack

| Technology | Usage |
| :--- | :--- |
| **FastAPI** | High-performance Web Framework |
| **PostgreSQL** | Relational Database |
| **SQLAlchemy** | SQL Toolkit and Object Relational Mapper (ORM) |
| **Pydantic** | Data validation and settings management |
| **Passlib** | Hashing and security utilities |
| **Requests** | External API communication (Weather) |

---

##  Project Structure

```text
backend/
├── ai/                  # AI Model integration & Recommendation logic
├── services/            # Business Logic (Resolver, Safety Rules, Weather API)
├── main.py              # Entry Point & Application Routes
├── models.py            # Database Tables (SQLAlchemy)
├── schemas.py           # Data Transfer Objects / Validation (Pydantic)
├── database.py          # Connection & Session Management
└── security.py          # JWT, Hashing, and Auth Dependencies
```

---

##  Installation & Setup

### 1. Clone the repository
```bash
git clone https://github.com/DOUNIAre/smart-home-backend.git
cd smart-home-backend
```

### 2. Configure Environment
```bash
# Create a virtual environment
python -m venv venv

# Activate it
# Windows:
venv\Scripts\activate
# Linux/Mac:
source venv/bin/activate
```

### 3. Install Dependencies
```bash
pip install fastapi uvicorn sqlalchemy psycopg2-binary pydantic[email] python-jose[cryptography] passlib[bcrypt] requests
```

### 4. Database Setup
1. Create a database named `smarthome_db` in PostgreSQL.
2. Update `database.py` with your credentials:
   ```python
   SQLALCHEMY_DATABASE_URL = "postgresql://USER:PASSWORD@localhost:5432/smarthome_db"
   ```

### 5. Launch the System
```bash
uvicorn main:app --reload
```

---

##  API Documentation

The backend provides interactive documentation for easy frontend testing:

- **Swagger UI (Interactive):** [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)
- **ReDoc:** [http://127.0.0.1:8000/redoc](http://127.0.0.1:8000/redoc)

### Key Endpoints Checklist
- [x] `POST /login` - Obtain JWT Access Token.
- [x] `GET /houses/{id}/summary` - Fetch house-wide status for Map UI.
- [x] `POST /devices/{id}/toggle` - Control devices with safety checks.
- [x] `GET /houses/{id}/recommendation/` - Trigger AI & Weather update.

---


> *This project was developed as a Final Year Project (PFE).*

