# Developer Guide

## 1. Prerequisites

| Tool           | Version  | Install                                                  |
| -------------- | -------- | -------------------------------------------------------- |
| Docker Desktop | Latest   | [docker.com](https://docker.com)                         |
| Java JDK       | 21 (LTS) | `sdk install java 21-tem` (sdkman)                       |
| Maven          | 3.9+     | Bundled with Maven Wrapper (`./mvnw`)                    |
| Go             | 1.22+    | [go.dev](https://go.dev/dl/)                             |
| Node.js        | 20 LTS   | `nvm install 20`                                         |
| Python         | 3.10+    | System or `pyenv`                                        |
| kubectl        | Latest   | [kubernetes.io](https://kubernetes.io/docs/tasks/tools/) |
| Helm           | 3.x      | `brew install helm` / `choco install kubernetes-helm`    |
| Kind           | Latest   | `go install sigs.k8s.io/kind@latest`                     |
| Skaffold       | Latest   | [skaffold.dev](https://skaffold.dev/docs/install/)       |

Quick validation before first run:

```bash
java -version
javac -version
docker --version
kubectl version --client
helm version
kind version
skaffold version
```

If `javac` is missing, install JDK (not only JRE).

## 2. First-Time Setup

```bash
# 1. Clone the repo
git clone https://github.com/<org>/food-delivery-microservices.git
cd food-delivery-microservices

# 2. Create local K8s cluster and install infrastructure
make local-setup
# This runs scripts/local_setup.py which:
#   - Creates a Kind cluster
#   - Installs PostgreSQL, Redis, Kafka via Helm
#   - Installs Kong Ingress Controller
#   - Creates app secrets (db-credentials in food-app namespace)
#   - Creates namespaces (food-app, databases, kafka, observability)

# 3. Verify everything is running
kubectl get pods --all-namespaces

# 4. Verify setup quality gates
make verify-setup

# 5. Load sample data (restaurants, menus, test users)
make seed
```

## 3. Daily Development Workflow

### Working on a Java Service

```bash
# 1. Create a feature branch
git checkout -b feature/FD-123-add-order-cancel

# 2. Start dev mode (Skaffold watches for changes and auto-deploys)
make dev svc=order-service

# 3. Code changes are automatically:
#    - Compiled (Maven)
#    - Docker image rebuilt
#    - Deployed to local K8s cluster
#    - Port-forwarded to localhost

# 4. Run tests
make test svc=order-service

# 5. Run linter
make lint svc=order-service

# 6. View logs
make logs svc=order-service

# 7. Commit and push
git add .
git commit -m "feat(order): add order cancellation endpoint"
git push origin feature/FD-123-add-order-cancel
```

### Working on Go Service (dispatch-service)

```bash
make dev svc=dispatch-service
# Same workflow as above, Skaffold handles Go builds
```

### Working on Node.js Service (notification-service)

```bash
make dev svc=notification-service
# Same workflow, Skaffold handles npm build
```

## 4. Accessing Services Locally

| Service              | Local URL (via port-forward) |
| -------------------- | ---------------------------- |
| Kong Gateway         | http://localhost:8000        |
| User Service         | http://localhost:8001        |
| Restaurant Service   | http://localhost:8002        |
| Order Service        | http://localhost:8003        |
| Payment Service      | http://localhost:8004        |
| Dispatch Service     | http://localhost:8005        |
| Notification Service | http://localhost:8006        |
| Grafana              | http://localhost:3000        |
| Jaeger UI            | http://localhost:16686       |

## 5. Environment Configuration

### Java Services (Spring Boot)

Configuration via environment variables (injected by K8s ConfigMap):

```yaml
# K8s ConfigMap values → Spring Boot properties
SPRING_DATASOURCE_URL: jdbc:postgresql://postgresql.databases.svc:5432/order_db
SPRING_DATASOURCE_USERNAME: postgres
SPRING_DATASOURCE_PASSWORD: <from-k8s-secret>
SPRING_KAFKA_BOOTSTRAP_SERVERS: food-delivery-kafka-kafka-bootstrap.kafka.svc:9092
```

### Go Service

```go
// internal/config/config.go
type Config struct {
    ServerPort     string `env:"SERVER_PORT" envDefault:"8080"`
    RedisAddr      string `env:"REDIS_ADDR" envDefault:"redis.databases.svc:6379"`
    KafkaBrokers   string `env:"KAFKA_BROKERS" envDefault:"food-delivery-kafka-kafka-bootstrap.kafka.svc:9092"`
    OrderServiceURL string `env:"ORDER_SERVICE_URL" envDefault:"http://order-service.food-app.svc:8080"`
}
```

### Node.js Service

```typescript
// src/config/index.ts
export const config = {
    port: process.env.PORT || 8080,
    kafkaBrokers: (
        process.env.KAFKA_BROKERS ||
        "food-delivery-kafka-kafka-bootstrap.kafka.svc:9092"
    ).split(","),
    redisUrl: process.env.REDIS_URL || "redis://redis.databases.svc:6379",
};
```

## 6. Troubleshooting

| Problem                                                           | Solution                                                                                                                                                                 |
| ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Pod CrashLoopBackOff                                              | `kubectl describe pod <name> -n food-app` then check events                                                                                                              |
| DB connection refused                                             | Verify PostgreSQL pod is running: `kubectl get pods -n databases`                                                                                                        |
| Kafka consumer not receiving                                      | Check consumer group: `kubectl exec food-delivery-kafka-kafka-0 -n kafka -- kafka-consumer-groups.sh --bootstrap-server food-delivery-kafka-kafka-bootstrap:9092 --list` |
| Skaffold build fails                                              | Check Dockerfile, ensure base image is accessible                                                                                                                        |
| Port already in use                                               | `lsof -i :<port>` then kill process                                                                                                                                      |
| `make verify-setup` fails with `release version 21 not supported` | You are using JRE only. Install JDK 21 and verify with `javac -version`                                                                                                  |
| `user-service` test prints many Kafka warnings/timeouts           | In local tests this can be non-fatal. Use final Maven summary as source of truth: `Tests run: 1, Failures: 0, Errors: 0` and `BUILD SUCCESS`                             |

## 7. Key References

-   [API Style Guide](API_STYLE_GUIDE.md) — REST conventions, error format
-   [Testing Strategy](TESTING_STRATEGY.md) — Testing pyramid, examples
-   [Database Guide](DATABASE_GUIDE.md) — Migrations, naming conventions
-   [OpenAPI Specs](../api/) — API contracts (source of truth)
