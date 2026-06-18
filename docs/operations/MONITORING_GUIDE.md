# Monitoring Guide

## 1. Observability Stack

| Component | Tool | Purpose | Access |
|-----------|------|---------|--------|
| Metrics | Prometheus + Grafana | Scrape & visualize metrics from all services | `make observe-grafana` |
| Logs | Promtail → Loki → Grafana | Aggregate structured JSON logs | Grafana → Explore → Loki |
| Traces | OTel Agent → OTel Collector → Jaeger | Distributed request tracing | `make observe-jaeger` |
| Alerts | Alertmanager | Fire alerts to Slack/PagerDuty | Integrated with Prometheus |

## 2. Quick Start — Access Observability (Local K8s)

```bash
# One command to open all tools
make observe-all

# Or individually:
make observe-grafana     # http://localhost:3000 (admin / food-delivery-admin)
make observe-jaeger      # http://localhost:16686
make observe-prometheus  # http://localhost:9090
```

**Quick Start — Docker Compose with Observability:**

```bash
docker compose --profile observability up -d
# Grafana:    http://localhost:3000  (admin/admin)
# Jaeger:     http://localhost:16686
# Prometheus: http://localhost:9090
```

### Verify Stack is Running

```bash
# K8s
make observability-status

# Should show:
# kube-prometheus-stack-prometheus  Running
# alertmanager-kube-prometheus...   Running
# grafana                           Running
# loki-0                            Running
# loki-promtail-xxxx                Running
# jaeger-all-in-one-xxxx            Running
# otel-collector-xxxx               Running
```

## 3. Grafana Dashboards

After opening Grafana (`make observe-grafana`):

### Pre-configured Dashboards

| Dashboard | Description | Path in Grafana |
|-----------|-------------|-----------------|
| Food Delivery Overview | Request rate, error rate, latency per service | Food Delivery / Overview |
| Kubernetes Cluster | Node CPU, Memory, Pod count | General / Kubernetes Cluster |
| JVM Micrometer | Java heap, GC, threads | General / JVM Micrometer |
| Kafka Overview | Consumer lag, throughput | General / Kafka |

### Browse Logs (Loki)

```
# In Grafana → Explore → Select "Loki" datasource

# All logs from order-service
{service="order-service"}

# Error logs only (level=error)
{service="order-service"} | json | level = "error"

# Trace a specific request end-to-end
{service=~".+"} | json | trace_id = "YOUR_TRACE_ID"

# Find logs for a specific order
{service="order-service"} | json | order_id = "SOME-ORDER-UUID"

# High latency requests (> 500ms)
{service=~".+"} | json | duration_ms > 500
```

## 4. Jaeger — Distributed Tracing

### How to Use

