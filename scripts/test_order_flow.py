#!/usr/bin/env python3
import json
import urllib.request
import urllib.error
import time

BASE_URL = "http://localhost:8000"

def api_call(method, path, data=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(f"{BASE_URL}{path}", data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        print(f"  ⚠️  {method} {path} → {e.code}: {body[:200]}")
        return {"error": True, "status": e.code, "body": body}

def login(email, password):
    res = api_call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    return res.get("data", {}).get("access_token")

def main():
    print("🔑 Logging in...")
    customer_token = login("customer@test.com", "Test@12345")
    owner_token = login("owner@test.com", "Test@12345")
    driver_token = login("driver@test.com", "Test@12345")
    if not customer_token or not owner_token or not driver_token:
        print("Failed to login")
        return

    print("🍔 Getting restaurants...")
    res = api_call("GET", "/api/v1/restaurants?page=0&size=10", token=customer_token)
    restaurants = res.get("data", [])
    if not restaurants:
        print("No restaurants found!")
        return
    restaurant = restaurants[0]
    restaurant_id = restaurant["id"]
    
    print(f"📋 Getting menu for restaurant {restaurant_id}...")
    res = api_call("GET", f"/api/v1/restaurants/{restaurant_id}/menu", token=customer_token)
    menu_categories = res.get("data", {}).get("categories", [])
    if not menu_categories:
        print("No menu found!")
        return
    item = menu_categories[0]["items"][0]
    item_id = item["id"]
    
    print(f"🛒 Creating order for item {item['name']}...")
    order_req = {
        "restaurant_id": restaurant_id,
        "items": [{"item_id": item_id, "quantity": 2}],
        "delivery_address": {
            "address_line": "123 Test St, HCM",
            "lat": 10.762622,
            "lng": 106.660172
        },
        "special_instructions": "No spicy"
    }
    res = api_call("POST", "/api/v1/orders", order_req, customer_token)
    if res.get("error"):
        print("Failed to create order")
        return
    order_id = res["data"]["orderId"]
    print(f"✅ Order created: {order_id}")
    
    print("⏳ Waiting for order status to update (Payment processing via events)...")
    time.sleep(3)
    
    res = api_call("GET", f"/api/v1/orders/{order_id}", token=customer_token)
    print(f"Order status: {res.get('data', {}).get('status')}")

    print("👨‍🍳 Owner accepting order...")
    res = api_call("PATCH", f"/api/v1/orders/{order_id}/accept", token=owner_token)
    print(f"Accept response: {res}")
    
    res = api_call("GET", f"/api/v1/orders/{order_id}", token=customer_token)
    print(f"Order status: {res.get('data', {}).get('status')}")

if __name__ == "__main__":
    main()
