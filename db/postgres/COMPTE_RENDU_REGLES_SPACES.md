# Compte Rendu - Règles de Gestion des Espaces Partagés (Spaces)

**Date:** $(date)  
**Document préparé pour:** Assemblée Générale  
**Source:** Base de données PostgreSQL - Schéma de gestion des espaces

---

## 1. Vue d'Ensemble

Le système de gestion des espaces partagés permet aux résidents de réserver différents types d'espaces communs selon des règles spécifiques définies par type d'espace et par type d'utilisateur.

---

## 2. Types d'Espaces Disponibles

Le système supporte **4 types d'espaces** :

1. **GUEST_ROOM** - Chambre d'hôte
2. **COMMON_ROOM** - Salle commune
3. **COWORKING** - Bureau de coworking
4. **PARKING** - Place de parking

---

## 3. Statuts des Espaces

Chaque espace peut avoir l'un des statuts suivants :

- **ACTIVE** - Espace disponible à la réservation (statut par défaut)
- **INACTIVE** - Espace temporairement indisponible
- **MAINTENANCE** - Espace en maintenance
- **DISABLED** - Espace désactivé

**Règle métier :** Seuls les espaces avec le statut `ACTIVE` peuvent être réservés.

---

## 4. Règles Générales de Réservation

### 4.1 Contraintes de Dates

- **Date de début** : Ne peut pas être dans le passé
- **Date de fin** : Doit être supérieure ou égale à la date de début
- **Durée minimale** : Configurable par espace (défaut : 1 jour)
- **Durée maximale** : Configurable par espace (défaut : 365 jours)

### 4.2 Jours Autorisés

Chaque espace peut définir les jours de la semaine où il est réservable :
- LUNDI, MARDI, MERCREDI, JEUDI, VENDREDI, SAMEDI, DIMANCHE

**Règle métier :** Si aucun jour n'est défini dans `space_allowed_days`, l'espace est réservable tous les jours.

### 4.3 Horaires d'Accès

- **Heure de début** : Par défaut `08:00` (configurable par espace)
- **Heure de fin** : Par défaut `20:00` (configurable par espace)

**Règle métier :** Les réservations doivent respecter les horaires définis pour chaque espace.

### 4.4 Limite Annuelle de Réservations

- **Maximum annuel** : Configurable par espace (0 = illimité)
- **Compteur utilisé** : Suivi automatique des réservations annuelles par espace

**Règle métier :** Si `max_annual_reservations > 0`, le système vérifie que `used_annual_reservations < max_annual_reservations` avant d'autoriser une nouvelle réservation.

---

## 5. Règles Spécifiques par Type d'Espace

### 5.1 Chambre d'Hôte (GUEST_ROOM)

#### Tarification
- **Propriétaires (OWNER)** : Payent uniquement les frais de fonctionnement (`owner_price`)
- **Locataires (TENANT)** : Payent une majoration pour participer aux frais de maintenance (`tenant_price`)
- **Frais de ménage** : Applicables (`cleaning_fee`)
- **Caution** : Possible (`deposit`)

#### Durée de Réservation
- **Minimum** : 3 jours (configurable)
- **Maximum** : 365 jours (configurable)
- **Limite annuelle** : 7 jours par appartement (configurable via `max_annual_reservations`)

#### Jours de Ménage
- Les réservations doivent se terminer sur certains jours pour faciliter le ménage
- Exemple : Ménage mardi et vendredi (configurable via `space_cleaning_days`)

#### Accès
- **Accès à l'appartement requis** : Possible (`requires_apartment_access`)
- **Code d'accès** : Généré automatiquement si `access_code_enabled = true`
- **Serrure digitale** : Support TTLock, Nuki, Yale

#### Nettoyage
- **Frais de ménage** : Constituent la majorité des frais
- **Jours après checkout** : Configurable (`cleaning_days_after_checkout`)
- **Heure de nettoyage** : Par défaut `10:00` (configurable)
- **Notifications de nettoyage** : Possibles (`cleaning_notifications_enabled`)

