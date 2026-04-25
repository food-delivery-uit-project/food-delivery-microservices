# ============================================================================
# Food Delivery Microservices - Makefile
# Top-level commands for development, testing, and deployment
# ============================================================================

.PHONY: help dev test test-all lint lint-all logs local-setup local-down seed health-check helm-lint verify-setup

# Default target
help: ## Show this help message
	@echo "Food Delivery Microservices - Available Commands"
	@echo "================================================"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ============================================================================
# Local Development
# ============================================================================

local-setup: ## Setup local K8s cluster (Kind) + install all infrastructure
	python3 scripts/local_setup.py setup

local-down: ## Tear down local K8s cluster
	python3 scripts/local_setup.py teardown

dev: ## Start dev mode for a service (usage: make dev svc=order-service)
	@if [ -z "$(svc)" ]; then echo "Usage: make dev svc=<service-name>"; exit 1; fi
	cd services/$(svc) && skaffold dev --port-forward

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

logs: ## Tail logs for a service (usage: make logs svc=order-service)
	@if [ -z "$(svc)" ]; then echo "Usage: make logs svc=<service-name>"; exit 1; fi
	kubectl logs -f -l app=$(svc) -n food-app --tail=100

seed: ## Load sample data into the system
	python3 scripts/seed_data.py

health-check: ## Check health of all services
	python3 scripts/health_check.py

# ============================================================================
# Docker
# ============================================================================

build: ## Build Docker image for a service (usage: make build svc=order-service)
	@if [ -z "$(svc)" ]; then echo "Usage: make build svc=<service-name>"; exit 1; fi
	docker build -t food-delivery/$(svc):latest services/$(svc)

# ============================================================================
# Helm / Deployment
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

verify-setup: ## Run setup readiness checks (tests, lint, Helm charts)
	@echo "Running setup verification checks..."
	@$(MAKE) test-all
	@$(MAKE) lint-all
	@$(MAKE) helm-lint
	@echo "\nSetup verification completed successfully."

deploy-infra: ## Deploy infrastructure (Kafka, PostgreSQL, Redis) to K8s
	helm upgrade --install postgresql bitnami/postgresql -n databases --create-namespace -f deployments/infrastructure/postgresql/values.yaml
	helm upgrade --install redis bitnami/redis -n databases --create-namespace -f deployments/infrastructure/redis/values.yaml
	kubectl apply -f deployments/infrastructure/kafka/ -n kafka

deploy-apps: ## Deploy all application services via Helm
	helm upgrade --install food-delivery deployments/helm/ -n food-app --create-namespace
