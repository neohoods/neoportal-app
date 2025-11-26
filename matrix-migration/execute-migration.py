#!/usr/bin/env python3
"""
Script pour exécuter la migration Matrix via l'API.
Utilise l'API Matrix pour créer les rooms et migrer les données.
"""

import json
import sys
import os
import requests
import time
from typing import Dict, List, Optional

# Configuration
HOMESERVER_URL = os.getenv("MATRIX_HOMESERVER_URL", "https://matrix.neohoods.com")
ACCESS_TOKEN = os.getenv("MATRIX_ACCESS_TOKEN")
TARGET_SPACE_ID = os.getenv("MATRIX_TARGET_SPACE_ID", "!YenniyNVsUoBCLHtZS:chat.neohoods.com")

def get_headers():
    """Retourne les headers pour l'API Matrix."""
    if not ACCESS_TOKEN:
        raise ValueError("MATRIX_ACCESS_TOKEN non configuré")
    return {
        "Authorization": f"Bearer {ACCESS_TOKEN}",
        "Content-Type": "application/json"
    }

def check_space_exists(space_id: str) -> bool:
    """Vérifie si le space existe."""
    url = f"{HOMESERVER_URL}/_matrix/client/v3/rooms/{space_id}/state/m.space.name"
    headers = get_headers()
    try:
        response = requests.get(url, headers=headers)
        if response.status_code == 200:
            print(f"✅ Space {space_id} existe")
            return True
        elif response.status_code == 404:
            print(f"❌ Space {space_id} n'existe pas")
            return False
        else:
            print(f"⚠️  Erreur lors de la vérification du space: {response.status_code}")
            return False
    except Exception as e:
        print(f"❌ Erreur lors de la vérification du space: {e}")
        return False

def create_room_in_space(room_name: str, description: str, space_id: str) -> Optional[str]:
    """Crée une room dans un space via l'API Matrix."""
    url = f"{HOMESERVER_URL}/_matrix/client/v3/createRoom"
    headers = get_headers()
    
    # Initial state pour lier la room au space
    initial_state = [
        {
            "type": "m.space.parent",
            "state_key": space_id,
            "content": {
                "canonical": True
            }
        }
    ]
    
    if description:
        initial_state.append({
            "type": "m.room.topic",
            "content": {
                "topic": description
            }
        })
    
    payload = {
        "name": room_name,
        "preset": "public_chat",
        "room_version": "10",
        "initial_state": initial_state
    }
    
    try:
        response = requests.post(url, headers=headers, json=payload)
        if response.status_code == 200:
            room_id = response.json().get("room_id")
            print(f"  ✅ Room '{room_name}' créée: {room_id}")
            return room_id
        else:
            print(f"  ❌ Erreur lors de la création de la room '{room_name}': {response.status_code} - {response.text}")
            return None
    except Exception as e:
        print(f"  ❌ Erreur lors de la création de la room '{room_name}': {e}")
        return None

def main():
    print("🚀 Exécution de la migration Matrix")
    print("=" * 50)
    
    # Vérifier le space
    if not check_space_exists(TARGET_SPACE_ID):
        print(f"\n❌ Le space {TARGET_SPACE_ID} n'existe pas.")
        print("   Veuillez créer le space ou vérifier l'ID.")
        sys.exit(1)
    
    # Charger le mapping
    print("\n📋 Chargement du mapping...")
    try:
        with open("room-mapping.json", "r", encoding="utf-8") as f:
            mapping_data = json.load(f)
        room_mapping = mapping_data["room_mapping"]
        print(f"✅ {len(room_mapping)} rooms à migrer")
    except FileNotFoundError:
        print("❌ Fichier room-mapping.json non trouvé")
        print("   Exécutez d'abord: ./migrate-all.sh")
        sys.exit(1)
    
    # Charger l'analyse pour obtenir les noms
    print("\n📊 Chargement de l'analyse...")
    try:
        with open("migration-analysis.json", "r", encoding="utf-8") as f:
            analysis_data = json.load(f)
        rooms_data = {r["room_id"]: r for r in analysis_data["rooms"]}
        print(f"✅ {len(rooms_data)} rooms analysées")
    except FileNotFoundError:
        print("❌ Fichier migration-analysis.json non trouvé")
        sys.exit(1)
    
    # Créer les nouvelles rooms
    print("\n🏗️  Création des rooms...")
    created = 0
    reused = 0
    errors = 0
    
    for old_room_id, mapping in room_mapping.items():
        new_room_id = mapping["new_room_id"]
        room_name = mapping.get("room_name")
        is_reused = mapping.get("reused", False)
        
        if is_reused:
            print(f"  ♻️  Room '{room_name}' réutilisée: {new_room_id}")
            reused += 1
        else:
            # Créer la room
            if not room_name:
                room_name = f"Room {old_room_id[:8]}"
            
            room_info = rooms_data.get(old_room_id, {})
            description = f"Migrée depuis {old_room_id}"
            
            created_room_id = create_room_in_space(room_name, description, TARGET_SPACE_ID)
            if created_room_id:
                created += 1
                # Mettre à jour le mapping avec le vrai room_id créé
                mapping["new_room_id"] = created_room_id
                mapping["created_via_api"] = True
            else:
                errors += 1
            
            # Délai pour éviter le rate limiting
            time.sleep(0.5)
    
    print(f"\n✅ Migration terminée:")
    print(f"   - Rooms créées: {created}")
    print(f"   - Rooms réutilisées: {reused}")
    print(f"   - Erreurs: {errors}")
    
    # Sauvegarder le mapping mis à jour
    with open("room-mapping-executed.json", "w", encoding="utf-8") as f:
        json.dump(mapping_data, f, ensure_ascii=False, indent=2)
    print(f"\n💾 Mapping sauvegardé dans room-mapping-executed.json")
    
    print("\n⚠️  NOTE: La migration des messages nécessite l'exécution du SQL")
    print("   sur la base de données Matrix. Utilisez migrate-matrix.sql")
    print("   après avoir créé les rooms via l'API.")

if __name__ == "__main__":
    main()

