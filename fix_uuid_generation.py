#!/usr/bin/env python3
"""
Script pour corriger les UUIDs invalides dans le fichier data.sql
"""

import re
import uuid

def generate_valid_uuid():
    """Génère un UUID v4 valide"""
    return str(uuid.uuid4())

def fix_uuids_in_file(file_path):
    """Corrige les UUIDs invalides dans le fichier SQL"""
    
    # Lire le fichier
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Pattern pour trouver les UUIDs invalides (contiennent des lettres g-z)
    invalid_uuid_pattern = r"'([a-f0-9-]*[g-z][a-f0-9-]*)'"
    
    def replace_invalid_uuid(match):
        invalid_uuid = match.group(1)
        # Vérifier si c'est vraiment invalide (contient des lettres g-z)
        if any(c in invalid_uuid for c in 'ghijklmnopqrstuvwxyz'):
            return f"'{generate_valid_uuid()}'"
        return match.group(0)
    
    # Remplacer tous les UUIDs invalides
    fixed_content = re.sub(invalid_uuid_pattern, replace_invalid_uuid, content)
    
    # Écrire le fichier corrigé
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(fixed_content)
    
    print(f"UUIDs corrigés dans {file_path}")

if __name__ == "__main__":
    fix_uuids_in_file('/Users/qcastel/Development/GIT/github/neohoods/neoportal-app/db/postgres/data.sql')
    print("Correction terminée !")
