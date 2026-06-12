#!/usr/bin/env python3
"""
Seed data script for the food delivery platform.
Loads sample restaurants, menus, and test users into the system.
"""

import json
import sys
import time
import subprocess
import urllib.request
import urllib.error


USER_SERVICE_URL = "http://localhost:8001"
RESTAURANT_SERVICE_URL = "http://localhost:8002"

SAMPLE_USERS = [
    {
        "email": "customer@test.com",
        "password": "Test@12345",
        "full_name": "Nguyễn Văn A",
        "phone": "0901234567",
        "role": "CUSTOMER",
    },
    {
        "email": "owner@test.com",
        "password": "Test@12345",
        "full_name": "Trần Thị B",
        "phone": "0907654321",
        "role": "RESTAURANT_OWNER",
    },
    {
        "email": "driver@test.com",
        "password": "Test@12345",
        "full_name": "Lê Văn C",
        "phone": "0909876543",
        "role": "DRIVER",
    },
]

SAMPLE_RESTAURANTS = [
    {
        "name": "Phở Hà Nội 36",
        "description": "Phở truyền thống Hà Nội, nước dùng ninh xương 12 tiếng",
        "address_line": "36 Nguyễn Huệ, Q1, TP.HCM",
        "lat": 10.7731,
        "lng": 106.7030,
        "cuisine_types": ["VIETNAMESE", "NOODLES"],
        "operating_hours": {
            "mon": {"open": "06:00", "close": "22:00"},
            "tue": {"open": "06:00", "close": "22:00"},
            "wed": {"open": "06:00", "close": "22:00"},
            "thu": {"open": "06:00", "close": "22:00"},
            "fri": {"open": "06:00", "close": "23:00"},
            "sat": {"open": "07:00", "close": "23:00"},
            "sun": {"open": "07:00", "close": "22:00"},
        },
        "menu": [
            {
                "name": "Phở",
                "items": [
                    {
                        "name": "Phở Bò Tái",
                        "description": "Phở bò tái nạm gầu, nước dùng xương bò",
                        "price": 55000,
                        "is_available": True,
                        "options": [
                            {
                                "name": "Size",
                                "choices": [
                                    {"label": "Nhỏ", "price_modifier": 0},
                                    {"label": "Lớn", "price_modifier": 15000},
                                ],
                            },
                            {
                                "name": "Thêm",
                                "choices": [
                                    {"label": "Trứng", "price_modifier": 5000},
                                    {"label": "Giò", "price_modifier": 10000},
                                ],
                            },
                        ],
                    },
                    {
                        "name": "Phở Gà",
                        "description": "Phở gà ta thả vườn",
                        "price": 50000,
                        "is_available": True,
                        "options": [
                            {
                                "name": "Size",
                                "choices": [
                                    {"label": "Nhỏ", "price_modifier": 0},
                                    {"label": "Lớn", "price_modifier": 15000},
                                ],
                            },
                        ],
                    },
                ],
            },
            {
                "name": "Đồ uống",
                "items": [
                    {"name": "Trà đá", "price": 5000, "is_available": True, "options": []},
                    {"name": "Nước ngọt", "price": 15000, "is_available": True, "options": []},
                ],
            },
        ],
    },
    {
        "name": "Cơm Tấm Sài Gòn",
        "description": "Cơm tấm sườn bì chả đúng vị Sài Gòn",
        "address_line": "123 Lê Lợi, Q1, TP.HCM",
        "lat": 10.7720,
        "lng": 106.6980,
        "cuisine_types": ["VIETNAMESE", "RICE"],
        "operating_hours": {
            "mon": {"open": "06:00", "close": "21:00"},
            "tue": {"open": "06:00", "close": "21:00"},
            "wed": {"open": "06:00", "close": "21:00"},
            "thu": {"open": "06:00", "close": "21:00"},
            "fri": {"open": "06:00", "close": "21:00"},
            "sat": {"open": "06:00", "close": "21:00"},
            "sun": {"open": "06:00", "close": "21:00"},
        },
        "menu": [
            {
                "name": "Cơm Tấm",
                "items": [
                    {"name": "Cơm Tấm Sườn", "price": 40000, "is_available": True, "options": []},
                    {"name": "Cơm Tấm Sườn Bì Chả", "price": 55000, "is_available": True, "options": []},
                    {"name": "Cơm Tấm Đặc Biệt", "price": 65000, "is_available": True, "options": []},
                ],
            },
        ],
    },
]


