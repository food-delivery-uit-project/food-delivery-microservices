"""
Simulate Restaurant Owner + Driver flow for a specific Order ID.

Usage:
    python3 scripts/simulate_restaurant_driver.py <ORDER_ID> <RESTAURANT_ID>

Example:
    python3 scripts/simulate_restaurant_driver.py abc-123 def-456

This script:
1. Logs in as owner@test.com and driver@test.com
2. Connects Driver WebSocket (broadcasts location near restaurant)
3. Owner accepts + marks order as READY_FOR_PICKUP
4. Waits for Dispatch to assign a driver
5. Driver confirms pickup → deliver
"""
import requests
import asyncio
import websockets
import json
import time
import argparse
import sys
import base64

BASE_URL = "http://localhost:8000"
WS_BASE_URL = "ws://localhost:8000"

def login(email, password):
    res = requests.post(f"{BASE_URL}/api/v1/auth/login",
                        json={"email": email, "password": password})
    res.raise_for_status()
    return res.json()["data"]["access_token"]

def get_user_id(token):
    parts = token.split(".")
    payload = parts[1]
    payload += "=" * ((4 - len(payload) % 4) % 4)
    decoded = base64.urlsafe_b64decode(payload).decode("utf-8")
    return json.loads(decoded).get("sub")

def api_call(method, path, token, data=None):
    headers = {"Authorization": f"Bearer {token}"}
    if method in ("POST", "PATCH"):
        res = getattr(requests, method.lower())(
            f"{BASE_URL}{path}", headers=headers, json=data)
    else:
        res = requests.get(f"{BASE_URL}{path}", headers=headers)

    if res.status_code >= 400:
        print(f"  ⚠️  {method} {path} → {res.status_code}: {res.text[:200]}")
    return res

async def run(order_id, restaurant_id):
    print("🔑 Logging in Owner and Driver...")
    try:
        owner_token  = login("owner@test.com",  "Test@12345")
        driver_token = login("driver@test.com", "Test@12345")
        driver_id    = get_user_id(driver_token)
        print(f"   Owner & Driver logged in. Driver ID: {driver_id}")
    except Exception as e:
        print(f"❌ Login failed: {e}")
        return 1

    # ── Connect Driver WebSocket (use driver_id query param) ──────────────────
    ws_url = f"{WS_BASE_URL}/ws/driver/location?driver_id={driver_id}"
    location_payload = json.dumps({"lat": 10.7731, "lng": 106.703})

    print(f"\n🔌 Connecting Driver WebSocket...")
    ws_task = None
    ws = None

    async def driver_ws_loop():
        nonlocal ws
        try:
            async with websockets.connect(ws_url, ping_interval=20) as websocket:
                ws = websocket
                print("   🟢 Driver WebSocket connected. Broadcasting location near restaurant...")
                while True:
                    await websocket.send(location_payload)
                    try:
                        await asyncio.wait_for(websocket.recv(), timeout=5.0)
                    except asyncio.TimeoutError:
                        pass
        except Exception as e:
            print(f"   WebSocket ended: {e}")

    ws_task = asyncio.create_task(driver_ws_loop())
    await asyncio.sleep(2)  # Let WS connect & broadcast location first

    print(f"\n🍔 Processing Order: {order_id} for Restaurant: {restaurant_id}")

    # ── Owner Accepts ──────────────────────────────────────────────────────────
    await asyncio.sleep(1)
    print("\n👨‍🍳 Owner is ACCEPTING order...")
    res = api_call("POST", f"/api/v1/restaurants/{restaurant_id}/orders/{order_id}/accept",
                   token=owner_token)
    if res.status_code not in (200, 201):
        print("   ⚠️  Accept failed (order may already be accepted)")

    # ── Owner Marks Ready ──────────────────────────────────────────────────────
    await asyncio.sleep(3)
    print("👨‍🍳 Owner is marking order as READY_FOR_PICKUP...")
    res = api_call("POST", f"/api/v1/restaurants/{restaurant_id}/orders/{order_id}/ready",
                   token=owner_token)
    if res.status_code not in (200, 201):
        print("   ⚠️  Ready failed (order may not be in correct state)")

    # ── Wait for Driver Assignment ─────────────────────────────────────────────
    print("\n⏳ Waiting for Dispatch Service to assign Driver (up to 60s)...")
    assigned = False
    for attempt in range(20):
        await asyncio.sleep(3)
        try:
            res = api_call("GET", f"/api/v1/delivery/{order_id}", token=owner_token)
            if res.status_code == 200:
                status = res.json().get("data", {}).get("status", "")
                print(f"   Attempt {attempt+1}/20: Delivery status = {status}")
                if status == "DRIVER_ASSIGNED":
                    assigned = True
                    print("   ✅ Driver ASSIGNED!")
                    break
        except Exception as e:
            print(f"   Error polling delivery: {e}")

    if not assigned:
        print("   ⚠️  Driver was not auto-assigned. Proceeding with manual pickup...")

    # ── Driver Pickup ──────────────────────────────────────────────────────────
    await asyncio.sleep(2)
    print("\n🛵 Driver is PICKING UP order...")
    res = api_call("PATCH", f"/api/v1/delivery/{order_id}/pickup", token=driver_token)
    if res.status_code in (200, 201):
        print("   ✅ Order PICKED UP!")
    else:
        print(f"   ⚠️  Pickup may have failed: {res.text[:100]}")

    # ── Driver Deliver ─────────────────────────────────────────────────────────
    await asyncio.sleep(4)
    print("\n🎁 Driver is DELIVERING order...")
    res = api_call("PATCH", f"/api/v1/delivery/{order_id}/deliver", token=driver_token)
    if res.status_code in (200, 201):
        print("   ✅ Order DELIVERED!")
    else:
        print(f"   ⚠️  Deliver may have failed: {res.text[:100]}")

    # ── Final Check ────────────────────────────────────────────────────────────
    await asyncio.sleep(2)
    res = api_call("GET", f"/api/v1/orders/{order_id}", token=owner_token)
    if res.status_code == 200:
        final = res.json().get("data", {}).get("status")
        print(f"\n📊 Final Order Status: {final}")

    ws_task.cancel()
    print("\n✅ Simulation complete! Check your Mobile App to see the DELIVERED state.")
    return 0

def main():
    parser = argparse.ArgumentParser(description="Simulate Restaurant+Driver for a specific Order")
    parser.add_argument("order_id",     help="Order ID to process")
    parser.add_argument("restaurant_id", help="Restaurant ID")
    args = parser.parse_args()

    try:
        exit_code = asyncio.run(run(args.order_id, args.restaurant_id))
        sys.exit(exit_code)
    except KeyboardInterrupt:
        print("\nInterrupted by user.")
        sys.exit(0)

if __name__ == "__main__":
    main()
