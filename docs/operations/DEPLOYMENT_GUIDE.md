# Deployment Guide

## 1. Environments

| Environment     | Cluster       | Purpose             | Deploy Method                 |
| --------------- | ------------- | ------------------- | ----------------------------- |
| **Local**       | Kind (Docker) | Development         | `make local-setup` + Skaffold |
| **Dev/Staging** | Azure AKS     | Integration testing | ArgoCD (auto-sync)            |
| **Production**  | Azure AKS     | Live system         | ArgoCD (manual sync)          |

## 2. Local Environment Setup

See [Developer Guide](../development/DEVELOPER_GUIDE.md) for local setup instructions.

## 3. Azure AKS Setup

### Prerequisites

-   Azure CLI (`az`) installed and authenticated
-   Azure subscription with credits (Azure for Students: $100)

### Provision AKS Cluster

```bash
# 1. Create resource group
az group create --name rg-food-delivery --location southeastasia

# 2. Create AKS cluster
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
# Create namespaces
kubectl create namespace food-app
kubectl create namespace databases
kubectl create namespace kafka
kubectl create namespace observability
kubectl create namespace argocd

# Install PostgreSQL
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install postgresql bitnami/postgresql -n databases -f deployments/infrastructure/postgresql/values.yaml

# Install Redis
helm install redis bitnami/redis -n databases -f deployments/infrastructure/redis/values.yaml

# Install Kafka (Strimzi)
kubectl apply -f deployments/infrastructure/kafka/ -n kafka

# Install Kong Ingress
helm repo add kong https://charts.konghq.com
helm install kong kong/ingress -n food-app -f deployments/infrastructure/kong/values.yaml

# Install ArgoCD
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl apply -f deployments/argocd/
```

## 4. ArgoCD GitOps Deployment

### How It Works

1. CI pipeline builds Docker image → pushes to ACR
2. CI updates `image.tag` in `deployments/helm/charts/<service>/values.yaml`
3. ArgoCD detects Git change → syncs to AKS cluster
4. Rolling update with zero downtime

### Access ArgoCD UI

```bash
kubectl port-forward svc/argocd-server -n argocd 8443:443
# Open https://localhost:8443
# Username: admin
# Password: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

### Manual Sync (Production)

```bash
argocd app sync food-delivery-prod
```

## 5. Deployment Strategy

-   **Rolling Update** (default): maxSurge=1, maxUnavailable=0
-   Zero downtime guaranteed
-   Automatic rollback if readiness probe fails

## 6. Secrets Management

```bash
# Create secrets for database credentials
kubectl create secret generic db-credentials \
  --from-literal=POSTGRES_PASSWORD=<password> \
  -n food-app

# Services reference via env vars in Helm values
```

> **Important:** Never commit secrets to Git. Use Kubernetes Secrets or Sealed Secrets.
