#!/bin/bash
# Script de test complet pour la migration Matrix

set -e

BACKUP_FILE="/Users/qcastel/Downloads/bzsnapshot_2025-11-26-09-32-25/backup_matrix_db_2025-11-26_01-00-30.sql"
SPACE_ID="!rgOFmqDljPgniKgNEx:chat.neohoods.com"

echo "🧪 Test complet de la migration Matrix"
echo "========================================"
echo ""

# Étape 1: Analyser le backup
echo "📊 Étape 1: Analyse du backup..."
python3 analyze-backup.py "$BACKUP_FILE"
if [ $? -eq 0 ]; then
    echo "✅ Analyse réussie"
else
    echo "❌ Échec de l'analyse"
    exit 1
fi
echo ""

# Étape 2: Vérifier l'analyse
echo "🔍 Vérification de l'analyse..."
python3 verify-migration.py --analysis migration-analysis.json
echo ""

# Étape 3: Utiliser les rooms de test (ou réelles)
if [ -f "existing-rooms.json" ]; then
    echo "📋 Utilisation des rooms existantes réelles..."
    EXISTING_ROOMS="existing-rooms.json"
else
    echo "📋 Utilisation des rooms de test..."
    EXISTING_ROOMS="test-existing-rooms.json"
fi

# Étape 4: Matcher les rooms
echo "🔗 Étape 2: Matching des rooms..."
python3 match-rooms.py \
    --analysis migration-analysis.json \
    --existing-rooms "$EXISTING_ROOMS" \
    --space-id "$SPACE_ID" \
    --output room-mapping.json
if [ $? -eq 0 ]; then
    echo "✅ Matching réussi"
else
    echo "❌ Échec du matching"
    exit 1
fi
echo ""

# Étape 5: Vérifier le mapping
echo "🔍 Vérification du mapping..."
python3 verify-migration.py --mapping room-mapping.json
echo ""

# Étape 6: Générer le SQL
echo "📝 Étape 3: Génération du SQL..."
python3 generate-migration-sql.py \
    --backup "$BACKUP_FILE" \
    --mapping room-mapping.json \
    --space-id "$SPACE_ID" \
    --output migrate-matrix.sql
if [ $? -eq 0 ]; then
    echo "✅ Génération SQL réussie"
else
    echo "❌ Échec de la génération SQL"
    exit 1
fi
echo ""

# Étape 7: Vérifier le SQL
echo "🔍 Vérification du SQL..."
python3 verify-migration.py --sql migrate-matrix.sql
echo ""

# Résumé final
echo "========================================"
echo "✅ Test complet terminé avec succès !"
echo ""
echo "Fichiers générés:"
echo "  - migration-analysis.json"
echo "  - room-mapping.json"
echo "  - migrate-matrix.sql"
echo ""
echo "Prochaines étapes:"
echo "  1. Examiner le SQL généré: cat migrate-matrix.sql"
echo "  2. Tester sur une instance de test"
echo "  3. Exécuter la migration sur la production"

