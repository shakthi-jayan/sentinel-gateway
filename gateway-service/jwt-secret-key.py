import jwt
import datetime

secret = "a9f3c7d2e4b6f8a1c3d5e7f9b2a4c6d8e0f2a4b6c8d0e2f4a6c8e0f2b4d6a8c0"

payload = {
    "sub": "test-user",
    "exp": datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(minutes=10)
}

token = jwt.encode(payload, secret, algorithm="HS256")
print(token)