#!/usr/bin/env python3
"""
Local development environment setup/teardown script.
Creates a Kind K8s cluster and installs all infrastructure + observability components.

Usage:
  python3 scripts/local_setup.py setup       # Full setup (K8s + infra + observability)
  python3 scripts/local_setup.py setup-infra # Infrastructure only (no observability)
  python3 scripts/local_setup.py teardown    # Delete cluster
  python3 scripts/local_setup.py status      # Print cluster status
"""

import subprocess
import sys
import time
import shutil


CLUSTER_NAME = "food-delivery"
NAMESPACES = ["food-app", "databases", "kafka", "observability", "argocd"]

REQUIRED_TOOLS = ["docker", "kind", "kubectl", "helm"]


def run(cmd: str, check: bool = True, capture: bool = False) -> subprocess.CompletedProcess:
    """Execute a shell command."""
    print(f"  → {cmd}")
    return subprocess.run(
        cmd, shell=True, check=check, capture_output=capture, text=True
    )


def check_prerequisites() -> None:
    """Verify all required tools are installed."""
    print("\n🔍 Checking prerequisites...")
    missing = []
    for tool in REQUIRED_TOOLS:
        if shutil.which(tool) is None:
            missing.append(tool)

    if missing:
        print(f"❌ Missing tools: {', '.join(missing)}")
        print("Please install them before continuing. See docs/development/DEVELOPER_GUIDE.md")
        sys.exit(1)

    # Check Docker is running
    result = run("docker info", check=False, capture=True)
    if result.returncode != 0:
        print("❌ Docker is not running. Please start Docker Desktop.")
        sys.exit(1)

    print("✅ All prerequisites met.")


def cluster_exists() -> bool:
    """Check if the Kind cluster already exists."""
    result = run(f"kind get clusters", check=False, capture=True)
    return CLUSTER_NAME in result.stdout.split()


def create_cluster() -> None:
    """Create a Kind K8s cluster with port mappings for local access."""
    if cluster_exists():
        print(f"ℹ️  Cluster '{CLUSTER_NAME}' already exists, skipping creation.")
        return

    print(f"\n🚀 Creating Kind cluster '{CLUSTER_NAME}'...")

    # Write Kind config to file
    kind_config_path = "deployments/local/kind-config.yaml"
    run(f"kubectl config get-contexts", check=False, capture=True)  # just to warm kubectl

    run(f"kind create cluster --name {CLUSTER_NAME} --config {kind_config_path}")
    print("✅ Cluster created.")


def create_namespaces() -> None:
    """Create K8s namespaces."""
    print("\n📁 Creating namespaces...")
    for ns in NAMESPACES:
        run(f"kubectl create namespace {ns}", check=False)
    print("✅ Namespaces ready.")


def add_helm_repos() -> None:
    """Add required Helm repositories."""
    print("\n📦 Adding Helm repositories...")
    repos = {
        "bitnami": "https://charts.bitnami.com/bitnami",
        "kong": "https://charts.konghq.com",
        "prometheus-community": "https://prometheus-community.github.io/helm-charts",
        "grafana": "https://grafana.github.io/helm-charts",
        "strimzi": "https://strimzi.io/charts/",
        "jaegertracing": "https://jaegertracing.github.io/helm-charts",
    }
    for name, url in repos.items():
        run(f"helm repo add {name} {url}", check=False)
    run("helm repo update")
    print("✅ Helm repos ready.")


def install_infrastructure() -> None:
    """Install databases and message broker."""
    print("\n🗄️  Installing PostgreSQL...")
    run(
        "helm upgrade --install postgresql bitnami/postgresql "
        "-n databases "
        "--set auth.postgresPassword=postgres "
        "--set auth.database=user_db "
        "--set primary.persistence.size=1Gi "
        "--wait --timeout 120s",
        check=False,
    )

    print("\n🔴 Installing Redis...")
    run(
        "helm upgrade --install redis bitnami/redis "
        "-n databases "
        "--set auth.enabled=false "
        "--set master.persistence.size=500Mi "
        "--set replica.replicaCount=0 "
        "--wait --timeout 120s",
        check=False,
    )

    print("\n📨 Installing Kafka (Strimzi Operator)...")
    run(
        "helm upgrade --install strimzi-operator strimzi/strimzi-kafka-operator "
        "--version 0.40.0 "
        "-n kafka "
        "--wait --timeout 180s",
        check=False,
    )
    # Wait for operator to be ready, then apply Kafka cluster
    print("  Waiting for Strimzi operator to be ready...")
    run("kubectl wait --for=condition=ready pod -l name=strimzi-cluster-operator -n kafka --timeout=120s", check=False)
    time.sleep(5)
    run("kubectl apply -f deployments/infrastructure/kafka/ -n kafka", check=False)

    print("\n🦍 Installing Kong Ingress Controller...")
    run(
        "helm upgrade --install kong kong/ingress "
        "-n food-app "
        "--set gateway.service.type=NodePort "
        "--set gateway.service.nodePorts.http=30080 "
        "--wait --timeout 120s",
        check=False,
    )

    print("✅ Infrastructure installed.")


