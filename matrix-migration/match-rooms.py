#!/usr/bin/env python3
"""
Script pour matcher les rooms par nom et générer le mapping old_room_id -> new_room_id.

Réutilise les rooms existantes si elles ont le même nom, sinon génère de nouveaux IDs.
"""

import json
import sys
import re
import secrets
import string
from typing import Dict, Optional
import argparse


def normalize_room_name(name: str) -> str:
    """
    Normalise un nom de room pour le matching (case-insensitive, trim spaces).
    """
    if not name:
        return ""
    return name.strip().lower()


def generate_room_id(server: str) -> str:
    """
    Génère un nouveau room ID au format Matrix: !random:server
    """
    # Générer un identifiant aléatoire de 18 caractères (base64url)
    alphabet = string.ascii_letters + string.digits + '-_'
    random_part = ''.join(secrets.choice(alphabet) for _ in range(18))
    return f"!{random_part}:{server}"


def match_rooms(
    migration_analysis: Dict,
    existing_rooms: Dict,
    space_id: str,
    new_server: str
) -> Dict[str, Dict]:
    """
    Match les rooms de l'ancienne instance avec les rooms existantes.
    
    Retourne un dict:
    {
        old_room_id: {
            'new_room_id': str,
            'room_name': str,
            'reused': bool,
            'reason': str
        }
    }
    """
    room_mapping = {}
    
    # Créer un index des rooms existantes par nom normalisé
    existing_rooms_by_name = {}
    for name, room_id in existing_rooms.items():
        normalized = normalize_room_name(name)
        if normalized:
            # Si plusieurs rooms ont le même nom, garder la première (celle déjà dans le space)
            if normalized not in existing_rooms_by_name:
                existing_rooms_by_name[normalized] = (name, room_id)
    
    # Parcourir les rooms à migrer
    for room in migration_analysis['rooms']:
        old_room_id = room['room_id']
        room_name = room.get('name')
        
        if not room_name:
            # Room sans nom, générer un nouveau ID
            new_room_id = generate_room_id(new_server)
            room_mapping[old_room_id] = {
                'new_room_id': new_room_id,
                'room_name': None,
                'reused': False,
                'reason': 'No room name in old instance'
            }
            continue
        
        # Normaliser le nom pour le matching
        normalized_name = normalize_room_name(room_name)
        
        # Chercher une room existante avec le même nom
        if normalized_name in existing_rooms_by_name:
            existing_name, existing_room_id = existing_rooms_by_name[normalized_name]
            room_mapping[old_room_id] = {
                'new_room_id': existing_room_id,
                'room_name': room_name,
                'reused': True,
                'reason': f'Matched with existing room "{existing_name}"'
            }
        else:
            # Aucune room existante avec ce nom, générer un nouveau ID
            new_room_id = generate_room_id(new_server)
            room_mapping[old_room_id] = {
                'new_room_id': new_room_id,
                'room_name': room_name,
                'reused': False,
                'reason': 'No matching room found, creating new'
            }
    
    return room_mapping


def match_users(
    migration_analysis: Dict,
    new_server: str
) -> Dict[str, str]:
    """
    Génère le mapping old_user_id -> new_user_id.
    Remplace simplement le server name.
    """
    user_mapping = {}
    old_server = migration_analysis['old_server']
    
    for old_user_id in migration_analysis['users']:
        # Remplacer le server name
        new_user_id = old_user_id.replace(f":{old_server}", f":{new_server}")
        user_mapping[old_user_id] = new_user_id
    
    return user_mapping


def main():
    parser = argparse.ArgumentParser(
        description='Match rooms by name and generate migration mappings'
    )
    parser.add_argument(
        '--analysis',
        default='migration-analysis.json',
        help='Migration analysis JSON file (default: migration-analysis.json)'
    )
    parser.add_argument(
        '--existing-rooms',
        default='existing-rooms.json',
        help='Existing rooms JSON file (default: existing-rooms.json)'
    )
    parser.add_argument(
        '--space-id',
        required=True,
        help='Target space ID (e.g., !rgOFmqDljPgniKgNEx:chat.neohoods.com)'
    )
    parser.add_argument(
        '--new-server',
        default='chat.neohoods.com',
        help='New server name (default: chat.neohoods.com)'
    )
    parser.add_argument(
        '--output',
        default='room-mapping.json',
        help='Output JSON file (default: room-mapping.json)'
    )
    
    args = parser.parse_args()
    
    # Charger les données
    print("Loading migration analysis...")
    with open(args.analysis, 'r', encoding='utf-8') as f:
        migration_analysis = json.load(f)
    
    print("Loading existing rooms...")
    with open(args.existing_rooms, 'r', encoding='utf-8') as f:
        existing_rooms_data = json.load(f)
        existing_rooms = existing_rooms_data.get('rooms', {})
    
    print(f"\nMatching rooms...")
    print(f"  Rooms to migrate: {len(migration_analysis['rooms'])}")
    print(f"  Existing rooms in space: {len(existing_rooms)}")
    print(f"  Target space: {args.space_id}")
    
    # Matcher les rooms
    room_mapping = match_rooms(
        migration_analysis,
        existing_rooms,
        args.space_id,
        args.new_server
    )
    
    # Matcher les users
    user_mapping = match_users(migration_analysis, args.new_server)
    
    # Compter les statistiques
    reused_count = sum(1 for m in room_mapping.values() if m['reused'])
    new_count = len(room_mapping) - reused_count
    
    # Préparer les données de sortie
    output_data = {
        'space_id': args.space_id,
        'new_server': args.new_server,
        'old_server': migration_analysis['old_server'],
        'room_mapping': room_mapping,
        'user_mapping': user_mapping,
        'statistics': {
            'total_rooms': len(room_mapping),
            'reused_rooms': reused_count,
            'new_rooms': new_count,
            'total_users': len(user_mapping)
        }
    }
    
    # Sauvegarder les résultats
    with open(args.output, 'w', encoding='utf-8') as f:
        json.dump(output_data, f, indent=2, ensure_ascii=False)
    
    print(f"\n✓ Matching complete!")
    print(f"  Total rooms: {len(room_mapping)}")
    print(f"  Reused rooms: {reused_count}")
    print(f"  New rooms: {new_count}")
    print(f"  Total users: {len(user_mapping)}")
    print(f"✓ Results saved to {args.output}")
    
    # Afficher quelques exemples
    if room_mapping:
        print("\nSample room mappings:")
        for i, (old_id, mapping) in enumerate(list(room_mapping.items())[:5]):
            status = "REUSED" if mapping['reused'] else "NEW"
            name = mapping['room_name'] or "(no name)"
            print(f"  {status}: {name}")
            print(f"    {old_id} -> {mapping['new_room_id']}")


if __name__ == '__main__':
    main()

