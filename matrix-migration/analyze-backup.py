#!/usr/bin/env python3
"""
Script d'analyse du backup SQL Matrix pour extraire les informations nécessaires à la migration.

Extrait :
- Liste des rooms non-encryptées
- Noms des rooms (depuis m.room.name state events)
- Liste des users
- Dépendances entre events
"""

import re
import json
import sys
from collections import defaultdict
from typing import Dict, Set, List, Optional

# Configuration
OLD_SERVER = "chat.terresdelaya.fr"
NEW_SERVER = "chat.neohoods.com"


def parse_copy_block(file_path: str, table_name: str) -> List[Dict]:
    """Parse un bloc COPY dans le backup SQL et retourne les lignes."""
    data = []
    in_copy = False
    current_table = None
    
    with open(file_path, 'r', encoding='utf-8') as f:
        for line in f:
            # Détecter le début d'un bloc COPY
            copy_match = re.match(rf'COPY public\.{table_name}\s*\((.*?)\)\s*FROM stdin;', line)
            if copy_match:
                in_copy = True
                current_table = table_name
                columns = [col.strip() for col in copy_match.group(1).split(',')]
                continue
            
            # Détecter la fin d'un bloc COPY
            if in_copy and line.strip() == '\.':
                in_copy = False
                current_table = None
                continue
            
            # Parser les données du bloc COPY
            if in_copy and current_table == table_name:
                # Les données sont séparées par des tabulations
                values = line.rstrip('\n').split('\t')
                if len(values) == len(columns):
                    row = dict(zip(columns, values))
                    # Convertir \N en None
                    for key, value in row.items():
                        if value == '\\N':
                            row[key] = None
                    data.append(row)
    
    return data


def extract_room_names_from_events(event_json_data: List[Dict], state_events: List[Dict]) -> Dict[str, str]:
    """
    Extrait les noms des rooms depuis les state_events de type m.room.name.
    Utilise event_json pour obtenir le contenu JSON complet.
    Retourne un dict room_id -> room_name
    """
    room_names = {}
    
    # Créer un index des event_json par event_id pour accéder au JSON
    event_json_by_id = {e['event_id']: e for e in event_json_data if e.get('event_id')}
    
    # Parcourir les state_events pour trouver m.room.name
    for state_event in state_events:
        if state_event.get('type') == 'm.room.name' and state_event.get('state_key') == '':
            room_id = state_event.get('room_id')
            event_id = state_event.get('event_id')
            
            if room_id and event_id and event_id in event_json_by_id:
                event_json = event_json_by_id[event_id]
                json_str = event_json.get('json')
                
                if json_str:
                    try:
                        # Le JSON contient l'event complet
                        event_data = json.loads(json_str)
                        content = event_data.get('content', {})
                        room_name = content.get('name')
                        if room_name:
                            room_names[room_id] = room_name
                    except (json.JSONDecodeError, AttributeError, TypeError):
                        pass
    
    return room_names


def identify_encrypted_rooms(state_events: List[Dict]) -> Set[str]:
    """Identifie les rooms qui ont m.room.encryption dans leurs state_events."""
    encrypted_rooms = set()
    
    for state_event in state_events:
        if state_event.get('type') == 'm.room.encryption':
            room_id = state_event.get('room_id')
            if room_id:
                encrypted_rooms.add(room_id)
    
    return encrypted_rooms


def extract_users_from_events(events: List[Dict], event_json_data: List[Dict]) -> Set[str]:
    """Extrait tous les user IDs uniques depuis les events."""
    users = set()
    
    # Créer un index event_json par event_id
    event_json_by_id = {e['event_id']: e for e in event_json_data if e.get('event_id')}
    
    for event in events:
        sender = event.get('sender')
        if sender and sender.startswith('@'):
            users.add(sender)
        
        # Extraire aussi depuis le JSON complet si disponible
        event_id = event.get('event_id')
        if event_id and event_id in event_json_by_id:
            event_json = event_json_by_id[event_id]
            json_str = event_json.get('json')
            if json_str:
                try:
                    event_data = json.loads(json_str)
                    # Extraire sender
                    sender = event_data.get('sender')
                    if sender and sender.startswith('@'):
                        users.add(sender)
                    
                    # Extraire depuis content
                    content = event_data.get('content', {})
                    if isinstance(content, dict):
                        for key, value in content.items():
                            if isinstance(value, str) and value.startswith('@'):
                                users.add(value)
                            elif isinstance(value, list):
                                for item in value:
                                    if isinstance(item, str) and item.startswith('@'):
                                        users.add(item)
                            elif isinstance(value, dict):
                                # Pour les objets comme users dans power_levels
                                for sub_key, sub_value in value.items():
                                    if isinstance(sub_value, (str, int)) and isinstance(sub_key, str) and sub_key.startswith('@'):
                                        users.add(sub_key)
                except (json.JSONDecodeError, AttributeError, TypeError):
                    pass
    
    return users


