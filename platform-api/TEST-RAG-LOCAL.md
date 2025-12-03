# Guide de test du RAG complémentaire en local

## Étape 1 : Créer un fichier de documentation RAG de test

Créez un fichier de test avec des informations spécifiques à votre copropriété.

**Exemple : `rag-test-local.md`** (à créer dans le répertoire du projet)

```markdown
---
Structure des bâtiments Terres de Laya

La copropriété Terres de Laya est composée de 3 bâtiments : A, B et C.

Les appartements suivent le format : [Bâtiment][Étage][Numéro]
- Exemple : A701 = Bâtiment A, 7ème étage, appartement 01
- Exemple : C302 = Bâtiment C, 3ème étage, appartement 02
- Exemple : B601 = Bâtiment B, 6ème étage, appartement 01

Chaque bâtiment a plusieurs étages numérotés de 1 à 9.
Chaque étage contient plusieurs appartements numérotés de 01 à 99.

Pour rechercher les résidents d'un étage spécifique d'un bâtiment, utilisez le format : "6ème étage du bâtiment C" ou "étage 6 du bâtiment C".
---

---
Règles de la copropriété

Les animaux de compagnie sont autorisés mais doivent être déclarés au syndic.
Les travaux sont autorisés de 8h à 18h en semaine uniquement (pas le week-end).
Les espaces communs doivent être libérés après utilisation.
Le stationnement est réservé aux résidents avec badge.
---

---
Informations pratiques

L'adresse de la copropriété est : 1 Rue des Copropriétaires, 75010 Paris.
Le syndic est ACAF, numéro d'urgence : +33 4 76 12 95 85.
Les boîtes aux lettres sont situées au rez-de-chaussée de chaque bâtiment.
Les ascenseurs sont disponibles dans tous les bâtiments.
---
```

## Étape 2 : Configurer le fichier dans application.yml

Ouvrez `src/main/resources/application.yml` et modifiez la ligne :

```yaml
neohoods:
  portal:
    matrix:
      assistant:
        rag:
          enabled: ${MATRIX_ASSISTANT_RAG_ENABLED:true}
          custom-documentation-file: ${MATRIX_ASSISTANT_RAG_CUSTOM_DOC_FILE:file:./rag-test-local.md}
```

**Options de configuration :**

1. **Chemin absolu** (recommandé pour les tests) :
   ```yaml
   custom-documentation-file: file:/Users/qcastel/Development/GIT/github/neohoods/neoportal-app/platform-api/rag-test-local.md
   ```

2. **Chemin relatif depuis le répertoire de travail** :
   ```yaml
   custom-documentation-file: file:./rag-test-local.md
   ```

3. **Fichier dans les resources** :
   ```yaml
   custom-documentation-file: classpath:rag-test-local.md
   ```
   (Il faudra alors copier le fichier dans `src/main/resources/`)

4. **Via variable d'environnement** :
   ```bash
   export MATRIX_ASSISTANT_RAG_CUSTOM_DOC_FILE=file:./rag-test-local.md
   ```

## Étape 3 : Vérifier que le RAG est activé

Dans `application.yml`, assurez-vous que :

```yaml
neohoods:
  portal:
    matrix:
      assistant:
        rag:
          enabled: true  # ou ${MATRIX_ASSISTANT_RAG_ENABLED:true}
```

## Étape 4 : Démarrer l'application et vérifier les logs

1. **Démarrez l'application** :
   ```bash
   cd neoportal-app/platform-api
   mvn spring-boot:run
   ```

2. **Vérifiez les logs au démarrage** :
   Vous devriez voir des logs comme :
   ```
   INFO  MatrixAssistantRAGService - Loading initial documentation for RAG
   INFO  MatrixAssistantRAGService - Indexed document 'Element Mobile Installation' with X chunks
   INFO  MatrixAssistantRAGService - Loading custom documentation from: file:./rag-test-local.md
   INFO  MatrixAssistantRAGService - Loaded custom documentation from file path: file:./rag-test-local.md
   INFO  MatrixAssistantRAGService - Indexed document 'Structure des bâtiments Terres de Laya' with X chunks
   INFO  MatrixAssistantRAGService - Indexed document 'Règles de la copropriété' with X chunks
   INFO  MatrixAssistantRAGService - Indexed document 'Informations pratiques' with X chunks
   INFO  MatrixAssistantRAGService - Successfully loaded custom documentation from: file:./rag-test-local.md
   INFO  MatrixAssistantRAGService - Loaded X document chunks for RAG
   ```

3. **Si vous voyez une erreur** :
   ```
   WARN  MatrixAssistantRAGService - Custom documentation file not found: file:./rag-test-local.md
   ```
   Vérifiez que le chemin est correct et que le fichier existe.

## Étape 5 : Tester avec l'assistant Matrix

Une fois l'application démarrée, testez avec des questions qui devraient utiliser le RAG :

### Questions de test :

1. **Test structure des bâtiments** :
   - "Combien y a-t-il de bâtiments ?"
   - "Comment sont numérotés les appartements ?"
   - "Quel est le format d'un appartement ?"

2. **Test règles de copropriété** :
   - "Quelles sont les heures autorisées pour les travaux ?"
   - "Les animaux sont-ils autorisés ?"
   - "Où sont les boîtes aux lettres ?"

3. **Test informations pratiques** :
   - "Quelle est l'adresse de la copropriété ?"
   - "Où sont les ascenseurs ?"

### Vérifier que le RAG fonctionne :

Dans les logs, vous devriez voir :
```
DEBUG MatrixAssistantRAGService - Searching RAG context for query: combien y a-t-il de bâtiments
```

Si le RAG trouve du contexte pertinent, il sera inclus dans le prompt système envoyé à Mistral.

## Étape 6 : Debug avancé

Pour voir exactement ce qui est indexé, vous pouvez ajouter un log temporaire dans `MatrixAssistantRAGService.java` :

```java
log.info("Document chunks: {}", documentChunks.stream()
    .map(chunk -> chunk.getTitle() + ": " + chunk.getContent().substring(0, Math.min(50, chunk.getContent().length())))
    .collect(Collectors.joining(", ")));
```

## Problèmes courants

### Le fichier n'est pas chargé
- ✅ Vérifiez que le chemin est correct (absolu ou relatif)
- ✅ Vérifiez que le fichier existe
- ✅ Vérifiez les permissions de lecture
- ✅ Regardez les logs au démarrage

### Le RAG ne trouve pas le contexte
- ✅ Vérifiez que `rag.enabled=true`
- ✅ Vérifiez que les mots-clés dans votre question correspondent au contenu du fichier
- ✅ Le RAG utilise une recherche simple par mots-clés (pas encore de recherche vectorielle)

### Le contexte n'est pas utilisé par le LLM
- ✅ Le RAG ajoute le contexte au prompt système
- ✅ Le LLM peut choisir d'ignorer le contexte s'il pense avoir une meilleure réponse
- ✅ Testez avec des questions très spécifiques au contenu de votre fichier

## Exemple complet de test

1. Créez `rag-test-local.md` avec le contenu ci-dessus
2. Configurez dans `application.yml` : `custom-documentation-file: file:./rag-test-local.md`
3. Démarrez l'application
4. Dans Matrix, demandez : "Combien y a-t-il de bâtiments dans la copropriété ?"
5. L'assistant devrait répondre : "La copropriété Terres de Laya est composée de 3 bâtiments : A, B et C."



