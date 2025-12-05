# Solutions pour Forcer l'Appel des Outils MCP

## Problème Identifié

Le bot dit parfois "Je vais vérifier" ou refuse de répondre sans appeler les outils MCP, même quand la question nécessite clairement un appel d'outil (ex: "qui habite au 808", "Il y a combien de bâtiments?").

## Solutions Implémentées

### 1. Détection Automatique et `tool_choice="required"` ✅

**Fichier**: `MatrixAssistantAIService.java`

**Méthode**: `determineToolChoice()`

**Fonctionnement**:

- Analyse le message de l'utilisateur pour détecter les patterns qui nécessitent des outils
- Si détecté, force `tool_choice="required"` au lieu de `"auto"`
- Patterns détectés:
  - Questions sur les résidents: "qui habite", "who lives", numéros d'appartement
  - Questions sur les contacts d'urgence: "ACAF", "urgence", "syndic", "numéro", "adresse"
  - Questions sur les espaces: "espace", "space", "réservation", "disponible"
  - Questions générales: "info", "information", "description", "service"

**Avantage**: Force Mistral à utiliser au moins un outil pour ces questions spécifiques.

### 2. Amélioration du Prompt Système ✅

**Fichiers**:

- `matrix-assistant-system-prompt.txt`
- `matrix-assistant-minimal-prompt.txt`

**Améliorations**:

- Règles CRITIQUES au début du prompt
- Instructions explicites: "NEVER say 'I'll check' - CALL THE TOOL IMMEDIATELY"
- Exemples concrets de questions qui nécessitent des outils
- Interdiction explicite de refuser sans appeler l'outil

### 3. Tests d'Intégration pour Vérifier le Comportement ✅

**Fichier**: `MatrixAssistantAIConversationIntegrationTest.java`

**Tests**:

- `testResidentInfoMustCallTool`: Vérifie que "qui habite au 808" appelle l'outil
- `testConversationFlowWithFollowUp`: Vérifie que "alors?" après "Je vais vérifier" force l'appel
- `testMultipleAsksMustCallTool`: Vérifie que même demandé plusieurs fois, l'outil est appelé
- `testNoVaisVerifierWithoutToolCall`: Détecte si le bot dit "Je vais vérifier" sans appeler

## Solutions Supplémentaires Possibles (Non Implémentées)

### 4. Détection Post-Réponse et Retry

**Idée**: Si le bot répond "Je vais vérifier" sans appeler d'outil, détecter et forcer un rappel avec `tool_choice="required"`.

**Implémentation possible**:

```java
// Dans processMistralResponse()
if (content.contains("vais vérifier") && toolCalls.isEmpty()) {
    return forceToolCall(userMessage, previousMessages, tools, authContext, ragContext);
}
```

**Avantage**: Catch les cas où le bot promet de vérifier mais ne le fait pas.

### 5. Utilisation de `tool_choice="any"` au lieu de `"required"`

**Idée**: `"any"` permet au modèle de choisir n'importe quel outil, ce qui peut être plus flexible que `"required"`.

**Note**: À tester - `"required"` est plus strict et garantit l'appel d'un outil.

### 6. Amélioration des Descriptions d'Outils dans `matrix-mcp-tools.yaml`

**Idée**: Rendre les descriptions encore plus explicites sur quand utiliser chaque outil.

**Exemple actuel**:

```yaml
description: |
  Get resident information for an apartment or floor.
```

**Amélioration possible**:

```yaml
description: |
  MANDATORY: Use this tool for ANY question about residents, apartments, or floors.
  Examples: "qui habite au 808", "who lives in apartment 808", "résidents du 6ème étage".
  NEVER refuse to answer without calling this tool first.
```

### 7. Logging et Monitoring

**Idée**: Logger toutes les réponses sans appel d'outil pour les questions qui en nécessitent.

**Implémentation**: Déjà partiellement fait avec les logs `🤖 BOT FINAL RESPONSE (no tool call)`.

## Recommandations

1. **Tester avec `tool_choice="required"`** pour les questions détectées - c'est la solution la plus directe
2. **Monitorer les logs** pour voir si le problème persiste
3. **Ajuster les patterns de détection** si nécessaire
4. **Considérer l'implémentation de la solution #4** (détection post-réponse) si le problème persiste

## Tests

Exécuter les tests d'intégration:

```bash
mvn test -Dtest=MatrixAssistantAIConversationIntegrationTest -DMISTRAL_AI_TOKEN=your_token
```

Ces tests vérifient que:

- Le bot appelle les outils pour les questions appropriées
- Le bot ne dit pas "Je vais vérifier" sans appeler l'outil
- Le bot ne refuse pas sans avoir appelé l'outil
