import urllib.request
import urllib.error
import json
import time
import base64
import threading
from websocket import create_connection
import sseclient

BASE_URL = "http://localhost:8000"

def get_user_info(token: str) -> dict:
    try:
        parts = token.split(".")
        if len(parts) == 3:
            payload = parts[1]
            padded = payload + "=" * ((4 - len(payload) % 4) % 4)
            decoded = base64.b64decode(padded).decode("utf-8")
            data = json.loads(decoded)
            return {
                "id": data.get("sub"),
                "role": data.get("role")
            }
    except Exception as e:
        print(f"Token decode error: {e}")
    return None

def api_call(method, path, data=None, token=None):
    url = BASE_URL + path
    headers = {'Content-Type': 'application/json'}
    if token:
        user_info = get_user_info(token)
        if user_info:
            headers["X-User-Id"] = user_info.get("id")
            headers["X-User-Role"] = user_info.get("role")
    
    req_data = json.dumps(data).encode('utf-8') if data else None
    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
    
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            res_body = response.read().decode()
            if not res_body:
                return None
            return json.loads(res_body)
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        print(f"HTTP {e.code}: {body}")
        raise e

sse_events = []

def listen_sse(order_id, token):
    headers = {}
    if token:
        user_info = get_user_info(token)
        if user_info:
            headers["X-User-Id"] = user_info.get("id")
            headers["X-User-Role"] = user_info.get("role")

    url = f"{BASE_URL}/api/v1/notifications/orders/{order_id}/stream"
    req = urllib.request.Request(url, headers=headers)
    try:
        response = urllib.request.urlopen(req, timeout=60)
        client = sseclient.SSEClient(response)
        for event in client.events():
            if event.data:
                data = json.loads(event.data)
                sse_events.append(data)
                print(f"[SSE] Received event: {data.get('status')}")
                if data.get("status") in ["DELIVERED", "CANCELLED"]:
                    break
    except Exception as e:
        print(f"[SSE] Listener stopped: {e}")

