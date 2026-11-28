# Pour le Mobile et le BFF — changements issus du dédoublonnage Go-Freelancer

> Document tenu à jour à chaque lot. Un changement n'y figure que s'il est visible depuis l'API.

## Lot 1 — énumérations (aucun changement visible)

Les statuts de livraison, de réponse à annonce et d'annonce sont désormais ceux de `tnt-delivery-core`. Les
valeurs sont **identiques** (`CREATED, PICKED_UP, IN_TRANSIT, AT_RELAY_POINT, DELIVERED, FAILED, CANCELLED,
PAUSED_BY_INCIDENT, TIMED_OUT, SLA_BREACHED` ; `SENT, ACCEPTED, REJECTED, CANCELLED` ;
`DRAFT, PUBLISHED, IN_NEGOTIATION, ASSIGNED, COMPLETED, CANCELLED`) : rien à changer côté client.

## Notifications — ce qui change pour les clients

- Les messages Go-Freelancer sont rédigés une fois par langue dans le catalogue et rendus **dans la langue du
  compte destinataire** (français ou anglais). Ne plus compter sur du texte français fixe.
- Le Core **n'envoie plus** par push ni e-mail les codes de remise (pickup / livraison). Le propriétaire du besoin les
  lit dans l'application (`HandoverCodesDTO`) ; le destinataire doit les recevoir de lui. Les OTP de compte
  (connexion, MFA) restent ceux du Kernel.

## Lot L4 — paiement, notifications locales, adresses, relais/dépôts, tarification

- Paiement : impact API **aucun** (`/api/v1/deliveries/{deliveryId}/payment/*` inchangé ; suppression de code mort).
- Notifications — **addition** : nouvelle route `GET /api/v1/realtime/sse/notifications` (flux SSE de l'utilisateur du jeton ;
  événements `notification`, commentaire `heartbeat` toutes les 15 s). Elle s'ajoute à `GET /api/notifications/stream/{freelancerId}`
  (voir plus bas pour la délégation de cette dernière).
- Notifications — `GET /api/notifications/stream/{freelancerId}` : **aucune** rupture. Même route, même garde d'accès (id de profil
  freelancer ou id utilisateur du jeton), même événement `notification`, même JSON octet pour octet
  (`freelancerId`, `announcementId`, `title`, `message` ; pinned par `NotificationStreamControllerSseContractTest`). Changement de
  fond sans effet sur le client : le flux est porté par Redis (multi-instances, survit à un redéploiement) ; un client déconnecté ne
  rattrape pas les notifications émises entre-temps (comme avant, les sinks en mémoire ne rejouaient rien).
- Les e-mails et push de mise en relation sont rendus dans la langue du compte par le catalogue (déjà le cas depuis le chantier i18n) ;
  `title`/`message` du flux SSE restent les deux libellés historiques (voir points non tranchés du rapport de lot).
