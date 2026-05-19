import requests
import os


API_KEY = os.getenv("API_KEY")
CITY = "Algiers"

def fetch_online_weather():
    """
    Connects to the internet to get real-time weather.
    """
    url = f"http://api.openweathermap.org/data/2.5/weather?q={CITY}&appid={API_KEY}&units=metric"
    
    try:
        response = requests.get(url, timeout=5)
        data = response.json()
        
        if response.status_code == 200:
            return {
                "temp": data["main"]["temp"],
                "weather": data["weather"][0]["main"], # e.g., "Clouds", "Rain"
                "success": True
            }
        else:
            return {"success": False, "error": "Could not find city"}
            
    except Exception as e:
        return {"success": False, "error": str(e)}