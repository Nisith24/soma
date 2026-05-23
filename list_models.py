import urllib.request
import json
import os

api_key = os.environ.get("GEMINI_API_KEY", "")

url = f"https://generativelanguage.googleapis.com/v1beta/models?key={api_key}"
try:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
        models = [m['name'] for m in data.get('models', [])]
        print(json.dumps(models, indent=2))
except Exception as e:
    print(f"Error: {e}")
