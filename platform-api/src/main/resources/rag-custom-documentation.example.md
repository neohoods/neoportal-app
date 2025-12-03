# Documentation RAG personnalisée pour la copropriété

Ce fichier peut être utilisé pour enrichir le contexte RAG de l'assistant Matrix avec des informations spécifiques à votre copropriété.

## Format

Le fichier peut contenir plusieurs sections séparées par `---`. Chaque section peut avoir un titre (première ligne) suivi du contenu.

## Exemple : Structure des bâtiments

---
Structure des bâtiments

La copropriété est composée de 3 bâtiments : A, B et C.

Les appartements suivent le format : [Bâtiment][Étage][Numéro]
- Exemple : A701 = Bâtiment A, 7ème étage, appartement 01
- Exemple : C302 = Bâtiment C, 3ème étage, appartement 02
- Exemple : B601 = Bâtiment B, 6ème étage, appartement 01

Chaque bâtiment a plusieurs étages numérotés de 1 à 9.
Chaque étage contient plusieurs appartements numérotés de 01 à 99.

Pour rechercher les résidents d'un étage spécifique d'un bâtiment, utilisez le format : "6ème étage du bâtiment C" ou "étage 6 du bâtiment C".
---

## Exemple : Règles de la copropriété

---
Règles de la copropriété

Les animaux de compagnie sont autorisés mais doivent être déclarés.
Les travaux sont autorisés de 8h à 18h en semaine uniquement.
Les espaces communs doivent être libérés après utilisation.
---

## Utilisation

Pour utiliser ce fichier, configurez la variable d'environnement :
```
MATRIX_ASSISTANT_RAG_CUSTOM_DOC_FILE=/path/to/rag-custom-documentation.md
```

Ou dans application.yml :
```yaml
neohoods:
  portal:
    matrix:
      assistant:
        rag:
          custom-documentation-file: /path/to/rag-custom-documentation.md
```

Le fichier peut être :
- Un chemin absolu : `/etc/neohoods/rag-documentation.md`
- Un fichier classpath : `classpath:rag-custom-documentation.md`
- Un fichier local : `file:./rag-custom-documentation.md`



