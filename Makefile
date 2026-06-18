# ============================================================================
# Food Delivery Microservices - Makefile
# Top-level commands for development, testing, and deployment
# ============================================================================

.PHONY: help dev test test-all lint lint-all logs local-setup local-down seed \
        health-check helm-lint helm-deploy verify-setup \
        observe-all observe-grafana observe-jaeger observe-prometheus observe-loki \
        observability-status k8s-status

# Default target
help: ## Show this help message
	@echo "Food Delivery Microservices - Available Commands"
	@echo "================================================"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-25s\033[0m %s\n", $$1, $$2}'

# ============================================================================
# Local K8s Development (Kind)
# ============================================================================

local-setup: ## Setup local K8s cluster (Kind) + infra + observability stack
	python3 scripts/local_setup.py setup

local-setup-infra: ## Setup K8s cluster + infra only (skip observability)
	python3 scripts/local_setup.py setup-infra

local-down: ## Tear down local K8s cluster
	python3 scripts/local_setup.py teardown

k8s-status: ## Show status of all pods in all namespaces
	kubectl get pods --all-namespaces -o wide

# ============================================================================
# Application Deployment (Helm)
# ============================================================================

helm-deploy: ## Deploy all application services via Helm umbrella chart
	helm upgrade --install food-delivery deployments/helm/ -n food-app --create-namespace \
		-f deployments/local/values-local.yaml --wait

helm-undeploy: ## Uninstall all application services
	helm uninstall food-delivery -n food-app

build-all: ## Build all docker images
	@for svc in user-service restaurant-service order-service payment-service dispatch-service notification-service; do \
		echo "Building $$svc..."; \
		docker build -t food-delivery/$$svc:latest services/$$svc; \
	done

kind-load: build-all ## Build and load all images into Kind
	@for svc in user-service restaurant-service order-service payment-service dispatch-service notification-service; do \
		echo "Loading $$svc into Kind..."; \
		kind load docker-image food-delivery/$$svc:latest --name food-delivery; \
	done

deploy-infra: ## Deploy infrastructure (Kafka, PostgreSQL, Redis, Kong) to K8s
	helm upgrade --install postgresql bitnami/postgresql -n databases --create-namespace \
		--set auth.postgresPassword=postgres --set primary.persistence.size=1Gi --wait
	helm upgrade --install redis bitnami/redis -n databases \
		--set auth.enabled=false --set replica.replicaCount=0 --wait
	helm upgrade --install strimzi-operator strimzi/strimzi-kafka-operator --version 0.40.0 -n kafka --create-namespace --wait
	kubectl apply -f deployments/infrastructure/kafka/ -n kafka
	
	helm upgrade --install sealed-secrets bitnami/sealed-secrets -n kube-system --wait
	
	@echo "Starting infrastructure components..."
	helm repo add bitnami https://charts.bitnami.com/bitnami
	helm repo add kong https://charts.konghq.com
	helm repo add grafana https://grafana.github.io/helm-charts
	helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
	helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
	helm repo update
	
	helm upgrade --install kong kong/kong -n food-app --create-namespace -f deployments/infrastructure/kong/values.yaml
	kubectl apply -f deployments/infrastructure/kong/plugins.yaml -n food-app
	kubectl apply -f deployments/infrastructure/security/network-policies.yaml -n food-app
	helm upgrade --install jaeger jaegertracing/jaeger \
		-n observability -f deployments/observability/jaeger/values.yaml --wait
	helm upgrade --install grafana grafana/grafana \
		-n observability -f deployments/observability/grafana/values.yaml --wait
	kubectl apply -f deployments/observability/otel-collector.yaml

deploy-observability: ## Deploy observability stack (Prometheus, Grafana, Loki, Jaeger, OTel)
	helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
		-n observability --create-namespace \
		-f deployments/observability/prometheus/values.yaml --wait
	kubectl apply -f deployments/observability/prometheus/alert-rules.yaml
	helm upgrade --install loki grafana/loki-stack \
		-n observability -f deployments/observability/loki/values.yaml --wait
	helm upgrade --install jaeger jaegertracing/jaeger \
		-n observability -f deployments/observability/jaeger/values.yaml --wait
	helm upgrade --install grafana grafana/grafana \
		-n observability -f deployments/observability/grafana/values.yaml --wait
	kubectl apply -f deployments/observability/otel-collector.yaml

observability-status: ## Show status of all observability pods
	@echo "=== Observability Stack Status ==="
	@kubectl get pods -n observability
	@echo ""
	@echo "=== ServiceMonitors ==="
	@kubectl get servicemonitors -n observability 2>/dev/null || echo "No ServiceMonitors found"
	@echo ""
	@echo "=== PrometheusRules ==="
	@kubectl get prometheusrules -n observability 2>/dev/null || echo "No PrometheusRules found"

# ============================================================================
# Observability Port-Forwarding (Access UIs locally)
# ============================================================================

observe-grafana: ## Port-forward Grafana UI → localhost:3000 (admin/food-delivery-admin)
	@echo "🌐 Grafana → http://localhost:3000  (admin / food-delivery-admin)"
	kubectl port-forward svc/grafana 3000:3000 -n observability

observe-jaeger: ## Port-forward Jaeger UI → localhost:16686
	@echo "🌐 Jaeger → http://localhost:16686"
	kubectl port-forward svc/jaeger-all-in-one-query 16686:16686 -n observability

observe-prometheus: ## Port-forward Prometheus UI → localhost:9090
	@echo "🌐 Prometheus → http://localhost:9090"
	kubectl port-forward svc/kube-prometheus-stack-prometheus 9090:9090 -n observability

observe-loki: ## Port-forward Loki → localhost:3100 (access via Grafana)
	@echo "🌐 Loki → http://localhost:3100  (use Grafana Explore for UI)"
	kubectl port-forward svc/loki 3100:3100 -n observability

observe-all: ## Port-forward ALL observability tools (runs in background)
	@echo "🚀 Starting all port-forwards..."
	@kubectl port-forward svc/grafana 3000:3000 -n observability &
	@kubectl port-forward svc/jaeger-all-in-one-query 16686:16686 -n observability &
	@kubectl port-forward svc/kube-prometheus-stack-prometheus 9090:9090 -n observability &
	@echo ""
	@echo "✅ Access:"
	@echo "  Grafana:    http://localhost:3000  (admin / food-delivery-admin)"
	@echo "  Jaeger:     http://localhost:16686"
	@echo "  Prometheus: http://localhost:9090"
	@echo ""
	@echo "  Press Ctrl+C to stop all port-forwards"
	@wait

# ============================================================================
# Testing
# ============================================================================

test: ## Run tests for a service (usage: make test svc=order-service)
	@if [ -z "$(svc)" ]; then echo "Usage: make test svc=<service-name>"; exit 1; fi
	@echo "Running tests for $(svc)..."
	@if [ -f "services/$(svc)/pom.xml" ]; then \
		cd services/$(svc) && chmod +x mvnw && ./mvnw test; \
	elif [ -f "services/$(svc)/go.mod" ]; then \
		cd services/$(svc) && go test ./...; \
	elif [ -f "services/$(svc)/package.json" ]; then \
		cd services/$(svc) && npm test; \
	else \
		echo "No supported test configuration for $(svc)"; exit 1; \
	fi

test-all: ## Run tests for all services
	@failed=0; \
	for dir in services/*/; do \
		svc=$$(basename $$dir); \
		echo "\n=== Testing $$svc ==="; \
		$(MAKE) test svc=$$svc || failed=1; \
	done; \
	if [ $$failed -ne 0 ]; then \
		echo "\nOne or more services failed tests."; \
		exit 1; \
	fi

# ============================================================================
# Linting
# ============================================================================

lint: ## Lint a service (usage: make lint svc=order-service)
	@if [ -z "$(svc)" ]; then echo "Usage: make lint svc=<service-name>"; exit 1; fi
	@echo "Linting $(svc)..."
	@if [ -f "services/$(svc)/pom.xml" ]; then \
		cd services/$(svc) && chmod +x mvnw && ./mvnw checkstyle:check; \
	elif [ -f "services/$(svc)/go.mod" ]; then \
		cd services/$(svc) && docker run --rm -v "$$(pwd):/app" -w /app golangci/golangci-lint:v1.60.3 golangci-lint run; \
	elif [ -f "services/$(svc)/package.json" ]; then \
		cd services/$(svc) && npm run lint; \
	else \
		echo "No supported lint configuration for $(svc)"; exit 1; \
	fi

lint-all: ## Run lint for all services
	@failed=0; \
	for dir in services/*/; do \
		svc=$$(basename $$dir); \
		echo "\n=== Linting $$svc ==="; \
		$(MAKE) lint svc=$$svc || failed=1; \
	done; \
	if [ $$failed -ne 0 ]; then \
		echo "\nOne or more services failed lint checks."; \
		exit 1; \
	fi

# ============================================================================
# Utilities
# ============================================================================

logs: ## Tail logs for a service in K8s (usage: make logs svc=order-service)
	@if [ -z "$(svc)" ]; then echo "Usage: make logs svc=<service-name>"; exit 1; fi
	kubectl logs -f -l app=$(svc) -n food-app --tail=100

e2e-test: ## Run end-to-end tests
	@echo "Running E2E tests..."
	python3 scripts/test_e2e.py

seed: ## Load sample data (works with both docker-compose and K8s via port-forward)
	python3 scripts/seed_data.py

health-check: ## Check health of all services (docker-compose mode)
	python3 scripts/health_check.py

# ============================================================================
# Docker (local build)
# ============================================================================

build: ## Build Docker image for a service (usage: make build svc=order-service)
	@if [ -z "$(svc)" ]; then echo "Usage: make build svc=<service-name>"; exit 1; fi
	docker build -t food-delivery/$(svc):latest services/$(svc)

# Docker Compose for quick local dev (without K8s)
compose-up: ## Start all services with Docker Compose (quick dev mode)
	docker compose up -d

compose-down: ## Stop Docker Compose services
	docker compose down

# ============================================================================
# Helm / Validation
# ============================================================================

helm-lint: ## Lint all Helm charts
	@failed=0; \
	for chart in deployments/helm/charts/*/; do \
		echo "Linting $$chart..."; \
		helm lint $$chart || failed=1; \
	done; \
	if [ $$failed -ne 0 ]; then \
		echo "\nOne or more Helm charts failed lint checks."; \
		exit 1; \
	fi

helm-template: ## Dry-run render all Helm templates
	helm template food-delivery deployments/helm/ --debug > /dev/null
	@echo "✅ Helm templates rendered successfully"

verify-setup: ## Run full setup readiness checks (tests, lint, Helm)
	@echo "Running setup verification checks..."
	@$(MAKE) helm-lint
	@$(MAKE) helm-template
	@echo "\nSetup verification completed successfully."

# Development mode with Skaffold hot-reload
dev: ## Start dev mode for a service with hot-reload via Skaffold (usage: make dev svc=order-service)
	@if [ -z "$(svc)" ]; then echo "Usage: make dev svc=<service-name>"; exit 1; fi
	cd services/$(svc) && skaffold dev --port-forward
