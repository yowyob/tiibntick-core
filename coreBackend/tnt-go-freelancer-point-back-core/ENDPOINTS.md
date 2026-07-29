# Documentation des endpoints — tnt-go-freelancer-point-back-core

Service réactif Spring WebFlux, architecture hexagonale.
Toutes les réponses sont en `application/json` sauf mention contraire (SSE, multipart).

---

## Sommaire

1. [Authentification & Mots de passe](#1-authentification--mots-de-passe)
2. [Utilisateurs](#2-utilisateurs)
3. [Clients](#3-clients)
4. [Livreurs (Freelancers)](#4-livreurs-freelancers)
5. [Annonces](#5-annonces)
6. [Besoins de livraison](#6-besoins-de-livraison)
7. [Livraisons](#7-livraisons)
8. [Abonnements livreurs](#8-abonnements-livreurs)
9. [Abonnements points relais](#9-abonnements-points-relais)
10. [Dépôts relais](#10-dépôts-relais)
11. [Adresses](#11-adresses)
12. [Tarification](#12-tarification)
13. [Évaluations](#13-évaluations)
14. [Contacts](#14-contacts)
15. [Profils GOFP — synchronisation inter-services](#15-profils-gofp--synchronisation-inter-services)
16. [Upload de fichiers](#16-upload-de-fichiers)
17. [Notifications temps réel (SSE)](#17-notifications-temps-réel-sse)
18. [Administration](#18-administration)

---

## 1. Authentification & Mots de passe

### `POST /api/auth/register`
Crée un nouveau compte utilisateur.

**Corps de la requête**
```json
{
  "lastName": "Dupont",
  "firstName": "Marie",
  "password": "motdepasse"
}
```

**Réponse `201 Created`** → [`AuthResponseDTO`](#authresponsedto)

---

### `POST /api/auth/login`
Authentifie un utilisateur.

**Corps de la requête**
```json
{
  "email": "marie@example.com",
  "password": "motdepasse"
}
```

**Réponse `200 OK`** → [`AuthResponseDTO`](#authresponsedto)

---

### `POST /api/auth/refresh`
Renouvelle les tokens à partir d'un refresh token valide.

**Corps de la requête**
```json
{ "refreshToken": "eyJ..." }
```

**Réponse `200 OK`**
```json
{ "accessToken": "eyJ...", "refreshToken": "eyJ..." }
```

---

### `POST /api/auth/logout`
Révoque le token courant. Nécessite `Authorization: Bearer <token>`.

**Réponse `200 OK`** — corps vide.

---

### `GET /api/auth/me`
Retourne le profil de l'utilisateur connecté (JWT requis).

**Réponse `200 OK`** → [`AuthResponseDTO`](#authresponsedto)

---

### `POST /api/auth/setup-password`
Finalise la configuration du mot de passe via un token signé (envoyé par email).

**Corps de la requête**
```json
{ "token": "abc123...", "newPassword": "nouveauMotDePasse" }
```

**Réponse `200 OK`** — corps vide.

---

### `POST /api/auth/request-password-reset`
Demande un email de réinitialisation de mot de passe.

**Query param** : `email=marie@example.com`

**Réponse `202 Accepted`** — corps vide.

---

#### AuthResponseDTO
| Champ | Type | Description |
|-------|------|-------------|
| `token` | `String` | JWT access token |
| `id` | `UUID` | ID de l'utilisateur |
| `lastName` / `firstName` | `String` | Nom et prénom |
| `email` / `phone` | `String` | Coordonnées |
| `userType` | `String` | `ADMIN`, `CLIENT`, `LIVREUR` |
| `role` | `String` | Rôle de sécurité |
| `isActive` | `Boolean` | Compte actif |
| `clientId` | `UUID` | ID client si applicable |
| `freelancerId` | `UUID` | ID livreur si applicable |
| `loyaltyStatus` | `String` | Niveau de fidélité client |
| `rating` / `totalDeliveries` | `Double` / `Integer` | Stats livreur |


---

## 2. Utilisateurs

### `POST /api/users/register`
Inscription rapide (nom + prénom + mot de passe uniquement).

**Corps de la requête**
```json
{ "lastName": "Dupont", "firstName": "Marie", "password": "motdepasse" }
```

**Réponse `201 Created`**
```json
{ "id": "uuid", "lastName": "Dupont", "firstName": "Marie", "createdAt": "2026-07-27T..." }
```

---

### `GET /api/users/{id}`
Récupère un utilisateur par son UUID.

**Réponse `200 OK`**
```json
{ "id": "uuid", "lastName": "Dupont", "firstName": "Marie", "createdAt": "2026-07-27T..." }
```

---

### `GET /api/users`
Liste tous les utilisateurs.

**Réponse `200 OK`** → tableau de `UserResponseDTO`

---

### `DELETE /api/users/{id}`
Supprime un utilisateur.

**Réponse `204 No Content`**

---

## 3. Clients

### `POST /api/clients`
Crée un nouveau profil client.

**Corps de la requête**
```json
{
  "lastName": "Dupont", "firstName": "Marie",
  "phone": "+237600000000", "email": "marie@example.com",
  "password": "motdepasse", "nationalId": "123456789",
  "photoCard": "url_ou_base64",
  "loyaltyStatus": "BRONZE",
  "criminalRecord": "url_casier",
  "profilePhoto": "url_photo"
}
```

**Réponse `201 Created`** → [`ClientResponseDTO`](#clientresponsedto)

---

### `GET /api/clients` / `GET /api/clients/{id}`
Liste tous les clients ou récupère un client par UUID.

**Réponse `200 OK`** → [`ClientResponseDTO`](#clientresponsedto)

---

### `PUT /api/clients/{id}`
Met à jour un profil client (mêmes champs que la création, tous optionnels).

**Réponse `200 OK`** → [`ClientResponseDTO`](#clientresponsedto)

---

### `DELETE /api/clients/{id}` → `204`

---

### `GET /api/clients/check-email?email=`
**Réponse `200 OK`** : `true` si l'email existe déjà, `false` sinon.

### `GET /api/clients/check-national-id?nationalId=`
**Réponse `200 OK`** : `true` / `false`

---

### `PATCH /api/clients/{id}/profile-photo`
**Corps de la requête**
```json
{ "profilePhoto": "data:image/jpeg;base64,..." }
```
**Réponse `200 OK`** → [`ClientResponseDTO`](#clientresponsedto)

---

### `DELETE /api/clients/{id}/profile-photo` → `204`

---

#### ClientResponseDTO
| Champ | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | ID du client |
| `personId` | `UUID` | ID de la personne associée |
| `lastName` / `firstName` | `String` | Nom et prénom |
| `phone` / `email` | `String` | Coordonnées |
| `nationalId` | `String` | Numéro national d'identité |
| `photoCard` / `profilePhoto` | `String` | URLs des photos |
| `rating` | `Double` | Note moyenne |
| `totalDeliveries` | `Integer` | Nombre de livraisons passées |
| `loyaltyStatus` | `String` | `BRONZE`, `SILVER`, `GOLD`, etc. |
| `status` | `String` | `ACTIVE`, `SUSPENDED`, `REVOKED` |


---

## 4. Livreurs (Freelancers)

### `POST /api/freelancers/register`
Inscription d'un livreur. Requête `multipart/form-data`.

| Part | Type | Obligatoire | Description |
|------|------|-------------|-------------|
| `data` | JSON (texte) | ✅ | Champs de `FreelancerRegistrationRequest` |
| `photoCard` | fichier | ❌ | Photo d'identité |
| `cniRecto` / `cniVerso` | fichier | ❌ | Recto / verso CNI |
| `nuiPhoto` | fichier | ❌ | Photo du NIU |
| `frontPhoto` / `backPhoto` | fichier | ❌ | Photos avant / arrière du véhicule |
| `storefrontPhoto` | fichier | ❌ | Photo de la devanture (si point relais) |

**Champs du JSON `data`**

| Champ | Obligatoire | Description |
|-------|-------------|-------------|
| `lastName` / `firstName` | ✅ | Identité |
| `phone` / `email` | ✅ | Coordonnées |
| `password` | ✅ | Mot de passe |
| `nationalId` | ❌ | Numéro CNI |
| `nui` | ❌ | Numéro d'Identification Unique |
| `plateNumber` | ❌ | Immatriculation du véhicule |
| `logisticsType` | ❌ | Type de véhicule (`BIKE`, `MOTORBIKE`, `CAR`, `VAN`, `TRUCK`…) |
| `logisticsClass` | ❌ | Classe logistique |
| `color` | ❌ | Couleur du véhicule |
| `length` / `width` / `height` / `unit` | ❌ | Dimensions de coffre |
| `commercialName` / `commercialRegister` | ❌ | Infos entreprise |
| `siret` | ❌ | Numéro SIRET |
| `street` / `city` / `district` / `country` | ❌ | Adresse |
| `openingHours` | ❌ | Horaires d'ouverture (tableau) |

**Réponse `201 Created`**
```json
{ "freelancerId": "uuid", "status": "PENDING" }
```

---

### `GET /api/freelancers/{id}`
Récupère le profil complet d'un livreur.

**Réponse `200 OK`** — `FreelancerDetailsResponse`

| Champ | Description |
|-------|-------------|
| `id`, `firstName`, `lastName`, `email`, `phone` | Identité |
| `status` | Statut du compte |
| `commercialName` | Nom commercial |
| `nuiNumber` / `nuiPhoto` | Identifiant fiscal |
| `nationalId` / `photoCard` / `cniRecto` / `cniVerso` | Documents d'identité |
| `idCardVerified` | Vérification manuelle CNI |
| `vehicleType` / `vehicleRegNumber` / `vehicleColor` | Véhicule |
| `vehicleFrontPhoto` / `vehicleBackPhoto` | Photos véhicule |
| `trunkLength` / `trunkWidth` / `trunkHeight` / `trunkDimensionUnit` | Coffre |
| `street` / `city` | Adresse |
| `createdAt` / `updatedAt` | Métadonnées |

---

### `PUT /api/freelancers/{id}`
Met à jour le profil. Tous les champs sont optionnels.

**Corps de la requête** : sous-ensemble de `FreelancerUpdateRequest`
(prénom, nom, email, téléphone, mot de passe, commercialName, véhicule, adresse…)

**Réponse `200 OK`** — corps vide.

---

### `DELETE /api/freelancers/{id}` → `204`

---

### `GET /api/freelancers/{id}/subscription`
État complet de l'abonnement — voir [section 8](#8-abonnements-livreurs).

---

### `PATCH /api/freelancers/{id}/location`
Met à jour la position GPS du livreur en temps réel (JWT requis).

**Corps de la requête**
```json
{
  "latitude": 3.867,
  "longitude": 11.517,
  "speedKmh": 45.0,
  "bearing": 180.0,
  "accuracy": 5.0,
  "missionId": "uuid-mission",
  "freelancerOrgId": "uuid-org"
}
```

`latitude` et `longitude` sont obligatoires. Les autres champs sont optionnels (défaut `0.0` / `null`).

**Réponse `200 OK`** — corps vide.


---

## 5. Annonces

### `POST /api/announcements`
Crée une annonce de livraison.

**Corps de la requête**
```json
{
  "clientId": "uuid",
  "title": "Livraison urgent",
  "description": "Colis fragile",
  "recipientFirstName": "Jean", "recipientLastName": "Paul",
  "recipientEmail": "jean@example.com", "recipientPhone": "+237...",
  "shipperFirstName": "Marie", "shipperLastName": "Dupont",
  "shipperEmail": "marie@example.com", "shipperPhone": "+237...",
  "amount": 5000, "currency": "XAF",
  "paymentMethod": "MOBILE_MONEY",
  "transportMethod": "MOTORBIKE",
  "requiredVehicleType": "MOTORBIKE",
  "distance": 12.5, "duration": 30,
  "autoPublish": true,
  "destinationRelayPointId": "uuid-ou-null",
  "logisticsPrice": 2000,
  "pickupAddress": { "address": { "street": "Rue A", "city": "Yaoundé" }, "type": "PICKUP" },
  "deliveryAddress": { "address": { "street": "Rue B", "city": "Yaoundé" }, "type": "DELIVERY" },
  "packet": {
    "weight": 2.5, "width": 30, "height": 20, "length": 40,
    "fragile": true, "isPerishable": false, "description": "Électronique"
  }
}
```

**Réponse `201 Created`** → [`AnnouncementResponseDTO`](#announcementresponsedto)

---

### `GET /api/announcements` / `GET /api/announcements/{id}`
**Réponse `200 OK`** → [`AnnouncementResponseDTO`](#announcementresponsedto)

### `GET /api/announcements/client/{clientId}`
**Réponse `200 OK`** → tableau de `AnnouncementResponseDTO`

---

### `PUT /api/announcements/{id}`
Met à jour une annonce (mêmes champs que la création).

**Réponse `200 OK`** → [`AnnouncementResponseDTO`](#announcementresponsedto)

---

### `DELETE /api/announcements/{id}` → `204`

---

### `PATCH /api/announcements/{id}/publish`
Publie une annonce pour la rendre visible aux livreurs.

**Réponse `200 OK`** → `AnnouncementResponseDTO` avec `status: PUBLISHED`

---

### `POST /api/announcements/{id}/subscribe`
Un livreur candidate sur une annonce.

**Corps de la requête**
```json
{ "freelancerId": "uuid-livreur" }
```

**Réponse `202 Accepted`** — corps vide.

---

### `GET /api/announcements/{id}/subscriptions`
Liste les candidatures reçues pour une annonce.

**Réponse `200 OK`** → tableau de `SubscriptionResponseDTO`
```json
[{ "freelancerId": "uuid", "announcementId": "uuid", "status": "PENDING" }]
```

---

### `POST /api/announcements/{id}/assign`
Assigne un livreur à une annonce. Déclenche la création de la livraison.

**Corps de la requête**
```json
{ "freelancerId": "uuid-livreur" }
```

**Réponse `200 OK`** → `AnnouncementResponseDTO` avec `status: ASSIGNED` et les champs `assignedFreelancer*` renseignés.

---

### `GET /api/announcements/subscriptions/freelancer/{freelancerId}`
Liste les annonces auxquelles un livreur a candidaté.

**Réponse `200 OK`** → tableau de `AnnouncementResponseDTO`

---

#### AnnouncementResponseDTO
Reprend tous les champs de la requête plus :

| Champ | Description |
|-------|-------------|
| `id` | UUID de l'annonce |
| `status` | `DRAFT`, `PUBLISHED`, `ASSIGNED`, `COMPLETED`, `CANCELLED` |
| `createdAt` / `updatedAt` | Horodatages |
| `assignedFreelancerId` | UUID du livreur assigné |
| `assignedFreelancerFirstName/LastName/Email/Phone` | Infos livreur assigné |


---

## 6. Besoins de livraison

### `POST /api/delivery-needs`
Exprime un besoin de livraison ponctuel.

**Corps de la requête**
```json
{
  "userId": "uuid",
  "title": "Envoi de documents",
  "description": "...",
  "paymentMethod": "CASH",
  "transportMethod": "BIKE",
  "distance": 5.0, "duration": 20,
  "pickupDeadline": "2026-07-28T10:00:00",
  "targetRelayPointId": "uuid-ou-null",
  "requestedStorageDays": 2,
  "pickupAddress": { "address": { "street": "...", "city": "..." } },
  "deliveryAddress": { "address": { "street": "...", "city": "..." } },
  "packet": { "weight": 1.0, "fragile": false, "description": "Documents" }
}
```

**Réponse `201 Created`** — `DeliveryNeedResponseDTO`

| Champ sortie | Description |
|-------------|-------------|
| `id` | UUID du besoin |
| `userId` | UUID de l'auteur |
| `status` | `PENDING`, `ASSIGNED`, `COMPLETED` |
| `packetId` / `pickupAddressId` / `deliveryAddressId` | UUIDs liés |
| `deliveryId` | UUID de la livraison créée (renseigné après assignation) |
| `createdAt` / `updatedAt` | Horodatages |

---

### `GET /api/delivery-needs` / `GET /api/delivery-needs/{id}` / `GET /api/delivery-needs/user/{userId}`
**Réponse `200 OK`** → `DeliveryNeedResponseDTO` ou tableau.

---

### `DELETE /api/delivery-needs/{id}` → `204`

---

### `GET /api/delivery-needs/{id}/candidates`
Retourne les livreurs disponibles avec leur tarif calculé.

**Réponse `200 OK`** → tableau de `FreelancerCandidateDTO`
```json
[{
  "freelancerId": "uuid",
  "lastName": "Kamga", "firstName": "Paul",
  "rating": 4.7,
  "estimatedPrice": 1500.0,
  "priceBreakdown": "Base 1000 + distance 500"
}]
```

---

### `POST /api/delivery-needs/{id}/assign`
Assigne un livreur à ce besoin.

**Corps de la requête**
```json
{ "freelancerId": "uuid-livreur" }
```

**Réponse `200 OK`** → `DeliveryNeedResponseDTO` avec `deliveryId` renseigné.

---

## 7. Livraisons

### `GET /api/v1/deliveries/{id}`
### `GET /api/v1/deliveries/announcement/{announcementId}`
### `GET /api/v1/deliveries/delivery-need/{deliveryNeedId}`

**Réponse `200 OK`** — `DeliveryResponseDTO`

| Champ | Description |
|-------|-------------|
| `id` | UUID de la livraison |
| `announcementId` / `deliveryNeedId` | UUID source |
| `freelancerId` | UUID du livreur |
| `status` | `PENDING`, `ASSIGNED`, `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `FAILED`, `CANCELLED` |
| `urgency` | Niveau d'urgence |
| `tarif` | Tarif de la livraison (FCFA) |
| `noteLivreur` / `deliveryNote` | Notes |
| `pickupMinTime` / `pickupMaxTime` | Fenêtre de collecte |
| `deliveryMinTime` / `deliveryMaxTime` | Fenêtre de livraison |
| `estimatedDelivery` | ETA |
| `distanceKm` | Distance |
| `announcementTitle` | Titre de l'annonce |
| `freelancerFirstName/LastName/Phone` | Infos livreur |

---

### `GET /api/v1/deliveries/freelancer/{freelancerId}`
### `GET /api/v1/deliveries/status/{status}`
**Réponse `200 OK`** → tableau de `DeliveryResponseDTO`

---

### `PUT /api/v1/deliveries/{id}`
Met à jour les détails d'une livraison.

**Corps de la requête** (tous les champs sont optionnels)
```json
{
  "tarif": 2000.0,
  "urgency": "HIGH",
  "pickupMinTime": "2026-07-27T08:00:00Z",
  "pickupMaxTime": "2026-07-27T10:00:00Z",
  "deliveryMinTime": "2026-07-27T11:00:00Z",
  "deliveryMaxTime": "2026-07-27T13:00:00Z",
  "estimatedDelivery": "2026-07-27T12:00:00Z",
  "noteLivreur": 4.5,
  "deliveryNote": 5.0
}
```

**Réponse `200 OK`** → `DeliveryResponseDTO`


---

### `PATCH /api/v1/deliveries/{id}/status`
Change le statut d'une livraison. Règles métier intégrées.

**Corps de la requête**
```json
{
  "status": "PICKED_UP",
  "confirmationCode": "482910",
  "relayPointId": null,
  "clientId": null,
  "storageFee": 0.0
}
```

| Champ | Obligatoire | Condition |
|-------|-------------|-----------|
| `status` | ✅ | Toujours |
| `confirmationCode` | Selon statut | Requis pour `PICKED_UP` et `DELIVERED` direct |
| `relayPointId` | ❌ | Si livraison vers point relais (`status: DELIVERED`) |
| `clientId` | ❌ | Requis si `relayPointId` fourni |
| `storageFee` | ❌ | Frais de stockage (défaut `0.0`) |

> Quand `status = DELIVERED` et `relayPointId` est fourni, un `RelayDeposit` est créé automatiquement.

**Réponse `200 OK`** → entité `Delivery` complète.

---

### `POST /api/v1/deliveries/{id}/init-otp`
Initialise les codes OTP de la livraison (idempotent).
- Envoie le code de collecte à l'expéditeur.
- Envoie le code de réception au destinataire.

**Réponse `200 OK`** — corps vide.

---

### `PATCH /api/v1/deliveries/{id}/cancel`
Annule une livraison en cours.

**Réponse `200 OK`** → `DeliveryResponseDTO` avec `status: CANCELLED`

---

### `POST /api/v1/deliveries/{deliveryId}/reroute`
Reroutage manuel d'une livraison `IN_TRANSIT`.

**Corps de la requête**
```json
{
  "newRouteId": "route-uuid-depuis-tnt-route-core",
  "reason": "Route bloquée par travaux"
}
```

**Réponse `202 Accepted`** — corps vide. L'alerte est dispatché de manière asynchrone.

---

### Suivi (tracking)

#### `GET /api/v1/deliveries/tracking/announcement/{announcementId}`
#### `GET /api/v1/deliveries/tracking/delivery-need/{deliveryNeedId}`
Snapshot de position à un instant T.

**Réponse `200 OK`** — `DeliveryTrackingDTO`

| Champ | Description |
|-------|-------------|
| `deliveryId` / `announcementId` / `deliveryNeedId` | UUIDs liés |
| `freelancerId` | UUID du livreur |
| `status` | Statut courant |
| `freelancerLatitude` / `freelancerLongitude` | Position du livreur |
| `pickupLatitude` / `pickupLongitude` | Coordonnées de collecte |
| `deliveryLatitude` / `deliveryLongitude` | Coordonnées de livraison |

#### `GET /api/v1/deliveries/tracking/stream/announcement/{announcementId}` *(SSE)*
#### `GET /api/v1/deliveries/tracking/stream/delivery-need/{deliveryNeedId}` *(SSE)*
Flux temps réel `text/event-stream`. Même structure que le snapshot, mis à jour en continu.

---

### `GET /api/v1/deliveries/{id}/assistance`
Fournit les informations de navigation pour le livreur.

**Réponse `200 OK`** — `DeliveryAssistanceDTO`

| Champ | Description |
|-------|-------------|
| `deliveryId` | UUID de la livraison |
| `currentStatus` | Statut actuel |
| `stepDescription` | Instruction textuelle pour l'étape courante |
| `currentLatitude/Longitude` | Position actuelle du livreur |
| `targetLatitude/Longitude` | Destination cible |
| `distanceKm` | Distance restante |
| `estimatedTimeMinutes` | Estimation de temps (minutes) |


---

## 8. Abonnements livreurs

### `GET /api/freelancers/{id}/subscription`
Retourne l'état complet de l'abonnement d'un livreur.

**Réponse `200 OK`** — `SubscriptionStatusResponseDTO`

| Champ | Description |
|-------|-------------|
| `subscriptionId` / `freelancerId` | Identifiants |
| `plan` | `FREE`, `STANDARD`, `ADVANCE` |
| `status` | `ACTIVE`, `PENDING`, `SUSPENDED`, `EXPIRED`, `CANCELLED` |
| `price` | Prix payé (FCFA) |
| `paymentMethod` | `MOBILE_MONEY`, `ORANGE_MONEY`, `CASH` |
| `maxDeliveries` | Quota max (`-1` si illimité) |
| `unlimited` | `true` si plan ADVANCE |
| `deliveriesUsed` | Livraisons effectuées ce mois |
| `deliveriesRemaining` | Livraisons restantes (`-1` si illimité) |
| `quotaUsagePercent` | Pourcentage consommé (0–100) |
| `resetDate` | Date de remise à zéro du quota |
| `commissionPercent` | Part TiiBnTick (%) |
| `netPercent` | Part livreur (%) |
| `startDate` / `endDate` | Validité de l'abonnement |

**Retourne `404`** si aucun abonnement n'existe.

---

### `PUT /api/v1/admin/tnt-go-freelancer/freelancers/{id}/price?price=` *(ADMIN)*
Modifie le prix de l'abonnement d'un livreur.

**Query param** : `price=5000.0`

**Réponse `200 OK`** → `SubscriptionStatusResponseDTO` mis à jour.

---

## 9. Abonnements points relais

### `GET /api/relay-points/{relayPointId}/subscription`
**Réponse `200 OK`** — `RelayPointSubscriptionStatusDTO`

| Champ | Description |
|-------|-------------|
| `subscriptionId` / `relayPointId` | Identifiants |
| `plan` | Type d'abonnement |
| `status` | Statut |
| `price` / `paymentMethod` | Paiement |
| `maxDeposits` / `unlimited` | Quota de dépôts |
| `depositsUsed` / `depositsRemaining` / `quotaUsagePercent` | Consommation |
| `resetDate` | Remise à zéro |
| `commissionPercent` / `netPercent` | Répartition commission |
| `startDate` / `endDate` | Validité |
| `eligible` | Le point relais est-il éligible |

---

### `POST /api/relay-points/{relayPointId}/subscription`
Crée ou renouvelle l'abonnement d'un point relais.

**Corps de la requête**
```json
{
  "subscriptionType": "STANDARD",
  "paymentMethod": "MOBILE_MONEY"
}
```

**Réponse `201 Created`** → `RelayPointSubscriptionStatusDTO`

---

### `DELETE /api/relay-points/{relayPointId}/subscription`
Annule l'abonnement.

**Réponse `200 OK`** → `RelayPointSubscriptionStatusDTO` avec `status: CANCELLED`

---

### `GET /api/relay-points/{relayPointId}/subscription/eligible`
**Réponse `200 OK`** : `true` ou `false`

---

## 10. Dépôts relais

### `GET /api/relay-deposits/relay-point/{relayPointId}`
Liste les colis en attente dans un point relais.

**Réponse `200 OK`** → tableau de `RelayDeposit`

| Champ notable | Description |
|---------------|-------------|
| `id` | UUID du dépôt |
| `deliveryId` | UUID de la livraison associée |
| `clientId` | UUID du client destinataire |
| `relayPointId` | UUID du point relais |
| `status` | `DEPOSITED`, `RETRIEVED` |
| `storageFee` | Frais de stockage |
| `depositedAt` / `retrievedAt` | Horodatages |

---

### `GET /api/relay-deposits/client/{clientId}`
Liste les dépôts à récupérer par un client.

**Réponse `200 OK`** → tableau de `RelayDeposit`

---

### `PATCH /api/relay-deposits/{id}/retrieve?otpCode=`
Marque un colis comme récupéré après vérification du code OTP.

**Query param** : `otpCode=482910`

**Réponse `200 OK`** → `RelayDeposit` avec `status: RETRIEVED` et `retrievedAt` renseigné.
**Réponse `400 Bad Request`** si le code OTP est invalide.


---

## 11. Adresses

### `POST /api/addresses`
**Corps de la requête**
```json
{
  "address": {
    "street": "Rue de la Paix", "city": "Yaoundé",
    "district": "Bastos", "country": "Cameroun"
  },
  "type": "HOME"
}
```
**Réponse `201 Created`** → `AddressDTO` avec `id` renseigné.

---

### `GET /api/addresses` / `GET /api/addresses/{id}`
**Réponse `200 OK`** → `AddressDTO`

---

### `GET /api/addresses/search?query=`
Recherche textuelle sur les adresses.

**Réponse `200 OK`** → tableau de `AddressDTO`

---

### `PUT /api/addresses/{id}`
**Corps de la requête** : même structure que la création.

**Réponse `200 OK`** → `AddressDTO`

---

### `DELETE /api/addresses/{id}` → `204`

---

## 12. Tarification

### `POST /api/pricing/calculate/freelancer/{id}`
### `POST /api/pricing/calculate/logistics/{id}`
Calcule le prix proposé par un livreur ou un opérateur logistique.

**Corps de la requête**
```json
{
  "weight": 2.5,
  "volumeCbm": 0.027,
  "isFragile": true,
  "isPerishable": false,
  "distanceKm": 12.5,
  "days": 1
}
```

| Champ | Défaut | Description |
|-------|--------|-------------|
| `weight` | `0.0` | Poids du colis (kg) |
| `volumeCbm` | `0.0` | Volume en m³ |
| `isFragile` | `false` | Colis fragile |
| `isPerishable` | `false` | Colis périssable |
| `distanceKm` | `0.0` | Distance (pour livreur) |
| `days` | `1` | Jours de stockage (pour point relais) |

**Réponse `200 OK`**
```json
{
  "totalPrice": 2500.0,
  "currency": "XAF",
  "breakdown": "Base 1500 + distance 750 + fragile 250"
}
```

---

## 13. Évaluations

### `POST /api/v1/evaluations`
Soumet une évaluation après livraison.

**Corps de la requête**
```json
{
  "deliveryId": "uuid",
  "evaluatorId": "uuid-évaluateur",
  "evaluatedId": "uuid-évalué",
  "rating": 4,
  "comment": "Très professionnel",
  "type": "CLIENT_TO_DP"
}
```

Types acceptés : `CLIENT_TO_DP`, `DP_TO_CLIENT`, `CLIENT_TO_RP`, `RP_TO_CLIENT`

**Réponse `200 OK`** → entité `Evaluation` complète.

---

### `GET /api/v1/evaluations/person/{personId}`
Liste les évaluations reçues par une personne.

**Réponse `200 OK`** → tableau d'`Evaluation`

---

## 14. Contacts

### `GET /api/v1/contacts/user/{userId}?search=`
Liste les contacts d'un utilisateur, avec filtrage optionnel par nom/email/téléphone.

**Query param optionnel** : `search=Marie`

**Réponse `200 OK`** → tableau de `ContactDTO`
```json
[{
  "id": "uuid",
  "userId": "uuid",
  "firstName": "Marie", "lastName": "Dupont",
  "email": "marie@example.com", "phone": "+237..."
}]
```

---

### `GET /api/v1/contacts/{id}`
**Réponse `200 OK`** → `ContactDTO`


---

## 15. Profils GOFP — synchronisation inter-services

Ces endpoints maintiennent des vues dénormalisées utilisées en interne par les microservices.

---

### Utilisateurs GOFP — `/api/v1/gofp/users`

#### `POST /api/v1/gofp/users`
**Corps** : entité `GofpUser` complète.

**Réponse `201 Created`** → `GofpUser`

#### `GET /api/v1/gofp/users` → liste
#### `GET /api/v1/gofp/users/{id}` → par UUID interne
#### `GET /api/v1/gofp/users/by-core-user/{coreUserId}` → par UUID core
#### `GET /api/v1/gofp/users/by-email/{email}` → par email
**Réponse `200 OK`** → `GofpUser`

#### `PATCH /api/v1/gofp/users/{id}/status?status=`
**Query param** : `status=SUSPENDED`
**Réponse `200 OK`** → `GofpUser`

#### `DELETE /api/v1/gofp/users/{id}` → `204`

#### `GET /api/v1/gofp/users/{coreUserId}/profiles-summary`
Agrège tous les profils d'un utilisateur en une seule réponse.

**Réponse `200 OK`** — `UserProfileSummaryDTO`

| Champ | Description |
|-------|-------------|
| `coreUserId` | UUID du core user |
| `firstName` / `lastName` / `email` / `phone` | Identité |
| `cniNumber` / `nui` / `profilePhotoUrl` | Documents |
| `isClient` | L'utilisateur a-t-il un profil client ? |
| `clientId` | UUID client (si `isClient: true`) |
| `isFreelancer` | L'utilisateur a-t-il un profil livreur ? |
| `freelancerId` | UUID livreur (si `isFreelancer: true`) |
| `isRelayPoint` | L'utilisateur gère-t-il un point relais ? |
| `relayPointId` | UUID du premier point relais (si applicable) |

---

### Clients GOFP — `/api/v1/gofp/clients`

#### `POST` / `GET` / `GET /{id}` / `GET /by-core-client/{id}` / `GET /by-core-user/{id}` / `DELETE /{id}`
Même pattern que les utilisateurs GOFP. Retournent l'entité `GofpClient`.

#### `GET /api/v1/gofp/clients/by-status/{status}` → tableau de `GofpClient`

#### `PATCH /api/v1/gofp/clients/{id}/status?status=`
Change le statut du client (`ACTIVE`, `SUSPENDED`…)

#### `PATCH /api/v1/gofp/clients/{id}/loyalty?loyaltyStatus=`
Change le tier de fidélité (`BRONZE`, `SILVER`, `GOLD`…)

#### `POST /api/v1/gofp/clients/by-core-client/{coreClientId}/record-order`
Incrémente le compteur de commandes et recalcule le tier de fidélité automatiquement.

**Réponse `200 OK`** → `GofpClient` avec `loyaltyStatus` potentiellement mis à jour.

---

### Profils livreurs GOFP — `/api/v1/gofp/freelancer-profiles`

#### CRUD et lectures : même pattern que ci-dessus. Retournent `GofpFreelancer`.

#### `GET /active-approved` → livreurs actifs et approuvés
#### `GET /by-status/{status}` → filtrés par statut

#### `PATCH /{id}/status?status=` / `PATCH /{id}/active?active=`
Changement de statut ou activation/désactivation.

#### `PATCH /by-core-freelancer/{coreFreelancerId}/location?lat=&lng=`
Mise à jour GPS légère (sans tout le pipeline realtime).

#### `POST /by-core-freelancer/{coreFreelancerId}/record-delivery-success`
#### `POST /by-core-freelancer/{coreFreelancerId}/record-delivery-failure`
Incrémentent les compteurs de livraisons réussies/échouées. **Réponse `200`** — corps vide.

---

### Profils points relais GOFP — `/api/v1/gofp/relay-point-profiles`

#### CRUD et lectures : même pattern. Retournent `GofpRelayPoint`.

#### `GET /active-approved` / `GET /by-status/{status}` / `GET /by-freelancer/{coreFreelancerId}`

#### `GET /available?packetVolumeM3=`
**Query param** : `packetVolumeM3=0.027` (volume en m³)

Retourne les points relais actifs/approuvés ayant assez de capacité restante pour stocker le colis.

**Réponse `200 OK`** → tableau de `GofpRelayPoint`

#### `PATCH /{id}/storage`
Met à jour les dimensions de stockage sans écraser les autres champs.

**Corps de la requête**
```json
{
  "storageLength": 10.0,
  "storageWidth": 5.0,
  "storageHeight": 3.0,
  "storageDimensionUnit": "m"
}
```

#### `POST /by-core-relay-point/{coreRelayPointId}/sync-owner-contact`
Resynchronise les informations de contact du propriétaire depuis son profil GOFP.

**Réponse `200 OK`** → `GofpRelayPoint` avec les champs de contact mis à jour.


---

## 16. Upload de fichiers

Base path : `/api/v1/gofp/files`

Tous les endpoints :
- Consomment `multipart/form-data` avec un champ `file`
- Retournent l'entité mise à jour avec l'URL du fichier persistée
- Les fichiers sont accessibles statiquement à `GET /uploads/{filename}`

### Photo de profil utilisateur

#### `POST /api/v1/gofp/files/users/{coreUserId}/profile-photo`
**Réponse `200 OK`** → `GofpUser` avec `profilePhotoUrl` renseigné.

---

### Documents d'onboarding livreur

| Endpoint | Réponse |
|----------|---------|
| `POST /freelancers/{freelancerId}/cni-recto` | `FreelancerOnboardingDocs` avec `cniRectoUrl` |
| `POST /freelancers/{freelancerId}/cni-verso` | `FreelancerOnboardingDocs` avec `cniVersoUrl` |
| `POST /freelancers/{freelancerId}/nui-photo` | `FreelancerOnboardingDocs` avec `nuiPhotoUrl` |
| `POST /freelancers/{freelancerId}/photo-card` | `FreelancerOnboardingDocs` avec `photoCardUrl` |
| `POST /freelancers/{freelancerId}/commercial-register` | `FreelancerOnboardingDocs` avec `commercialRegisterUrl` |

---

### Photos de véhicule

| Endpoint | Réponse |
|----------|---------|
| `POST /freelancers/{freelancerId}/vehicle/front-photo` | `FreelancerVehicle` avec `frontPhotoUrl` |
| `POST /freelancers/{freelancerId}/vehicle/back-photo` | `FreelancerVehicle` avec `backPhotoUrl` |

---

### Photos de point relais

| Endpoint | Réponse |
|----------|---------|
| `POST /relay-points/{coreRelayPointId}/storefront-photo` | `RelayPointVisuals` avec `storefrontPhotoUrl` |
| `POST /relay-points/{coreRelayPointId}/shop-photo` | `RelayPointVisuals` avec `shopPhotoUrl` |

---

### Photo de colis

| Endpoint | Réponse |
|----------|---------|
| `POST /packets/{corePacketId}/cover-image` | `PacketProof` avec `coverImageUrl` |

---

## 17. Notifications temps réel (SSE)

### `GET /api/notifications/stream/{freelancerId}`
Flux de notifications de matching en Server-Sent Events (`text/event-stream`).

Le client doit maintenir la connexion ouverte. Chaque événement est un `MatchingNotificationEvent`.

**Structure d'un événement**
```json
{
  "type": "NEW_ANNOUNCEMENT",
  "announcementId": "uuid",
  "freelancerId": "uuid",
  "message": "Nouvelle annonce proche de vous",
  "timestamp": "2026-07-27T10:00:00Z"
}
```

Types d'événements possibles : `NEW_ANNOUNCEMENT`, `ASSIGNMENT_CONFIRMED`, `DELIVERY_CANCELLED`…

---

## 18. Administration

Tous les endpoints nécessitent le rôle `ADMIN` (`@PreAuthorize("hasRole('ADMIN')")`).

### Dashboard

#### `GET /api/v1/admin/tnt-go-freelancer/dashboard/stats`
**Réponse `200 OK`** — corps vide pour l'instant (stub à implémenter).

---

### Gestion des livreurs

#### `PUT /api/v1/admin/tnt-go-freelancer/freelancers/{id}/validate?approved=&reason=`
Valide ou refuse l'inscription d'un livreur.

**Query params** : `approved=true`, `reason=Documents valides`

**Réponse `200 OK`** — corps vide (stub — logique à brancher sur `AdminFreelancerUseCase`).

---

#### `PUT /api/v1/admin/tnt-go-freelancer/freelancers/{id}/suspend`
**Réponse `200 OK`** — corps vide (stub).

#### `PUT /api/v1/admin/tnt-go-freelancer/freelancers/{id}/revoke`
**Réponse `200 OK`** — corps vide (stub).

---

#### `PUT /api/v1/admin/tnt-go-freelancer/freelancers/{id}/price?price=` ✅ **Implémenté**
Modifie le prix de l'abonnement d'un livreur.

**Query param** : `price=5000.0`

**Réponse `200 OK`** → [`SubscriptionStatusResponseDTO`](#8-abonnements-livreurs) avec le nouveau prix.

---

### Gestion des clients

#### `PUT /api/v1/admin/tnt-go-freelancer/clients/{id}/suspend` — stub
#### `PUT /api/v1/admin/tnt-go-freelancer/clients/{id}/revoke` — stub
#### `PUT /api/v1/admin/tnt-go-freelancer/clients/{id}/activate` — stub

**Réponse `200 OK`** — corps vide.

---

### Gestion des points relais

#### `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/suspend` — stub
#### `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/revoke` — stub
#### `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/activate` — stub

**Réponse `200 OK`** — corps vide.

---

*Document généré le 27/07/2026 — tnt-go-freelancer-point-back-core*
