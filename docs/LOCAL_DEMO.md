# 🚀 Food Delivery System — Local Demo Guide

This guide covers running the full Food Delivery Microservices system locally.

You have two options for the demo:
1. **Local Kubernetes (Kind)** — **RECOMMENDED**. Full production-like architecture with Ingress, autoscaling, and complete observability stack.
2. **Docker Compose** — Quick development setup.

---

## Option 1: Local Kubernetes (RECOMMENDED)

This option spins up a local Kubernetes cluster using `kind`, deploys all infrastructure (PostgreSQL, Redis, Kafka), the observability stack (Prometheus, Grafana, Loki, Jaeger), and all application microservices using Helm.

### Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Docker Desktop | v24+ | `docker --version` |
| kind | v0.23+ | `kind version` |
| kubectl | any | `kubectl version --client` |
| helm | v3.15+ | `helm version` |
| python3 | 3.10+ | `python3 --version` |

### Step 1: Create Cluster & Install Infrastructure

Run the automated setup script. This will create the Kind cluster, namespaces, infrastructure, and observability tools.

```bash
make local-setup
```
*(Note: This might take 10-15 minutes on the first run as it pulls multiple Helm charts and Docker images).*

### Step 2: Deploy Microservices

Deploy all 6 microservices (User, Restaurant, Order, Payment, Dispatch, Notification) using the umbrella Helm chart:

```bash
make helm-deploy
```

Verify everything is running:
```bash
make k8s-status
```

### Step 3: Access the System

#### API Gateway (Kong)
All services are routed through Kong API Gateway on port 8000.
* API Base URL: `http://localhost:8000`

#### Observability Stack
Open all monitoring tools in the background:
```bash
make observe-all
```
* **Grafana**: http://localhost:3000 (admin / food-delivery-admin)
* **Jaeger**: http://localhost:16686
* **Prometheus**: http://localhost:9090

### Step 4: Run E2E Test

Once all pods are `Running`, load seed data and run an automated E2E test to verify the Saga flow:

```bash
# Load users, restaurants, drivers
make seed

# Run the end-to-end test script
/tmp/e2e_test.sh
```

*(You can also use the manual cURL commands listed in the Docker Compose section below, just change port `8001/8002/etc` to `8000`).*

### Teardown

```bash
make local-down
```

---

## Option 2: Docker Compose (Quick Dev)

For rapid iteration without Kubernetes overhead.

### 1. Start the Stack

```bash
# App services + Infrastructure only
docker compose up -d

# OR: App + Infrastructure + Observability
docker compose --profile observability up -d
```

**Expected ports:**
* User: 8001
* Restaurant: 8002
* Order: 8003
* Payment: 8004
* Dispatch: 8005
* Notification: 8006
* Grafana: 3000
* Jaeger: 16686
* Prometheus: 9090

### 2. Load Seed Data

```bash
python3 scripts/seed_data.py
```

### 3. Demo: End-to-End Order Flow (Saga)

#### Step 1: Login as Customer

```bash
CUSTOMER_TOKEN=$(curl -s -X POST http://localhost:8001/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@test.com","password":"Test@12345"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['access_token'])")

echo "Customer token: ${CUSTOMER_TOKEN:0:50}..."
```

#### Step 2: Browse Restaurants & Menu

```bash
# Get Restaurants
curl -s http://localhost:8002/api/v1/restaurants | python3 -m json.tool

RESTAURANT_ID="<paste-restaurant-id-here>"

# Get Menu
curl -s http://localhost:8002/api/v1/restaurants/$RESTAURANT_ID/menu | python3 -m json.tool
```

#### Step 3: Register Driver (for dispatch to work)

```bash
# Login as driver
DRIVER_TOKEN=$(curl -s -X POST http://localhost:8001/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"driver@test.com","password":"Test@12345"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['access_token'])")

# Extract driver ID
DRIVER_ID=$(python3 -c "
import base64, json, sys
token = '$DRIVER_TOKEN'
payload = token.split('.')[1]
payload += '=' * (4 - len(payload) % 4)
decoded = json.loads(base64.b64decode(payload))
print(decoded.get('sub', decoded.get('userId', 'unknown')))
")

# Add driver to Redis (as AVAILABLE near Ho Chi Minh City)
docker exec fd-redis redis-cli GEOADD active_drivers 106.7030 10.7731 "$DRIVER_ID"
docker exec fd-redis redis-cli HSET driver:$DRIVER_ID status "AVAILABLE" last_seen "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "Driver is now AVAILABLE and online"
```

#### Step 4: Place an Order

```bash
ITEM_ID="<paste-item-id-here>"

ORDER_RESPONSE=$(curl -s -X POST http://localhost:8003/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $(python3 -c "
import base64, json
token = '$CUSTOMER_TOKEN'
payload = token.split('.')[1]
payload += '=' * (4 - len(payload) % 4)
decoded = json.loads(base64.b64decode(payload))
print(decoded.get('sub', decoded.get('userId', '')))
")" \
  -H "X-User-Role: CUSTOMER" \
  -d "{
    \"restaurantId\": \"$RESTAURANT_ID\",
    \"items\": [{\"item_id\": \"$ITEM_ID\", \"quantity\": 2}],
    \"deliveryAddress\": {
      \"addressLine\": \"123 Lê Lợi, Q1, TP.HCM\",
      \"lat\": 10.7765,
      \"lng\": 106.7009
    },
    \"specialInstructions\": \"Không hành\"
  }")

ORDER_ID=$(echo $ORDER_RESPONSE | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['orderId'])")
echo "Order ID: $ORDER_ID"
```

#### Step 5: Watch the Saga Flow & Tracing

1. **Watch Logs:**
   ```bash
   docker compose logs -f order-service payment-service restaurant-service dispatch-service
   ```
2. **Watch Traces:**
   Open Jaeger (`http://localhost:16686`), select `order-service`, and find traces. You will see the distributed trace covering Order API, Database writes, Kafka publishes, and async Payment processing.

#### Step 6: Simulate Restaurant Accepting Order

```bash
OWNER_TOKEN=$(curl -s -X POST http://localhost:8001/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"owner@test.com","password":"Test@12345"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['access_token'])")

# Accept order
curl -s -X PATCH http://localhost:8003/api/v1/orders/$ORDER_ID/accept \
  -H "X-User-Role: RESTAURANT_OWNER"

# Mark food ready (triggers dispatch matching)
sleep 5
curl -s -X PATCH http://localhost:8003/api/v1/orders/$ORDER_ID/ready \
  -H "X-User-Role: RESTAURANT_OWNER"
```

### Stop Everything

```bash
docker compose down -v
```
