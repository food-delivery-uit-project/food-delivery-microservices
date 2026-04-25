# Food Delivery Microservices Platform

A production-grade food delivery system built with **Event-Driven Microservices Architecture**, deployed on **Kubernetes (Azure AKS)** with full **GitOps** and **Observability** stack.

## Architecture Overview

| Service              | Tech                 | Internal Architecture            | Description                                         |
| -------------------- | -------------------- | -------------------------------- | --------------------------------------------------- |
| User Service         | Java (Spring Boot)   | Simplified Layered               | Authentication, Authorization (JWT), User Profiles  |
| Restaurant Service   | Java (Spring Boot)   | Simplified Layered               | Restaurant & Menu management (PostgreSQL JSONB)     |
| Order Service        | Java (Spring Boot)   | **Hexagonal (Ports & Adapters)** | Order lifecycle, State Machine, Saga coordination   |
| Payment Service      | Java (Spring Boot)   | **Hexagonal (Ports & Adapters)** | Payment processing, Refunds, Compensating Tx        |
| Dispatch Service     | Go                   | Idiomatic Go (Clean)             | Real-time driver tracking, GPS matching (Redis Geo) |
| Notification Service | Node.js (TypeScript) | Simple Modular                   | Real-time push via SSE/WebSocket                    |

**Infrastructure:** Kong API Gateway · Apache Kafka · PostgreSQL · Redis · Kubernetes · ArgoCD · Prometheus · Grafana · Loki · Jaeger

## Quick Start

### Prerequisites

-   Docker Desktop (with Kubernetes enabled) or [Kind](https://kind.sigs.k8s.io/)
-   Python 3.10+
-   Java 21 (JDK, must include `javac`)
-   Go 1.22+
-   Node.js 20 LTS
-   kubectl, Helm 3, Skaffold

### Preflight Check (Recommended)

```bash
java -version
javac -version
docker --version
kubectl version --client
helm version
kind version
skaffold version
```

If all tools are available, run the full repository gate:

```bash
make verify-setup
```

Expected success marker:

```text
Setup verification completed successfully.
```

### Local Development Setup

```bash
# 1. Clone the repository
git clone https://github.com/<org>/food-delivery-microservices.git
cd food-delivery-microservices

# 2. Create local K8s cluster and install shared infrastructure
make local-setup

# 3. Verify setup quality gates (tests, lint, Helm)
make verify-setup

# 4. Start developing a specific service
make dev svc=order-service

# 5. Run tests for your current service
make test svc=order-service

# 6. Check health of all services
make health-check

# 7. Load sample data
make seed
```

Alternative local infra (without Kind/Skaffold) for quick component testing:

```bash
docker compose up -d
```

### Available Make Commands

```bash
make help           # Show all available commands
make local-setup    # Setup local K8s + infra (Kafka, PostgreSQL, Redis, Kong)
make local-down     # Tear down local cluster
make dev svc=<name> # Dev mode with hot-reload (Skaffold)
make test svc=<name># Run tests for a service
make test-all       # Run tests for all services
make lint svc=<name># Run linter for a service
make lint-all       # Run linter for all services
make verify-setup   # Run full setup readiness checks
make logs svc=<name># Tail logs from K8s
make seed           # Load sample data
make health-check   # Check all services health
```

## Documentation

| Category          | Document                                                         | Description                                            |
| ----------------- | ---------------------------------------------------------------- | ------------------------------------------------------ |
| **Architecture**  | [SADD](docs/architecture/SADD.md)                                | System Architecture Design Document                    |
| **Architecture**  | [ADRs](docs/architecture/adr/)                                   | Architecture Decision Records                          |
| **Development**   | [Developer Guide](docs/development/DEVELOPER_GUIDE.md)           | Onboarding, local setup, workflow                      |
| **Development**   | [API Style Guide](docs/development/API_STYLE_GUIDE.md)           | REST conventions, error format, pagination             |
| **Development**   | [Testing Strategy](docs/development/TESTING_STRATEGY.md)         | Testing pyramid, tools, coverage                       |
| **Development**   | [Database Guide](docs/development/DATABASE_GUIDE.md)             | Migrations (Flyway), naming, JSONB                     |
| **Development**   | [Java Services Quick Start](docs/development/SWE_QUICK_START.md) | Implementation patterns and examples for Java services |
| **API Contracts** | [OpenAPI Specs](docs/api/)                                       | Source of truth for all service APIs                   |
| **Operations**    | [Deployment Guide](docs/operations/DEPLOYMENT_GUIDE.md)          | Azure AKS, ArgoCD, Helm                                |
| **Operations**    | [Runbook](docs/operations/RUNBOOK.md)                            | Incident response procedures                           |
| **Operations**    | [Monitoring Guide](docs/operations/MONITORING_GUIDE.md)          | Alerts, dashboards, SLI/SLO                            |

## Project Structure

```
├── services/              # Microservices source code
│   ├── user-service/      # Java (Spring Boot)
│   ├── restaurant-service/# Java (Spring Boot)
│   ├── order-service/     # Java (Spring Boot) - Hexagonal
│   ├── payment-service/   # Java (Spring Boot) - Hexagonal
│   ├── dispatch-service/  # Go
│   └── notification-service/ # Node.js (TypeScript)
├── deployments/           # K8s manifests, Helm charts, ArgoCD
├── docs/                  # All documentation
├── scripts/               # Python automation scripts
└── .github/workflows/     # CI/CD pipelines
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for Git workflow, commit conventions, and PR guidelines.

## License

This project is for educational purposes.