1. Open Jaeger (`make observe-jaeger` → http://localhost:16686)
2. **Select Service** (e.g., `order-service`)
3. **Search** by operation, time range, or minimum duration
4. Click a trace → view the full span waterfall across services

### Example: Trace an Order Creation

After running the E2E test, search in Jaeger:
- Service: `order-service`
- Operation: `POST /api/v1/orders`

You will see spans across:
```
order-service  → POST /api/v1/orders              (HTTP handler)
  ↳ order-service → validateRestaurant            (REST call)
    ↳ restaurant-service → GET /api/v1/restaurants/{id}
  ↳ order-service → save Order                    (DB write)
  ↳ order-service → publish to Kafka              (Kafka produce)
    ↳ payment-service (async consumer)
```

### Correlation: Logs + Traces

In Grafana → Explore → Loki, when you see a `trace_id` in a log line, click the **Jaeger link** to jump directly to the trace. This is configured automatically via the Loki datasource's derived fields.

## 5. Prometheus — Metrics & Alerts

### Key PromQL Queries

```promql
# --- HTTP Request Rate (req/s) ---
sum by(service) (rate(http_server_requests_seconds_count{namespace="food-app"}[1m]))

# --- HTTP Error Rate (%) ---
sum by(service) (rate(http_server_requests_seconds_count{namespace="food-app", status=~"5.."}[5m]))
/
sum by(service) (rate(http_server_requests_seconds_count{namespace="food-app"}[5m]))

# --- P99 Latency ---
histogram_quantile(0.99,
  sum by(service, le) (
    rate(http_server_requests_seconds_bucket{namespace="food-app"}[5m])
  )
)

# --- Order Creation Rate ---
rate(http_server_requests_seconds_count{
  service="order-service",
  uri="/api/v1/orders",
  method="POST",
  status="201"
}[5m])

# --- JVM Heap Usage ---
jvm_memory_used_bytes{area="heap", service="order-service"}
/
jvm_memory_max_bytes{area="heap", service="order-service"}

# --- Kafka Consumer Lag ---
kafka_consumergroup_lag_sum

# --- Pod Restart Count ---
rate(kube_pod_container_status_restarts_total{namespace="food-app"}[5m]) * 60
```

## 6. SLI/SLO Definitions

| Service | SLI | SLO Target |
|---------|-----|-----------|
| Kong Gateway | Request success rate | ≥ 99.5% |
| Order Service | POST /orders success rate | ≥ 99% |
| Order Service | P99 latency (POST /orders) | < 500ms |
| Payment Service | Payment success rate | ≥ 98% |
| Dispatch Service | Driver match success rate | ≥ 95% |
| All Services | Pod restart count/hour | < 2 |
| Kafka | Consumer lag (messages) | < 1000 |

## 7. Alert Rules

Alerts are defined in `deployments/observability/prometheus/alert-rules.yaml`:

### Critical (Immediate Action)

| Alert | Condition | Impact |
|-------|-----------|--------|
| `ServiceDown` | Pod down > 1min | Service unavailable |
| `PodCrashLooping` | Restart rate > 3/5min | Service instability |
| `HighErrorRate` | HTTP 5xx > 5% for 2min | User-facing errors |
| `KafkaConsumerGroupLag` | Lag > 10,000 for 5min | Event processing delay |
| `OrderCreationSLOViolation` | Success rate < 99% | Core flow broken |
| `PaymentSuccessRateSLOViolation` | Success rate < 98% | Revenue impact |

### Warning

| Alert | Condition |
|-------|-----------|
| `HighLatencyP99` | P99 > 1s for 5min |
| `HighMemoryUsage` | Memory > 85% limit |
| `KafkaConsumerGroupLagWarning` | Lag > 1,000 |
| `DeploymentReplicasMismatch` | Available < Desired |

## 8. Structured Logging

All services output JSON structured logs. Example from `order-service`:

```json
{
  "timestamp": "2026-06-14T01:30:00Z",
  "level": "INFO",
  "service": "order-service",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "message": "Order created successfully",
  "order_id": "836bc40f-680e-4825-99aa-9713ebd189b0",
  "customer_id": "b57b6258-56d0-404f-9ab0-38338b7b5e98",
  "duration_ms": 45
}
```

### Loki Label Strategy

| Label | Source | Example |
|-------|--------|---------|
| `service` | Container name (strip `fd-`) | `order-service` |
| `level` | JSON field | `INFO`, `ERROR` |
| `container` | Docker container name | `fd-order-service` |
| `namespace` | K8s namespace | `food-app` |

## 9. OpenTelemetry Instrumentation

### Java Services (order, payment, user, restaurant)

The OTel Java Agent is bundled in the Docker image via:
```dockerfile
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/otel-agent.jar"
```

Key env vars (configured in Helm `values.yaml`):
```yaml
OTEL_SERVICE_NAME: order-service
OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector.observability.svc:4317
OTEL_RESOURCE_ATTRIBUTES: "deployment.environment=k8s-local"
```

The agent automatically instruments:
- Spring MVC HTTP handlers
- Spring Data JPA queries
- Spring Kafka producer/consumer
- Outbound HTTP calls (RestTemplate/WebClient)

### Go Service (dispatch-service)

Manual instrumentation via `go.opentelemetry.io/otel`. Traces are created for:
- Kafka consumer handlers
- Redis geo queries
- HTTP handler endpoints

### Trace Context Propagation

All Kafka messages include trace context in message headers (`traceparent`), enabling cross-service trace correlation even across async event boundaries.
