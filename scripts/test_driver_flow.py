import asyncio
import json
import urllib.request
import urllib.error
import websockets
import time

BASE_URL = "http://localhost:8000"
WS_URL = "ws://localhost:8000"

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

async def driver_ws(token):
    headers = {"Authorization": f"Bearer {token}"}
    uri = f"{WS_URL}/ws/driver/location"
    try:
        async with websockets.connect(uri, additional_headers=headers) as ws:
            print("🟢 Driver connected to WebSocket")
            for _ in range(30): # send location for 30 seconds
                loc = {"lat": 10.762622, "lng": 106.660172} # Same location
                await ws.send(json.dumps(loc))
                await asyncio.sleep(1)
    except Exception as e:
        print(f"🔴 WebSocket error: {e}")

async def main():
    print("🔑 Logging in...")
    customer_token = login("customer@test.com", "Test@12345")
    owner_token = login("owner@test.com", "Test@12345")
    driver_token = login("driver@test.com", "Test@12345")

    # Start driver in background
    ws_task = asyncio.create_task(driver_ws(driver_token))
    
    # Wait a bit for driver to be active
    await asyncio.sleep(2)

    print("🍔 Getting restaurants...")
    res = api_call("GET", "/api/v1/restaurants?page=0&size=10", token=customer_token)
    restaurant = res.get("data", [])[0]
    restaurant_id = restaurant["id"]

    print("🛒 Creating order...")
    res = api_call("GET", f"/api/v1/restaurants/{restaurant_id}/menu", token=customer_token)
    item_id = res["data"]["categories"][0]["items"][0]["id"]
    
    order_req = {
        "restaurant_id": restaurant_id,
        "items": [{"item_id": item_id, "quantity": 1}],
        "delivery_address": {"address_line": "123 Test St", "lat": 10.762622, "lng": 106.660172},
        "special_instructions": ""
    }
    res = api_call("POST", "/api/v1/orders", order_req, customer_token)
    order_id = res["data"]["orderId"]
    print(f"✅ Order created: {order_id}")
    
    await asyncio.sleep(3)
    print("👨‍🍳 Owner accepting order...")
    api_call("POST", f"/api/v1/restaurants/{restaurant_id}/orders/{order_id}/accept", token=owner_token)

    await asyncio.sleep(1)
    print("👨‍🍳 Owner marking order as READY...")
    api_call("POST", f"/api/v1/restaurants/{restaurant_id}/orders/{order_id}/ready", token=owner_token)

    print("⏳ Waiting for Driver to be assigned by Dispatch Service...")
    for _ in range(15):
        await asyncio.sleep(2)
        res = api_call("GET", f"/api/v1/orders/{order_id}", token=customer_token)
        status = res.get("data", {}).get("status")
        print(f"Order status: {status}")
        if status == "DRIVER_ASSIGNED":
            break
            
    print("🛵 Driver picking up order...")
    api_call("PATCH", f"/api/v1/delivery/{order_id}/pickup", token=driver_token)
    
    await asyncio.sleep(1)
    print("🎁 Driver delivering order...")
    api_call("PATCH", f"/api/v1/delivery/{order_id}/deliver", token=driver_token)

    res = api_call("GET", f"/api/v1/orders/{order_id}", token=customer_token)
    print(f"Final Order status: {res.get('data', {}).get('status')}")

    ws_task.cancel()

if __name__ == "__main__":
    asyncio.run(main())
