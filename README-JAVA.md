# Configuration Java 21 pour ce projet

Ce projet nécessite Java 21 pour compiler et exécuter les tests.

## Configuration automatique

### Option 1 : Utiliser direnv (recommandé)

Si vous avez `direnv` installé, le fichier `.envrc` sera automatiquement chargé :

```bash
# Installer direnv (si pas déjà installé)
brew install direnv

# Autoriser direnv pour ce projet
direnv allow
```

### Option 2 : Utiliser le script setup-java.sh

```bash
# Sourcer le script dans votre shell actuel
source setup-java.sh

# Ou l'exécuter directement
./setup-java.sh
```

### Option 3 : Configurer manuellement

1. Copier `.env.example` vers `.env` :
```bash
cp .env.example .env
```

2. Modifier `.env` avec votre chemin Java 21 :
```bash
JAVA_HOME=/votre/chemin/vers/java-21
```

3. Charger les variables d'environnement :
```bash
export $(grep -v '^#' .env | xargs)
export PATH=$JAVA_HOME/bin:$PATH
```

## Vérification

Vérifiez que Java 21 est bien configuré :
```bash
java -version
# Devrait afficher : openjdk version "21.0.5" ...
```

## Compilation et tests

Une fois Java 21 configuré, vous pouvez compiler et tester :
```bash
mvn clean compile
mvn test
```