import base64

def get_user_info(token: str) -> dict:
    try:
        parts = token.split('.')
        if len(parts) != 3:
            return {}
        payload = parts[1]
        # Pad base64 string
        payload += '=' * (4 - len(payload) % 4)
        decoded = base64.urlsafe_b64decode(payload).decode('utf-8')
        data = json.loads(decoded)
        return {
            "id": data.get("sub"),
            "role": data.get("role")
        }
    except Exception as e:
        print(f"  ❌ Error decoding token: {e}")
        return {}

def api_call(method: str, path: str, data: dict = None, token: str = None) -> dict:
    """Make an HTTP request to the API."""
    headers = {"Content-Type": "application/json"}
    
    # Route directly to the respective service ports
    if path.startswith("/api/v1/auth") or path.startswith("/api/v1/users"):
        url = f"{USER_SERVICE_URL}{path}"
        if token:
            headers["Authorization"] = f"Bearer {token}"
    else:
        url = f"{RESTAURANT_SERVICE_URL}{path}"
        if token:
            # Emulate Gateway headers for Restaurant Service
            user_info = get_user_info(token)
            if user_info:
                headers["X-User-Id"] = user_info.get("id")
                headers["X-User-Role"] = user_info.get("role")

    body = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        print(f"  ⚠️  {method} {path} → {e.code}: {body[:200]}")
        return {"error": True, "status": e.code}
    except urllib.error.URLError as e:
        print(f"  ❌ Connection failed: {e.reason}")
        print("  Make sure services are running locally on port 8001 and 8002.")
        sys.exit(1)


def seed_users() -> dict:
    """Create sample users and return tokens."""
    print("\n👤 Seeding users...")
    tokens = {}
    for user in SAMPLE_USERS:
        result = api_call("POST", "/api/v1/auth/register", user)
        if not result.get("error"):
            print(f"  ✅ Created user: {user['email']} ({user['role']})")
        else:
            print(f"  ℹ️  User {user['email']} may already exist, trying login...")

        login_result = api_call("POST", "/api/v1/auth/login", {
            "email": user["email"],
            "password": user["password"],
        })
        if not login_result.get("error"):
            tokens[user["role"]] = login_result.get("data", {}).get("access_token", "")
            print(f"  🔑 Got token for {user['role']}")

    return tokens


def seed_restaurants(owner_token: str) -> None:
    """Create sample restaurants with menus."""
    print("\n🍜 Seeding restaurants...")
    for restaurant in SAMPLE_RESTAURANTS:
        menu = restaurant.pop("menu")
        result = api_call("POST", "/api/v1/restaurants", restaurant, owner_token)
        if not result.get("error"):
            restaurant_id = result.get("data", {}).get("id", "unknown")
            print(f"  ✅ Created restaurant: {restaurant['name']} (id: {restaurant_id})")

            api_call("PUT", f"/api/v1/restaurants/{restaurant_id}/menu",
                     {"categories": menu}, owner_token)
            print(f"  📋 Menu uploaded")
        restaurant["menu"] = menu


def main() -> None:
    print("🌱 Seeding Food Delivery Platform with sample data...")
    print(f"   User Service: {USER_SERVICE_URL}")
    print(f"   Restaurant Service: {RESTAURANT_SERVICE_URL}")

    tokens = seed_users()

    owner_token = tokens.get("RESTAURANT_OWNER")
    if owner_token:
        seed_restaurants(owner_token)
    else:
        print("\n⚠️  Could not get RESTAURANT_OWNER token, skipping restaurant seeding.")

    print("\n" + "=" * 50)
    print("🎉 Seed data loaded!")
    print("=" * 50)
    print("\nTest accounts:")
    for user in SAMPLE_USERS:
        print(f"  {user['role']}: {user['email']} / {user['password']}")


if __name__ == "__main__":
    main()
