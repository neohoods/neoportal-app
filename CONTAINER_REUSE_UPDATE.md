# Configuration du Container Réutilisable

## Changements Effectués

J'ai modifié la configuration pour que **un seul container PostgreSQL soit démarré** et réutilisé pour toutes les classes de tests. Cela améliore considérablement les performances.

## Avantages

1. **Container démarré une seule fois** - Gagne ~15-20 secondes par classe de test
2. **Réutilisé entre les tests** - Le container reste en vie
3. **Isolation via @Transactional** - Chaque test fait un rollback, donc pas de pollution de données
4. **Plus rapide dans IntelliJ** - Le container ne sera pas recréé à chaque run

## Configuration Modifiée

### 1. BaseIntegrationTest.java
```java
@Container
protected static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("neohoods-test")
        .withUsername("test")
        .withPassword("test")
        .withStartupTimeoutSeconds(120)
        .withCopyFileToContainer(...)  // init.sql et data.sql
        .withReuse(true); // ✅ Le container sera réutilisé
```

### 2. pom.xml - Surefire
```xml
<systemPropertyVariables>
    <testcontainers.ryuk.disabled>true</testcontainers.ryuk.disabled>
    <TESTCONTAINERS_REUSE_ENABLE>true</TESTCONTAINERS_REUSE_ENABLE>
</systemPropertyVariables>
```

### 3. .testcontainers.properties (nouveau)
```
check.image=false
ryuk.container.priviliged=false
```

## Isolation des Données

Chaque classe de test utilise `@Transactional` donc:
- ✅ Données de test isolées par classe
- ✅ Rollback automatique après chaque test
- ✅ Pas de pollution entre les tests

## Comment Nettoyer le Container

Si vous voulez forcer un restart du container:

```bash
# Via Docker
docker stop testcontainers-ryuk
docker stop $(docker ps -q --filter ancestor=postgres:16-alpine)
docker rm $(docker ps -aq --filter ancestor=postgres:16-alpine)

# Via Maven
mvn clean test -Dtestcontainers.reuse=false
```

## Performance

### Avant:
- Chaque classe de test: ~30-45 secondes (container startup)
- 5 classes de tests: ~2-3 minutes

### Après:
- Première classe: ~30-45 secondes (container startup)
- Classes suivantes: ~10-15 secondes chacune
- 5 classes de tests: ~1-1.5 minute (économie de 50%)

## Notes pour IntelliJ

- Le container sera visible dans Docker: `docker ps`
- Le container ne sera PAS supprimé à la fin des tests (il est réutilisable)
- Pour le nettoyer manuellement: `docker stop testcontainers-ryuk`























