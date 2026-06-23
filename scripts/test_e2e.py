"""
End-to-End Test: Full Order Flow (Customer -> Restaurant -> Dispatch -> Driver -> Delivered)
Tests the full backend flow through Kong API Gateway.

Usage:
    python3 scripts/test_e2e.py

Prerequisites:
    pip install websocket-client
    kubectl port-forward svc/kong-kong-proxy 8000:80 -n food-app
    make seed  # Ensure test data exists
"""
import urllib.request
import urllib.error
import json
import time
import base64
import threading
import websocket
import sys

BASE_URL = "http://localhost:8000"
WS_BASE_URL = "ws://localhost:8000"

# Color codes for terminal output
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
RESET = "\033[0m"

def log_ok(msg):  print(f"{GREEN}✅ {msg}{RESET}")
def log_err(msg): print(f"{RED}❌ {msg}{RESET}")
def log_wait(msg):print(f"{YELLOW}⏳ {msg}{RESET}")
def log_info(msg):print(f"{BLUE}   {msg}{RESET}")

def get_user_info(token: str) -> dict:
    try:
        parts = token.split(".")
        if len(parts) == 3:
            payload = parts[1]
            padded = payload + "=" * ((4 - len(payload) % 4) % 4)
            decoded = base64.b64decode(padded).decode("utf-8")
            data = json.loads(decoded)
            return {"id": data.get("sub"), "role": data.get("role")}
    except Exception as e:
        print(f"Token decode error: {e}")
    return None

def api_call(method, path, data=None, token=None):
    url = BASE_URL + path
    headers = {'Content-Type': 'application/json'}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req_data = json.dumps(data).encode('utf-8') if data else None
    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            res_body = response.read().decode()
            if not res_body:
                return None
            return json.loads(res_body)
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        print(f"HTTP {e.code}: {body}")
        raise e

def wait_for_status(order_id, expected_status, token, max_attempts=15, poll_interval=2, endpoint="/api/v1/orders"):
    """Poll an endpoint until order reaches expected status."""
    for i in range(max_attempts):
        time.sleep(poll_interval)
        try:
            resp = api_call("GET", f"{endpoint}/{order_id}", token=token)
            status = resp.get("data", {}).get("status", "")
            log_info(f"Attempt {i+1}/{max_attempts}: {status}")
            if status == expected_status:
                return True
        except Exception:
            pass
    return False

