# Configuration Auth0 - Redirection après vérification d'email

## Problème résolu

L'erreur `400 Bad Request` avec le message `'Additional properties not allowed: returnTo'` était causée par l'utilisation du paramètre `returnTo` dans l'API de création d'utilisateur Auth0, qui n'est pas supporté.

## Solution implémentée

Le paramètre `returnTo` a été supprimé du code. La redirection après vérification d'email doit être configurée dans le Dashboard Auth0.

## Configuration requise dans Auth0 Dashboard

### 1. Accéder aux paramètres de l'application

1. Connectez-vous au [Auth0 Dashboard](https://manage.auth0.com/)
2. Allez dans **Applications** > **Applications**
3. Sélectionnez votre application NeoHoods

### 2. Configurer l'URL de redirection

1. Dans l'onglet **Settings**, allez à la section **Advanced Settings**
2. Cliquez sur l'onglet **URLs**
3. Dans le champ **Allowed Callback URLs**, ajoutez :
   ```
   https://votre-domaine.com/email-confirmation?verified=true&source=auth0
   ```
4. Dans le champ **Allowed Web Origins**, ajoutez :
   ```
   https://votre-domaine.com
   ```

### 3. Configurer l'email de vérification (optionnel)

1. Allez dans **Branding** > **Email Templates**
2. Sélectionnez **Verification Email**
3. Personnalisez le template si nécessaire
4. Dans la section **Settings**, assurez-vous que l'URL de redirection est correcte

### 4. Tester la configuration

1. Créez un nouvel utilisateur via l'API
2. Vérifiez que l'email de vérification est envoyé
3. Cliquez sur le lien dans l'email
4. Vérifiez que l'utilisateur est redirigé vers `/email-confirmation?verified=true&source=auth0`

## Code modifié

### Auth0Service.java

- Suppression du paramètre `returnToUrl` de la méthode `registerUser`
- Suppression de la surcharge de méthode
- Ajout de commentaires explicatifs

### AuthApi.java

- Suppression de la construction de l'URL `returnToUrl`
- Simplification de l'appel à `auth0Service.registerUser`

## Avantages de cette approche

1. **Conformité Auth0** : Utilise l'API Auth0 de manière standard
2. **Configuration centralisée** : L'URL de redirection est configurée dans le Dashboard
3. **Flexibilité** : Peut être modifiée sans redéploiement du code
4. **Sécurité** : Auth0 gère la validation des URLs de redirection

## Notes importantes

- L'URL de redirection doit être configurée dans Auth0 Dashboard
- L'URL doit correspondre exactement à celle utilisée dans le code frontend
- Les URLs de test et de production doivent être configurées séparément
