# Migration de SendGrid vers MailerSend

## Problème résolu

SendGrid a atteint sa limite de crédits avec l'erreur : `"Maximum credits exceeded"`. Migration vers MailerSend pour une solution plus économique et fiable.

## Changements effectués

### 1. Dépendances Maven

- **Supprimé** : `sendgrid-java` dependency
- **Utilisé** : `RestTemplate` (déjà disponible dans Spring Boot)

### 2. Configuration

- **Fichier** : `application.yml`
- **Ancien** : `sendgrid.api-key`, `sendgrid.from.*`
- **Nouveau** : `mailersend.api-key`, `mailersend.from.*`

### 3. Service MailService

- **Remplacement complet** de l'implémentation SendGrid
- **Nouvelle API** : Utilise l'API REST MailerSend directement
- **Endpoint** : `https://api.mailersend.com/v1/email`
- **Authentification** : Bearer token

## Configuration requise

### Variables d'environnement

```bash
# Remplacez SENDGRID_API_KEY par MAILERSEND_API_KEY
export MAILERSEND_API_KEY="your_mailersend_api_key_here"
```

### Configuration MailerSend

1. **Créer un compte** sur [MailerSend](https://www.mailersend.com/)
2. **Obtenir l'API key** depuis le dashboard
3. **Configurer le domaine** d'envoi
4. **Vérifier les DNS** pour l'authentification

## Avantages de MailerSend

### Économique

- ✅ **Gratuit** : 3,000 emails/mois
- ✅ **Prix compétitifs** : $0.0004 par email au-delà
- ✅ **Pas de frais cachés**

### Fiabilité

- ✅ **Délivrabilité élevée** : 99%+ de taux de livraison
- ✅ **Infrastructure robuste** : Serveurs redondants
- ✅ **Monitoring** : Analytics détaillés

### Fonctionnalités

- ✅ **Templates** : Support des templates HTML
- ✅ **Variables** : Substitution de variables
- ✅ **Tracking** : Ouvertures et clics
- ✅ **Webhooks** : Notifications en temps réel

## Structure de l'API MailerSend

### Requête POST `/v1/email`

```json
{
  "from": {
    "email": "dev@mail.portal.neohoods.com",
    "name": "portal NeoHoods dev environment"
  },
  "to": [
    {
      "email": "user@example.com",
      "name": ""
    }
  ],
  "subject": "Email Subject",
  "html": "<html>...</html>"
}
```

### Headers requis

```
Content-Type: application/json
Authorization: Bearer YOUR_API_KEY
```

## Code modifié

### MailService.java

- **Méthode `sendMail()`** : Utilise RestTemplate au lieu de SendGrid SDK
- **Méthode `sendTemplatedEmail()`** : Inchangée (utilise les templates Thymeleaf)
- **Gestion d'erreurs** : Adaptée pour l'API MailerSend
- **Logging** : Messages mis à jour pour MailerSend

### Configuration

- **Properties** : `mailersend.*` au lieu de `sendgrid.*`
- **Variables** : `MAILERSEND_API_KEY` au lieu de `SENDGRID_API_KEY`

## Test de la migration

### 1. Vérifier la configuration

```bash
# Vérifier que la variable d'environnement est définie
echo $MAILERSEND_API_KEY
```

### 2. Tester l'envoi d'email

1. Démarrer l'application
2. Créer un nouvel utilisateur
3. Vérifier que l'email de vérification est envoyé
4. Vérifier les logs pour confirmer l'envoi via MailerSend

### 3. Vérifier les logs

```
INFO  - Sending email via MailerSend to: user@example.com, subject: Email Subject
INFO  - Email sent successfully via MailerSend to: user@example.com, status: 202
```

## Rollback (si nécessaire)

### 1. Restaurer SendGrid

```yaml
# application.yml
sendgrid:
  api-key: ${SENDGRID_API_KEY}
  from:
    email: dev@mail.portal.neohoods.com
    name: portal NeoHoods dev environment
```

### 2. Restaurer le code

- Remplacer `MailService.java` par la version SendGrid
- Ajouter la dépendance `sendgrid-java` dans `pom.xml`

## Support

- **Documentation MailerSend** : https://developers.mailersend.com/
- **API Reference** : https://developers.mailersend.com/api/email
- **Status Page** : https://status.mailersend.com/