- Adresses — `/api/addresses` (POST, GET, GET `/{id}`, GET `/search`, PUT, DELETE) : mêmes routes, mêmes DTO, mêmes ids.
  Deux nuances sans changement de forme : (1) `type` est le libellé posé par l'utilisateur connecté (PRIMARY par défaut s'il n'en
  donne pas) ; une adresse créée sans utilisateur connecté, ou une adresse ancienne jamais rattachée à une personne, est relue avec
  `type: null` (avant : la valeur de la colonne `addresses.type`, le plus souvent `PRIMARY`) ; (2) la recherche locale traite `%` et
  `_` comme des caractères ordinaires. Une création sans rue **ni** repère est refusée par le cœur (le VO `Address` l'exigeait déjà).
- Dépôts relais — `/api/relay-deposits` (`/mine`, `/relay-point/{id}`, `/client/{id}`, `PATCH /{id}/retrieve`) : mêmes routes,
  même JSON (`id, packetId, clientId, relayPointId, storageFee, penaltyFee, currency, status, createdAt, retrievedAt, new`).
  Nuances sans changement de forme : (1) `id` d'un dépôt créé désormais = id de l'entrée d'inventaire ; pour un dépôt ancien
  déjà reflété dans l'inventaire, la migration garde l'id de l'**entrée**, pas celui de l'ancienne ligne `relay_deposits`
  (un client qui aurait gardé l'ancien id en cache doit relire la liste) ; les dépôts jamais reflétés gardent leur id ;
  (2) `new` vaut toujours `false` ; (3) la liste d'un relais montre toutes les entrées d'inventaire du hub.
- Grille tarifaire du livreur — `GET/PUT …/{id}/policies` : mêmes routes et DTO. `source` ne vaut plus jamais `LEGACY` (la grille
  héritée a été recopiée en politique au démarrage → `POLICY`) ; un PUT sans tenant dans le jeton crée la politique (dans le
  tenant système) au lieu de ne remplir que l'ancienne table. Devis (`quote`) : même montant, même détail ; `source` passe de
  `LEGACY_GRID` à `POLICY` et `policyId` est renseigné pour ces livreurs.
- Revue L4 (B1–B5) : aucune route ni DTO modifié. Correctif visible : créer un besoin, une annonce ou un profil avec une adresse
  neuve ne renvoie plus d'erreur (les anciennes clés étrangères vers `addresses` sont supprimées). Un paiement ancien payé
  (table `payments`, s'il y en a) reste « payé » côté règlement, avec sa date de paiement.

## Lot L1 — livraison et colis (2026-11-25, commits 19ad95e3, 251e434d)

- Quoi : la livraison Go n'est plus qu'une extension de la livraison de `tnt-delivery-core`. Statut, livreur
  affecté, heures réelles, prix convenu et fenêtres sont lus dans le cœur à chaque lecture. Les colis sont
  enregistrés dans le cœur (`tnt_parcels`) ; la table `packets` n'est plus utilisée par l'application.
- Impact API : **addition** (aucune route, aucun champ retiré ou renommé). Détail ci-dessous.

| Élément | Avant | Après | Classe |
|---|---|---|---|
| Routes `/api/v1/deliveries/...` (`GofpDeliveryController`) | toutes | identiques | aucune |
| `DeliveryResponseDTO` (champs) | id, announcementId, deliveryNeedId, freelancerId, status, urgency, tarif, noteLivreur, pickup/delivery Min/MaxTime, estimatedDelivery, duration, deliveryNote, distanceKm, champs enrichis | identiques ; `status`, `freelancerId`, `tarif` et les 4 fenêtres viennent du cœur | aucune |
| `DeliveryResponseDTO.urgency` (sortie) | type Go `NORMAL / EXPRESS / STANDARD`, jamais rempli (toujours `null`) | type du cœur `STANDARD / EXPRESS / SAME_DAY / SCHEDULED`, toujours non rempli | aucune en pratique (voir note) |
| `DeliveryUpdateDTO.urgency` (entrée, `PUT /{id}`) | `NORMAL`, `EXPRESS`, `STANDARD` ; autre valeur → 400 | `STANDARD`, `EXPRESS`, `SAME_DAY`, `SCHEDULED`, et `NORMAL` toujours accepté = `STANDARD` ; casse indifférente ; vide = absent ; autre valeur → 400 | addition |
| `PUT /{id}` : `tarif` et fenêtres | écrits sur la ligne Go, sans contrôle | enregistrés sur la livraison du cœur ; un champ absent garde la valeur du cœur ; refusé si la livraison est terminée, si le prix est négatif ou si une fenêtre finit avant de commencer | contrôle ajouté (code HTTP du refus non vérifié) |
| `PUT /{id}` : `urgency`, `estimatedDelivery`, `noteLivreur`, `deliveryNote` | ignorés | toujours ignorés | aucune |
| `PacketDTO` (création d'un besoin, lecture du besoin) | lu/écrit dans `packets` | mêmes champs, lus/écrits dans le colis du cœur ; poids ou dimension absents ou ≤ 0 enregistrés à 1 kg / 10 cm | voir note |
| Id du colis (`packetId` du besoin) | id de la ligne `packets` | même UUID, devenu l'id du colis du cœur (conservé à la migration) | aucune |

Notes :
- `urgency` : la section 3.5 du document cible prévoyait une rupture de sortie (`NORMAL` → `STANDARD`). Le
  code ne remplit jamais ce champ en sortie (avant comme après) : aucun client n'a pu recevoir `NORMAL`.
  Un client qui envoie `NORMAL` continue de fonctionner.
- `PacketDTO` : un colis créé sans poids ni dimensions était relu avec des champs vides ; il est désormais
  relu avec 1 kg et 10 × 10 × 10 cm, car le cœur exige des valeurs positives (mêmes valeurs neutres que la
  publication d'annonce utilisait déjà).

### Lot L1 — complément après revue (2026-11-25)

- **Volume occupé du coffre d'un livreur (changement d'unité, visible dans le matching).** Avant L1, la
  requête Go multipliait les dimensions de `packets` en les supposant en **mètres** (commentaire « assumed to
  be in metres »), alors que les colis sont saisis en cm : un colis de 30 × 20 × 12 cm comptait pour
  7 200 m³. Depuis L1, le volume vient des dimensions du colis du cœur, en **cm**, divisées par 1 000 000 :
  le même colis compte pour 0,0072 m³ (facteur 1e6). Effet observable : `MatchingUseCase` (filtre de capacité
  du coffre) et `SubscriptionConfirmationConsumer` voient désormais de la place libre là où un livreur
  apparaissait plein ; des livreurs auparavant exclus des candidats peuvent apparaître. Aucun champ de
  l'API ne change.
- **Sélection d'une réponse (`assignResponse`, invitation acceptée, assignation directe).** Si le prix
  convenu ne peut pas être enregistré dans le cœur, l'appel échoue désormais au lieu de réussir avec une
  livraison sans prix (qui aurait été livrée sans paiement). La sélection côté cœur n'est pas annulée.
## Lot L2 — annonces et besoins de livraison (2026-11-25, commits b43af5dd, 798a7ef3)
- Quoi : les annonces ne sont plus recopiées côté Go-Freelancer ; tout est lu et écrit dans le cœur. Les routes
  et les DTO sont conservés.
- Impact API : addition + deux ruptures justifiées (décision « ids du cœur partout » et statut dérivé).

**Addition — `urgency` (optionnel) en entrée de `POST /api/announcements` :** `STANDARD` (défaut si absent),
`EXPRESS`, `SAME_DAY`, `SCHEDULED`. L'ancienne valeur Go `NORMAL` reste acceptée et est enregistrée `STANDARD`.
Valeur inconnue → **400**.

```
avant : "urgency" ignoré (toujours STANDARD)        après : {"urgency":"NORMAL"}   → stocké STANDARD
                                                            {"urgency":"SAME_DAY"} → stocké SAME_DAY
                                                            {"urgency":"URGENT"}   → 400 "Unknown urgency: URGENT ..."
```

**Addition — champs désormais renseignés dans `AnnouncementResponseDTO`** (ils venaient du miroir, sont lus du
cœur) : `duration` (jamais renvoyé auparavant), et sur toutes les lectures (liste, proches, détail, candidat,
après sélection) `shipper*`, `recipientEmail`, `paymentMethod`, `transportMethod`, `distance`, `logisticsPrice`,
`requiredVehicleType`, `destinationRelayPointId`, `signatureUrl`.

**Changement de valeur — noms du destinataire.** Le cœur garde un seul nom ; il est recoupé au premier espace.

```
avant : {"recipientFirstName":"Marie Ngono","recipientLastName":"Ngono"}   (prénom = nom complet)
après : {"recipientFirstName":"Marie","recipientLastName":"Ngono"}
limite : "Jean Paul Mbarga" → prénom "Jean", nom "Paul Mbarga" (prénoms composés)
```

**Changement de valeur — `PUT /api/announcements/{id}`** : il modifie l'annonce du cœur (avant : seulement le
miroir, et `404 "Local announcement mirror not found"` sans miroir). Un nouveau `amount` est donc visible au
`GET` suivant. Annonce attribuée : changer `paymentMethod` ou `amount` → **409** (inchangé).

**Rupture justifiée — `assignedFreelancerId` d'un besoin (`DeliveryNeedResponseDTO`)** : c'est l'id du livreur
dans le cœur (celui de sa réponse, le même que `freelancerId` envoyé à `POST /api/announcements/{id}/respond`),
et non plus `gofp_freelancers.id`.

```
avant : {"status":"ASSIGNED","assignedFreelancerId":"<gofp_freelancers.id>","deliveryId":null}
après : {"status":"ASSIGNED","assignedFreelancerId":"<id livreur du cœur>","deliveryId":"<id livraison du cœur>"}
```

**Statut d'un besoin (`status`) : même vocabulaire, valeurs calculées.** `PENDING` (non publié ou annonce
ouverte), `ASSIGNED` (réponse retenue, livraison créée), `IN_PROGRESS` (colis pris en charge, en transit, au relais,
échec en cours de reprise, incident), `COMPLETED` (livré), `CANCELLED`. Auparavant le besoin restait `ASSIGNED`
jusqu'au bout ; `ACCEPTED` n'est jamais renvoyé.

**Rupture justifiée — assigner un besoin jamais publié** (`POST /api/delivery-needs/{id}/assign`, besoin sans
annonce, cas antérieur au lot C-43) :

```
avant : 200 {"status":"ASSIGNED", ...}
après : 409 "This delivery need has no announcement: it cannot be assigned"
```

**`GET /api/announcements/subscriptions/freelancer/{id}`** : liste des annonces auxquelles le livreur a répondu
(réponses du cœur), au lieu de l'ancienne table `announcement_subscriptions`.

- Changesets : voir `agency-et-modules.md` (Lot L2).
- Preuves / restes : voir `journal-technique.md` (Lot L2).

### Lot L2 — corrections après revue (2026-11-26)
- **Besoins assignés avant L2 (B1)** : un besoin assigné avant ce lot sans livraison dans le cœur (assignation
  directe dont l'annonce a été retirée, ou annonce restée dans l'ancien miroir) garde son statut stocké
  `ASSIGNED` / `IN_PROGRESS` / `COMPLETED`, son `deliveryId` et son livreur, au lieu de remonter `CANCELLED` ou
  `PENDING` sans livreur. `assignedFreelancerId` y est aussi l'id du livreur dans le cœur quand le livreur est
  connu (sinon l'id stocké). Valable jusqu'à la clôture (suppression des colonnes).
  **Clôture gdd-final (2026-11-27)** : ce repli est retiré avec les colonnes ; la migration s'arrête (HALT) si un
  tel besoin existe encore sans livraison dans le cœur, donc aucun besoin ne change de statut en silence.

```
besoin assigné directement avant L2, annonce retirée :
  sans repli : {"status":"CANCELLED","assignedFreelancerId":null,"deliveryId":null}
  avec repli : {"status":"ASSIGNED","assignedFreelancerId":"<id livreur du cœur>","deliveryId":"<delivery_id stocké>"}
```
- **Création d'un besoin rejouée** : si le besoin a déjà son annonce, elle est réutilisée (avant le correctif : 500
  sur l'index unique). Aucun changement de contrat.

## Lot L3 — acteurs : profils, véhicules, évaluations, fidélité, statuts

(en cours de rédaction : voir les sous-sections ajoutées au fil des commits du lot)

### Évaluations — `/api/v1/evaluations` (routes conservées)

Les notes sont désormais des notes tp-core (`tnt_tp_ratings`) ; la moyenne d'un client ou d'un livreur est celle
du profil actor-core.

| | Avant | Après |
|---|---|---|
| Permission | aucune | `rating:create` (CLIENT, FREELANCER, gestionnaires) ; sinon **403** |
| Évaluateur | `evaluatorId` du corps, quel qu'il soit | **l'acteur du jeton** (C5). `evaluatorId` devient facultatif : absent, c'est l'appelant ; présent, il doit désigner l'appelant (son actorId ou l'un de ses profils), sinon **403**. Seul le personnel (TNT_ADMIN, SUPPORT_AGENT) peut noter au nom d'une autre partie. Sans jeton : **403** |
| `evaluatedId` en entrée | id Go (client, freelancer, relais) | id de profil (client, freelancer), id de relais **ou** actorId — tous acceptés |
| `evaluatorId` / `evaluatedId` en sortie | l'id reçu | **actorId** (id core) |
| `id` en sortie | id de la ligne `evaluations` | id de la note core |
| `rating` | entier | entier (arrondi de la note core), inchangé |
| `score`, `subjectKind` | — | **ajouts** optionnels : note décimale ; `CLIENT` / `DELIVERY_PERSON` / `RELAY_OPERATOR` |
| 2e note d'une même livraison par le même évaluateur | acceptée si `type` différent | acceptée si elle vise **un autre genre de partie** (le client note le livreur `CLIENT_TO_DP` **et** le relais `CLIENT_TO_RP`) ; **409** pour une 2e note du même genre (`CLIENT_TO_DP` puis `CLIENT_RATING_DELIVERY_PERSON`) |
| partie inconnue | 500 (FK) | **400** |
| `GET /person/{personId}` | notes reçues par cet id | notes reçues par l'acteur correspondant (pour un relais : ses seules notes de relais) |

Exemple (jeton du client) : `POST {deliveryId: D, evaluatorId: <id profil client>, evaluatedId: <id profil freelancer>, rating: 4, type: "CLIENT_TO_DP"}`
renvoie `{id: <id note>, evaluatorId: <actorId client>, evaluatedId: <actorId freelancer>, rating: 4, score: 4.0,
subjectKind: "DELIVERY_PERSON", type: "CLIENT_TO_DP", ...}`.

### Fidélité et notes du client — `/api/v1/gofp/clients` (H3, rupture de valeur)

`loyaltyStatus` est le palier du compte de fidélité tp-core de l'acteur : 10 points par livraison terminée, paliers
BRONZE 0-499, SILVER 500-1999, GOLD 2000-4999, PLATINUM ≥ 5000. Les seuils Go (10 / 25 / 50 commandes) disparaissent.
La migration crédite `total_orders * 10` points.

| Commandes | Palier Go (avant) | Points core | Palier (après) |
|---|---|---|---|
| 10 | SILVER | 100 | **BRONZE** (rétrogradation) |
| 25 | GOLD | 250 | **BRONZE** |
| 50 | PLATINUM | 500 | **SILVER** |
| 200 | PLATINUM | 2000 | GOLD |

- `loyaltyStatus` prend les valeurs de `LoyaltyTier` (mêmes noms). Il est `null` si l'appelant n'a pas `loyalty:read`
  (un FREELANCER par exemple) ou si le client n'a pas encore de compte.
- `totalOrders` = points cumulés / 10 (une livraison express, créditée 20 points, compte double).
- `rating` = moyenne du profil client actor-core (`null` sans note).
- `PATCH /{id}/loyalty` : **409**, le palier ne se fixe plus à la main (rupture justifiée : il dérive des points).
- `POST /by-core-client/{coreClientId}/record-order` : **déprécié, sans effet** (C4). Répond **200** avec le client
  (en-tête `Deprecation: true`), sans permission supplémentaire et sans créditer de points : les applications
  actuelles ne reçoivent donc pas de 403. Le crédit se fait **une seule fois par livraison** par le core, à la fin
  de livraison (événement `tnt.delivery.mission.completed`, `senderId` publié par L1, idempotent par id de livraison).
  Route supprimée à la clôture : cesser de l'appeler. Client inconnu : 404, comme avant.
- `PATCH /{id}/status` (statut du client) : inchangé, reste local (aucun cas d'usage core, voir journal).
- Ids : `id` d'un client Go devient l'id de son profil client actor-core (migration). Les routes qui le prennent
  continuent de fonctionner avec la nouvelle valeur ; un id mémorisé avant la migration n'est plus reconnu.

### Freelancers — statut d'agrément et note (`/api/freelancers/{id}`, `/api/v1/gofp/freelancers`)

- `status` est lu de delivery-core (`tnt_delivery_persons`) ; mêmes valeurs (PENDING, APPROVED, SUSPENDED,
  REJECTED, REVOKED), même machine à états. Un freelancer sans livreur core apparaît **PENDING** et son changement
  de statut renvoie **404** (n'arrive plus pour un freelancer créé après ce lot : sa ligne core est créée avec lui).
- **Agrément par un administrateur (nouveau parcours, décision métier)** : un nouveau livreur (profil freelancer
  créé, ou `POST` de création Go) est **PENDING** et ne peut ni s'abonner ni répondre aux annonces tant qu'un
  administrateur ne l'a pas agréé (`PATCH /api/v1/gofp/freelancers/{id}/status` → `APPROVED`). Avant, il était
  agréé d'office à la projection. L'application doit afficher l'attente d'agrément (statut PENDING) au lieu de
  proposer les annonces. Un `status` envoyé dans le corps de création est ignoré. Les livreurs déjà agréés gardent
  leur statut (la migration ne rétrograde personne).
- Ids à la création (C1) : un freelancer créé prend `id = coreFreelancerId` (id de profil = id du livreur core) ;
  `coreUserId` devient obligatoire à la création (**400** sinon). Un utilisateur Go prend `id = coreUserId`, un
  client Go l'id de son profil client actor-core (trouvé ou créé par le core) ; l'`id` éventuel du corps est ignoré.
- `rating` = moyenne du profil freelancer actor-core (`null` sans note) ; les moyennes Go stockées ne sont plus lues.
- `/api/freelancers/{id}` prend toujours l'**id de profil** (non changé en actorId). Les quelques freelancers dont
  l'id Go différait de l'id core (H9) reçoivent l'id core à la migration.

### Véhicules — `PUT /api/v1/gofp/freelancer-profiles/me/vehicle` (rupture d'API, décision utilisateur)

- **Rupture** : le corps devient `{vehicleType, plateNumber, maxCapacityKg}` ; `maxCapacityKg` (capacité maximale en
  kg, nombre `> 0`) est **obligatoire**. Absent, nul ou négatif → **400** (rien n'est écrit). L'application doit
  ajouter le champ au formulaire véhicule du livreur (KYC compris).
- Le véhicule est désormais le **véhicule personnel du livreur dans tnt-resource-core** (propriétaire = son acteur,
  sans organisation), écrit de façon synchrone : premier `PUT` = création, `PUT` suivants = mise à jour du même
  véhicule. Une plaque déjà enregistrée sur le véhicule d'un autre → **409**.
- `vehicleType` accepte toujours les deux vocabulaires (`moto|tricycle|taxi|camion|bus` ou `MOTORBIKE|SCOOTER|BIKE|
  TRICYCLE|CAR|TRUCK|VAN|BUS`) ; un type précis (`SCOOTER`, `VAN`…) est conservé dans le core, la réponse garde la
  catégorie (`moto`, `camion`…) comme avant.
- Réponse (`GET` et `PUT`) : champ ajouté `maxCapacityKg` (`null` tant que le livreur n'a pas redéclaré son véhicule).
- **Véhicules existants** : aucun n'a de capacité connue, ils ne sont donc pas recréés dans le core ; `maxCapacityKg`
  y est `null` et l'application doit inviter le livreur à redéclarer son véhicule (un `PUT` avec la capacité suffit).
  Photos, type et plaque déjà saisis sont conservés. Décision utilisateur confirmée (H10, 2026-11-26) : le livreur
  saisit la capacité à son prochain enregistrement du véhicule ; aucune valeur par défaut n'est appliquée.
- Code mort supprimé plus tôt dans le lot : `VehicleCreateRequest`, `VehicleClass`, enregistrement HTTP vers une
  route inexistante qui inventait un id de véhicule.

### Mots de passe locaux (H8, rupture effective à la clôture du 2026-11-27, D-154)

`POST /api/auth/setup-password` et `POST /api/auth/request-password-reset` sont **supprimés** (404) ; la colonne
`gofp_users.password_hash` et la table `password_tokens` aussi. Définition et réinitialisation du mot de passe :
routes du Kernel uniquement. L'administrateur local `admin@test.com` que le module recréait à chaque démarrage dans
l'ancienne table `persons` n'existe plus (aucune route ne l'utilisait ; les administrateurs sont des comptes Kernel).

### Clôture `gdd-final` (2026-11-27, D-154) — ce qui change pour Mobile et BFF

- Routes supprimées : les deux routes de mot de passe local ci-dessus. Aucune autre route ne disparaît.
- Valeurs : inchangées. Le statut, le livreur et la livraison d'un besoin viennent du cœur depuis L2 ; les besoins
  assignés avant L2 n'ont plus de repli (voir L2, B1) ; `source` d'une grille tarifaire ne valait déjà plus `LEGACY`.
- Création d'un besoin : plus d'écriture dans la table héritée `users` ; `userId` n'a plus besoin d'y exister.
- Migration de la base : si des lignes héritées n'ont pas de contrepartie dans le cœur, le démarrage s'arrête
  avant toute suppression (rien n'est perdu) ; voir le journal technique pour le diagnostic.
- Compte à deux profils (client et freelancer) désigné par son actorId dans une évaluation : la note va au profil
  désigné par `type` (`CLIENT_TO_DP` → freelancer, `DP_TO_CLIENT` → client). Envoyer l'id de profil lève toute ambiguïté.

## Points relais : suspension, réactivation, agrément (décisions D-138 à D-140, 2026-11-26)

| Route | Avant | Après |
|---|---|---|
| `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/suspend` | 200 toujours, sans effet | 200 si le relais était `APPROVED` (ou déjà `SUSPENDED` : aucun effet), **409** sinon, 404 si inconnu |
| `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/activate` | 200 toujours, sans effet | 200 si le relais était `SUSPENDED` (ou déjà actif : aucun effet), **409** sinon (un relais en attente passe par la validation), 404 si inconnu |
| Choix d'un relais comme destination, dépôt au relais | possible quel que soit le statut | refusé si le relais est suspendu, révoqué ou non agréé |

Le statut d'agrément affiché d'un relais est celui du cœur (le relais Go le recopie).

## Statut d'un relais et destination d'un besoin (décisions D-142, D-144, 2026-11-26)

| Route | Avant | Après |
|---|---|---|
| `PATCH /api/v1/gofp/relay-point-profiles/{id}/status` | ouverte au personnel | permission d'administrateur Go (`gofp-admin:manage`) ; 403 sinon |
| Création d'un besoin avec une destination relais | destination non vérifiée | destination inconnue du cœur ou non agréée → refus (404 ou 409) |

## Destination relais en pause ou pleine (décisions D-145, D-146, 2026-11-26)

| Cas | Après |
|---|---|
| Relais agréé mais mis en pause par son propriétaire | non proposé ; besoin qui le vise refusé (409) |
| Relais agréé mais plein (capacité atteinte) | non proposé ; besoin qui le vise refusé (409) |

## Correctifs après intégration : propriétaires hérités (D-128) et révocation des relais (D-129) — 2026-11-26

### Adresses et dépôts hérités désormais visibles par leur propriétaire (D-128)

Les liens d'adresse et les dépôts relais copiés par L4 gardaient l'ancien id de personne / de client : le carnet
d'adresses (`GET` des adresses de l'utilisateur) et la liste « mes colis en dépôt » les ignoraient, puisqu'ils lisent
par l'acteur du jeton. Le changeset `gdd-l4-417` les rattache à l'acteur du cœur quand la correspondance existe.
Aucun changement de route ni de format. Les lignes sans correspondance restent invisibles (voir le journal technique).

### Révocation d'un point relais — `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/revoke` (rupture d'API)

- Avant : route sans corps ni effet (réponse 200 vide, aucun appel au service).
- Maintenant : **corps obligatoire** `{"reason": "…"}` (1 à 1 000 caractères), `?loginUrl=` facultatif, permission
  `relay:revoke` (administrateur de la plateforme seulement ; `gofp-admin:manage` ne suffit plus pour cette route).
- Réponse 200 : `relayPointId`, `hubId`, `status` (`REVOKED`), `reason`, `revokedAt`, `coreHubRevoked`,
  `custodyKnown`, `parcelsInCustody[]` (`entryId`, `packageId`, `trackingCode`, `ownerActorId`, `depositedAt`).
  400 motif absent, 401 non authentifié, 403 sans permission, 404 relais inconnu, 409 relais ni `APPROVED` ni
  `SUSPENDED` (déjà révoqué compris).
- Route du cœur équivalente : `POST /api/v1/geo/tenants/{tenantId}/hubs/{hubId}/revoke` (même corps, même permission).
- `REVOKED` est définitif : un relais révoqué ne peut plus être validé, suspendu ni activé (409 côté service).
- L'opérateur reçoit la notification `notify.gofp.relay_status.revoked_reason` (paramètre `reason`, langue du compte),
  et le courriel « compte révoqué ». Les clients mobiles qui affichent le statut du relais doivent connaître `REVOKED`
  (déjà présent dans l'énumération Go `RelayPointStatus`) ; côté cœur, `HubStatus` gagne `REVOKED`.

### Suite (décisions D-134, D-135) — 2026-11-26

- Adresses héritées orphelines **supprimées** (D-134, changeset `gdd-l4-420`) : un utilisateur ne verra jamais ces
  adresses ; il les ressaisit. Les dépôts relais orphelins sont gardés (rattachement par un administrateur).
- `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/suspend` et `/activate` ont maintenant un effet (avant : 200
  sans rien faire). Nouvelles réponses : 404 relais inconnu, 409 transition refusée. Suspendre : `APPROVED` seulement ;
  réactiver : `SUSPENDED` seulement ; redemander le statut courant : 200 sans effet.
- Les notifications de statut d'un relais (validé, rejeté, suspendu, réactivé) vont désormais à l'acteur de
  l'opérateur (H1) et non plus à l'id de profil freelancer : un client qui écoutait le flux du profil doit écouter celui
  de l'utilisateur.

### Fusion de `main` dans `feat/single-system-tenant` — 2026-11-27 (D-157, D-158)

La branche reste la référence ; les lots de `main` sont intégrés sur son modèle (annonces, livraisons et colis du
cœur, pas de miroir local).

- **Boîte in-app (C-36, D-157)** — mêmes routes et mêmes corps que sur `main` : `GET /api/v1/gofp/notifications/mine`
  (`items`, `unread`, `page`, `size` ; non lus d'abord puis plus récents), `POST /{id}/read`, `POST /read-all`,
  `DELETE /{id}`. Différences :
  - `id` est l'identifiant du message dans la boîte du **Kernel** (canal `IN_APP`, application `tiibntick-go`), plus une
    ligne locale ;
  - `DELETE` **marque lu** (le Kernel n'a pas de suppression, K17) : le message reste listé comme lu ; l'app peut
    masquer les lus si elle veut un effet « supprimé » ;
  - Kernel indisponible : **503** (plus jamais une boîte vide trompeuse) ;
  - pas de purge 30/90 jours côté Core : la rétention est celle du Kernel (K18) ;
  - `type`, `params`, `linkKind`, `linkId` inchangés (portés en métadonnées du message Kernel).
- **Contacts du besoin (C-56)** — les contacts saisis sont gardés sur le besoin et relus tels quels. Le destinataire
  part dans l'annonce du cœur (« — » s'il manque, jamais « Recipient » ni +237000000000). L'expéditeur part dans
  l'annonce du cœur (`senderName`, `senderPhone`) : le cœur ne garde **qu'un nom**, relu en prénom = premier mot,
  nom = le reste.
- **Tenant de l'annonce (C-56)** — consulter une annonce, y répondre et l'élire se font dans le tenant où elle a été
  publiée (livreur d'un autre tenant accepté, sinon 404/409 sur `main` avant C-56).
- **Approbation automatique des relais (C-57, D-158)** — un relais enregistré par son propriétaire revient `APPROVED`
  et actif dans la même réponse (interrupteur `TNT_GOFP_RELAY_AUTO_APPROVE`, vrai par défaut). Sur cette branche, le
  hub du cœur est agréé d'abord (D-138) : le relais est aussitôt une destination proposable (D-144).
- **Tarifs** — la grille par véhicule de la branche (D-058 : minimum de course, puis suppléments fragile et
  périssable) fait foi ; « fragile ET périssable » paie bien les deux suppléments (C-56). Exemple moto, 0 km, 1 kg :
  1 000 / 1 500 / 1 500 / 2 000 XAF (au lieu de 550 / 1 050 / 1 050 / 1 550 sur `main`).
- **Dépôt au relais** — les notifications « colis au relais » / « colis reçu » ne partent que si le relais est la
  destination (D-149) ; un relais de transfert ne déclenche rien.
