# Marque Blanche (White Label) Configuration

Ce guide explique comment configurer l'application pour une marque blanche personnalisée.

## Variables de Configuration

Les variables de marque blanche sont définies dans les fichiers d'environnement :

- `src/environments/environment.ts` - Configuration par défaut
- `src/environments/environment.dev.ts` - Configuration de développement
- `src/environments/environment.terresdelaya.ts` - Exemple pour Terres de Laya

### Variables Disponibles

```typescript
export const environment = {
  // ... autres variables ...

  // Configuration de marque blanche
  brandName: "NeoHoods", // Nom de la marque
  brandDisplayName: "portal NeoHoods", // Nom affiché dans l'interface
  brandCopyright: "2025 portal NeoHoods", // Texte de copyright
  brandLogo: "/logo-bright.png", // Chemin vers le logo
};
```

## Impact des Variables

### 1. Footer (`footer.component.html`)

- **Logo** : `environment.brandLogo`
- **Copyright** : `environment.brandCopyright`
- **Powered by** : "NeoHoods" (hardcodé)
- **GitHub URL** : "https://github.com/neohoods" (hardcodé)

### 2. Header (`hub-layout.component.html`)

- **Nom affiché** : `environment.brandDisplayName`

### 3. Titre de la page

- Utilise le service `TitleService` pour définir le titre dynamiquement

## Comment Créer une Nouvelle Marque

### 1. Créer un fichier d'environnement

```bash
# Créer un nouveau fichier d'environnement
cp src/environments/environment.ts src/environments/environment.monclient.ts
```

### 2. Modifier la configuration

```typescript
// src/environments/environment.monclient.ts
export const environment = {
  production: true,
  API_BASE_PATH: "https://portal.monclient.com/api",
  useMockApi: false,
  demoMode: false,
  defaultLocale: "fr",

  // Configuration de marque blanche
  brandName: "Mon Client",
  brandDisplayName: "Portal Mon Client",
  brandCopyright: "2025 Portal Mon Client",
  brandLogo: "/logo-monclient.png",
};
```

### 3. Ajouter la configuration dans angular.json

```json
{
  "configurations": {
    "monclient": {
      "optimization": true,
      "outputHashing": "all",
      "sourceMap": false,
      "extractLicenses": true,
      "fileReplacements": [
        {
          "replace": "src/environments/environment.ts",
          "with": "src/environments/environment.monclient.ts"
        }
      ]
    }
  }
}
```

### 4. Ajouter un script de build dans package.json

```json
{
  "scripts": {
    "build:monclient": "ng build --configuration=monclient"
  }
}
```

### 5. Construire l'application

```bash
npm run build:monclient
```

## Exemples de Configuration

### Terres de Laya

```bash
npm run build:terresdelaya
```

### Configuration par défaut

```bash
npm run build
```

## Assets Personnalisés

Pour utiliser des logos personnalisés :

1. Placez vos logos dans le dossier `public/`
2. Mettez à jour `brandLogo` dans la configuration
3. Assurez-vous que les logos sont optimisés pour le web

## Notes Importantes

- Les variables d'environnement sont remplacées au moment de la compilation
- Pour les changements de marque en runtime, il faudrait implémenter une solution plus complexe
- Les traductions peuvent aussi être personnalisées via les fichiers de traduction
