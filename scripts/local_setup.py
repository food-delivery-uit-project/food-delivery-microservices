#!/usr/bin/env python3
"""
Local development environment setup/teardown script.
Creates a Kind K8s cluster and installs all infrastructure components.
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
    """Create a Kind K8s cluster."""
    if cluster_exists():
        print(f"ℹ️  Cluster '{CLUSTER_NAME}' already exists, skipping creation.")
        return

    print(f"\n🚀 Creating Kind cluster '{CLUSTER_NAME}'...")
    kind_config = """
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30080
        hostPort: 8000
        protocol: TCP
      - containerPort: 30443
        hostPort: 8443
        protocol: TCP
  - role: worker
  - role: worker
"""
    with open("/tmp/kind-config.yaml", "w") as f:
        f.write(kind_config)

    run(f"kind create cluster --name {CLUSTER_NAME} --config /tmp/kind-config.yaml")
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

    print("\n📨 Installing Kafka (Strimzi)...")
    run(
        "helm upgrade --install strimzi-operator strimzi/strimzi-kafka-operator "
        "-n kafka "
        "--wait --timeout 120s",
        check=False,
    )
    # Wait for operator to be ready, then apply Kafka cluster
    time.sleep(5)
    run("kubectl apply -f deployments/infrastructure/kafka/ -n kafka", check=False)

    print("\n🦍 Installing Kong Ingress Controller...")
    run(
        "helm upgrade --install kong kong/ingress "
        "-n food-app "
        "--set gateway.service.type=NodePort "
        "--wait --timeout 120s",
        check=False,
    )

    print("✅ Infrastructure installed.")


def create_databases() -> None:
    """Create additional databases in PostgreSQL."""
    print("\n🗃️  Creating databases...")
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
    print("✅ Application secrets ready.")


def print_status() -> None:
    """Print cluster status summary."""
    print("\n" + "=" * 60)
    print("🎉 Local environment setup complete!")
    print("=" * 60)
    print("\nCluster pods:")
    run("kubectl get pods --all-namespaces")
    print("\n📖 Next steps:")
    print("  1. make dev svc=<service-name>  # Start developing")
    print("  2. make seed                     # Load sample data")
    print("  3. make health-check             # Verify services")
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
        print("Usage: python3 local_setup.py [setup|teardown]")
        sys.exit(1)

    action = sys.argv[1]

    if action == "setup":
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
    else:
        print(f"Unknown action: {action}")
        print("Usage: python3 local_setup.py [setup|teardown]")
        sys.exit(1)


if __name__ == "__main__":
    main()
