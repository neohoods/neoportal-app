# Configuration des tests d'intégration

Les tests d'intégration nécessitent une base de données PostgreSQL. Il y a deux options :

## Option 1 : Utiliser Testcontainers (recommandé)

Testcontainers démarre automatiquement un container PostgreSQL pour les tests.

**Prérequis :**
- Docker Desktop doit être installé et en cours d'exécution
- Docker doit être accessible (vérifier avec `docker ps`)

**Note :** Sur macOS, Testcontainers peut avoir des problèmes pour se connecter à Docker Desktop. Si vous voyez l'erreur "Could not find a valid Docker environment", utilisez l'Option 2.

**Si Docker n'est pas disponible :**
- Démarrer Docker Desktop
- Vérifier que Docker est accessible : `docker info`
- Si le problème persiste, utiliser l'Option 2

## Option 2 : Utiliser PostgreSQL local

Si Docker n'est pas disponible, vous pouvez utiliser PostgreSQL local.

**Configuration :**

1. Démarrer PostgreSQL localement :
   ```bash
   docker run -d -p 5432:5432 \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=postgres \
     --name test-postgres \
     postgres:16-alpine
   ```

2. Ou utiliser une instance PostgreSQL existante en définissant les variables d'environnement :
   ```bash
   export USE_LOCAL_POSTGRES=true
   export POSTGRES_PORT=5432
   export POSTGRES_USER=postgres
   export POSTGRES_PASSWORD=postgres
   ```

3. Exécuter les tests :
   ```bash
   mvn test -Dtest="*IntegrationTest"
   ```

## Vérification

Pour vérifier que tout fonctionne :

```bash
# Vérifier Docker
docker ps

# Vérifier PostgreSQL local (si utilisé)
psql -h localhost -p 5432 -U postgres -c "SELECT version();"
```

## Dépannage

### Erreur : "Could not find a valid Docker environment"
- Vérifier que Docker Desktop est démarré
- Vérifier que Docker est accessible : `docker info`
- Si le problème persiste, utiliser l'Option 2

### Erreur : "Connection to localhost:5432 refused"
- Vérifier que PostgreSQL est en cours d'exécution
- Vérifier le port : `lsof -i :5432`
- Vérifier les variables d'environnement POSTGRES_*

### Erreur : "SQL script not found"
- Vérifier que les scripts `init.sql` et `data.sql` existent dans `neoportal-app/db/postgres/`
- Vérifier que vous exécutez les tests depuis le répertoire `platform-api/`