def install_observability() -> None:
    """Install full observability stack: Prometheus, Grafana, Loki, Jaeger, OTel Collector."""
    print("\n📊 Installing Observability Stack...")

    # 1. Prometheus Operator + kube-prometheus-stack
    print("  📈 Installing kube-prometheus-stack (Prometheus + Alertmanager)...")
    run(
        "helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack "
        "-n observability "
        "-f deployments/observability/prometheus/values.yaml "
        "--wait --timeout 300s",
        check=False,
    )

    # 2. Apply custom PrometheusRules (food-delivery alerts)
    print("  🔔 Applying alert rules...")
    run("kubectl apply -f deployments/observability/prometheus/alert-rules.yaml", check=False)

    # 3. Loki Stack (Loki + Promtail)
    print("  📋 Installing Loki Stack (Loki + Promtail)...")
    run(
        "helm upgrade --install loki grafana/loki-stack "
        "-n observability "
        "-f deployments/observability/loki/values.yaml "
        "--wait --timeout 180s",
        check=False,
    )

    # 4. Jaeger (all-in-one)
    print("  🔭 Installing Jaeger (all-in-one)...")
    run(
        "helm upgrade --install jaeger jaegertracing/jaeger "
        "-n observability "
        "-f deployments/observability/jaeger/values.yaml "
        "--wait --timeout 120s",
        check=False,
    )

    # 5. Grafana
    print("  📊 Installing Grafana...")
    run(
        "helm upgrade --install grafana grafana/grafana "
        "-n observability "
        "-f deployments/observability/grafana/values.yaml "
        "--wait --timeout 120s",
        check=False,
    )

    # 6. OTel Collector
    print("  🔗 Deploying OpenTelemetry Collector...")
    run("kubectl apply -f deployments/observability/otel-collector.yaml", check=False)

    print("✅ Observability stack installed.")


def create_databases() -> None:
    """Create additional databases in PostgreSQL."""
    print("\n🗃️  Creating databases...")
    # Wait for postgres to be ready
    run(
        "kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=postgresql -n databases --timeout=120s",
        check=False,
    )
    dbs = ["order_db", "restaurant_db", "payment_db"]
    for db in dbs:
        run(
            f"kubectl exec postgresql-0 -n databases -- "
            f"psql -U postgres -c \"CREATE DATABASE {db};\"",
            check=False,
        )
    print("✅ Databases created.")


def create_application_secrets() -> None:
    """Create required application secrets in the food-app namespace."""
    print("\n🔐 Creating application secrets...")
    run(
        "kubectl create secret generic db-credentials "
        "-n food-app "
        "--from-literal=POSTGRES_PASSWORD=postgres "
        "--dry-run=client -o yaml | kubectl apply -f -",
        check=False,
    )
    # JWT secret for user-service
    run(
        "kubectl create secret generic jwt-secret "
        "-n food-app "
        "--from-literal=JWT_SECRET=local-dev-jwt-secret-key-minimum-256-bits-required "
        "--dry-run=client -o yaml | kubectl apply -f -",
        check=False,
    )
    print("✅ Application secrets ready.")


def print_status() -> None:
    """Print cluster status summary and access URLs."""
    print("\n" + "=" * 65)
    print("🎉 Local K8s environment setup complete!")
    print("=" * 65)
    print("\nNamespace status:")
    run("kubectl get pods --all-namespaces --field-selector=status.phase!=Running 2>/dev/null || true")
    print("\n📖 Access URLs (after port-forward or NodePort):")
    print("  Services:       http://localhost:8000   (Kong Gateway - NodePort 30080)")
    print("  Grafana:        make observe-grafana    (localhost:3000)")
    print("  Jaeger UI:      make observe-jaeger     (localhost:16686)")
    print("  Prometheus:     make observe-prometheus (localhost:9090)")
    print("  Loki (Grafana): Use Grafana → Explore → Loki datasource")
    print("\n🚀 Next steps:")
    print("  make seed                       # Load sample data via docker-compose")
    print("  make helm-deploy                # Deploy all app services")
    print("  make observe-all                # Port-forward all observability tools")
    print(f"\n  To tear down: make local-down")


def teardown() -> None:
    """Delete the Kind cluster."""
    if not cluster_exists():
        print(f"ℹ️  Cluster '{CLUSTER_NAME}' does not exist.")
        return

    print(f"\n🗑️  Deleting cluster '{CLUSTER_NAME}'...")
    run(f"kind delete cluster --name {CLUSTER_NAME}")
    print("✅ Cluster deleted.")


def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    action = sys.argv[1]

    if action == "setup":
        check_prerequisites()
        create_cluster()
        create_namespaces()
        add_helm_repos()
        install_infrastructure()
        install_observability()
        create_databases()
        create_application_secrets()
        print_status()
    elif action == "setup-infra":
        check_prerequisites()
        create_cluster()
        create_namespaces()
        add_helm_repos()
        install_infrastructure()
        create_databases()
        create_application_secrets()
        print_status()
    elif action == "teardown":
        teardown()
    elif action == "status":
        run("kubectl get pods --all-namespaces")
    else:
        print(f"Unknown action: {action}")
        print(__doc__)
        sys.exit(1)


if __name__ == "__main__":
    main()