def main():
    print("🚀 Starting Full E2E Flow Test (Customer + Driver)...")
    tokens = {}
    
    # 1. Registration / Login
    users = [
        {"email": "customer@test.com", "password": "Test@12345", "role": "CUSTOMER"},
        {"email": "owner@test.com", "password": "Test@12345", "role": "RESTAURANT_OWNER"},
        {"email": "driver@test.com", "password": "Test@12345", "role": "DRIVER"}
    ]
    
    for u in users:
        # Try register first, ignore 409
        try:
            api_call("POST", "/api/v1/auth/register", {"email": u["email"], "password": u["password"], "fullName": "Test User", "role": u["role"]})
        except Exception:
            pass
            
        res = api_call("POST", "/api/v1/auth/login", {"email": u["email"], "password": u["password"]})
        tokens[u["role"]] = res.get("data", {}).get("access_token", "")
        if not tokens[u["role"]]:
             tokens[u["role"]] = res.get("data", "")
        
    customer_token = tokens["CUSTOMER"]
    owner_token = tokens["RESTAURANT_OWNER"]
    driver_token = tokens["DRIVER"]
    
    driver_info = get_user_info(driver_token)
    driver_id = driver_info.get("id")
    print(f"✅ Driver Logged in. ID: {driver_id}")

    # 2. Get Restaurant
    restaurants = api_call("GET", "/api/v1/restaurants?page=0&size=10", token=customer_token)["data"]
    
    restaurant_id = None
    item_id = None
    restaurant_name = None
    
    for r in restaurants:
        menu_data = api_call("GET", f"/api/v1/restaurants/{r['id']}/menu", token=customer_token)["data"]
        for cat in menu_data.get("categories", []):
            if cat.get("items"):
                item_id = cat["items"][0]["id"]
                restaurant_id = r["id"]
                restaurant_name = r["name"]
                break
        if item_id:
            break
            
    if not item_id:
        print("❌ Could not find any restaurant with menu items!")
        return
    print(f"✅ Found Restaurant: {restaurant_name} with Item ID: {item_id}")
    
    # 3. Connect Driver WebSocket to Dispatch Service
    ws_url = "ws://localhost:8000/ws/driver/location"
    ws = create_connection(ws_url, header=[
        f"X-User-Id: {driver_id}",
        "X-User-Role: DRIVER"
    ])
    
    # Send location near the restaurant (10.7731, 106.703)
    loc_payload = json.dumps({
        "lat": 10.772,
        "lng": 106.702
    })
    ws.send(loc_payload)
    print("✅ Driver location broadcasted via WebSocket")

    # 4. Create Order
    order_req = {
        "restaurant_id": restaurant_id,
        "items": [{"item_id": item_id, "quantity": 1}],
        "delivery_address": {
            "address_line": "123 Main St, HCM",
            "lat": 10.7,
            "lng": 106.7
        },
        "special_instructions": "Hurry up"
    }
    order_id = api_call("POST", "/api/v1/orders", order_req, token=customer_token)["data"]["orderId"]
    print(f"✅ Order Created: {order_id}. Waiting for payment and dispatch...")

    # 5. Start SSE Listener
    sse_thread = threading.Thread(target=listen_sse, args=(order_id, customer_token))
    sse_thread.daemon = True
    sse_thread.start()

    # 6. Wait for Payment and Simulate Restaurant Acceptance
    print("⏳ Waiting for Saga Payment...")
    paid = False
    for i in range(15):
        time.sleep(2)
        try:
            status_data = api_call("GET", f"/api/v1/orders/{order_id}", token=customer_token)
            if status_data and status_data.get("data", {}).get("status") == "PAID":
                paid = True
                print("✅ Order is PAID!")
                break
        except Exception:
            pass
            
    if not paid:
        print("❌ Order was not PAID in time. Check Payment/Order Service.")
        ws.close()
        return

    # Simulate Restaurant accepting and making order ready
    print("👩‍🍳 Restaurant is preparing the order...")
    api_call("POST", f"/api/v1/restaurants/{restaurant_id}/orders/{order_id}/accept", token=owner_token)
    time.sleep(2)
    api_call("POST", f"/api/v1/restaurants/{restaurant_id}/orders/{order_id}/ready", token=owner_token)
    print("✅ Order is READY_FOR_PICKUP!")

    # 7. Wait for Driver Assignment (Dispatch)
    print("⏳ Waiting for Dispatch Matching...")
    assigned = False
    for i in range(15):
        time.sleep(2)
        try:
            status_data = api_call("GET", f"/api/v1/delivery/{order_id}", token=customer_token)
            if status_data and status_data.get("data", {}).get("status") == "DRIVER_ASSIGNED":
                assigned = True
                print("✅ Driver has been assigned!")
                break
        except Exception:
            pass
    
    if not assigned:
        print("❌ Driver was not assigned in time.")
        ws.close()
        return

    # 7. Driver Pickup
    print("🚗 Driver picking up the order...")
    api_call("PATCH", f"/api/v1/delivery/{order_id}/pickup", token=driver_token)
    time.sleep(2)
    
    # 8. Driver Delivery
    print("🏁 Driver delivering the order...")
    api_call("PATCH", f"/api/v1/delivery/{order_id}/deliver", token=driver_token)
    time.sleep(2)

    ws.close()
    
    # 9. Verify Final Order Status
    order = api_call("GET", f"/api/v1/orders/{order_id}", token=customer_token)["data"]
    print(f"✅ Final Order Status: {order['status']}")
    
    # 10. Verify SSE Events Received
    statuses = [e.get("status") for e in sse_events]
    print(f"✅ SSE Statuses Received: {statuses}")
    
    if "DELIVERED" in statuses:
         print("🎉 FULL E2E TEST COMPLETED SUCCESSFULLY!")
    else:
         print("❌ SSE Did not receive DELIVERED status.")

if __name__ == "__main__":
    main()
