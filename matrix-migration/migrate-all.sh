#!/bin/bash
# Script complet de migration Matrix - Enchaîne toutes les étapes

set -e

BACKUP_FILE="${1:-/Users/qcastel/Downloads/bzsnapshot_2025-11-26-09-32-25/backup_matrix_db_2025-11-26_01-00-30.sql}"
SPACE_ID="${2:-!rgOFmqDljPgniKgNEx:chat.neohoods.com}"
OLD_SERVER="${3:-chat.terresdelaya.fr}"
NEW_SERVER="${4:-chat.neohoods.com}"

echo "🚀 Migration Matrix - Pipeline Complet"
echo "========================================"
echo "Backup: $BACKUP_FILE"
echo "Space ID: $SPACE_ID"
echo "Old Server: $OLD_SERVER"
echo "New Server: $NEW_SERVER"
echo ""

# Étape 1: Analyser le backup
echo "📊 Étape 1/4: Analyse du backup..."
python3 analyze-backup.py "$BACKUP_FILE" || {
    echo "❌ Échec de l'analyse"
    exit 1
}
echo "✅ Analyse terminée"
echo ""

# Étape 2: Récupérer les rooms existantes (optionnel)
if [ -n "$MATRIX_ACCESS_TOKEN" ] && [ -n "$MATRIX_HOMESERVER_URL" ]; then
    echo "📋 Étape 2/4: Récupération des rooms existantes..."
    export MATRIX_TARGET_SPACE_ID="$SPACE_ID"
    python3 fetch-existing-rooms.py || {
        echo "⚠️  Échec de la récupération des rooms existantes, utilisation du fichier de test"
        EXISTING_ROOMS="test-existing-rooms.json"
    }
    EXISTING_ROOMS="existing-rooms.json"
else
    echo "📋 Étape 2/4: Utilisation des rooms de test (MATRIX_ACCESS_TOKEN non défini)..."
    EXISTING_ROOMS="test-existing-rooms.json"
fi
echo "✅ Rooms existantes chargées"
echo ""

# Étape 3: Matcher les rooms
echo "🔗 Étape 3/4: Matching des rooms..."
python3 match-rooms.py \
    --analysis migration-analysis.json \
    --existing-rooms "$EXISTING_ROOMS" \
    --space-id "$SPACE_ID" \
    --output room-mapping.json || {
    echo "❌ Échec du matching"
    exit 1
}
echo "✅ Matching terminé"
echo ""

# Étape 4: Générer le SQL
echo "📝 Étape 4/4: Génération du SQL de migration..."
python3 generate-migration-sql.py \
    --backup "$BACKUP_FILE" \
    --mapping room-mapping.json \
    --space-id "$SPACE_ID" \
    --old-server "$OLD_SERVER" \
    --new-server "$NEW_SERVER" \
    --output migrate-matrix.sql || {
    echo "❌ Échec de la génération SQL"
    exit 1
}
echo "✅ SQL généré"
echo ""

# Vérification finale
echo "🔍 Vérification finale..."
python3 verify-migration.py --analysis migration-analysis.json --mapping room-mapping.json --sql migrate-matrix.sql || {
    echo "⚠️  Certaines vérifications ont échoué, mais les fichiers sont générés"
}
echo ""

# Résumé
echo "========================================"
echo "✅ Migration préparée avec succès !"
echo ""
echo "📁 Fichiers générés:"
echo "  - migration-analysis.json"
echo "  - room-mapping.json"
echo "  - migrate-matrix.sql"
echo ""
echo "📊 Statistiques:"
python3 -c "
import json
with open('migration-analysis.json') as f:
    analysis = json.load(f)
with open('room-mapping.json') as f:
    mapping = json.load(f)
print(f\"  - Rooms à migrer: {len(mapping['room_mapping'])}\")
print(f\"  - Rooms réutilisées: {sum(1 for m in mapping['room_mapping'].values() if m.get('reused'))}\")
print(f\"  - Rooms nouvelles: {sum(1 for m in mapping['room_mapping'].values() if not m.get('reused'))}\")
print(f\"  - Users: {len(analysis.get('users', []))}\")
"
echo ""
echo "⚠️  PROCHAINES ÉTAPES:"
echo "  1. Examiner le SQL: cat migrate-matrix.sql"
echo "  2. Faire un backup de la DB cible"
echo "  3. Tester sur une instance de test"
echo "  4. Exécuter: psql -h HOST -U USER -d DATABASE -f migrate-matrix.sql"
echo "  5. Vérifier: psql -h HOST -U USER -d DATABASE -f verify-migration.sql"

