#!/bin/bash
set -e

echo "=== Test du build dans Docker (comme la CI) ==="

# Aller dans le répertoire neoportal-app
cd "$(dirname "$0")"

# Construire l'image exactement comme l'action GitHub (Dockerfile.ci)
echo "📦 Construction de l'image Docker (comme l'action GitHub)..."
docker build -f Dockerfile.ci -t neoportal-ci:latest .

# Exécuter les tests dans le conteneur avec la même configuration que la CI
echo "🧪 Exécution des tests dans Docker (comme la CI)..."
docker run --rm \
    -e USE_LOCAL_POSTGRES=true \
    -e JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
    neoportal-ci:latest \
    clean verify -Ddockerfile.skip -DdockerCompose.skip -Djib.skip

echo "✅ Tests terminés !"
