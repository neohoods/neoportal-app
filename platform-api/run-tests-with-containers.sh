#!/bin/bash

# Script pour lancer les tests avec Testcontainers (Docker)
# Ce script configure l'environnement pour que Testcontainers puisse se connecter à Docker

set -e

# Configuration Java
export JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null)}
export PATH=$JAVA_HOME/bin:$PATH

# Configuration Docker pour Testcontainers
# Docker Desktop utilise un socket différent selon la version
if [ -S "$HOME/.docker/run/docker.sock" ]; then
    export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"
elif [ -S "/var/run/docker.sock" ]; then
    export DOCKER_HOST="unix:///var/run/docker.sock"
else
    echo "WARNING: Docker socket not found. Testcontainers may not work."
    echo "Please ensure Docker Desktop is running."
fi

# Désactiver le fallback PostgreSQL local pour forcer l'utilisation de Testcontainers
unset USE_LOCAL_POSTGRES
unset POSTGRES_PORT
unset POSTGRES_USER
unset POSTGRES_PASSWORD

# Vérifier que Docker est accessible
if ! docker ps > /dev/null 2>&1; then
    echo "ERROR: Docker is not accessible. Please ensure Docker Desktop is running."
    exit 1
fi

echo "✅ Docker configuration:"
echo "   DOCKER_HOST=$DOCKER_HOST"
echo "   Java version: $(java -version 2>&1 | head -1)"
echo ""
echo "🚀 Running tests with Testcontainers..."
echo ""

# Lancer les tests
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
mvn test "$@"

