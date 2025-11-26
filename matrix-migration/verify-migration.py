#!/usr/bin/env python3
"""
Script de vérification pour valider chaque étape de la migration.

Permet de vérifier :
- L'analyse du backup
- Les rooms existantes récupérées
- Le mapping des rooms
- La génération du SQL
"""

import json
import sys
import os
import argparse
from typing import Dict, List, Optional, Tuple


def verify_analysis(analysis_file: str) -> Tuple[bool, List[str]]:
    """Vérifie le fichier d'analyse."""
    errors = []
    warnings = []
    
    if not os.path.exists(analysis_file):
        return False, [f"❌ Fichier d'analyse introuvable: {analysis_file}"]
    
    try:
        with open(analysis_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        # Vérifications
        if 'rooms' not in data:
            errors.append("❌ Clé 'rooms' manquante dans l'analyse")
        elif len(data['rooms']) == 0:
            warnings.append("⚠️  Aucune room à migrer trouvée")
        
        if 'users' not in data:
            errors.append("❌ Clé 'users' manquante dans l'analyse")
        elif len(data['users']) == 0:
            warnings.append("⚠️  Aucun user trouvé")
        
        if 'statistics' not in data:
            errors.append("❌ Clé 'statistics' manquante dans l'analyse")
        else:
            stats = data['statistics']
            if stats.get('non_encrypted_rooms', 0) == 0:
                warnings.append("⚠️  Aucune room non-encryptée trouvée")
            if stats.get('rooms_with_names', 0) == 0:
                warnings.append("⚠️  Aucune room avec nom trouvée")
        
        # Vérifier que les rooms ont des noms
        rooms_with_names = sum(1 for r in data.get('rooms', []) if r.get('name'))
        rooms_without_names = len(data.get('rooms', [])) - rooms_with_names
        if rooms_without_names > 0:
            warnings.append(f"⚠️  {rooms_without_names} rooms sans nom (seront créées avec nouveaux IDs)")
        
        if errors:
            return False, errors + warnings
        else:
            return True, warnings
    
    except json.JSONDecodeError as e:
        return False, [f"❌ Erreur de parsing JSON: {e}"]
    except Exception as e:
        return False, [f"❌ Erreur lors de la vérification: {e}"]


def verify_existing_rooms(existing_rooms_file: str) -> Tuple[bool, List[str]]:
    """Vérifie le fichier des rooms existantes."""
    errors = []
    warnings = []
    
    if not os.path.exists(existing_rooms_file):
        return False, [f"❌ Fichier des rooms existantes introuvable: {existing_rooms_file}"]
    
    try:
        with open(existing_rooms_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        if 'rooms' not in data:
            errors.append("❌ Clé 'rooms' manquante")
        elif len(data.get('rooms', {})) == 0:
            warnings.append("⚠️  Aucune room existante trouvée dans le space (toutes les rooms seront créées)")
        
        if 'space_id' not in data:
            errors.append("❌ Clé 'space_id' manquante")
        
        if errors:
            return False, errors + warnings
        else:
            return True, warnings
    
    except json.JSONDecodeError as e:
        return False, [f"❌ Erreur de parsing JSON: {e}"]
    except Exception as e:
        return False, [f"❌ Erreur lors de la vérification: {e}"]


def verify_mapping(mapping_file: str) -> Tuple[bool, List[str]]:
    """Vérifie le fichier de mapping."""
    errors = []
    warnings = []
    
    if not os.path.exists(mapping_file):
        return False, [f"❌ Fichier de mapping introuvable: {mapping_file}"]
    
    try:
        with open(mapping_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        if 'room_mapping' not in data:
            errors.append("❌ Clé 'room_mapping' manquante")
        elif len(data.get('room_mapping', {})) == 0:
            errors.append("❌ Aucun mapping de room trouvé")
        
        if 'user_mapping' not in data:
            errors.append("❌ Clé 'user_mapping' manquante")
        elif len(data.get('user_mapping', {})) == 0:
            warnings.append("⚠️  Aucun mapping d'user trouvé")
        
        # Vérifier la cohérence des mappings
        room_mapping = data.get('room_mapping', {})
        reused_count = sum(1 for m in room_mapping.values() if m.get('reused', False))
        new_count = len(room_mapping) - reused_count
        
        if reused_count > 0:
            warnings.append(f"ℹ️  {reused_count} rooms seront réutilisées, {new_count} seront créées")
        
        # Vérifier que tous les new_room_id sont valides
        for old_id, mapping in room_mapping.items():
            new_id = mapping.get('new_room_id')
            if not new_id:
                errors.append(f"❌ Room {old_id} n'a pas de new_room_id")
            elif not new_id.startswith('!'):
                errors.append(f"❌ new_room_id invalide pour {old_id}: {new_id}")
            elif ':chat.neohoods.com' not in new_id:
                warnings.append(f"⚠️  new_room_id pour {old_id} n'utilise pas chat.neohoods.com: {new_id}")
        
        if errors:
            return False, errors + warnings
        else:
            return True, warnings
    
    except json.JSONDecodeError as e:
        return False, [f"❌ Erreur de parsing JSON: {e}"]
    except Exception as e:
        return False, [f"❌ Erreur lors de la vérification: {e}"]


def verify_sql(sql_file: str) -> Tuple[bool, List[str]]:
    """Vérifie le fichier SQL généré."""
    errors = []
    warnings = []
    
    if not os.path.exists(sql_file):
        return False, [f"❌ Fichier SQL introuvable: {sql_file}"]
    
    try:
        with open(sql_file, 'r', encoding='utf-8') as f:
            sql_content = f.read()
        
        # Vérifications de base
        if 'BEGIN;' not in sql_content:
            warnings.append("⚠️  Pas de BEGIN; trouvé (pas de transaction)")
        
        if 'COMMIT;' not in sql_content:
            warnings.append("⚠️  Pas de COMMIT; trouvé (transaction non fermée)")
        
        if 'INSERT INTO public.rooms' not in sql_content:
            warnings.append("⚠️  Pas d'INSERT pour les rooms trouvé")
        
        if 'INSERT INTO public.state_events' not in sql_content:
            warnings.append("⚠️  Pas d'INSERT pour les state_events trouvé")
        
        if 'm.space.parent' not in sql_content:
            errors.append("❌ Pas d'event m.space.parent trouvé dans le SQL")
        
        # Compter les INSERT
        insert_count = sql_content.count('INSERT INTO')
        if insert_count == 0:
            errors.append("❌ Aucun INSERT trouvé dans le SQL")
        else:
            warnings.append(f"ℹ️  {insert_count} instructions INSERT trouvées")
        
        if errors:
            return False, errors + warnings
        else:
            return True, warnings
    
    except Exception as e:
        return False, [f"❌ Erreur lors de la vérification: {e}"]


def main():
    parser = argparse.ArgumentParser(
        description='Vérifier les fichiers de migration'
    )
    parser.add_argument(
        '--analysis',
        default='migration-analysis.json',
        help='Fichier d\'analyse (default: migration-analysis.json)'
    )
    parser.add_argument(
        '--existing-rooms',
        default='existing-rooms.json',
        help='Fichier des rooms existantes (default: existing-rooms.json)'
    )
    parser.add_argument(
        '--mapping',
        default='room-mapping.json',
        help='Fichier de mapping (default: room-mapping.json)'
    )
    parser.add_argument(
        '--sql',
        default='migrate-matrix.sql',
        help='Fichier SQL (default: migrate-matrix.sql)'
    )
    parser.add_argument(
        '--all',
        action='store_true',
        help='Vérifier tous les fichiers'
    )
    
    args = parser.parse_args()
    
    print("🔍 Vérification des fichiers de migration...\n")
    
    all_ok = True
    all_messages = []
    
    # Vérifier l'analyse
    if args.all or args.analysis:
        print("1. Vérification de l'analyse...")
        ok, messages = verify_analysis(args.analysis)
        if ok:
            print("   ✅ Analyse valide")
        else:
            print("   ❌ Analyse invalide")
            all_ok = False
        for msg in messages:
            print(f"   {msg}")
        print()
        all_messages.extend(messages)
    
    # Vérifier les rooms existantes
    if args.all or args.existing_rooms:
        print("2. Vérification des rooms existantes...")
        ok, messages = verify_existing_rooms(args.existing_rooms)
        if ok:
            print("   ✅ Rooms existantes valides")
        else:
            print("   ❌ Rooms existantes invalides")
            all_ok = False
        for msg in messages:
            print(f"   {msg}")
        print()
        all_messages.extend(messages)
    
    # Vérifier le mapping
    if args.all or args.mapping:
        print("3. Vérification du mapping...")
        ok, messages = verify_mapping(args.mapping)
        if ok:
            print("   ✅ Mapping valide")
        else:
            print("   ❌ Mapping invalide")
            all_ok = False
        for msg in messages:
            print(f"   {msg}")
        print()
        all_messages.extend(messages)
    
    # Vérifier le SQL
    if args.all or args.sql:
        print("4. Vérification du SQL...")
        ok, messages = verify_sql(args.sql)
        if ok:
            print("   ✅ SQL valide")
        else:
            print("   ❌ SQL invalide")
            all_ok = False
        for msg in messages:
            print(f"   {msg}")
        print()
        all_messages.extend(messages)
    
    # Résumé
    print("=" * 60)
    if all_ok:
        print("✅ Toutes les vérifications sont passées !")
        sys.exit(0)
    else:
        print("❌ Certaines vérifications ont échoué")
        error_count = sum(1 for msg in all_messages if msg.startswith('❌'))
        warning_count = sum(1 for msg in all_messages if msg.startswith('⚠️'))
        print(f"   {error_count} erreur(s), {warning_count} avertissement(s)")
        sys.exit(1)


if __name__ == '__main__':
    main()

