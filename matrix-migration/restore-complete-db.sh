#!/bin/bash
set -e

echo "🔄 RESTAURATION COMPLÈTE DE LA DB MATRIX"
echo "============================================================"
echo ""
echo "Cette opération va :"
echo "  1. Exporter homeserver.yaml et keys de chat.terresdelaya.fr"
echo "  2. Faire un dump complet de la DB de chat.terresdelaya.fr"
echo "  3. Sauvegarder les tables MAS de chat.neohoods.com"
echo "  4. Restaurer la DB complète dans chat.neohoods.com"
echo "  5. Restaurer les tables MAS"
echo "  6. Mettre à jour les configs pour chat.neohoods.com"
echo ""

# Namespaces
OLD_NS="chat"
NEW_NS="neohoods-chat"
OLD_DB="matrix"
NEW_DB="synapse"

# Pods
OLD_SYNAPSE_POD=$(kubectl get pods -n $OLD_NS | grep "chat-matrix" | grep Running | awk '{print $1}' | head -1)
OLD_POSTGRES_POD=$(kubectl get pods -n $OLD_NS | grep "chat-postgresql" | grep Running | awk '{print $1}' | head -1)
NEW_POSTGRES_POD="neohoods-chat-postgres-0"

echo "📋 Pods identifiés:"
echo "  - Old Synapse: $OLD_SYNAPSE_POD"
echo "  - Old Postgres: $OLD_POSTGRES_POD"
echo "  - New Postgres: $NEW_POSTGRES_POD"
echo ""

# Créer le dossier de backup
BACKUP_DIR="./complete-restore-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"
echo "📁 Dossier de backup: $BACKUP_DIR"
echo ""

# 1. Exporter homeserver.yaml
echo "1️⃣  Export homeserver.yaml..."
kubectl exec -n $OLD_NS $OLD_SYNAPSE_POD -- cat /data/homeserver.yaml > "$BACKUP_DIR/homeserver.terresdelaya.yaml" 2>/dev/null || \
kubectl exec -n $OLD_NS $OLD_SYNAPSE_POD -- cat /etc/matrix-synapse/homeserver.yaml > "$BACKUP_DIR/homeserver.terresdelaya.yaml" 2>/dev/null || \
echo "⚠️  Impossible de trouver homeserver.yaml, continuons..."

