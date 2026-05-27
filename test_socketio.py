import json, urllib.request, urllib.parse

# Login
login_data = json.dumps({"username": "admin", "password": "Admin@123"}).encode()
req = urllib.request.Request("http://localhost/api/auth/login", data=login_data, headers={"Content-Type": "application/json"})
resp = urllib.request.urlopen(req)
token = json.loads(resp.read())["token"]

# Get editor config
req2 = urllib.request.Request("http://localhost/api/documents/6/config", headers={"Authorization": "Bearer " + token})
resp2 = urllib.request.urlopen(req2)
config = json.loads(resp2.read())

editor_token = config["token"]
doc_key = config["document"]["key"]
print(f"Doc key: {doc_key}")
print(f"Token: {editor_token[:50]}...")

# Test socket.io GET handshake with the real token
url = f"http://localhost/ds-vpath/9.3.1-4e329689b8c2b18100ca43f7b620ffc0/doc/{doc_key}?EIO=4&transport=polling&token={urllib.parse.quote(editor_token)}"
print(f"URL: {url[:120]}...")
req3 = urllib.request.Request(url)
resp3 = urllib.request.urlopen(req3)
print(f"GET response: {resp3.read().decode()}")
