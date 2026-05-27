import json, urllib.request

login_data = json.dumps({"username": "admin", "password": "Admin@123"}).encode()
req = urllib.request.Request("http://localhost/api/auth/login", data=login_data, headers={"Content-Type": "application/json"})
resp = urllib.request.urlopen(req)
token = json.loads(resp.read())["token"]
print("Token OK")

req2 = urllib.request.Request("http://localhost/api/documents/6/config", headers={"Authorization": "Bearer " + token})
try:
    resp2 = urllib.request.urlopen(req2)
    data = resp2.read().decode()
    print(data)
except urllib.error.HTTPError as e:
    print(f"HTTP {e.code}: {e.read().decode()}")
except Exception as e:
    print(f"Error: {e}")