# 2. Exporter les keys
echo "2️⃣  Export des keys..."
kubectl exec -n $OLD_NS $OLD_SYNAPSE_POD -- tar czf - /data/*.key /data/*.pem /data/signatures 2>/dev/null | tar xzf - -C "$BACKUP_DIR/" || \
kubectl exec -n $OLD_NS $OLD_SYNAPSE_POD -- tar czf - /etc/matrix-synapse/*.key /etc/matrix-synapse/*.pem 2>/dev/null | tar xzf - -C "$BACKUP_DIR/" || \
echo "⚠️  Impossible de trouver les keys, continuons..."

# 3. Dump complet de la DB old
echo "3️⃣  Dump complet de la DB chat.terresdelaya.fr..."
OLD_PG_PASSWORD=$(kubectl get secret -n $OLD_NS chat-postgresql -o jsonpath="{.data.password}" | base64 -d)
kubectl exec -n $OLD_NS $OLD_POSTGRES_POD -- env PGPASSWORD="$OLD_PG_PASSWORD" pg_dump -U matrix -d $OLD_DB > "$BACKUP_DIR/db-terresdelaya-complete.sql"
echo "✅ Dump créé: $BACKUP_DIR/db-terresdelaya-complete.sql ($(du -h "$BACKUP_DIR/db-terresdelaya-complete.sql" | cut -f1))"
echo ""

# 4. Sauvegarder les tables MAS de la nouvelle DB
echo "4️⃣  Sauvegarde des tables MAS de chat.neohoods.com..."
kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "
-- Dump des tables MAS
\copy (SELECT * FROM application_services_state) TO STDOUT WITH CSV HEADER
" > "$BACKUP_DIR/mas-application_services_state.csv" 2>&1 || echo "⚠️  Erreur application_services_state"

kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "
\copy (SELECT * FROM application_services_txns) TO STDOUT WITH CSV HEADER
" > "$BACKUP_DIR/mas-application_services_txns.csv" 2>&1 || echo "⚠️  Erreur application_services_txns"

kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "
\copy (SELECT * FROM applied_module_schemas) TO STDOUT WITH CSV HEADER
" > "$BACKUP_DIR/mas-applied_module_schemas.csv" 2>&1 || echo "⚠️  Erreur applied_module_schemas"

echo "✅ Tables MAS sauvegardées"
echo ""

# 5. Restaurer la DB complète (ATTENTION: cela va écraser la DB actuelle)
echo "⚠️  ⚠️  ⚠️  ATTENTION ⚠️  ⚠️  ⚠️"
echo "Vous êtes sur le point d'écraser la DB de chat.neohoods.com"
echo "Appuyez sur ENTER pour continuer ou Ctrl+C pour annuler..."
read

echo "5️⃣  Restauration de la DB complète..."
echo "   Création d'un dump propre (sans CREATE/DROP pour tables MAS)..."

# Créer un dump modifié qui exclut les tables MAS
grep -v "CREATE TABLE.*application_services" "$BACKUP_DIR/db-terresdelaya-complete.sql" | \
grep -v "CREATE TABLE.*applied_module" | \
grep -v "DROP TABLE.*application_services" | \
grep -v "DROP TABLE.*applied_module" > "$BACKUP_DIR/db-terresdelaya-no-mas.sql" || true

# D'abord, vider la DB (sauf les tables MAS)
echo "   Nettoyage de la DB (sauf tables MAS)..."
kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB << 'EOF'
-- Supprimer toutes les tables sauf les tables MAS
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

# Restaurer le dump
echo "   Import du dump..."
kubectl exec -i -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB < "$BACKUP_DIR/db-terresdelaya-no-mas.sql" 2>&1 | grep -E "(ERROR|FATAL)" || true
echo "✅ DB restaurée"
echo ""

# 6. Restaurer les tables MAS
echo "6️⃣  Restauration des tables MAS..."
if [ -f "$BACKUP_DIR/mas-application_services_state.csv" ] && [ -s "$BACKUP_DIR/mas-application_services_state.csv" ] && [ $(wc -l < "$BACKUP_DIR/mas-application_services_state.csv") -gt 1 ]; then
    echo "   Restauration application_services_state..."
    kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "TRUNCATE application_services_state;" 2>&1 >/dev/null
    kubectl cp "$BACKUP_DIR/mas-application_services_state.csv" $NEW_NS/$NEW_POSTGRES_POD:/tmp/mas_state.csv 2>&1 >/dev/null
    kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "\copy application_services_state FROM '/tmp/mas_state.csv' WITH CSV HEADER" 2>&1 >/dev/null || echo "⚠️  Erreur restauration application_services_state"
fi

if [ -f "$BACKUP_DIR/mas-application_services_txns.csv" ] && [ -s "$BACKUP_DIR/mas-application_services_txns.csv" ] && [ $(wc -l < "$BACKUP_DIR/mas-application_services_txns.csv") -gt 1 ]; then
    echo "   Restauration application_services_txns..."
    kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "TRUNCATE application_services_txns;" 2>&1 >/dev/null
    kubectl cp "$BACKUP_DIR/mas-application_services_txns.csv" $NEW_NS/$NEW_POSTGRES_POD:/tmp/mas_txns.csv 2>&1 >/dev/null
    kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "\copy application_services_txns FROM '/tmp/mas_txns.csv' WITH CSV HEADER" 2>&1 >/dev/null || echo "⚠️  Erreur restauration application_services_txns"
fi

if [ -f "$BACKUP_DIR/mas-applied_module_schemas.csv" ] && [ -s "$BACKUP_DIR/mas-applied_module_schemas.csv" ] && [ $(wc -l < "$BACKUP_DIR/mas-applied_module_schemas.csv") -gt 1 ]; then
    echo "   Restauration applied_module_schemas..."
    kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "TRUNCATE applied_module_schemas;" 2>&1 >/dev/null
    kubectl cp "$BACKUP_DIR/mas-applied_module_schemas.csv" $NEW_NS/$NEW_POSTGRES_POD:/tmp/mas_schemas.csv 2>&1 >/dev/null
    kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB -c "\copy applied_module_schemas FROM '/tmp/mas_schemas.csv' WITH CSV HEADER" 2>&1 >/dev/null || echo "⚠️  Erreur restauration applied_module_schemas"
fi

echo "✅ Tables MAS restaurées"
echo ""

# 7. Mettre à jour les configs pour chat.neohoods.com
echo "7️⃣  Mise à jour des configs pour chat.neohoods.com..."
kubectl exec -n $NEW_NS $NEW_POSTGRES_POD -- psql -U postgres -d $NEW_DB << 'EOF'
-- Remplacer chat.terresdelaya.fr par chat.neohoods.com dans les tables clés
UPDATE users SET name = REPLACE(name, ':chat.terresdelaya.fr', ':chat.neohoods.com') WHERE name LIKE '%:chat.terresdelaya.fr';
UPDATE rooms SET room_id = REPLACE(room_id, ':chat.terresdelaya.fr', ':chat.neohoods.com') WHERE room_id LIKE '%:chat.terresdelaya.fr';
UPDATE events SET room_id = REPLACE(room_id, ':chat.terresdelaya.fr', ':chat.neohoods.com'), sender = REPLACE(sender, ':chat.terresdelaya.fr', ':chat.neohoods.com') WHERE room_id LIKE '%:chat.terresdelaya.fr' OR sender LIKE '%:chat.terresdelaya.fr';
UPDATE state_events SET room_id = REPLACE(room_id, ':chat.terresdelaya.fr', ':chat.neohoods.com'), state_key = REPLACE(state_key, ':chat.terresdelaya.fr', ':chat.neohoods.com') WHERE room_id LIKE '%:chat.terresdelaya.fr' OR state_key LIKE '%:chat.terresdelaya.fr';
UPDATE current_state_events SET room_id = REPLACE(room_id, ':chat.terresdelaya.fr', ':chat.neohoods.com'), state_key = REPLACE(state_key, ':chat.terresdelaya.fr', ':chat.neohoods.com') WHERE room_id LIKE '%:chat.terresdelaya.fr' OR state_key LIKE '%:chat.terresdelaya.fr';
UPDATE room_memberships SET room_id = REPLACE(room_id, ':chat.terresdelaya.fr', ':chat.neohoods.com'), user_id = REPLACE(user_id, ':chat.terresdelaya.fr', ':chat.neohoods.com'), sender = REPLACE(sender, ':chat.terresdelaya.fr', ':chat.neohoods.com') WHERE room_id LIKE '%:chat.terresdelaya.fr' OR user_id LIKE '%:chat.terresdelaya.fr' OR sender LIKE '%:chat.terresdelaya.fr';
UPDATE event_json SET room_id = REPLACE(room_id, ':chat.terresdelaya.fr', ':chat.neohoods.com') WHERE room_id LIKE '%:chat.terresdelaya.fr';
-- Mettre à jour les JSON dans event_json
UPDATE event_json SET json = REPLACE(json::text, ':chat.terresdelaya.fr', ':chat.neohoods.com')::json WHERE json::text LIKE '%:chat.terresdelaya.fr%';
EOF

echo "✅ Configs mises à jour"
echo ""

# 8. Redémarrer Synapse
echo "8️⃣  Redémarrage de Synapse..."
kubectl delete pod -n $NEW_NS neohoods-chat-synapse-main-0
echo "✅ Synapse redémarré"
echo ""

echo "✅✅✅ RESTAURATION TERMINÉE ✅✅✅"
echo ""
echo "📁 Backup sauvegardé dans: $BACKUP_DIR"
echo ""
echo "⚠️  Prochaines étapes:"
echo "  1. Vérifier que Synapse démarre correctement"
echo "  2. Vérifier les rooms et messages"
echo "  3. Déplacer les rooms dans le space !YenniyNVsUoBCLHtZS:chat.neohoods.com"
echo ""

