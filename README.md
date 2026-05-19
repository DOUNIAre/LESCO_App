# LESCO Smart Home System

An AI-driven, context-aware smart home automation and optimization system. LESCO features a native Android application built with **Jetpack Compose** and a high-performance **FastAPI** backend integrated with a **PPO Reinforcement Learning** agent to generate personalized energy optimization recommendations.

---

## 📂 Project Structure

The repository is structured into two main components:

```text
LESCO_App/
├── frontend/        # Native Android (Kotlin & Jetpack Compose)
└── backend/         # REST API & AI Recommender (Python, FastAPI, PyTorch)
```

---

## ✨ Key Features

### 📱 Frontend (Android Client)
* **Glassmorphic UI Design:** Translucent UI elements with custom gradients, interactive tabs, and a dynamic ambient backdrop matching Figma specifications.
* **Smart Device & Room Management:** Dynamic room cards mapped to access permissions (Shared vs. Personal) and real-time device control triggers with active/inactive states.
* **Role-Based Controls:** Home Owners can create houses, generate invites, assign members, and manage automation preferences, while family members have tailored access.
* **Weather & Environmental Sensors:** Real-time synchronization of local temperature, humidity, and indoor metrics.

### ⚙️ Backend (Python REST Service & AI Agent)
* **PPO Reinforcement Learning:** A custom PyTorch-based Proximal Policy Optimization (PPO) agent that dynamically learns smart home configurations and recommends actions to minimize energy waste while maximizing comfort.
* **FastAPI Server:** Asynchronous endpoint structure supporting instant toggling, invitations, and device assignment flows.
* **Robust Database Integration:** Relational mapping for houses, rooms, devices, preferences, and notifications with automatic schema creation.
* **Security & Auth:** Password hashing, secure user verification, and JWT-based session tokens.
* **Mail Notifications:** SMTP mail client configuration for instant email alerts.

---

## 🛠️ Tech Stack

* **Frontend:** Kotlin, Jetpack Compose, Android Architecture Components, Retrofit, Coroutines, Material Design 3.
* **Backend:** Python 3.10+, FastAPI, PyTorch, SQLAlchemy (PostgreSQL/SQLite), Pydantic, Uvicorn.

---

## 🚀 Getting Started

### 1. Backend Setup
1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Create and activate a Python virtual environment:
   ```bash
   python -m venv venv
   source venv/Scripts/activate  # On Linux/macOS: source venv/bin/activate
   ```
3. Install the dependencies:
   ```bash
   pip install -r requirements.txt
   ```
4. Configure your environment variables. Copy `.env.example` to `.env` and fill in your details:
   ```bash
   cp .env.example .env
   ```
5. Run the FastAPI development server:
   ```bash
   uvicorn main:app --reload --host 0.0.0.0 --port 8000
   ```

### 2. Frontend Setup
1. Open the `frontend/` directory in **Android Studio**.
2. Sync the project with Gradle Files.
3. Configure the local connection base URL to point to your backend server in `RetrofitInstance.kt`.
4. Run the application on your emulator or physical device.

---

## 🔒 Security and Credentials
Sensitives files, local configuration assets (`.env`, `local.properties`, build caches, and system crash logs) are strictly ignored in `.gitignore` to prevent any exposure of production keys, API tokens, or server credentials. Refer to `.env.example` for environment structures.
