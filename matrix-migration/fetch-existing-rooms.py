#!/usr/bin/env python3
"""
Script pour interroger la nouvelle instance Matrix et lister les rooms existantes dans le space.

Utilise l'API Matrix pour récupérer la liste des rooms dans le space de destination.
"""

import json
import sys
import requests
from typing import Dict, Optional
import argparse


def get_rooms_in_space(
    homeserver_url: str,
    access_token: str,
    space_id: str
) -> Dict[str, str]:
    """
    Récupère toutes les rooms dans un space via l'API Matrix.
    Retourne un dict room_name -> room_id
    """
    rooms_map = {}
    
    # URL de base de l'API
    base_url = homeserver_url.rstrip('/')
    if not base_url.startswith('http'):
        base_url = f'https://{base_url}'
    
    headers = {
        'Authorization': f'Bearer {access_token}',
        'Content-Type': 'application/json'
    }
    
    # 1. Récupérer toutes les rooms jointes
    print(f"Fetching joined rooms...")
    joined_rooms_url = f"{base_url}/_matrix/client/v3/joined_rooms"
    
    try:
        response = requests.get(joined_rooms_url, headers=headers, timeout=30)
        response.raise_for_status()
        joined_rooms_data = response.json()
        joined_room_ids = joined_rooms_data.get('joined_rooms', [])
        print(f"  Found {len(joined_room_ids)} joined rooms")
    except requests.exceptions.RequestException as e:
        print(f"  Error fetching joined rooms: {e}")
        return rooms_map
    
    # 2. Pour chaque room, vérifier si elle appartient au space et récupérer son nom
    print(f"Checking rooms for space {space_id}...")
    for room_id in joined_room_ids:
        # Ignorer le space lui-même
        if room_id == space_id:
            continue
        
        try:
            # Récupérer les state events de la room
            state_url = f"{base_url}/_matrix/client/v3/rooms/{room_id}/state"
            response = requests.get(state_url, headers=headers, timeout=30)
            response.raise_for_status()
            state_events = response.json()
            
            # Vérifier si la room appartient au space
            belongs_to_space = False
            room_name = None
            
            for state_event in state_events:
                event_type = state_event.get('type')
                state_key = state_event.get('state_key', '')
                
                # Vérifier m.space.parent
                if event_type == 'm.space.parent' and state_key == space_id:
                    belongs_to_space = True
                
                # Extraire m.room.name
                if event_type == 'm.room.name' and state_key == '':
                    content = state_event.get('content', {})
                    room_name = content.get('name')
            
            # Si la room appartient au space et a un nom, l'ajouter
            if belongs_to_space and room_name:
                rooms_map[room_name] = room_id
                print(f"  Found room: '{room_name}' -> {room_id}")
        
        except requests.exceptions.RequestException as e:
            # Ignorer les erreurs pour les rooms individuelles
            continue
    
    return rooms_map


def main():
    parser = argparse.ArgumentParser(
        description='Fetch existing rooms in a Matrix space'
    )
    parser.add_argument(
        '--homeserver',
        required=True,
        help='Matrix homeserver URL (e.g., https://matrix.neohoods.com or matrix.neohoods.com)'
    )
    parser.add_argument(
        '--access-token',
        required=True,
        help='Matrix access token (Bearer token)'
    )
    parser.add_argument(
        '--space-id',
        required=True,
        help='Space ID (e.g., !rgOFmqDljPgniKgNEx:chat.neohoods.com)'
    )
    parser.add_argument(
        '--output',
        default='existing-rooms.json',
        help='Output JSON file (default: existing-rooms.json)'
    )
    
    args = parser.parse_args()
    
    print("Fetching existing rooms in space...")
    print(f"  Homeserver: {args.homeserver}")
    print(f"  Space ID: {args.space_id}")
    
    rooms_map = get_rooms_in_space(
        args.homeserver,
        args.access_token,
        args.space_id
    )
    
    # Sauvegarder les résultats
    output_data = {
        'space_id': args.space_id,
        'homeserver': args.homeserver,
        'rooms': rooms_map,
        'count': len(rooms_map)
    }
    
    with open(args.output, 'w', encoding='utf-8') as f:
        json.dump(output_data, f, indent=2, ensure_ascii=False)
    
    print(f"\n✓ Found {len(rooms_map)} existing rooms in space")
    print(f"✓ Results saved to {args.output}")
    
    if rooms_map:
        print("\nExisting rooms:")
        for name, room_id in sorted(rooms_map.items()):
            print(f"  - {name}: {room_id}")


if __name__ == '__main__':
    main()

