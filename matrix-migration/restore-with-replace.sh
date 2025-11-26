#!/bin/bash
set -e

echo "🔄 RESTAURATION AVEC REMPLACEMENT DANS LE DUMP SQL"
echo "============================================================"

OLD_NS="chat"
NEW_NS="neohoods-chat"
OLD_DB="matrix"
NEW_DB="synapse"
NEW_POSTGRES_POD="neohoods-chat-postgres-0"

# Chemin du dump original
DUMP_FILE="/Users/qcastel/Downloads/bzsnapshot_2025-11-26-09-32-25/backup_matrix_db_2025-11-26_01-00-30.sql"
MODIFIED_DUMP="./db-terresdelaya-modified.sql"

if [ ! -f "$DUMP_FILE" ]; then
    echo "❌ Fichier dump non trouvé: $DUMP_FILE"
    exit 1
fi

echo ""
echo "1️⃣  Création du dump modifié avec remplacement des domaines..."
sed 's/:chat\.terresdelaya\.fr/:chat.neohoods.com/g' "$DUMP_FILE" > "$MODIFIED_DUMP"
echo "✅ Dump modifié créé: $MODIFIED_DUMP ($(du -h "$MODIFIED_DUMP" | cut -f1))"
echo ""

# Sauvegarder les tables MAS
echo "2️⃣  Sauvegarde des tables MAS..."
kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "
\copy (SELECT * FROM application_services_state) TO STDOUT WITH CSV HEADER
" > mas-application_services_state.csv 2>&1 || echo "⚠️  application_services_state vide ou erreur"

kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "
\copy (SELECT * FROM application_services_txns) TO STDOUT WITH CSV HEADER
" > mas-application_services_txns.csv 2>&1 || echo "⚠️  application_services_txns vide ou erreur"

kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "
\copy (SELECT * FROM applied_module_schemas) TO STDOUT WITH CSV HEADER
" > mas-applied_module_schemas.csv 2>&1 || echo "⚠️  applied_module_schemas vide ou erreur"

echo "✅ Tables MAS sauvegardées"
echo ""

echo "⚠️  ⚠️  ⚠️  ATTENTION ⚠️  ⚠️  ⚠️"
echo "Vous allez écraser la DB actuelle"
echo "Appuyez sur ENTER pour continuer ou Ctrl+C pour annuler..."
read

echo ""
echo "3️⃣  Nettoyage de la DB (sauf tables MAS)..."
kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB << 'EOF'
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public' 
              AND tablename NOT IN ('application_services_state', 'application_services_txns', 'applied_module_schemas')) LOOP
        EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.tablename) || ' CASCADE';
    END LOOP;
END $$;
EOF

echo "4️⃣  Restauration du dump modifié..."
kubectl exec -i -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB < "$MODIFIED_DUMP" 2>&1 | grep -E "(ERROR|FATAL)" | head -20 || true
echo "✅ Dump restauré"
echo ""

echo "5️⃣  Restauration des tables MAS..."
if [ -f mas-application_services_state.csv ] && [ -s mas-application_services_state.csv ] && [ $(wc -l < mas-application_services_state.csv) -gt 1 ]; then
    kubectl cp mas-application_services_state.csv $NEW_NS/$NEW_POSTGRES_POD:/tmp/mas_state.csv 2>&1 >/dev/null
    kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "TRUNCATE application_services_state; \copy application_services_state FROM '/tmp/mas_state.csv' WITH CSV HEADER" 2>&1 >/dev/null || echo "⚠️  Erreur restauration application_services_state"
fi

if [ -f mas-application_services_txns.csv ] && [ -s mas-application_services_txns.csv ] && [ $(wc -l < mas-application_services_txns.csv) -gt 1 ]; then
    kubectl cp mas-application_services_txns.csv $NEW_NS/$NEW_POSTGRES_POD:/tmp/mas_txns.csv 2>&1 >/dev/null
    kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "TRUNCATE application_services_txns; \copy application_services_txns FROM '/tmp/mas_txns.csv' WITH CSV HEADER" 2>&1 >/dev/null || echo "⚠️  Erreur restauration application_services_txns"
fi

if [ -f mas-applied_module_schemas.csv ] && [ -s mas-applied_module_schemas.csv ] && [ $(wc -l < mas-applied_module_schemas.csv) -gt 1 ]; then
    kubectl cp mas-applied_module_schemas.csv $NEW_NS/$NEW_POSTGRES_POD:/tmp/mas_schemas.csv 2>&1 >/dev/null
    kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "TRUNCATE applied_module_schemas; \copy applied_module_schemas FROM '/tmp/mas_schemas.csv' WITH CSV HEADER" 2>&1 >/dev/null || echo "⚠️  Erreur restauration applied_module_schemas"
fi

echo "✅ Tables MAS restaurées"
echo ""

echo "6️⃣  Redémarrage de Synapse..."
kubectl delete pod -n $NEW_NS neohoods-chat-synapse-main-0
echo "✅ Synapse redémarré"
echo ""

echo "✅✅✅ RESTAURATION TERMINÉE ✅✅✅"
echo ""
echo "📁 Fichiers créés:"
echo "  - $MODIFIED_DUMP"
echo "  - mas-*.csv (tables MAS)"
echo ""