def analyze_dependencies(events: List[Dict], event_json_data: List[Dict]) -> Dict[str, List[str]]:
    """
    Analyse les dépendances entre events (prev_events, auth_events).
    Retourne un dict event_id -> [dépendant_event_ids]
    """
    dependencies = defaultdict(list)
    
    # Créer un index event_json par event_id
    event_json_by_id = {e['event_id']: e for e in event_json_data if e.get('event_id')}
    
    for event in events:
        event_id = event.get('event_id')
        if not event_id:
            continue
        
        # Utiliser event_json pour obtenir le JSON complet
        if event_id in event_json_by_id:
            event_json = event_json_by_id[event_id]
            json_str = event_json.get('json')
            if json_str:
                try:
                    event_data = json.loads(json_str)
                    
                    # Extraire prev_events
                    prev_events = event_data.get('prev_events', [])
                    if isinstance(prev_events, list):
                        for prev_event in prev_events:
                            if isinstance(prev_event, list) and len(prev_event) > 0:
                                prev_event_id = prev_event[0]
                                if prev_event_id:
                                    dependencies[prev_event_id].append(event_id)
                            elif isinstance(prev_event, str):
                                dependencies[prev_event].append(event_id)
                    
                    # Extraire auth_events
                    auth_events = event_data.get('auth_events', [])
                    if isinstance(auth_events, list):
                        for auth_event in auth_events:
                            if isinstance(auth_event, str):
                                dependencies[auth_event].append(event_id)
                except (json.JSONDecodeError, AttributeError, TypeError):
                    pass
    
    return dict(dependencies)


def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze-backup.py <backup_sql_file>")
        sys.exit(1)
    
    backup_file = sys.argv[1]
    
    print("Analyzing backup SQL file...")
    print(f"Reading: {backup_file}")
    
    # Parser les tables nécessaires
    print("\n1. Parsing rooms table...")
    rooms = parse_copy_block(backup_file, 'rooms')
    print(f"   Found {len(rooms)} rooms")
    
    print("\n2. Parsing state_events table...")
    state_events = parse_copy_block(backup_file, 'state_events')
    print(f"   Found {len(state_events)} state events")
    
    print("\n3. Parsing events table...")
    events = parse_copy_block(backup_file, 'events')
    print(f"   Found {len(events)} events")
    
    print("\n3b. Parsing event_json table...")
    event_json_data = parse_copy_block(backup_file, 'event_json')
    print(f"   Found {len(event_json_data)} event JSON entries")
    
    # Identifier les rooms encryptées
    print("\n4. Identifying encrypted rooms...")
    encrypted_rooms = identify_encrypted_rooms(state_events)
    print(f"   Found {len(encrypted_rooms)} encrypted rooms")
    
    # Identifier les rooms non-encryptées
    all_room_ids = {room['room_id'] for room in rooms}
    non_encrypted_rooms = all_room_ids - encrypted_rooms
    print(f"   Found {len(non_encrypted_rooms)} non-encrypted rooms")
    
    # Extraire les noms des rooms
    print("\n5. Extracting room names...")
    room_names = extract_room_names_from_events(event_json_data, state_events)
    print(f"   Found names for {len(room_names)} rooms")
    
    # Extraire les users
    print("\n6. Extracting users...")
    users = extract_users_from_events(events, event_json_data)
    print(f"   Found {len(users)} unique users")
    
    # Analyser les dépendances
    print("\n7. Analyzing event dependencies...")
    dependencies = analyze_dependencies(events, event_json_data)
    print(f"   Found dependencies for {len(dependencies)} events")
    
    # Préparer les données pour la migration
    migration_data = {
        'old_server': OLD_SERVER,
        'new_server': NEW_SERVER,
        'rooms': [
            {
                'room_id': room['room_id'],
                'name': room_names.get(room['room_id'], None),
                'is_public': room.get('is_public') == 't',
                'creator': room.get('creator'),
                'room_version': room.get('room_version'),
                'is_encrypted': room['room_id'] in encrypted_rooms
            }
            for room in rooms
            if room['room_id'] in non_encrypted_rooms
        ],
        'users': sorted(list(users)),
        'encrypted_rooms': sorted(list(encrypted_rooms)),
        'statistics': {
            'total_rooms': len(rooms),
            'encrypted_rooms': len(encrypted_rooms),
            'non_encrypted_rooms': len(non_encrypted_rooms),
            'rooms_with_names': len(room_names),
            'total_users': len(users),
            'total_events': len(events),
            'total_state_events': len(state_events)
        }
    }
    
    # Sauvegarder les résultats
    output_file = 'migration-analysis.json'
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(migration_data, f, indent=2, ensure_ascii=False)
    
    print(f"\n✓ Analysis complete! Results saved to {output_file}")
    print("\nStatistics:")
    for key, value in migration_data['statistics'].items():
        print(f"  {key}: {value}")
    
    print(f"\n✓ Ready for migration of {len(migration_data['rooms'])} non-encrypted rooms")
    print(f"✓ Ready for migration of {len(migration_data['users'])} users")


if __name__ == '__main__':
    main()