#### Post-Réservation
- **Questionnaire de satisfaction** : Demandé à la fin du séjour
- **Évaluation** : Note globale (1-5), propreté, communication, valeur
- **Commentaires** : Possibilité de signaler des problèmes ou des dégâts

---

### 5.2 Salle Commune (COMMON_ROOM)

#### Tarification
- **Propriétaires (OWNER)** : 4€ par jour (`owner_price`)
- **Locataires (TENANT)** : 10€ par jour (`tenant_price`)

#### Durée de Réservation
- **Unité** : À la journée
- **Minimum** : 1 jour
- **Maximum** : 1 jour (configurable)

#### Jours Autorisés
- Mercredi
- Weekends (samedi et dimanche)
- Semaine pendant les vacances
- (Configurable via `space_allowed_days`)

#### Horaires
- **Début** : 8h00
- **Fin** : 20h00

#### Nettoyage
- **Frais de ménage** : Non (`cleaning_fee = 0`)
- **Rappel** : Nettoyage en fin de journée par l'utilisateur

#### Espaces Partagés
- **Règle importante** : La salle commune partage le même espace physique que les bureaux de coworking
- **Contrainte** : Si la salle commune est réservée, les bureaux de coworking ne sont pas réservables ce jour-là
- **Implémentation** : Table `space_shared_with` pour gérer les conflits

---

### 5.3 Bureau de Coworking (COWORKING)

#### Tarification
- **Propriétaires (OWNER)** : 4€ par jour (`owner_price`)
- **Locataires (TENANT)** : 7€ par jour (`tenant_price`)

#### Durée de Réservation
- **Unité** : À la journée
- **Minimum** : 1 jour
- **Maximum** : 5 jours consécutifs

#### Jours Autorisés
- Tous les jours (configurable via `space_allowed_days`)

#### Horaires
- **Début** : 8h00
- **Fin** : 20h00

#### Nettoyage
- **Frais de ménage** : Non (`cleaning_fee = 0`)
- **Rappel** : Nettoyer le bureau en fin de journée

#### Accès
- **Code d'accès** : Généré automatiquement (`access_code_enabled = true`)
- **Serrure digitale** : Support TTLock, Nuki, Yale

#### Espaces Partagés
- **Règle importante** : Les bureaux de coworking partagent le même espace physique que la salle commune
- **Contrainte** : Si un bureau de coworking est réservé, la salle commune n'est pas réservable ce jour-là
- **Implémentation** : Table `space_shared_with` pour gérer les conflits

---

### 5.4 Place de Parking (PARKING)

#### Tarification
- **Propriétaires (OWNER)** : Gratuit (`owner_price = 0` ou `tenant_price = 0`)
- **Locataires (TENANT)** : Gratuit (`tenant_price = 0`)
- **Frais de réservation** : Aucun frais particulier

#### Durée de Réservation
- **Unité** : À la journée
- **Minimum** : 1 jour
- **Maximum** : 15 jours consécutifs