def main():
    print("\n" + "="*55)
    print("   FOOD DELIVERY - FULL E2E FLOW TEST")
    print("="*55 + "\n")
    tokens = {}

    # ── Step 1: Auth ──────────────────────────────────────────────────────────
    print("── STEP 1: Authentication ───────────────────────────────")
    users = [
        {"email": "customer@test.com", "password": "Test@12345", "role": "CUSTOMER"},
        {"email": "owner@test.com",    "password": "Test@12345", "role": "RESTAURANT_OWNER"},
        {"email": "driver@test.com",   "password": "Test@12345", "role": "DRIVER"},
    ]
    for u in users:
        try:
            api_call("POST", "/api/v1/auth/register", {
                "email": u["email"], "password": u["password"],
                "fullName": "Test User", "role": u["role"]
            })
        except Exception:
            pass  # 409 = already exists, ignore
        res = api_call("POST", "/api/v1/auth/login", {"email": u["email"], "password": u["password"]})
        tokens[u["role"]] = res.get("data", {}).get("access_token", "")

    customer_token = tokens["CUSTOMER"]
    owner_token    = tokens["RESTAURANT_OWNER"]
    driver_token   = tokens["DRIVER"]

    driver_info = get_user_info(driver_token)
    driver_id   = driver_info.get("id")
    log_ok(f"Auth OK - Driver ID: {driver_id}")

    # ── Step 2: Get Restaurant & Menu ─────────────────────────────────────────
    print("\n── STEP 2: Get Restaurant & Menu ────────────────────────")
    restaurants = api_call("GET", "/api/v1/restaurants?page=0&size=10", token=customer_token)["data"]
    restaurant_id, item_id, restaurant_name = None, None, None
    for r in restaurants:
        menu = api_call("GET", f"/api/v1/restaurants/{r['id']}/menu", token=customer_token)["data"]
        for cat in menu.get("categories", []):
            if cat.get("items"):
                item_id       = cat["items"][0]["id"]
                item_name     = cat["items"][0]["name"]
                restaurant_id = r["id"]
                restaurant_name = r["name"]
                break
        if item_id:
            break

    if not item_id:
        log_err("Could not find any restaurant with menu items! Run 'make seed' first.")
        sys.exit(1)
    log_ok(f"Restaurant: {restaurant_name}")
    log_ok(f"Item: {item_name} (id: {item_id})")

    # ── Step 3: Connect Driver WebSocket (via query param) ────────────────────
    print("\n── STEP 3: Driver WebSocket ──────────────────────────────")
    # Use driver_id as query param (supported fallback in dispatch-service WebSocket handler)
    # This is the recommended way when Kong's jwt-to-headers plugin handles the WS auth
    ws_url = f"{WS_BASE_URL}/ws/driver/location?driver_id={driver_id}"
    ws_connected = False
    ws_conn = None
    try:
        ws_conn = websocket.create_connection(ws_url, timeout=10)
        # Immediately send location near restaurant Phở Hà Nội 36 (10.7731, 106.703)
        ws_conn.send(json.dumps({"lat": 10.7731, "lng": 106.703}))
        ws_connected = True
        log_ok(f"Driver WebSocket connected, location broadcasted near restaurant")
    except Exception as e:
        log_err(f"WebSocket connection failed: {e}")
        log_info("Driver dispatch matching will not work (no driver available in Redis).")
        log_info("Proceeding with manual pickup/deliver instead.")

    # ── Step 4: Create Order ──────────────────────────────────────────────────
    print("\n── STEP 4: Place Order ───────────────────────────────────")
    order_resp = api_call("POST", "/api/v1/orders", {
        "restaurant_id": restaurant_id,
        "items": [{"item_id": item_id, "quantity": 2}],
        "delivery_address": {
            "address_line": "123 Nguyễn Văn Cừ, Quận 5, TP.HCM",
            "lat": 10.7624,
            "lng": 106.6812
        }
    }, token=customer_token)
    order_id = order_resp["data"]["orderId"]
    log_ok(f"Order created: {order_id}")

    # ── Step 5: Wait for PAID (Saga: Order → Payment → Order) ────────────────
    print("\n── STEP 5: Waiting for PAID status (Kafka Saga) ─────────")
    if not wait_for_status(order_id, "PAID", customer_token, max_attempts=15, poll_interval=2):
        log_err("Order was NOT PAID in time. Check Kafka/Payment service.")
        if ws_conn: ws_conn.close()
        sys.exit(1)
    log_ok("Order is PAID!")

    # ── Step 6: Restaurant Accept & Ready ─────────────────────────────────────
    print("\n── STEP 6: Restaurant accepts & marks READY ─────────────")
    api_call("POST", f"/api/v1/restaurants/{restaurant_id}/orders/{order_id}/accept", token=owner_token)
    log_ok("Restaurant ACCEPTED order")
    time.sleep(2)
    api_call("POST", f"/api/v1/restaurants/{restaurant_id}/orders/{order_id}/ready", token=owner_token)
    log_ok("Restaurant marked READY_FOR_PICKUP")

    # ── Step 7: Wait for Driver Assignment ────────────────────────────────────
    print("\n── STEP 7: Dispatch matching ─────────────────────────────")
    if ws_connected:
        # Keep sending location updates while waiting for assignment
        def keep_alive_location():
            for _ in range(20):
                try:
                    ws_conn.send(json.dumps({"lat": 10.7731, "lng": 106.703}))
                    time.sleep(5)
                except Exception:
                    break
        threading.Thread(target=keep_alive_location, daemon=True).start()

    assigned = wait_for_status(order_id, "DRIVER_ASSIGNED", customer_token,
                                max_attempts=15, poll_interval=3, endpoint="/api/v1/delivery")
    if assigned:
        log_ok("Driver ASSIGNED by dispatch!")
    else:
        log_info("Auto-dispatch did not match (driver may not be in range). Proceeding manually.")

    # ── Step 8: Driver Pickup & Deliver ───────────────────────────────────────
    print("\n── STEP 8: Driver Pickup ─────────────────────────────────")
    time.sleep(2)
    pickup_resp = api_call("PATCH", f"/api/v1/delivery/{order_id}/pickup", token=driver_token)
    if pickup_resp and pickup_resp.get("success"):
        log_ok("Driver PICKED UP order")
    else:
        log_err(f"Pickup failed: {pickup_resp}")

    print("\n── STEP 9: Driver Deliver ────────────────────────────────")
    time.sleep(3)
    deliver_resp = api_call("PATCH", f"/api/v1/delivery/{order_id}/deliver", token=driver_token)
    if deliver_resp and deliver_resp.get("success"):
        log_ok("Driver DELIVERED order")
    else:
        log_err(f"Deliver failed: {deliver_resp}")

    # Close WebSocket
    if ws_conn:
        ws_conn.close()

    # ── Final Status Check ────────────────────────────────────────────────────
    print("\n── STEP 10: Final Verification ──────────────────────────")
    time.sleep(2)
    final_order = api_call("GET", f"/api/v1/orders/{order_id}", token=customer_token)
    final_status = final_order["data"]["status"]
    final_del = api_call("GET", f"/api/v1/delivery/{order_id}", token=customer_token)
    final_del_status = final_del["data"]["status"]

    print(f"\n{'='*55}")
    print(f"   Order Status:    {final_status}")
    print(f"   Delivery Status: {final_del_status}")
    print(f"{'='*55}")

    if final_status == "DELIVERED" and final_del_status == "DELIVERED":
        log_ok("🎉 FULL E2E TEST PASSED SUCCESSFULLY!")
        print()
        return 0
    else:
        log_err(f"Test did not reach DELIVERED state. Order={final_status}, Delivery={final_del_status}")
        print()
        return 1

if __name__ == "__main__":
    sys.exit(main())
