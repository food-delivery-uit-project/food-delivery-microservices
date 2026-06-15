# Secret Management với Bitnami Sealed Secrets

## Mục đích
Project này sử dụng **Bitnami Sealed Secrets** để quản lý an toàn các secrets (DB passwords, API keys, JWT secrets) trong một môi trường GitOps. Thay vì commit Plaintext Kubernetes `Secret` lên Git (điều này rất nguy hiểm), chúng ta sẽ mã hoá chúng thành `SealedSecret` và đẩy lên Git.

Chỉ có Sealed Secrets Controller chạy trong cụm K8s mới giữ Private Key để giải mã `SealedSecret` thành `Secret` thực sự mà Pods có thể dùng.

## Cài đặt (đã tích hợp)
Sealed Secrets đã được cài đặt thông qua `make infra-deploy` hoặc `make local-setup`.
Nếu cần cài riêng:
```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install sealed-secrets bitnami/sealed-secrets -n kube-system
```

## Hướng dẫn mã hoá Secret mới

### 1. Cài đặt kubeseal CLI
```bash
wget https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.3/kubeseal-0.24.3-linux-amd64.tar.gz
tar -xvzf kubeseal-0.24.3-linux-amd64.tar.gz kubeseal
sudo install -m 755 kubeseal /usr/local/bin/kubeseal
```

### 2. Lấy Public Key của Controller
```bash
kubeseal --fetch-cert \
  --controller-namespace kube-system \
  --controller-name sealed-secrets \
  > pub-cert.pem
```

### 3. Mã hoá Secret
Tạo một file Secret nháp (không commit file này):
```yaml
# unsealed-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: jwt-secret
  namespace: food-app
type: Opaque
stringData:
  JWT_SECRET: "my-super-secret-key-12345"
```

Chạy `kubeseal` để mã hoá:
```bash
kubeseal --format=yaml --cert=pub-cert.pem \
  < unsealed-secret.yaml > jwt-sealed-secret.yaml
```

Bây giờ bạn có thể an tâm commit `jwt-sealed-secret.yaml` lên Git repository!
Khi apply file này vào cluster:
```bash
kubectl apply -f jwt-sealed-secret.yaml
```
Sealed Secrets Controller sẽ tự động sinh ra một K8s Secret tên là `jwt-secret` để sử dụng.
