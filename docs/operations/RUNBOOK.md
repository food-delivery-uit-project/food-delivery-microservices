# Runbook - Incident Response

## 1. General Debugging Flow

```
1. Check Grafana dashboards → identify which service has errors/high latency
2. Check Jaeger → find the trace ID with the error
3. Search Loki logs with trace_id → find exact error message
4. kubectl describe pod → check events, restarts
5. kubectl logs → check application logs
```

## 2. Common Issues & Solutions

### Service Pod CrashLoopBackOff

```bash
# Check pod events
kubectl describe pod <pod-name> -n food-app

# Check logs
kubectl logs <pod-name> -n food-app --previous

# Common causes:
# - DB connection failed → Check PostgreSQL pod in databases namespace
# - Kafka connection failed → Check Kafka pods in kafka namespace
# - Missing config/env vars → Check ConfigMap
# - OOM (Out of Memory) → Increase memory limit in Helm values
```

### Database Connection Issues

```bash
# Verify PostgreSQL is running
kubectl get pods -n databases
kubectl logs postgresql-0 -n databases

# Test connectivity from a service pod
kubectl exec -it <service-pod> -n food-app -- sh
# Inside pod:
# nc -zv postgresql.databases.svc 5432
```

### Kafka Consumer Lag

```bash
# Check consumer group lag
kubectl exec food-delivery-kafka-kafka-0 -n kafka -- \
  kafka-consumer-groups.sh \
  --bootstrap-server food-delivery-kafka-kafka-bootstrap:9092 \
  --group order-service-group \
  --describe

# If lag is growing:
# 1. Check if consumer pod is healthy
# 2. Check for errors in consumer logs
# 3. Consider scaling consumer pods (increase replicas)
```

### Order Stuck in Status

```bash
# Check order status in DB
kubectl exec -it postgresql-0 -n databases -- \
  psql -U postgres -d order_db -c \
  "SELECT id, status, updated_at FROM orders WHERE id = '<order-id>';"

# Check if event was published (outbox)
kubectl exec -it postgresql-0 -n databases -- \
  psql -U postgres -d order_db -c \
  "SELECT * FROM outbox_events WHERE aggregate_id = '<order-id>' AND published = false;"

# If unpublished events exist → Outbox relay may be stuck
# Restart order-service pod
kubectl rollout restart deployment/order-service -n food-app
```

### Kong Gateway 502 Bad Gateway

```bash
# Check if backend service is running
kubectl get svc -n food-app
kubectl get endpoints <service-name> -n food-app

# If endpoints list is empty → service has no ready pods
# Check pods: kubectl get pods -n food-app
```

### Redis Connection Issues (Dispatch Service)

```bash
# Check Redis
kubectl get pods -n databases | grep redis
kubectl logs redis-master-0 -n databases

# Test Redis connectivity
kubectl exec -it redis-master-0 -n databases -- redis-cli ping
# Should return: PONG
```

## 3. Rollback Procedures

### Application Rollback (ArgoCD)

```bash
# List deployment history
argocd app history food-delivery

# Rollback to previous version
argocd app rollback food-delivery <revision-number>
```

### Kubernetes Rollback

```bash
# Check rollout history
kubectl rollout history deployment/order-service -n food-app

# Rollback to previous
kubectl rollout undo deployment/order-service -n food-app
```

### Database Rollback (Flyway)

```bash
# Check migration status
kubectl exec -it <service-pod> -n food-app -- \
  java -cp app.jar org.flywaydb.commandline.Main info

# Note: Flyway community does NOT support undo.
# Write a new V{n}__rollback_xxx.sql migration to reverse changes.
```

## 4. Useful Commands

```bash
# All pods status
kubectl get pods --all-namespaces

# Resource usage
kubectl top pods -n food-app
kubectl top nodes

# Restart a service
kubectl rollout restart deployment/<service-name> -n food-app

# Scale a service
kubectl scale deployment/<service-name> --replicas=3 -n food-app

# Port-forward for debugging
kubectl port-forward svc/order-service 8080:8080 -n food-app
kubectl port-forward svc/grafana 3000:3000 -n observability
kubectl port-forward svc/jaeger-query 16686:16686 -n observability
```
