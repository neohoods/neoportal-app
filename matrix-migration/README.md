# Migration Matrix : Ancienne instance → Nouvelle instance avec Space

Ce répertoire contient les scripts et la documentation pour migrer une instance Matrix (chat.terresdelaya.fr) vers la nouvelle instance (chat.neohoods.com) en déplaçant toutes les rooms à la racine vers un space Matrix.

## Vue d'ensemble

- **Ancienne instance** : `chat.terresdelaya.fr` (rooms à la racine)
- **Nouvelle instance** : `chat.neohoods.com` (rooms dans space)
- **Space de test** : `!rgOFmqDljPgniKgNEx:chat.neohoods.com`
- **Space de production** : `!YenniyNVsUoBCLHtZS:chat.neohoods.com`

## Scope de la migration

- ✅ Users (migration complète)
- ✅ Rooms non-encryptées (avec réutilisation si même nom)
- ✅ Messages non-encryptés
- ✅ Metadata des rooms (state events)
- ❌ Rooms encryptées (impossible à migrer)

## Prérequis

1. Backup SQL de l'ancienne instance Matrix
2. Accès à la nouvelle instance Matrix (API ou DB)
3. Token d'accès Matrix avec permissions admin
4. Python 3.8+
5. PostgreSQL client (pour exécuter les scripts SQL)

## Installation

```bash
# Installer les dépendances Python
pip install requests

# Rendre les scripts exécutables
chmod +x *.py
```

## Processus de migration

### Étape 1 : Analyser le backup

```bash
python analyze-backup.py /path/to/backup_matrix_db_2025-11-26_01-00-30.sql
```

Ce script :
- Parse le backup SQL
- Identifie les rooms non-encryptées
- Extrait les noms des rooms
- Liste tous les users
- Génère `migration-analysis.json`

### Étape 2 : Récupérer les rooms existantes

```bash
python fetch-existing-rooms.py \
  --homeserver https://matrix.neohoods.com \
  --access-token YOUR_ACCESS_TOKEN \
  --space-id !rgOFmqDljPgniKgNEx:chat.neohoods.com \
  --output existing-rooms.json
```

Ce script :
- Interroge l'API Matrix
- Liste toutes les rooms dans le space de destination
- Génère `existing-rooms.json`

### Étape 3 : Matcher les rooms par nom

```bash
python match-rooms.py \
  --analysis migration-analysis.json \
  --existing-rooms existing-rooms.json \
  --space-id !rgOFmqDljPgniKgNEx:chat.neohoods.com \
  --output room-mapping.json
```

Ce script :
- Match les rooms par nom (case-insensitive)
- Réutilise les rooms existantes si même nom
- Génère de nouveaux IDs pour les autres
- Génère `room-mapping.json`

### Étape 4 : Générer le SQL de migration

```bash
python generate-migration-sql.py \
  --backup /path/to/backup_matrix_db_2025-11-26_01-00-30.sql \
  --mapping room-mapping.json \
  --space-id !rgOFmqDljPgniKgNEx:chat.neohoods.com \
  --output migrate-matrix.sql
```

Ce script :
- Génère le script SQL de migration
- Gère les mappings d'IDs
- Ajoute les events `m.space.parent`
- Génère `migrate-matrix.sql`

### Étape 5 : Backup de la nouvelle DB

**⚠️ IMPORTANT : Faire un backup complet avant la migration !**

```bash
pg_dump -h HOST -U USER -d DATABASE > backup_before_migration_$(date +%Y%m%d_%H%M%S).sql
```

### Étape 6 : Exécuter la migration

```bash
# Examiner le script SQL généré
cat migrate-matrix.sql

# Exécuter la migration (dans une transaction)
psql -h HOST -U USER -d DATABASE -f migrate-matrix.sql
```

### Étape 7 : Vérifier la migration

```bash
psql -h HOST -U USER -d DATABASE -f verify-migration.sql
```

## Points d'attention

### Event IDs

Les event IDs doivent être uniques et suivre le format Matrix (`$random:server`). Le script génère automatiquement de nouveaux IDs.

### Stream ordering

Pour les rooms réutilisées, le `stream_ordering` doit continuer depuis le dernier message existant. Le script SQL de base ne gère pas cela automatiquement - une migration complète nécessite l'utilisation de l'API Matrix ou un script plus sophistiqué.

### State chains

Les `prev_state` doivent être maintenues pour l'intégrité. Le script met à jour les références automatiquement.

### Auth events

Les références `auth_events` dans le JSON content doivent pointer vers les nouveaux IDs. Cela nécessite une mise à jour du JSON content, ce qui est complexe et peut nécessiter l'utilisation de l'API Matrix.

### Room versions

Vérifier la compatibilité des room versions. Toutes les rooms dans le backup utilisent la version 10, qui est compatible.

### Space parent

Chaque room doit avoir exactement un `m.space.parent` pointant vers le space de destination. Le script l'ajoute automatiquement.

## Rollback

En cas de problème, restaurer le backup :

```bash
psql -h HOST -U USER -d DATABASE < backup_before_migration_YYYYMMDD_HHMMSS.sql
```

## Limitations

1. **Events complexes** : La migration complète des events (messages) nécessite de mettre à jour le JSON content, les signatures, etc. Le script SQL généré est un squelette - pour une migration complète, considérer l'utilisation de l'API Matrix.

2. **Rooms encryptées** : Les rooms avec `m.room.encryption` sont exclues de la migration car les messages encryptés ne peuvent pas être décryptés sans les clés.

3. **Stream ordering** : Pour les rooms réutilisées, le `stream_ordering` doit être géré manuellement ou via l'API.

4. **Event signatures** : Les signatures des events doivent être régénérées par le serveur Matrix.

## Support

Pour toute question ou problème, consulter :
- La documentation Matrix : https://matrix.org/docs/
- La documentation Synapse : https://matrix-org.github.io/synapse/

## Fichiers générés

- `migration-analysis.json` : Analyse du backup
- `existing-rooms.json` : Rooms existantes dans le space
- `room-mapping.json` : Mapping old_room_id → new_room_id
- `migrate-matrix.sql` : Script SQL de migration
- `verify-migration.sql` : Scripts de vérification

## Notes de production

Pour la migration finale en production :

1. Utiliser le space de production : `!YenniyNVsUoBCLHtZS:chat.neohoods.com`
2. Tester d'abord sur une instance de test
3. Faire un backup complet avant
4. Exécuter pendant une fenêtre de maintenance
5. Vérifier l'intégrité après la migration
6. Tester l'accès via l'API Matrix
7. Monitorer les logs pour détecter les erreurs

