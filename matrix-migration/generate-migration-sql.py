#!/usr/bin/env python3
"""
Script pour générer le SQL de migration à partir des mappings.

Génère un script SQL qui migre les données de l'ancienne instance vers la nouvelle.
"""

import json
import sys
import re
import secrets
import string
from typing import Dict, List, Set
import argparse
from datetime import datetime


def generate_event_id(server: str) -> str:
    """Génère un nouvel event ID au format Matrix: $random:server"""
    alphabet = string.ascii_letters + string.digits + '-_'
    random_part = ''.join(secrets.choice(alphabet) for _ in range(43))
    return f"${random_part}:{server}"


def parse_backup_sql(backup_file: str, room_mapping: Dict, user_mapping: Dict) -> Dict:
    """Parse le backup SQL et prépare les données pour la migration."""
    print("Parsing backup SQL...")
    
    # Lire les données nécessaires
    rooms_data = []
    state_events_data = []
    events_data = []
    event_json_data = []
    
    # Parser les rooms
    print("  Parsing rooms...")
    in_rooms_copy = False
    with open(backup_file, 'r', encoding='utf-8') as f:
        for line in f:
            if 'COPY public.rooms' in line:
                in_rooms_copy = True
                continue
            if in_rooms_copy and line.strip() == '\.':
                in_rooms_copy = False
                continue
            if in_rooms_copy:
                values = line.rstrip('\n').split('\t')
                if len(values) >= 5:
                    old_room_id = values[0]
                    if old_room_id in room_mapping:
                        rooms_data.append({
                            'old_room_id': old_room_id,
                            'is_public': values[1],
                            'creator': values[2],
                            'room_version': values[3],
                            'has_auth_chain_index': values[4]
                        })
    
    # Parser les state_events
    print("  Parsing state_events...")
    in_state_events_copy = False
    with open(backup_file, 'r', encoding='utf-8') as f:
        for line in f:
            if 'COPY public.state_events' in line:
                in_state_events_copy = True
                continue
            if in_state_events_copy and line.strip() == '\.':
                in_state_events_copy = False
                continue
            if in_state_events_copy:
                values = line.rstrip('\n').split('\t')
                if len(values) >= 5:
                    old_room_id = values[1]
                    event_type = values[2]
                    # Exclure m.room.encryption
                    if event_type != 'm.room.encryption' and old_room_id in room_mapping:
                        state_events_data.append({
                            'old_event_id': values[0],
                            'old_room_id': old_room_id,
                            'type': event_type,
                            'state_key': values[3],
                            'prev_state': values[4] if len(values) > 4 else None
                        })
    
    # Parser les events (messages uniquement)
    print("  Parsing events (messages)...")
    in_events_copy = False
    with open(backup_file, 'r', encoding='utf-8') as f:
        for line in f:
            if 'COPY public.events' in line:
                in_events_copy = True
                continue
            if in_events_copy and line.strip() == '\.':
                in_events_copy = False
                continue
            if in_events_copy:
                values = line.rstrip('\n').split('\t')
                if len(values) >= 17:
                    old_room_id = values[2]
                    event_type = values[1]
                    # Migrer uniquement les messages des rooms non-encryptées
                    if event_type == 'm.room.message' and old_room_id in room_mapping:
                        events_data.append({
                            'topological_ordering': values[0],
                            'old_event_id': values[1],
                            'type': event_type,
                            'old_room_id': old_room_id,
                            'content': values[4],
                            'unrecognized_keys': values[5],
                            'processed': values[6],
                            'outlier': values[7],
                            'depth': values[8],
                            'origin_server_ts': values[9],
                            'received_ts': values[10],
                            'sender': values[11],
                            'contains_url': values[12],
                            'instance_name': values[13],
                            'stream_ordering': values[14],
                            'state_key': values[15],
                            'rejection_reason': values[16] if len(values) > 16 else None
                        })
    
    # Parser event_json pour obtenir le JSON complet des events
    print("  Parsing event_json...")
    in_event_json_copy = False
    with open(backup_file, 'r', encoding='utf-8') as f:
        for line in f:
            if 'COPY public.event_json' in line:
                in_event_json_copy = True
                continue
            if in_event_json_copy and line.strip() == '\.':
                in_event_json_copy = False
                continue
            if in_event_json_copy:
                values = line.rstrip('\n').split('\t')
                if len(values) >= 5:
                    old_event_id = values[0]
                    old_room_id = values[1]
                    # Ne garder que les events des rooms non-encryptées
                    if old_room_id in room_mapping:
                        event_json_data.append({
                            'old_event_id': old_event_id,
                            'old_room_id': old_room_id,
                            'internal_metadata': values[2],
                            'json': values[3],
                            'format_version': values[4] if len(values) > 4 else None
                        })
    
    return {
        'rooms': rooms_data,
        'state_events': state_events_data,
        'events': events_data,
        'event_json': event_json_data
    }


