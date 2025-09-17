#!/bin/bash

# Deploy observability stack for NeoHoods Platform API
# This script sets up Grafana + Loki + Tempo + OpenTelemetry Collector

set -e

echo "🚀 Deploying NeoHoods Observability Stack..."

# Create namespace
kubectl create namespace observability --dry-run=client -o yaml | kubectl apply -f -

# Deploy Loki
echo "📊 Deploying Loki..."
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm upgrade --install loki grafana/loki-stack \
  --namespace observability \
  --set loki.persistence.enabled=true \
  --set loki.persistence.size=10Gi \
  --set promtail.enabled=true \
  --set grafana.enabled=false \
  --wait

# Deploy Tempo
echo "🔍 Deploying Tempo..."
helm upgrade --install tempo grafana/tempo \
  --namespace observability \
  --set storage.traces.backend=s3 \
  --set storage.traces.s3.bucket=tempo-traces \
  --set storage.traces.s3.region=us-east-1 \
  --set storage.traces.s3.endpoint=http://minio.observability.svc.cluster.local:9000 \
  --set storage.traces.s3.insecure=true \
  --wait

# Deploy Grafana
echo "📈 Deploying Grafana..."
helm upgrade --install grafana grafana/grafana \
  --namespace observability \
  --set persistence.enabled=true \
  --set persistence.size=10Gi \
  --set adminPassword=admin123 \
  --set datasources."datasources\.yaml".apiVersion=1 \
  --set datasources."datasources\.yaml".datasources[0].name=Prometheus \
  --set datasources."datasources\.yaml".datasources[0].type=prometheus \
  --set datasources."datasources\.yaml".datasources[0].url=http://prometheus.observability.svc.cluster.local:9090 \
  --set datasources."datasources\.yaml".datasources[0].isDefault=true \
  --set datasources."datasources\.yaml".datasources[1].name=Loki \
  --set datasources."datasources\.yaml".datasources[1].type=loki \
  --set datasources."datasources\.yaml".datasources[1].url=http://loki.observability.svc.cluster.local:3100 \
  --set datasources."datasources\.yaml".datasources[2].name=Tempo \
  --set datasources."datasources\.yaml".datasources[2].type=tempo \
  --set datasources."datasources\.yaml".datasources[2].url=http://tempo.observability.svc.cluster.local:3200 \
  --wait

# Deploy OpenTelemetry Collector
echo "🔧 Deploying OpenTelemetry Collector..."
kubectl apply -f otel-collector-config.yaml

# Deploy Prometheus
echo "📊 Deploying Prometheus..."
helm upgrade --install prometheus grafana/prometheus \
  --namespace observability \
  --set server.persistentVolume.enabled=true \
  --set server.persistentVolume.size=10Gi \
  --wait

# Wait for all pods to be ready
echo "⏳ Waiting for all pods to be ready..."
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=grafana -n observability --timeout=300s
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=loki -n observability --timeout=300s
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=tempo -n observability --timeout=300s
kubectl wait --for=condition=ready pod -l app=otel-collector -n observability --timeout=300s

echo "✅ Observability stack deployed successfully!"

# Get service URLs
echo ""
echo "🌐 Service URLs:"
echo "Grafana: http://$(kubectl get svc grafana -n observability -o jsonpath='{.status.loadBalancer.ingress[0].ip}'):3000"
echo "Loki: http://$(kubectl get svc loki -n observability -o jsonpath='{.status.loadBalancer.ingress[0].ip}'):3100"
echo "Tempo: http://$(kubectl get svc tempo -n observability -o jsonpath='{.status.loadBalancer.ingress[0].ip}'):3200"
echo "Prometheus: http://$(kubectl get svc prometheus-server -n observability -o jsonpath='{.status.loadBalancer.ingress[0].ip}'):9090"

echo ""
echo "🔑 Grafana credentials:"
echo "Username: admin"
echo "Password: admin123"

echo ""
echo "📝 Next steps:"
echo "1. Access Grafana and configure data sources"
echo "2. Import the NeoHoods dashboard"
echo "3. Deploy your Spring Boot app with OpenTelemetry enabled"
echo "4. Check traces in Tempo and logs in Loki"
