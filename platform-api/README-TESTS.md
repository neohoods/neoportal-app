# Guide d'exécution des tests

Ce projet supporte deux modes d'exécution des tests :

## 1. Tests avec Testcontainers (Docker) - Recommandé

Les tests utilisent Testcontainers pour créer automatiquement un container PostgreSQL isolé.

### Prérequis
- Docker Desktop doit être en cours d'exécution
- Java 21 installé

### Méthode 1 : Script automatique (recommandé)

```bash
cd neoportal-app/platform-api
./run-tests-with-containers.sh
```

### Méthode 2 : Configuration manuelle

```bash
cd neoportal-app/platform-api

# Configurer Docker pour Testcontainers
export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"

# Configurer Java
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH=$JAVA_HOME/bin:$PATH

# S'assurer que les variables PostgreSQL local ne sont PAS définies
unset USE_LOCAL_POSTGRES
unset POSTGRES_PORT
unset POSTGRES_USER
unset POSTGRES_PASSWORD

# Lancer les tests
mvn test
```

### Avantages
- ✅ Isolation complète : chaque test utilise sa propre base de données
- ✅ Pas de configuration manuelle nécessaire
- ✅ Compatible avec CI/CD
- ✅ Container réutilisé entre les tests (plus rapide)

## 2. Tests avec PostgreSQL local (Fallback)

Si Docker n'est pas disponible, les tests peuvent utiliser un PostgreSQL local existant.

### Configuration

```bash
cd neoportal-app/platform-api

# Activer le mode PostgreSQL local
export USE_LOCAL_POSTGRES=true
export POSTGRES_PORT=8433          # Port de votre PostgreSQL local
export POSTGRES_USER=local         # Utilisateur PostgreSQL
export POSTGRES_PASSWORD=local     # Mot de passe PostgreSQL

# Configurer Java
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH=$JAVA_HOME/bin:$PATH

# Lancer les tests
mvn test
```

### Avantages
- ✅ Fonctionne sans Docker
- ✅ Plus rapide (pas de démarrage de container)
- ⚠️ Nécessite un PostgreSQL local configuré
- ⚠️ Moins d'isolation entre les tests

## Dépannage

### Testcontainers ne trouve pas Docker

Si vous obtenez l'erreur `Could not find a valid Docker environment` :

1. Vérifiez que Docker Desktop est en cours d'exécution :
   ```bash
   docker ps
   ```

2. Configurez `DOCKER_HOST` :
   ```bash
   export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"
   ```

3. Ou utilisez le script automatique :
   ```bash
   ./run-tests-with-containers.sh
   ```

### Tests échouent avec PostgreSQL local

Vérifiez que :
- PostgreSQL est en cours d'exécution
- Le port, l'utilisateur et le mot de passe sont corrects
- Les scripts SQL d'initialisation existent dans `src/main/resources/db/postgres/`

## Exécuter un test spécifique

```bash
# Avec Testcontainers
./run-tests-with-containers.sh -Dtest="SpaceSearchTest#testGetSpaceById"

# Avec PostgreSQL local
export USE_LOCAL_POSTGRES=true
export POSTGRES_PORT=8433
export POSTGRES_USER=local
export POSTGRES_PASSWORD=local
mvn test -Dtest="SpaceSearchTest#testGetSpaceById"
```


