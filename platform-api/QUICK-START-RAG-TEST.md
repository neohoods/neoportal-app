# 🚀 Test rapide du RAG complémentaire en local

## En 3 étapes

### 1️⃣ Créer le fichier de test

Le fichier `rag-test-local.md` a déjà été créé à la racine de `platform-api/`.

### 2️⃣ Configurer dans application.yml

Ouvrez `src/main/resources/application.yml` et modifiez la ligne 118 :

```yaml
custom-documentation-file: ${MATRIX_ASSISTANT_RAG_CUSTOM_DOC_FILE:file:./rag-test-local.md}
```

**OU** utilisez un chemin absolu (plus fiable) :

```yaml
custom-documentation-file: ${MATRIX_ASSISTANT_RAG_CUSTOM_DOC_FILE:file:/Users/qcastel/Development/GIT/github/neohoods/neoportal-app/platform-api/rag-test-local.md}
```

### 3️⃣ Démarrer et vérifier

```bash
cd neoportal-app/platform-api
mvn spring-boot:run
```

**Vérifiez les logs au démarrage :**
```
INFO  MatrixAssistantRAGService - Loading custom documentation from: file:./rag-test-local.md
INFO  MatrixAssistantRAGService - Loaded custom documentation from file path: file:./rag-test-local.md
INFO  MatrixAssistantRAGService - Indexed document 'Structure des bâtiments Terres de Laya' with X chunks
INFO  MatrixAssistantRAGService - Successfully loaded custom documentation from: file:./rag-test-local.md
```

## 🧪 Tester dans Matrix

Posez ces questions à Alfred dans Matrix :

1. **"Combien y a-t-il de bâtiments ?"**
   → Devrait répondre : "3 bâtiments : A, B et C"

2. **"Quelles sont les heures autorisées pour les travaux ?"**
   → Devrait répondre : "8h à 18h en semaine uniquement"

3. **"Où sont les boîtes aux lettres ?"**
   → Devrait répondre : "au rez-de-chaussée de chaque bâtiment"

## 🔍 Debug

Si ça ne fonctionne pas, vérifiez :

1. **Le fichier existe** : `ls rag-test-local.md`
2. **Les logs au démarrage** : Cherchez "Loading custom documentation"
3. **Le RAG est activé** : `rag.enabled: true` dans application.yml
4. **Les logs de recherche** : Cherchez "Searching RAG context" quand vous posez une question

## 📝 Modifier le fichier de test

Éditez `rag-test-local.md` et redémarrez l'application. Les modifications seront chargées au démarrage.