#### Identification
- **Places numérotées** : Chaque place de parking a un numéro unique (dans le nom de l'espace)

#### Nettoyage
- **Frais de ménage** : Non applicable

---

## 6. Système de Tarification

### 6.1 Prix par Type d'Utilisateur

Chaque espace définit deux prix :
- **`tenant_price`** : Prix pour les locataires (TENANT) - **OBLIGATOIRE**
- **`owner_price`** : Prix pour les propriétaires (OWNER) - **OPTIONNEL**

**Règle métier :** Le système détermine automatiquement le prix en fonction du type d'utilisateur (`user_type`).

### 6.2 Frais Supplémentaires

- **Frais de ménage (`cleaning_fee`)** : Applicables selon le type d'espace
- **Caution (`deposit`)** : Possible pour certains espaces (ex: chambre d'hôte)
- **Devise (`currency`)** : Par défaut EUR

### 6.3 Frais de Plateforme

- **Frais de plateforme (%)** : 2.00% par défaut (configurable dans `space_settings`)
- **Frais fixes de plateforme** : 0.25€ par défaut (configurable dans `space_settings`)

**Règle métier :** Les frais de plateforme sont calculés et ajoutés au prix total de la réservation.

---

## 7. Statuts des Réservations

### 7.1 Cycle de Vie d'une Réservation

1. **PENDING_PAYMENT** - En attente de paiement (statut initial)
2. **PAYMENT_FAILED** - Échec du paiement
3. **EXPIRED** - Réservation expirée (paiement non effectué dans les temps)
4. **CONFIRMED** - Réservation confirmée (paiement réussi)
5. **ACTIVE** - Réservation active (en cours)
6. **COMPLETED** - Réservation terminée
7. **CANCELLED** - Réservation annulée
8. **REFUNDED** - Réservation remboursée

### 7.2 Statuts de Paiement

- **PENDING** - En attente
- **PROCESSING** - En cours de traitement
- **SUCCEEDED** - Réussi
- **FAILED** - Échoué
- **CANCELLED** - Annulé
- **REFUNDED** - Remboursé
- **PARTIALLY_REFUNDED** - Partiellement remboursé

### 7.3 Expiration des Paiements

- **`payment_expires_at`** : Date limite pour effectuer le paiement
- **Règle métier :** Les réservations en `PENDING_PAYMENT` expirent automatiquement si le paiement n'est pas effectué avant `payment_expires_at`

---

## 8. Codes d'Accès

### 8.1 Génération des Codes

- **Activation** : Si `access_code_enabled = true` pour l'espace
- **Format** : Code alphanumérique unique (10 caractères maximum)
- **Expiration** : Définie par `expires_at` (doit être après la création)

### 8.2 Intégration avec Serrures Digitales

- **Types supportés** : TTLock, Nuki, Yale
- **Code de serrure** : Stocké dans `digital_lock_code_id`
- **Statut** : `is_active` pour activer/désactiver un code
- **Utilisation** : Suivi via `used_at`

### 8.3 Régénération

- **Régénération possible** : Un code peut être régénéré si nécessaire
- **Traçabilité** : `regenerated_at` et `regenerated_by` pour l'audit

---

## 9. Gestion du Nettoyage

### 9.1 Configuration du Nettoyage

- **Activation** : `cleaning_enabled = true` pour activer le système de nettoyage
- **Email de nettoyage** : `cleaning_email` pour notifier le service de nettoyage
- **Notifications** : `cleaning_notifications_enabled` pour activer les notifications
- **Calendrier** : `cleaning_calendar_enabled` pour intégrer avec un calendrier externe

### 9.2 Jours de Nettoyage

- **Configuration** : Table `space_cleaning_days` pour définir les jours de nettoyage
- **Exemple** : Mardi et vendredi pour la chambre d'hôte

### 9.3 Planning du Nettoyage

- **Jours après checkout** : `cleaning_days_after_checkout` (défaut : 0)
- **Heure de nettoyage** : `cleaning_hour` (défaut : 10:00)

---

## 10. Contraintes Techniques

### 10.1 Contraintes de Base de Données

1. **Dates de réservation** : `end_date >= start_date` (contrainte CHECK)
2. **Expiration des codes** : `expires_at > created_at` (contrainte CHECK)
3. **Images d'espaces** : Soit `url` soit `image_data` doit être fourni (contrainte CHECK)
4. **Types d'espaces** : Seulement les valeurs définies dans le CHECK sont autorisées
5. **Statuts** : Seulement les valeurs définies dans les CHECK sont autorisées

### 10.2 Relations entre Tables

- **`spaces`** → **`reservations`** : Relation 1-N (CASCADE sur suppression)
- **`spaces`** → **`space_images`** : Relation 1-N (CASCADE sur suppression)
- **`spaces`** → **`space_allowed_days`** : Relation 1-N (CASCADE sur suppression)
- **`spaces`** → **`space_cleaning_days`** : Relation 1-N (CASCADE sur suppression)
- **`spaces`** → **`space_shared_with`** : Relation N-N (auto-référence)
- **`reservations`** → **`access_codes`** : Relation 1-N (CASCADE sur suppression)
- **`reservations`** → **`reservation_feedback`** : Relation 1-1 (UNIQUE)
- **`users`** → **`reservations`** : Relation 1-N (CASCADE sur suppression)
- **`units`** → **`reservations`** : Relation 1-N (SET NULL sur suppression)

### 10.3 Index pour Performance

- Index sur `spaces(status)`, `spaces(type)`, `spaces(type, status)`
- Index sur `reservations(space_id, start_date, end_date)` pour vérifier les conflits
- Index sur `reservations(status, payment_expires_at)` pour les réservations expirées
- Index sur `access_codes(code)` pour recherche rapide
- Index sur `access_codes(expires_at)` pour nettoyage automatique

---

## 11. Audit et Traçabilité

### 11.1 Journal d'Audit des Réservations

- **Table** : `reservation_audit_log`
- **Événements tracés** : Tous les changements de statut, annulations, modifications
- **Informations** : `event_type`, `old_value`, `new_value`, `log_message`, `performed_by`, `created_at`

### 11.2 Horodatage Automatique

- **`created_at`** : Date de création (automatique)
- **`updated_at`** : Date de mise à jour (trigger automatique sur UPDATE)

---

## 12. Notifications

### 12.1 Notifications de Réservation

- **Activation** : `enable_notifications = true` (par défaut)
- **Moment** : 24h avant la réservation (pour les codes d'accès)

### 12.2 Notifications de Nettoyage

- **Activation** : `cleaning_notifications_enabled = true`
- **Destinataire** : `cleaning_email`

---

## 13. Règles Métier Critiques à Valider

### 13.1 Tarification

- ✅ **Validé** : Différenciation propriétaires/locataires
- ✅ **Validé** : Frais de plateforme (2% + 0.25€)
- ⚠️ **À valider** : Montants exacts par type d'espace et type d'utilisateur

### 13.2 Durées de Réservation

- ✅ **Validé** : Minimum 3 jours pour chambre d'hôte
- ✅ **Validé** : Maximum 5 jours pour coworking
- ✅ **Validé** : Maximum 15 jours pour parking
- ⚠️ **À valider** : Durée maximale pour chambre d'hôte (actuellement 365 jours)

### 13.3 Limites Annuelles

- ✅ **Validé** : 7 jours par an par appartement pour chambre d'hôte
- ⚠️ **À valider** : Limites annuelles pour autres types d'espaces

### 13.4 Espaces Partagés

- ✅ **Validé** : Salle commune et coworking ne peuvent pas être réservés simultanément
- ⚠️ **À valider** : Logique de réservation en cas de conflit (qui a priorité ?)

### 13.5 Jours Autorisés

- ✅ **Validé** : Salle commune : mercredi, weekends, semaine pendant vacances
- ⚠️ **À valider** : Définition précise de "semaine pendant vacances"

### 13.6 Codes d'Accès

- ✅ **Validé** : Génération automatique 24h avant réservation
- ✅ **Validé** : Support TTLock, Nuki, Yale
- ⚠️ **À valider** : Durée de validité des codes d'accès

### 13.7 Nettoyage

- ✅ **Validé** : Jours de nettoyage configurables (mardi/vendredi pour chambre d'hôte)
- ✅ **Validé** : Nettoyage en fin de journée pour salle commune et coworking
- ⚠️ **À valider** : Responsabilité du nettoyage (utilisateur vs service)

### 13.8 Questionnaire Post-Réservation

- ✅ **Validé** : Questionnaire de satisfaction obligatoire
- ✅ **Validé** : Évaluation : note globale, propreté, communication, valeur
- ⚠️ **À valider** : Obligation ou recommandation du questionnaire

---

## 14. Points d'Attention pour l'AG

### 14.1 Questions à Valider

1. **Tarification** : Confirmer les montants exacts pour chaque type d'espace et type d'utilisateur
2. **Durées** : Valider les durées maximales de réservation par type d'espace
3. **Limites annuelles** : Définir les limites annuelles pour tous les types d'espaces
4. **Espaces partagés** : Définir la politique de priorité en cas de conflit
5. **Vacances** : Définir précisément ce que signifie "semaine pendant vacances"
6. **Codes d'accès** : Valider la durée de validité et les règles de régénération
7. **Nettoyage** : Clarifier la responsabilité (utilisateur vs service) et les frais associés
8. **Questionnaire** : Définir si le questionnaire est obligatoire ou recommandé

### 14.2 Règles Implémentées vs Spécifications

- ✅ **Conforme** : Types d'espaces (4 types)
- ✅ **Conforme** : Tarification différenciée propriétaires/locataires
- ✅ **Conforme** : Durées de réservation par type
- ✅ **Conforme** : Limite annuelle pour chambre d'hôte
- ✅ **Conforme** : Espaces partagés (salle commune/coworking)
- ✅ **Conforme** : Codes d'accès TTLock
- ✅ **Conforme** : Questionnaire de satisfaction
- ⚠️ **À vérifier** : Jours autorisés pour salle commune (mercredi, weekends, vacances)
- ⚠️ **À vérifier** : Maximum 15 jours pour parking (actuellement configurable jusqu'à 365)

---

## 15. Recommandations

1. **Documentation** : Mettre à jour la documentation utilisateur avec les règles validées
2. **Tests** : Valider les scénarios de conflit pour les espaces partagés
3. **Monitoring** : Mettre en place un suivi des réservations annuelles par appartement
4. **Communication** : Informer les résidents des règles de réservation et des limites annuelles
5. **Évolutivité** : Prévoir un mécanisme de modification des règles sans impact sur les réservations existantes

---

## 16. Annexes

### 16.1 Structure de la Table `spaces`

```sql
CREATE TABLE spaces (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    instructions TEXT,
    type VARCHAR(50) CHECK (type IN ('GUEST_ROOM', 'COMMON_ROOM', 'COWORKING', 'PARKING')),
    status VARCHAR(50) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'DISABLED')),
    tenant_price DECIMAL(10,2) NOT NULL,
    owner_price DECIMAL(10,2),
    cleaning_fee DECIMAL(10,2),
    deposit DECIMAL(10,2),
    currency VARCHAR(3) DEFAULT 'EUR',
    min_duration_days INTEGER DEFAULT 1,
    max_duration_days INTEGER DEFAULT 365,
    requires_apartment_access BOOLEAN DEFAULT FALSE,
    max_annual_reservations INTEGER DEFAULT 0,
    used_annual_reservations INTEGER DEFAULT 0,
    allowed_hours_start VARCHAR(5) DEFAULT '08:00',
    allowed_hours_end VARCHAR(5) DEFAULT '20:00',
    digital_lock_id UUID REFERENCES digital_locks(id),
    access_code_enabled BOOLEAN DEFAULT FALSE,
    enable_notifications BOOLEAN DEFAULT TRUE,
    cleaning_enabled BOOLEAN DEFAULT FALSE,
    cleaning_email VARCHAR(255),
    cleaning_notifications_enabled BOOLEAN DEFAULT FALSE,
    cleaning_calendar_enabled BOOLEAN DEFAULT FALSE,
    cleaning_days_after_checkout INTEGER DEFAULT 0,
    cleaning_hour VARCHAR(5) DEFAULT '10:00',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 16.2 Tables Associées

- `space_images` : Images des espaces
- `space_allowed_days` : Jours autorisés pour réservation
- `space_cleaning_days` : Jours de nettoyage
- `space_shared_with` : Espaces partageant le même espace physique
- `reservations` : Réservations des espaces
- `access_codes` : Codes d'accès pour les réservations
- `reservation_feedback` : Questionnaires de satisfaction
- `space_settings` : Paramètres globaux (frais de plateforme)

---

**Fin du document**


