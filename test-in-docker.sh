#!/bin/bash
set -e

echo "=== Test du build dans Docker (comme la CI) ==="

# Aller dans le répertoire neoportal-app
cd "$(dirname "$0")"

# Construire l'image Docker de test
echo "📦 Construction de l'image Docker de test..."
docker build -f Dockerfile.test -t neoportal-test:latest .

# Exécuter les tests dans le conteneur
echo "🧪 Exécution des tests dans Docker..."
docker run --rm \
    -e USE_LOCAL_POSTGRES=false \
    neoportal-test:latest \
    -f platform-api/pom.xml clean test -DskipTests=false

echo "✅ Tests terminés !"

