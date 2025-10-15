# Test Architecture - Isolation Complète

## Configuration Finale

### Architecture Actuelle

Chaque classe de test obtient maintenant :

- ✅ **Son propre container PostgreSQL**
- ✅ **Sa propre base de données** avec un nom unique (UUID)
- ✅ **Isolation complète** des autres tests

### Exemple

```
Container PostgreSQL #1 (neohoods-test-abc123)
└── ReservationCreationTest
    └── Utilise la DB unique "neohoods-test-abc123"

Container PostgreSQL #2 (neohoods-test-def456)
└── ReservationConfirmationTest
    └── Utilise la DB unique "neohoods-test-def456"

Container PostgreSQL #3 (neohoods-test-ghi789)
└── SpaceAvailabilityTest
    └── Utilise la DB unique "neohoods-test-ghi789"
```

### Avantages

1. **Isolation Parfaite** - Aucun risque de contamination de données entre les classes de test
2. **Tests Parallèles** - Possible de lancer les tests en parallèle sans conflits
3. **Débogage Facilité** - Chaque test a sa propre base de données propre et isolée
4. **Fiabilité Maximale** - Plus de problèmes de rollback ou de données résiduelles

### Inconvénients

1. **Temps de démarrage** - Chaque classe doit démarrer son propre container (~15-20s)
2. **Ressources** - Plus de containers Docker actifs en même temps
3. **Mémoire** - Chaque container utilise de la mémoire

## Configuration

### BaseIntegrationTest.java

```java
// UUID unique généré à chaque chargement de classe
private static final String uniqueDatabaseName = "neohoods-test-" + UUID.randomUUID().toString().replace("-", "");

@Container
protected static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName(uniqueDatabaseName)
        // ... configuration
```

### pom.xml

```xml
<parallel>classes</parallel>
<threadCount>4</threadCount>
```

Permet d'exécuter jusqu'à 4 classes de test en parallèle.

### .testcontainers.properties

```properties
check.image=false
ryuk.container.priviliged=false
```

## Utilisation

### Lancer tous les tests

```bash
mvn clean test
```

### Lancer une classe spécifique

```bash
mvn test -Dtest=ReservationCreationTest
```

### Lancer avec verbose output

```bash
mvn test -X
```

## Performance

- **Premier container** : ~15-20 secondes (téléchargement image + démarrage)
- **Containers suivants** : ~5-10 secondes (container réutilisé si possible)
- **Parallélisation** : 4 classes simultanées avec `threadCount=4`

## Consommation Ressources

- **RAM** : ~50-100 MB par container
- **Disque** : Containers temporaires, nettoyés automatiquement
- **Réseau** : Ports temporaires alloués dynamiquement

## Nettoyage

Les containers sont automatiquement supprimés par Testcontainers Ryuk après les tests.

Pour forcer le nettoyage manuel :

```bash
# Voir tous les containers testcontainers
docker ps -a | grep postgres

# Nettoyer tous les containers
docker stop $(docker ps -a -q --filter ancestor=testcontainers/postgres:16-alpine)
docker rm $(docker ps -a -q --filter ancestor=testcontainers/postgres:16-alpine)
```
