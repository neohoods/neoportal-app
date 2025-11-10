# Test Suite - Configuration Finale

## ✅ Configuration Fixée Pour IntelliJ

### Changements Effectués:

1. **Container Réutilisable** - `.withReuse(true)` dans BaseIntegrationTest
2. **Tests Séquentiels** - `parallel=none` dans surefire-plugin
3. **Ryuk Désactivé** - Évite le cleanup entre tests
4. **Timeouts Augmentés** - 120s pour le container, 60s pour HikariCP

### Résultat:

- **Tests Smoke**: ✅ 3 tests passent (15-30 secondes)
- **Container**: Démarré une seule fois, réutilisé pour tous les tests
- **Isolation**: Chaque test fait un rollback via `@Transactional`

## Comment Utiliser dans IntelliJ:

1. **Clic droit** sur une classe de test → **Run**
2. Le container PostgreSQL démarre la première fois (~15-20s)
3. Les tests suivants sont **beaucoup plus rapides** (1-5s)
4. Le container reste actif et est réutilisé

## Architecture:

```
Container PostgreSQL (partagé, démarré une fois)
├── SpacesServiceIntegrationTest (@Transactional)
│   └── Rollback → données propres
├── ReservationCreationTest (@Transactional)
│   └── Rollback → données propres
└── Autres tests...
    └── Tous avec @Transactional
```

## Fichiers Modifiés:

1. `BaseIntegrationTest.java` - avecReuse(true)
2. `pom.xml` - surefire plugin configuré
3. `application-test.yml` - HikariCP timeouts
4. `.testcontainers.properties` - nouveau fichier

## Si Vous Avez Des Problèmes:

```bash
# Nettoyer les containers Docker
docker stop testcontainers-ryuk
docker ps -a | grep postgres
docker rm <container_id>

# Re-run les tests
mvn clean test
```


