def generate_migration_sql(
    backup_file: str,
    room_mapping_file: str,
    space_id: str,
    old_server: str,
    new_server: str,
    output_file: str
):
    """Génère le script SQL de migration."""
    
    # Charger les mappings
    print("Loading room mapping...")
    with open(room_mapping_file, 'r', encoding='utf-8') as f:
        mapping_data = json.load(f)
    
    room_mapping = mapping_data['room_mapping']
    user_mapping = mapping_data['user_mapping']
    
    # Parser le backup
    backup_data = parse_backup_sql(backup_file, room_mapping, user_mapping)
    
    # Générer les mappings d'event IDs
    print("Generating event ID mappings...")
    event_mapping = {}
    
    # Mapper tous les event IDs (state_events, events, event_json)
    all_event_ids = set()
    for state_event in backup_data['state_events']:
        all_event_ids.add(state_event['old_event_id'])
    for event in backup_data['events']:
        all_event_ids.add(event['old_event_id'])
    for event_json in backup_data['event_json']:
        all_event_ids.add(event_json['old_event_id'])
    
    for old_event_id in all_event_ids:
        if old_event_id not in event_mapping:
            event_mapping[old_event_id] = generate_event_id(new_server)
    
    # Générer le SQL
    print("Generating migration SQL...")
    sql_lines = []
    
    sql_lines.append("-- Matrix Migration SQL Script")
    sql_lines.append(f"-- Generated: {datetime.now().isoformat()}")
    sql_lines.append(f"-- Space ID: {space_id}")
    sql_lines.append(f"-- Old Server: {old_server}")
    sql_lines.append(f"-- New Server: {new_server}")
    sql_lines.append("")
    sql_lines.append("BEGIN;")
    sql_lines.append("")
    
    # 1. Migrer les users (créer une table temporaire pour le mapping)
    sql_lines.append("-- ========================================")
    sql_lines.append("-- 1. User Migration")
    sql_lines.append("-- ========================================")
    sql_lines.append("")
    sql_lines.append("-- Note: Users are handled separately via MAS/Matrix API")
    sql_lines.append("-- This script focuses on rooms and events")
    sql_lines.append("")
    
    # 2. Migrer les rooms (seulement les nouvelles, pas les réutilisées)
    sql_lines.append("-- ========================================")
    sql_lines.append("-- 2. Room Migration (new rooms only)")
    sql_lines.append("-- ========================================")
    sql_lines.append("")
    
    new_rooms = [r for r in backup_data['rooms'] if not room_mapping[r['old_room_id']]['reused']]
    if new_rooms:
        sql_lines.append("INSERT INTO public.rooms (room_id, is_public, creator, room_version, has_auth_chain_index)")
        sql_lines.append("VALUES")
        room_values = []
        for room in new_rooms:
            new_room_id = room_mapping[room['old_room_id']]['new_room_id']
            creator = room['creator']
            if creator:
                creator = creator.replace(f":{old_server}", f":{new_server}")
            creator_sql = 'NULL' if not creator else f"'{creator}'"
            is_public_sql = "'t'" if room['is_public'] == 't' else "'f'"
            has_auth_chain_index_sql = "'t'" if room['has_auth_chain_index'] == 't' else "'f'"
            room_values.append(
                f"('{new_room_id}', {is_public_sql}, "
                f"{creator_sql}, "
                f"'{room['room_version']}', {has_auth_chain_index_sql})"
            )
        sql_lines.append(",\n".join(room_values) + ";")
    else:
        sql_lines.append("-- No new rooms to create (all rooms are reused)")
    
    sql_lines.append("")
    
    # 3. Migrer les state_events
    sql_lines.append("-- ========================================")
    sql_lines.append("-- 3. State Events Migration")
    sql_lines.append("-- ========================================")
    sql_lines.append("")
    
    # Générer les INSERT pour state_events
    if backup_data['state_events']:
        sql_lines.append("INSERT INTO public.state_events (event_id, room_id, type, state_key, prev_state)")
        sql_lines.append("VALUES")
        state_event_values = []
        
        for state_event in backup_data['state_events']:
            new_event_id = event_mapping[state_event['old_event_id']]
            new_room_id = room_mapping[state_event['old_room_id']]['new_room_id']
            event_type = state_event['type']
            state_key = state_event['state_key'] or ''
            
            # Mettre à jour les user IDs dans state_key
            if state_key and state_key.startswith('@'):
                for old_user_id, new_user_id in user_mapping.items():
                    if old_user_id in state_key:
                        state_key = state_key.replace(old_user_id, new_user_id)
                        break
            
            prev_state = state_event['prev_state']
            
            # Mapper prev_state si nécessaire
            if prev_state and prev_state != '\\N' and prev_state in event_mapping:
                prev_state = event_mapping[prev_state]
            elif prev_state == '\\N':
                prev_state = 'NULL'
            
            state_key_escaped = state_key.replace("'", "''")
            prev_state_sql = f"'{prev_state}'" if prev_state and prev_state != 'NULL' else 'NULL'
            
            state_event_values.append(
                f"('{new_event_id}', '{new_room_id}', '{event_type}', "
                f"'{state_key_escaped}', {prev_state_sql})"
            )
        
        sql_lines.append(",\n".join(state_event_values) + ";")
    
    sql_lines.append("")
    
    # 4. Ajouter m.space.parent pour toutes les rooms (nouvelles et réutilisées)
    sql_lines.append("-- ========================================")
    sql_lines.append("-- 4. Add m.space.parent for all rooms")
    sql_lines.append("-- ========================================")
    sql_lines.append("")
    
    # Pour chaque room migrée, créer un event m.space.parent
    space_parent_events = []
    for old_room_id, mapping in room_mapping.items():
        new_room_id = mapping['new_room_id']
        # Vérifier si m.space.parent existe déjà (pour les rooms réutilisées)
        # On va l'ajouter quand même, PostgreSQL gérera les conflits
        space_parent_event_id = generate_event_id(new_server)
        space_parent_events.append({
            'event_id': space_parent_event_id,
            'room_id': new_room_id,
            'space_id': space_id
        })
    
    if space_parent_events:
        # Note: On doit aussi créer l'event correspondant dans la table events
        # Pour simplifier, on va juste créer le state_event
        # L'event complet devra être créé via l'API Matrix ou un script séparé
        sql_lines.append("-- Add m.space.parent state events")
        sql_lines.append("-- Note: These events also need to be added to the events table")
        sql_lines.append("-- This is a simplified version - full events should be created via Matrix API")
        sql_lines.append("")
        sql_lines.append("INSERT INTO public.state_events (event_id, room_id, type, state_key, prev_state)")
        sql_lines.append("VALUES")
        space_parent_values = []
        for spe in space_parent_events:
            space_parent_values.append(
                f"('{spe['event_id']}', '{spe['room_id']}', 'm.space.parent', "
                f"'{spe['space_id']}', NULL)"
            )
        sql_lines.append(",\n".join(space_parent_values) + ";")
    
    sql_lines.append("")
    
    # 5. Migrer les events (messages) et event_json
    sql_lines.append("-- ========================================")
    sql_lines.append("-- 5. Events (Messages) Migration")
    sql_lines.append("-- ========================================")
    sql_lines.append("")
    
    # Créer un index event_json par event_id pour faciliter la mise à jour
    event_json_by_id = {ej['old_event_id']: ej for ej in backup_data['event_json']}
    
    # Migrer les events (table events)
    if backup_data['events']:
        sql_lines.append("-- Migrate events table")
        sql_lines.append("-- Note: stream_ordering and topological_ordering need to be adjusted")
        sql_lines.append("-- for reused rooms (continue from existing max values)")
        sql_lines.append("")
        sql_lines.append("INSERT INTO public.events (topological_ordering, event_id, type, room_id, content, unrecognized_keys, processed, outlier, depth, origin_server_ts, received_ts, sender, contains_url, instance_name, stream_ordering, state_key, rejection_reason)")
        sql_lines.append("VALUES")
        event_values = []
        
        for event in backup_data['events']:
            new_event_id = event_mapping[event['old_event_id']]
            new_room_id = room_mapping[event['old_room_id']]['new_room_id']
            event_type = event['type']
            
            # Mettre à jour le sender
            sender = event['sender']
            if sender:
                for old_user_id, new_user_id in user_mapping.items():
                    if old_user_id in sender:
                        sender = sender.replace(old_user_id, new_user_id)
                        break
            
            # Content est NULL dans events, le vrai content est dans event_json
            content_sql = 'NULL'
            
            # Autres champs
            unrecognized_keys = event.get('unrecognized_keys') or 'NULL'
            if unrecognized_keys != 'NULL' and unrecognized_keys != '\\N':
                unrecognized_keys_escaped = unrecognized_keys.replace("'", "''")
                unrecognized_keys = f"'{unrecognized_keys_escaped}'"
            
            processed_sql = "'t'" if event['processed'] == 't' else "'f'"
            outlier_sql = "'t'" if event['outlier'] == 't' else "'f'"
            
            if not sender or sender == '\\N':
                sender_sql = 'NULL'
            else:
                sender_escaped = sender.replace("'", "''")
                sender_sql = f"'{sender_escaped}'"
            
            if not event.get('state_key') or event['state_key'] == '\\N':
                state_key_sql = 'NULL'
            else:
                state_key_escaped = event['state_key'].replace("'", "''")
                state_key_sql = f"'{state_key_escaped}'"
            
            if not event.get('rejection_reason') or event['rejection_reason'] == '\\N':
                rejection_reason_sql = 'NULL'
            else:
                rejection_reason_escaped = event['rejection_reason'].replace("'", "''")
                rejection_reason_sql = f"'{rejection_reason_escaped}'"
            
            event_values.append(
                f"({event['topological_ordering']}, '{new_event_id}', '{event_type}', "
                f"'{new_room_id}', {content_sql}, {unrecognized_keys}, {processed_sql}, "
                f"{outlier_sql}, {event['depth']}, {event['origin_server_ts']}, "
                f"{event['received_ts']}, {sender_sql}, '{event['contains_url']}', "
                f"'{event['instance_name']}', {event['stream_ordering']}, {state_key_sql}, "
                f"{rejection_reason_sql})"
            )
        
        sql_lines.append(",\n".join(event_values) + ";")
        sql_lines.append("")
    
    # Migrer event_json avec mise à jour du JSON content
    if backup_data['event_json']:
        sql_lines.append("-- Migrate event_json table with updated JSON content")
        sql_lines.append("-- JSON content is updated to replace old IDs with new ones")
        sql_lines.append("-- Using ON CONFLICT DO NOTHING to skip existing events")
        sql_lines.append("")
        sql_lines.append("INSERT INTO public.event_json (event_id, room_id, internal_metadata, json, format_version)")
        sql_lines.append("VALUES")
        event_json_values = []
        
        for event_json in backup_data['event_json']:
            new_event_id = event_mapping[event_json['old_event_id']]
            new_room_id = room_mapping[event_json['old_room_id']]['new_room_id']
            json_str = event_json['json']
            
            # Mettre à jour le JSON content
            try:
                json_data = json.loads(json_str)
                
                # Mettre à jour room_id
                if 'room_id' in json_data:
                    json_data['room_id'] = new_room_id
                
                # Mettre à jour sender
                if 'sender' in json_data:
                    old_sender = json_data['sender']
                    for old_user_id, new_user_id in user_mapping.items():
                        if old_user_id in old_sender:
                            json_data['sender'] = old_sender.replace(old_user_id, new_user_id)
                            break
                
                # Mettre à jour prev_events
                if 'prev_events' in json_data and isinstance(json_data['prev_events'], list):
                    updated_prev_events = []
                    for prev_event in json_data['prev_events']:
                        if isinstance(prev_event, list) and len(prev_event) > 0:
                            old_prev_id = prev_event[0]
                            if old_prev_id in event_mapping:
                                updated_prev_events.append([event_mapping[old_prev_id], prev_event[1] if len(prev_event) > 1 else ""])
                            else:
                                updated_prev_events.append(prev_event)
                        else:
                            updated_prev_events.append(prev_event)
                    json_data['prev_events'] = updated_prev_events
                
                # Mettre à jour auth_events
                if 'auth_events' in json_data and isinstance(json_data['auth_events'], list):
                    updated_auth_events = []
                    for auth_event in json_data['auth_events']:
                        if isinstance(auth_event, str) and auth_event in event_mapping:
                            updated_auth_events.append(event_mapping[auth_event])
                        else:
                            updated_auth_events.append(auth_event)
                    json_data['auth_events'] = updated_auth_events
                
                # Mettre à jour origin (server name)
                if 'origin' in json_data:
                    json_data['origin'] = new_server
                
                # Mettre à jour les signatures (changer le server name)
                if 'signatures' in json_data and isinstance(json_data['signatures'], dict):
                    if old_server in json_data['signatures']:
                        json_data['signatures'][new_server] = json_data['signatures'].pop(old_server)
                
                # Régénérer le JSON
                updated_json_str = json.dumps(json_data, ensure_ascii=False)
                
            except (json.JSONDecodeError, TypeError, KeyError) as e:
                # Si erreur, faire un remplacement simple
                updated_json_str = json_str
                updated_json_str = updated_json_str.replace(f":{old_server}", f":{new_server}")
                for old_room_id, mapping in room_mapping.items():
                    updated_json_str = updated_json_str.replace(old_room_id, mapping['new_room_id'])
                for old_user_id, new_user_id in user_mapping.items():
                    updated_json_str = updated_json_str.replace(old_user_id, new_user_id)
                for old_event_id, new_event_id in event_mapping.items():
                    updated_json_str = updated_json_str.replace(old_event_id, new_event_id)
            
            # Échapper les quotes pour SQL
            updated_json_str_escaped = updated_json_str.replace("'", "''")
            internal_metadata = event_json.get('internal_metadata') or '{}'
            internal_metadata_escaped = internal_metadata.replace("'", "''")
            format_version = event_json.get('format_version') or '3'
            
            event_json_values.append(
                f"('{new_event_id}', '{new_room_id}', '{internal_metadata_escaped}', "
                f"'{updated_json_str_escaped}', {format_version})"
            )
        
        sql_lines.append(",\n".join(event_json_values))
        sql_lines.append("ON CONFLICT (event_id) DO NOTHING;")
        sql_lines.append("")
    
    sql_lines.append("COMMIT;")
    sql_lines.append("")
    sql_lines.append("-- ========================================")
    sql_lines.append("-- Migration Summary")
    sql_lines.append("-- ========================================")
    sql_lines.append(f"-- Rooms to migrate: {len(room_mapping)}")
    sql_lines.append(f"--   - Reused: {sum(1 for m in room_mapping.values() if m['reused'])}")
    sql_lines.append(f"--   - New: {sum(1 for m in room_mapping.values() if not m['reused'])}")
    sql_lines.append(f"-- State events: {len(backup_data['state_events'])}")
    sql_lines.append(f"-- Message events: {len(backup_data['events'])}")
    sql_lines.append(f"-- Event JSON entries: {len(backup_data['event_json'])}")
    sql_lines.append("")
    sql_lines.append("-- IMPORTANT: Review and test this script before running on production!")
    
    # Écrire le fichier SQL
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write('\n'.join(sql_lines))
    
    print(f"\n✓ Migration SQL generated: {output_file}")
    print(f"  - Rooms: {len(backup_data['rooms'])}")
    print(f"  - State events: {len(backup_data['state_events'])}")
    print(f"  - Message events: {len(backup_data['events'])}")
    print(f"  - Event JSON entries: {len(backup_data['event_json'])}")
    print("\n⚠️  NOTE: Event JSON content has been updated with new IDs.")
    print("   Stream ordering may need adjustment for reused rooms.")


def main():
    parser = argparse.ArgumentParser(
        description='Generate SQL migration script from mappings'
    )
    parser.add_argument(
        '--backup',
        required=True,
        help='Backup SQL file path'
    )
    parser.add_argument(
        '--mapping',
        default='room-mapping.json',
        help='Room mapping JSON file (default: room-mapping.json)'
    )
    parser.add_argument(
        '--space-id',
        required=True,
        help='Target space ID'
    )
    parser.add_argument(
        '--old-server',
        default='chat.terresdelaya.fr',
        help='Old server name (default: chat.terresdelaya.fr)'
    )
    parser.add_argument(
        '--new-server',
        default='chat.neohoods.com',
        help='New server name (default: chat.neohoods.com)'
    )
    parser.add_argument(
        '--output',
        default='migrate-matrix.sql',
        help='Output SQL file (default: migrate-matrix.sql)'
    )
    
    args = parser.parse_args()
    
    generate_migration_sql(
        args.backup,
        args.mapping,
        args.space_id,
        args.old_server,
        args.new_server,
        args.output
    )


if __name__ == '__main__':
    main()
