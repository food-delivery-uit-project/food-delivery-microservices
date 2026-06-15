# Deployment Guide

## 1. Environments

| Environment | Cluster | Purpose | Deploy Method |
|-------------|---------|---------|---------------|
| **Local Dev (Compose)** | Docker Compose | Quick dev, no K8s needed | `docker compose up -d` |
| **Local K8s** | Kind (Docker) | Full architecture demo | `make local-setup` |
| **Dev/Staging** | Azure AKS | Integration testing | ArgoCD (auto-sync) |
| **Production** | Azure AKS | Live system | ArgoCD (manual sync + approval) |

## 2. Local K8s Setup (Kind) — Recommended for Demo

### Prerequisites

```bash
# Install Kind
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.23.0/kind-linux-amd64
chmod +x ./kind && sudo mv ./kind /usr/local/bin/kind

# Install Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Install kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl && sudo mv kubectl /usr/local/bin/
```

### One-Command Setup

```bash
# Full setup: Kind cluster + PostgreSQL + Redis + Kafka + Kong + full observability stack
make local-setup

# Expected output:
# ✅ Cluster created.
# ✅ Namespaces ready.
# ✅ Helm repos ready.
# ✅ Infrastructure installed.
# ✅ Observability stack installed.
# ✅ Databases created.
# ✅ Application secrets ready.
# Access URLs:
#   Services:       http://localhost:8000   (Kong Gateway)
#   Grafana:        make observe-grafana    → localhost:3000
#   Jaeger:         make observe-jaeger     → localhost:16686
#   Prometheus:     make observe-prometheus → localhost:9090
```

### Deploy Application Services

```bash
# Deploy all 6 services via Helm umbrella chart
make helm-deploy

# Verify pods are running
make k8s-status

# Load sample data
make seed
```

### Access Observability

```bash
# Open all monitoring tools at once
make observe-all

# Grafana:    http://localhost:3000  (admin / food-delivery-admin)
# Jaeger:     http://localhost:16686
# Prometheus: http://localhost:9090
```

### Teardown

```bash
make local-down  # Delete Kind cluster
```

## 3. Observability Stack — K8s Deployment Details

### Components

| Component | Helm Chart | Namespace | Values File |
|-----------|-----------|-----------|-------------|
| Prometheus + Alertmanager | `prometheus-community/kube-prometheus-stack` | observability | `deployments/observability/prometheus/values.yaml` |
| Grafana | `grafana/grafana` | observability | `deployments/observability/grafana/values.yaml` |
| Loki + Promtail | `grafana/loki-stack` | observability | `deployments/observability/loki/values.yaml` |
| Jaeger | `jaegertracing/jaeger` | observability | `deployments/observability/jaeger/values.yaml` |
| OTel Collector | Raw K8s manifest | observability | `deployments/observability/otel-collector.yaml` |

### Deploy/Update Observability Separately

```bash
make deploy-observability

# Or manually:
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  -n observability -f deployments/observability/prometheus/values.yaml --wait

kubectl apply -f deployments/observability/prometheus/alert-rules.yaml
```

### Check Status

```bash
make observability-status

# kubectl get pods -n observability
# kubectl get servicemonitors -n observability
# kubectl get prometheusrules -n observability
```

## 4. Azure AKS Setup (Production/Staging)

### Provision AKS Cluster

```bash
# 1. Create resource group
az group create --name rg-food-delivery --location southeastasia

# 2. Create AKS cluster (2-4 nodes with autoscaler)
az aks create \
  --resource-group rg-food-delivery \
  --name aks-food-delivery \
  --node-count 2 \
  --node-vm-size Standard_B2s \
  --enable-cluster-autoscaler \
  --min-count 2 \
  --max-count 4 \
  --generate-ssh-keys

# 3. Get credentials
az aks get-credentials --resource-group rg-food-delivery --name aks-food-delivery

# 4. Create ACR
az acr create --resource-group rg-food-delivery --name acrfooddelivery --sku Basic
az aks update --resource-group rg-food-delivery --name aks-food-delivery --attach-acr acrfooddelivery
```

### Install Infrastructure on AKS

```bash
# Namespaces
kubectl create namespace food-app
kubectl create namespace databases
kubectl create namespace kafka
kubectl create namespace observability
kubectl create namespace argocd

# Add Helm repos
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add kong https://charts.konghq.com
helm repo add strimzi https://strimzi.io/charts/
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo add jaegertracing https://jaegertracing.github.io/helm-charts
helm repo update

# PostgreSQL
helm install postgresql bitnami/postgresql -n databases \
  -f deployments/infrastructure/postgresql/values.yaml

# Redis
helm install redis bitnami/redis -n databases \
  -f deployments/infrastructure/redis/values.yaml

# Kafka (Strimzi)
helm install strimzi-operator strimzi/strimzi-kafka-operator -n kafka --wait
kubectl apply -f deployments/infrastructure/kafka/ -n kafka

# Kong Ingress
helm install kong kong/ingress -n food-app \
  -f deployments/infrastructure/kong/values.yaml

# ArgoCD
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Observability Stack
make deploy-observability
```

## 5. ArgoCD GitOps Deployment

### How It Works

```
Code push to main
    │
    ▼
GitHub Actions CI:
  1. Build Docker image
  2. Run tests
  3. Push to ACR
  4. Update image.tag in deployments/helm/charts/<service>/values.yaml
  5. Commit & push to main
    │
    ▼
ArgoCD detects Git change
    │
    ▼
ArgoCD syncs to AKS cluster
    │
    ▼
Rolling update (maxSurge=1, maxUnavailable=0)
Zero-downtime deployment ✅
```

### Access ArgoCD UI

```bash
kubectl port-forward svc/argocd-server -n argocd 8443:443
# Open https://localhost:8443
# Password: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

### Apply ApplicationSet

```bash
kubectl apply -f deployments/argocd/applicationset.yaml -n argocd
```

### Manual Sync (Production)

```bash
argocd app sync order-service
argocd app sync payment-service
```

## 6. Deployment Strategy

Each service uses:
- **Rolling Update**: `maxSurge: 1`, `maxUnavailable: 0`
- **PodDisruptionBudget**: `minAvailable: 1` (no full outage during node drain)
- **HPA**: Scale from 1→4 pods based on 70% CPU / 80% memory
- **Readiness Gate**: No traffic until `/actuator/health/readiness` returns 200

## 7. Secrets Management

```bash
# Database credentials
kubectl create secret generic db-credentials \
  --from-literal=POSTGRES_PASSWORD=<secure-password> \
  -n food-app

# JWT signing key
kubectl create secret generic jwt-secret \
  --from-literal=JWT_SECRET=<min-256-bit-random-key> \
  -n food-app
```

> **Important:** Never commit secrets to Git. In production, use Azure Key Vault with AKS Workload Identity.

## 8. Helm Chart Structure

```
deployments/helm/
├── Chart.yaml              # Umbrella chart (depends on all 6 service charts)
├── values.yaml             # Global overrides
└── charts/
    ├── order-service/
    │   ├── Chart.yaml
    │   ├── values.yaml     # Service config (image, resources, HPA, PDB, ServiceMonitor)
    │   └── templates/
    │       ├── deployment.yaml    # With security context, topology spread
    │       ├── service.yaml       # Named port 'http' for ServiceMonitor
    │       ├── ingress.yaml       # Kong IngressClass
    │       ├── hpa.yaml           # HorizontalPodAutoscaler
    │       ├── pdb.yaml           # PodDisruptionBudget
    │       └── servicemonitor.yaml # Prometheus ServiceMonitor
    └── ... (same structure for all 6 services)
```
