# Documentation RAG personnalisée

## Fichier : `rag-custom-documentation.md`

Ce fichier contient la documentation complémentaire qui sera chargée dans le système RAG de l'assistant Matrix.

## Comment personnaliser

1. **Éditez le fichier** `rag-custom-documentation.md` dans ce répertoire
2. **Ajoutez vos sections** en les séparant par `---`
3. **Redémarrez l'application** pour que les modifications soient prises en compte

## Format

Chaque section doit être séparée par `---` sur une ligne seule.

La première ligne de chaque section est le titre (optionnel mais recommandé).

Exemple :
```
---
Titre de la section

Contenu de la section ici.
Vous pouvez mettre plusieurs paragraphes.
---

---
Autre section

Autre contenu...
---
```

## Exemples de sections utiles

- Structure des bâtiments
- Règles de la copropriété
- Informations pratiques (adresses, contacts)
- Services disponibles
- Horaires et réglementations
- Procédures spécifiques

## Test

Après modification, redémarrez l'application et vérifiez les logs :

```
INFO  MatrixAssistantRAGService - Loading custom documentation from: classpath:rag-custom-documentation.md
INFO  MatrixAssistantRAGService - Indexed document 'Titre de la section' with X chunks
```

Puis testez dans Matrix avec des questions liées au contenu de votre fichier.



