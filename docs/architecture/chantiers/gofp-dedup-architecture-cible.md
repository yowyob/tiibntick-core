# Architecture cible du module Go-Freelancer (dédoublonnage vers les couches basses)

Dépôt : `tiibntick-core`, branche `feat/single-system-tenant`, base = commit 61ad1750 (lot 1 enums déjà fait).
Module cible : `coreBackend/tnt-go-freelancer-point-back-core` (Java 21, Spring Boot 4 WebFlux/R2DBC, hexagonal).
Rédigé en lecture seule du dépôt. Chaque affirmation est appuyée par `fichier:ligne` quand la ligne a été lue ; sinon la mention « non vérifié » ou « ligne non relevée » est portée.

Abréviations de chemins utilisées dans tout le document :

| Alias | Chemin |
|---|---|
| `GOFP/` | `coreBackend/tnt-go-freelancer-point-back-core/src/main/java/com/yowyob/tiibntick/core/gofreelancer/` |
| `GOFP-SQL/` | `coreBackend/tnt-go-freelancer-point-back-core/src/main/resources/db/changelog/` |
| `DLV/` | `logistics/tnt-delivery-core/src/main/java/com/yowyob/tiibntick/core/delivery/` |
| `ACT/` | `identity/tnt-actor-core/src/main/java/com/yowyob/tiibntick/core/actor/` |
| `TP/` | `identity/tnt-tp-core/src/main/java/com/yowyob/tiibntick/core/tp/` |
| `RES/` | `business/tnt-resource-core/src/main/java/com/yowyob/tiibntick/core/resource/` |

---

## 0. Hypothèses à valider (avant de lancer les développeurs)

Ces points ne sont pas tranchés par les décisions de l'utilisateur. Chaque développeur applique l'option « retenue par défaut » tant que le modérateur n'a pas répondu. Chaque hypothèse est reprise dans la checklist (section 9).

| # | Hypothèse | Option retenue par défaut | Impact si faux |
|---|---|---|---|
| H1 | Le « client Go » est identifié côté core par son `actorId` (= `userId` du JWT, c'est ce que contient déjà `delivery_needs.user_id`, voir `GOFP/domain/model/DeliveryNeed`). Le profil de référence est `tnt_actor.client_profiles` (actor-core), pas `tnt_client_profiles` (tp-core). | `actorId` partout ; `thirdPartyId` (tp-core) = `actorId` pour la fidélité et les notes. | La clé tp-core change : migration des lignes `tnt_loyalty_accounts` / `tnt_tp_ratings` (L3). Sens exact de `thirdPartyId` côté Kernel : non vérifié. |
| H2 | Les statuts `FreelancerStatus` / `RelayPointStatus` (PENDING, APPROVED, SUSPENDED, REJECTED, REVOKED) sont portés par `DeliveryPersonStatus` (delivery-core) auquel on AJOUTE `REVOKED` et les méthodes `reject()` / `revoke()` ; `ActorStatus`/`KycStatus` (actor-core) restent des états d'identité, pas de cycle d'agrément. | Ajout de `REVOKED` à `DeliveryPersonStatus` (delivery-core n'a que PENDING, APPROVED, SUSPENDED, REJECTED, avec seulement `approve()` et `suspend()`, `DLV/domain/model/aggregate/DeliveryPerson.java`). Le point relais utilise le même enum via son opérateur. | Si on préfère `ActorStatus`+`KycStatus`, la table de correspondance de C9 change ; le code Go ne change pas de forme. |
| H3 | Seuils de fidélité : le core fait foi (points : 10 par livraison, `LoyaltyAccount.java:26` côté tp-core ; paliers SILVER dès 500 points, `LoyaltyTier.java:15`). Les seuils Go (10 / 25 / 50 commandes, `GOFP/application/service/GofpClientService.java:157`) disparaissent. | Le niveau affiché au client change pour les clients existants (rétrogradation possible). Les points initiaux sont recalculés à `total_orders * 10` à la migration. | Si l'utilisateur veut conserver 10/25/50, il faut ajouter une politique de seuils paramétrable dans tp-core (ajout générique, pas de nom gofp). |
| H4 | Le crédit de fidélité tp-core ne se déclenche probablement pas aujourd'hui : `DeliveryEventConsumer` lit `senderThirdPartyId` (`TP/adapter/in/kafka/DeliveryEventConsumer.java:62`) et `missionId`, alors que `DeliveryCompletedEvent` (`DLV/domain/event/DeliveryCompletedEvent.java:14`) porte `eventId, aggregateId, tenantId, deliveryPersonId, recipient, finalCost, occurredAt`. Non vérifié à l'exécution (sérialisation réelle du message). | Ajouter `senderId` (= client) dans l'événement core (ajout générique) et faire lire `aggregateId` comme `missionId` côté tp-core. | Si un autre producteur alimente ce topic, ne pas casser son format : ajouter, ne pas renommer. |
| H5 | Commission : `DeliveryPaymentApplicationService.COMMISSION_RATE = 0.05` fixe (`GOFP/application/service/DeliveryPaymentApplicationService.java:46`) alors que les abonnements Go impliquent 30/20/10 % (freelancers) et 15/10/5 % (relais). | On conserve 5 % à la migration (comportement actuel), et on prévoit un point d'extension : le taux est lu depuis la politique de facturation (billing-pricing). | Changer le taux est une décision métier ; le lot L4 ne la prend pas. |
| H6 | `DeliveryUrgency.NORMAL` (Go) n'est jamais persisté (DTO seulement : `DeliveryUpdateDTO:16`, `DeliveryResponseDTO:23`). On le remappe vers `STANDARD` à l'entrée (alias toléré) et on ne l'émet plus en sortie. | Rupture d'API mineure et justifiée (classe « rupture justifiée ») sur la valeur sortante. | Si un client mobile envoie `NORMAL`, l'alias d'entrée l'absorbe. |
| H7 | Les colonnes `deliveries.status`, `deliveries.actual_pickup_time`, `deliveries.actual_delivery_time`, `deliveries.freelancer_id` sont supprimées en changeset de CLÔTURE (L-final), après que tous les lecteurs lisent le core. | Suppression en fin de chantier seulement. | Garder les colonnes : aucun risque fonctionnel, mais la dette reste. |
| H8 | `gofp_users.password_hash`, `password_tokens`, `PasswordSetupController` (`/api/auth/setup-password`, `/request-password-reset`) sont des restes d'auth locale ; l'authentification est déjà déléguée au Kernel (`AuthController` appelle le Kernel). | On les supprime en clôture si le frontend n'appelle plus ces routes (à confirmer par le modérateur). | Casse des parcours « définir son mot de passe » s'ils servent encore. |
| H9 | `tnt_delivery_persons.id == freelancer_profiles.id == gofp_freelancers.id == gofp_freelancers.core_freelancer_id` est l'invariant déclaré (`GOFP/application/service/GofpFreelancerProjectionService.java:31`). Il est supposé vrai pour les lignes projetées ; les lignes plus anciennes créées par `GofpFreelancerService.createOrUpdate` peuvent avoir un id aléatoire (non vérifié, à mesurer par requête avant migration). | La migration L3 mesure d'abord (requête de diagnostic), puis repointe. | Voir le plan de migration C11. |
| H10 | `VehicleRegistrationAdapter` retombe sur `UUID.randomUUID()` en cas d'erreur (`GOFP/adapter/out/clients/VehicleRegistrationAdapter.java:66`) : une partie des `core_vehicle_id` existants est donc probablement fictive. Non vérifié sur les données. | La migration marque ces lignes `core_vehicle_id` invalides et les recrée dans resource-core si la plaque est connue ; sinon elles restent en attente d'action de l'utilisateur. | Perte éventuelle de véhicules sans plaque. |
| H11 | Le `thirdPartyId` / les droits : les services core appelés en direct sont protégés par `@RequirePermission` via un aspect AOP (`foundation/tnt-roles-core/.../adapter/in/web/TntPermissionAspect.java:36`). `RatingService.rate` exige `actor:write` (`TP/application/service/RatingService.java:44`) et `LoyaltyService` exige `billing:write` (`TP/application/service/LoyaltyService.java:45,72`), or `CLIENT` ne possède ni `ACTOR_WRITE` ni `BILLING_WRITE` (`TntRole.java:191-209`) et `FREELANCER` n'a que `ACTOR_READ` (`TntRole.java:122-141`). | La délégation synchrone exige d'ajouter des permissions génériques (`rating:create`, `loyalty:read`) aux rôles CLIENT/FREELANCER dans `TntRole`, et de les tester via `TntProfileRolePermissionsTest` (règle de la mémoire « source des permissions Core »). | Sans cela, les appels en processus échouent en 403 pour les utilisateurs finaux. |
| H12 | Le `tenant` : toutes les tables gofp sont majoritairement sans `tenant_id` ; le tenant du core vient du JWT `tid` ; `TenantContextHolder.SYSTEM_TENANT = SystemTenant.id()` (`TenantContextHolder.java:30`). On suppose qu'un appel Go-Freelancer utilise le tenant courant du contexte (`tenantContextHolder.currentTenantId()`, comme `AnnouncementApplicationService.java:240`). | Pas de colonne tenant à ajouter côté Go. | Si le JWT n'a pas de `tid`, les appels core échouent : déjà le comportement actuel. |

---

## 1. Constats de départ (ce qui est déjà délégué, ce qui est dupliqué)

Déjà délégué au core (à ne pas refaire) :

- Cycle de vie de la livraison : `IDeliveryLifecyclePort` (`findState`, `confirmPickup`, `startOrResumeTransit`, `depositAtRelayPoint`, `complete`, `fail`, `cancel`) implémenté par `DeliveryLifecyclePortAdapter` qui appelle `DeliveryLifecycleUseCase` / `DeliveryQueryUseCase` (delivery-core). L'état est recopié sur la ligne gofp par `CoreDeliveryProjection` (`status`, `actualPickupTime`, `actualDeliveryTime`) et par le projecteur Kafka `CoreDeliveryStatusProjector` (groupe `tnt-gofp-delivery-projection`, ignore les états terminaux et les livraisons inconnues).
- Annonces : `IDeliveryAnnouncementPort` (ADR-021) via `DeliveryAnnouncementPortAdapter` : publication, réponse, sélection.
- Tarification : billing-pricing (`FreelancerGridPolicyMapper`, `FreelancerPricingPolicyApplicationService`).
- Règlement : `ISettlementUseCase` / `billing.wallet_settlements`.
- Push et e-mail : tnt-notify-core (`GofpNotifyEmailAdapter`, `GofpNotifyPushAdapter`, `sendLocalized`).
- Stock du relais et occupation : inventory-core (`DepositHubPackageUseCase`, `PickupHubPackageUseCase`) et geo-core (`IFindNearbyHubsUseCase.updateHubOccupancy`).

Ce qui reste dupliqué, sous forme de « miroirs » de persistance :

- `announcements` (miroir « soft » du core : `AnnouncementApplicationService.softMirrorLocal`, ligne 437) et `deliveries` (créé avec le même UUID que la livraison core : `softMirrorDeliveryAndInitOtp`, ligne 517).
- `delivery_needs`, `packets` (table SQL héritée, sans entité Java, accédée en SQL brut dans `DeliveryNeedApplicationService.persistPacketIfPresent`, ligne 343).
- `gofp_users` / `gofp_clients` / `gofp_freelancers` / `gofp_relay_points`.
- `evaluations`, `gofreelancer_vehicles`, `payments` (entité inutilisée), `notifications*`, `addresses` / `person_addresses`, `relay_deposits`, `delivery_person_pricing` (miroir), `announcement_subscriptions`.

Une constatation structurante : les identifiants d'annonce et de livraison sont DÉJÀ ceux du core (même UUID). Le mappage d'id y est trivial. Les vraies ruptures d'id sont : `gofp_users.id`, `gofp_clients.id`, `gofp_relay_points.id`, la confusion `packetId` / `needId`, et `deliveries.freelancer_id` / `announcement_subscriptions.delivery_person_id` (ids de profil).

---

## 2. Principes d'architecture cible

1. Le core fait foi pour l'état, l'identité, le colis, l'annonce, le règlement. Go-Freelancer garde les règles de produit et les données qui n'existent nulle part ailleurs (section 3.18).
2. Délégation synchrone derrière le contrôleur : routes et DTO Go conservés par défaut, les services appellent les use cases du core dans la même requête. Une rupture d'API n'est admise que si elle est logique et nécessaire, et elle est documentée avant/après (section 8).
3. Ids du core partout, sans période de transition. Les données existantes sont migrées par de NOUVEAUX changesets Liquibase numérotés ; un changeset publié n'est jamais modifié.
4. Les manques des couches basses sont comblés par AJOUT, en nom générique (aucun « gofp » dans un nom de colonne, de classe ou de route du core).
5. Respect des permissions : tout appel en processus d'un use case annoté `@RequirePermission` passe par l'aspect (`TntPermissionAspect.java:36`). Ajouter les permissions manquantes aux rôles du core (H11).
6. Règles git (mémoire du projet et CLAUDE.md) : une seule branche par développeur dans son worktree, commits en anglais SANS trailer co-auteur, jamais de push (donner les commandes à l'utilisateur).

---

## 3. Tableaux par concept dupliqué

Légende des lignes : (a) classe/table Go ; (b) équivalent core exact ; (c) champs à AJOUTER au module bas ; (d) ce qui reste côté Go ; (e) mappage d'id et migration ; (f) impact API ; (g) risques ; (h) tests.

### 3.1 Cycle de vie de la livraison (lot L1)

| | |
|---|---|
| (a) Go | Entité `GOFP/domain/model/Delivery.java` (135 lignes, table `deliveries`) : id, announcement_id, freelancer_id, `status`, delivery_need_id, tarif, note_livreur, fenêtres pickup/delivery min/max, delivery_note, hashes OTP, codes chiffrés (C-53), `actual_pickup_time`, `actual_delivery_time`, driver_user_id, receive_code(+expiry), `duration`. `CoreDeliveryProjection` (47 lignes) recopie statut + heures réelles depuis le core. `CoreDeliveryStatusProjector` (61 lignes) + consommateur Kafka `CoreMissionStatusProjectionConsumer` (topic `tnt.delivery.mission.status.changed`). `DeliveryStatusApplicationService` (784 lignes lues) mêle règles produit et orchestration. |
| (b) Core | Agrégat `DLV/domain/model/aggregate/Delivery.java` (`Delivery.create` génère son propre id aléatoire, ligne 221), table `tnt_deliveries` (colonnes `status`, `urgency`, `tracking_code`, `delivery_person_id`, `actual_*`, `estimated_cost_*`, `final_cost_total`, `platform`, `agency_id`, `parcel_id`…), politique `DeliveryStateTransitionPolicy` (CREATED→PICKED_UP|CANCELLED|PAUSED ; PICKED_UP→IN_TRANSIT|FAILED|CANCELLED|PAUSED ; IN_TRANSIT→DELIVERED|FAILED|AT_RELAY_POINT|PAUSED ; AT_RELAY_POINT→IN_TRANSIT|DELIVERED|FAILED|PAUSED ; l.56 pour AT_RELAY_POINT). Use cases : `DeliveryLifecycleUseCase` (confirmPickup, startTransit, depositAtRelayPoint, resumeFromRelayPoint, completeDelivery, failDelivery, cancelDelivery, assignToFreelancerOrg), `DeliveryQueryUseCase` (findDeliveryById, findDeliveriesByDeliveryPerson, findDeliveriesByStatus, resolveActorIdForDeliveryPerson). Routes core `/api/v1/tenants/{tenantId}/deliveries/...` (`DeliveryController.java:46`, dépôt relais l.143, assign-freelancer l.243). |
| (c) À ajouter | Au core, `tnt_deliveries` / `Delivery` : `agreed_amount NUMERIC(14,2)` + `agreed_currency VARCHAR(3)` (remplace `tarif`), `pickup_window_start/end`, `delivery_window_start/end TIMESTAMPTZ` (remplacent les 4 colonnes min/max), `source_announcement_id UUID` (annonce d'origine, générique). Le core a déjà l'ETA (`estimatedDeliveryTime`) : ne pas dupliquer. |
| (d) Reste Go | Vérification OTP à la prise en charge et à la remise (`OtpService`, `DeliveryOtpService`), codes de remise (`pickup_code_enc`, `delivery_code_enc`, `receive_code*`), `note_livreur`, `delivery_note`, preuves photo (`gofp_delivery_proofs` : delivery_id, kind, file_id, note, captured_at, uploaded_by), assistance, ancrage de confiance (`RecordCustodyTransferUseCase`, `RecordMissionUseCase`, `RecordPaymentUseCase`), règlement (`ISettlementUseCase`), gardes d'accès (`assertCallerIsAssignedDeliveryPerson`, `assertCallerCanCancelDelivery`, `assertCallerCanReadDelivery`, `assertCallerCanUpdateDelivery`, `assertParty`). La ligne `deliveries` ne devient qu'une EXTENSION produit : id (= id core), codes/OTP, notes. |
| (e) Id + migration | Id déjà égal à l'id core (même UUID). Plan : (1) ajout des colonnes core par changesets `gdd-l1-1xx` côté delivery-core ; (2) backfill des fenêtres et du montant core depuis `deliveries` pour les lignes dont l'id existe dans `tnt_deliveries` ; (3) les lectures passent par `DeliveryQueryUseCase` et un assembleur ; (4) suppression des colonnes `status`, `actual_*`, `freelancer_id`, `tarif`, fenêtres seulement en CLÔTURE (H7). Lignes gofp sans contrepartie core (orphelines) : les lister dans le rapport de migration et les marquer ; ne pas les supprimer sans accord. |
| (f) API | Routes et DTO `/api/v1/deliveries/...` (`GofpDeliveryController`) CONSERVÉS : `/{id}`, `/announcement/{id}`, `/freelancer/{id}`, `/status/{status}`, `PUT /{id}`, `PATCH /{id}/status`, `PATCH /{id}/cancel`, `/delivery-need/{id}`, `/tracking/*`, `/{id}/assistance`. Classe : aucune (statut sérialisé par `getValue()` du core, ajouté au lot 1). `DeliveryUrgency` : voir 3.5. Le `PATCH /{id}/status` vers FAILED/CANCELLED continue d'appeler `fail`/`cancel` du core (`DeliveryStatusApplicationService.java:126` pour le point d'entrée). |
| (g) Risques | Le service de 784 lignes est la zone la plus chargée : ne pas le réécrire d'un bloc ; découper en (i) orchestrateur produit mince, (ii) gardes, (iii) règlement. Le projecteur Kafka ne doit plus écrire dans gofp après suppression des colonnes (retirer `CoreDeliveryProjection`) ; ordre : lecteurs d'abord, puis projecteur, puis colonnes. Le placeholder `UNKNOWN_DRIVER` et `ensureInTransit` (PICKED_UP → IN_TRANSIT avant DELIVERED/AT_RELAY_POINT) compensent la politique du core : conserver tant que la politique n'autorise pas PICKED_UP→DELIVERED. Cas de reprise FAILED→IN_TRANSIT à ne pas perdre. |
| (h) Tests | Étendre `DeliveryStatusLifecycleConvergenceTest`, `CoreDeliveryStatusProjectorTest`, `DeliveryCoreConvergenceIntegrationTest` ; ajouter `Gdd1DeliveryMigrationIntegrationTest` (Testcontainers PostGIS, modèle `GoNeedAnnouncementMigrationIntegrationTest`) ; IT : parcours complet CREATED→PICKED_UP→IN_TRANSIT→DELIVERED avec OTP et règlement ; IT : AT_RELAY_POINT puis reprise ; test d'archi : plus aucune lecture de `deliveries.status` hors migration. |

### 3.2 Colis : Parcel du core (lot L1)

| | |
|---|---|
| (a) Go | Table héritée `packets` (title, designation, description, weight, height, width, length, thickness, is_fragile, is_perishable, photo_url), SANS entité Java, accédée en SQL brut dans `DeliveryNeedApplicationService` (`persistPacketIfPresent` l.343, `loadPacketMetrics` l.850, `loadPacket` ~l.968). FK : `delivery_needs.packet_id` → `packets` (ON DELETE SET NULL) ; `relay_deposits.packet_id` → `packets` (ON DELETE CASCADE) ; `announcements.packet_id` existe mais le miroir ne l'alimente jamais. `PacketProof` (`gofreelancer_packet_proofs` : `core_packet_id`, `cover_image_url`). |
| (b) Core | `DLV/domain/model/aggregate/Parcel` (id, `PackageSpecification(weightKg, widthCm, heightCm, lengthCm, fragile, perishable, description)`, `photoUrl`, version), table `tnt_parcels` (weight_kg, width_cm, height_cm, length_cm, fragile, perishable, description, photo_url, created_at, updated_at, version). L'annonce core crée son Parcel (`DeliveryAnnouncementService.java:56`, `Parcel.create(cmd.packageSpec())`) et la livraison directe aussi (l.144). |
| (c) À ajouter | `tnt_parcels` / `PackageSpecification` : `designation VARCHAR(255)` (ex. « Carton électroménager »), `thickness_cm DOUBLE PRECISION`. Le titre Go (`title`, défaut « Colis ») n'a pas d'équivalent générique : il est repris dans `designation`. |
| (d) Reste Go | Rien de persistant : plus de table `packets`. Les DTO `PacketDTO` restent dans l'API. |
| (e) Id + migration | `packets.id` → `tnt_parcels.id` (même UUID, on conserve l'id). Changeset L1 : (1) `ALTER TABLE tnt_parcels ADD COLUMN designation, thickness_cm` ; (2) `INSERT INTO tnt_parcels (id, weight_kg, …, designation, thickness_cm, …) SELECT id, weight, … FROM packets ON CONFLICT (id) DO NOTHING` ; (3) retrait des FK vers `packets` (`delivery_needs.packet_id`, `relay_deposits.packet_id`) ; (4) en cas de `relay_deposits.packet_id` contenant un `needId` (confusion constatée, voir ci-dessous), le convertir en `parcel_id` via `delivery_needs.packet_id`. Confusion constatée : dans l'appel de dépôt, `trackingCode = packetId.toString()` et `packageId = packetId`, et `packetId` reçoit un `needId` (ou `deliveryId`) (`DeliveryStatusApplicationService.java:279`, `RelayDepositService.java:104`, `RelayDepositService.java:190` qui fait `findByDeliveryNeedId(deposit.getPacketId())`). Cible : `parcelId` = id Parcel du core ; le `HubPackageEntry.packageId` (`inventory .../HubPackageEntry.java:24`) reçoit l'id Parcel. |
| (f) API | `PacketDTO` conservé en entrée/sortie. Aucun changement de route. Classe : aucune. |
| (g) Risques | Poids/dimensions : types Go `Double` ; le core est en `weightKg`/`cm` (unités à confirmer champ par champ : non vérifié). Paquets orphelins (pas de besoin ni de dépôt) : les migrer quand même (id conservé). `ON DELETE CASCADE` sur `relay_deposits` disparaît : prévoir la suppression explicite. Entrées d'inventaire déjà créées avec `packageId = needId` : à corriger par UPDATE dans `tnt_hub_package_entries` (hors de ce module : faire l'UPDATE dans un changeset du lot L4 côté inventory-core, avec un numéro réservé). |
| (h) Tests | `ParcelMigrationIntegrationTest` (packets → tnt_parcels, ids conservés, FK retirées) ; test de bout en bout besoin avec colis → annonce → livraison → dépôt relais : même id Parcel à chaque étape ; test unitaire de mappage `PacketDTO`↔`PackageSpecification` (désignation, épaisseur). |

### 3.3 Annonce (lot L2)

| | |
|---|---|
| (a) Go | `GOFP/domain/model/Announcement.java`, table `announcements` : client_id, packet_id, pickup_address_id / delivery_address_id (`@NotNull`), title, description, status, duration, distance, transport_method, required_vehicle_type, assigned_freelancer_id, delivery_need_id, destination_relay_point_id, shipper_* / recipient_* (prénom, nom, e-mail, téléphone), amount, currency, logistics_price, payment_method, signature_url, horodatages. Documentée comme « soft mirror ». Écrit par `softMirrorLocal` (`AnnouncementApplicationService.java:437`), relu par `enrichFromLocalMirror` (~l.701). |
| (b) Core | `DLV/domain/model/aggregate/DeliveryAnnouncement.java` (champs l.37-66 : id, tenantId, clientId, title, description, offeredAmount, currency, pricingMode, parcel, pickupAddress, deliveryAddress, recipient, urgency, status, responses, selectedResponseId, createdDeliveryId), table `tnt_delivery_announcements` (+ `tnt_announcement_responses`). `RecipientInfo(name, phoneNumber, alternativePhone)` (`RecipientInfo.java:8-11`). Port : `DeliveryAnnouncementUseCase` (publish, respond, selectResponse, cancel), `DeliveryQueryUseCase.findAnnouncement*`, `findOpenAnnouncementsNear`. Routes `/api/v1/tenants/{tenantId}/delivery-announcements` (`DeliveryAnnouncementController.java:31`). |
| (c) À ajouter | Sur `DeliveryAnnouncement` / `tnt_delivery_announcements` (noms génériques) : `required_vehicle_type VARCHAR(40)`, `payment_method VARCHAR(30)`, `transport_method VARCHAR(30)`, `distance_km NUMERIC(10,3)`, `estimated_duration_min INT`, `destination_hub_id UUID` (relais de destination), `sender_name`, `sender_phone`, `sender_email`, `recipient_email VARCHAR(255)`, `handover_signature_ref VARCHAR(500)` (signature, remplace `signature_url`), `service_fee NUMERIC(14,2)` (remplace `logistics_price`), `origin_ref_type VARCHAR(30)` + `origin_ref_id UUID` (demande d'origine, remplace `delivery_need_id`). Prénom/nom séparés Go : repliés dans `RecipientInfo.name` et dans `sender_name`. |
| (d) Reste Go | Messages de négociation (`gofp_negotiation_messages`), invitations (`gofp_need_invitations`), favoris. La ligne `announcements` locale disparaît. |
| (e) Id + migration | Id déjà égal. Changesets L2 : ajout des colonnes au core ; backfill `UPDATE tnt_delivery_announcements a SET required_vehicle_type = l.required_vehicle_type, … FROM announcements l WHERE l.id = a.id` ; les lignes sans contrepartie core sont comptées (rapport). Retrait de `NOT NULL` sur `announcements.pickup_address_id` / `delivery_address_id` d'abord (si le miroir n'est plus écrit), puis suppression de la table en CLÔTURE. `enrichFromLocalMirror` est remplacé par la lecture des colonnes core. |
| (f) API | DTO et routes `AnnouncementController` conservés. Aucun changement requis. Classe : aucune (addition : `urgency` optionnel en entrée, voir 3.5). |
| (g) Risques | `DeliveryAnnouncementPortAdapter` (283 lignes) traduit `PublishAnnouncementPortCommand` vers le core ; le mode de prix accepte FIXED/GO et QUOTE/FREELANCER. Ne pas casser l'invariant domaine : Go = FIXED_PRICE, Freelancer = QUOTE_REQUEST. `CreateDeliveryAnnouncementCommand(tenantId, clientId, title, description, offeredAmount, currency, pricingMode, packageSpec, pickupAddress, deliveryAddress, recipient, urgency)` : toute nouvelle donnée devient un champ optionnel (compat). Le lot L2 dépend de L1 pour `PackageSpecification` (designation/thickness). |
| (h) Tests | `AnnouncementMirrorRemovalTest` (aucun accès à la table locale hors migration) ; IT publication → réponse → sélection → livraison avec tous les champs ajoutés relus ; `Gdd2AnnouncementMigrationIntegrationTest` ; contrat `DeliveryAnnouncementPortAdapter` (champs ajoutés aller-retour). |

### 3.4 Besoin de livraison `DeliveryNeed` (lot L2)

| | |
|---|---|
| (a) Go | `delivery_needs` : user_id (= Kernel actorId), packet_id, target_relay_point_id, requested_storage_days, pickup/delivery_address_id, title, description, `status` (`DeliveryNeedStatus` PENDING, ACCEPTED, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED), duration, signature_url, payment_method, transport_method, required_vehicle_type, distance, delivery_id, assigned_freelancer_id, pickup_deadline. `DeliveryNeedApplicationService` (1025 lignes). `NeedAnnouncementService.publishFor` (`NeedAnnouncementService.java:59`) le publie comme annonce QUOTE_REQUEST. |
| (b) Core | Une annonce `QUOTE_REQUEST` du core EST le besoin (publié, réponses, sélection). Le statut du besoin se déduit de `AnnouncementStatus` + `DeliveryStatus` de la livraison créée. Aucune entité « besoin » côté core. |
| (c) À ajouter | `origin_ref_*` (voir 3.3) pour relier annonce et besoin ; `pickup_deadline` et `requested_storage_days` sont propres au produit (voir (d)). |
| (d) Reste Go | La ligne `delivery_needs` reste comme DONNÉE PRODUIT (délai de prise en charge, relais cible, jours de stockage, `title`), rattachée à l'id core de l'annonce. Elle perd : `status` (dérivé), `packet_id` (devient `parcel_id` = id Parcel du core), `delivery_id`, `assigned_freelancer_id` (dérivés de l'annonce/livraison core). Usages actuels de `DeliveryNeedStatus` à remplacer : `RelayPointDirectoryService.java:62`, `DeliveryNeedApplicationService.java:289,634`, `NeedAnnouncementService.java:98`, `NeedInvitationService.java:73,134`. |
| (e) Id + migration | `user_id` = actorId déjà. Ajout `parcel_id` (reprise de `packet_id`, même UUID). Renommage logique sans rupture : on garde `delivery_needs.id` ; on ajoute `announcement_id` (si la publication a déjà eu lieu : il vient de `announcements.delivery_need_id`). Le statut dérivé est calculé par un assembleur ; les valeurs stockées sont ignorées puis supprimées en clôture. `ensureLegacyUser` (`DeliveryNeedApplicationService.java:393`) insère dans la table héritée `users` : à supprimer avec la table. |
| (f) API | Routes et DTO du besoin conservés. `status` renvoyé : même vocabulaire (valeurs calculées). Classe : aucune. |
| (g) Risques | Calcul de statut dérivé plus coûteux (jointure sur le core) : rester en lecture groupée. Cas ACCEPTED vs ASSIGNED : définir la table de correspondance AnnouncementStatus/DeliveryStatus → DeliveryNeedStatus et la figer par test. Besoins sans annonce (non publiés) : restent PENDING. |
| (h) Tests | Table de correspondance de statut (test paramétré) ; IT besoin → annonce → sélection → livraison ; `Gdd2NeedMigrationIntegrationTest` (packet_id → parcel_id, user_id inchangé). |

### 3.5 `DeliveryUrgency` (lot L1 pour l'enum et les DTO, lot L2 pour l'adaptateur)

| | |
|---|---|
| (a) Go | Enum Go `DeliveryUrgency` : NORMAL, EXPRESS, STANDARD. Utilisé seulement dans `DeliveryUpdateDTO:16` et `DeliveryResponseDTO:23` et l'adaptateur ; jamais persisté. |
| (b) Core | `DLV/domain/model/enums/DeliveryUrgency` : STANDARD, EXPRESS, SAME_DAY (l.17), SCHEDULED ; colonne `urgency` de `tnt_deliveries` et `tnt_delivery_announcements`. `DeliveryAnnouncementPortAdapter` utilise toujours `STANDARD` (ligne 149). |
| (c) À ajouter | Rien. |
| (d) Reste Go | Rien. |
| (e) Migration | Pas de données. Mappage d'entrée : `NORMAL` → `STANDARD` (alias toléré à la désérialisation). L2 ajoute `urgency` optionnel à `AnnouncementRequestDTO` et le transmet à la commande core au lieu de la constante. |
| (f) API | Rupture justifiée (valeur de sortie `NORMAL` remplacée par `STANDARD`) ; addition (nouvelles valeurs `SAME_DAY`, `SCHEDULED` acceptées). Avant : `{"urgency":"NORMAL"}` ; après : `{"urgency":"STANDARD"}`. |
| (g) Risques | Un client mobile qui compare littéralement à `NORMAL`. Vérifier l'import de l'enum dans `DeliveryAnnouncementPortAdapter` (core ou Go : non vérifié) avant de supprimer l'enum Go. |
| (h) Tests | Désérialisation JSON de `NORMAL`/`STANDARD`/`SAME_DAY` ; test d'archi : plus de classe `DeliveryUrgency` dans le module gofp. |

### 3.6 Véhicules et classes (lot L3)

| | |
|---|---|
| (a) Go | `VehicleClass` (STANDARD, DAMAGED, PREMIUM, RESTRICTED) utilisé uniquement dans le DTO orphelin `VehicleCreateRequest` ; `VehicleType` Go (BIKE, MOTORBIKE, CAR, VAN, TRUCK, SCOOTER, TRICYCLE, BUS) ; `VehicleCategory` (moto, tricycle, taxi, camion, bus → type canonique). |
| (b) Core | `LogisticsClass` (delivery-core) = exactement les 4 valeurs de `VehicleClass` ; `LogisticsType` (BIKE, MOTORBIKE, CAR, VAN, TRUCK) ; resource-core `VehicleType` (MOTORCYCLE, BICYCLE, CAR, TRICYCLE, VAN, TRUCK, MOTO, VELO, VOITURE, CAMIONNETTE, VELO_CARGO). Trois enums de types qui se recoupent : à ne pas fusionner ici (risque hors périmètre). |
| (c) À ajouter | `LogisticsType` : `SCOOTER`, `TRICYCLE`, `BUS` (valeurs génériques) pour couvrir le Go ; resource-core `VehicleType` : rien (déjà large) ; une table de correspondance `LogisticsType`↔resource `VehicleType` publiée comme classe utilitaire du core. |
| (d) Reste Go | `VehicleCategory` (libellés produit moto/taxi/bus) comme couche d'affichage seulement. |
| (e) Migration | Pas de colonne persistée pour `VehicleClass`. Pour `gofreelancer_vehicles.vehicle_type` : normaliser vers le vocabulaire core par UPDATE (`MOTORBIKE`→`MOTORCYCLE`… selon la table de correspondance). |
| (f) API | Aucune pour `VehicleClass` (DTO orphelin : suppression de `VehicleCreateRequest` et `VehicleClass`). Pour `vehicleType` : tolérer les anciennes valeurs en entrée ; sortie en valeur core. Classe : justifiée seulement si une valeur sortante change (documenter avant/après). |
| (g) Risques | `required_vehicle_type` de l'annonce porte aujourd'hui un vocabulaire Go : le normaliser dans L2 avec la même table. |
| (h) Tests | Test exhaustif de la table de correspondance (chaque valeur Go → une valeur core) ; test d'archi (aucun enum de véhicule dupliqué). |

### 3.7 Fidélité : `LoyaltyStatus` vs `LoyaltyTier` (lot L3)

| | |
|---|---|
| (a) Go | `LoyaltyStatus` BRONZE/SILVER/GOLD/PLATINUM, calculé dans `GofpClientService.computeLoyalty` (`GofpClientService.java:156-160`) sur `total_orders` : ≥10 SILVER, ≥25 GOLD, ≥50 PLATINUM. Colonnes `gofp_clients.loyalty_status`, `total_orders`. |
| (b) Core | tp-core `LoyaltyTier` (BRONZE 0-499, SILVER 500-1999, GOLD 2000-4999, PLATINUM ≥5000 ; `LoyaltyTier.java:15`), `LoyaltyAccount` (10 points par livraison, `LoyaltyAccount.java:26`, 20 pour une express, 1 point par 5 XAF), table `tnt_loyalty_accounts`, `LoyaltyService.earn` (crée le compte à la volée), routes `/api/v1/tnt-tp/loyalty/{thirdPartyId}` (+ earn, redeem). |
| (c) À ajouter | Au core : `senderId` dans `DeliveryCompletedEvent` (H4) ; permission de lecture pour CLIENT (H11). Option si les seuils Go doivent survivre : politique de seuils paramétrable (H3). |
| (d) Reste Go | Rien (le niveau est lu dans tp-core). |
| (e) Migration | Pour chaque client Go avec `total_orders > 0` : créer/compléter le compte tp-core avec `points = total_orders * 10` (calcul à valider, H3), clé `thirdPartyId = actorId` (H1). Les colonnes `loyalty_status` et `total_orders` sont retirées en clôture. |
| (f) API | Les DTO client exposent `loyaltyStatus` : conservé (valeur lue du palier core). Classe : rupture justifiée pour la valeur (seuils différents), à documenter avant/après avec un exemple chiffré. |
| (g) Risques | Rétrogradation de niveaux ; crédit possiblement inopérant (H4, non vérifié) : sans correction de l'événement, le niveau ne bouge plus après migration. `LoyaltyService` exige `billing:write` (`LoyaltyService.java:45,72`) : permission à ouvrir (H11). |
| (h) Tests | IT Kafka : `DeliveryCompletedEvent` → points crédités → palier ; test de migration (3 clients à 9/10/50 commandes) ; test de permission avec le vrai registre de rôles (`TntProfileRolePermissionsTest`). |

### 3.8 Statuts freelancer / relais (lot L3 pour FreelancerStatus, lot L4 pour RelayPointStatus)

| | |
|---|---|
| (a) Go | `FreelancerStatus` (PENDING, APPROVED, SUSPENDED, REJECTED, REVOKED ; transitions PENDING→APPROVED|REJECTED, APPROVED→SUSPENDED|REVOKED, SUSPENDED→APPROVED|REVOKED) ; `RelayPointStatus` (mêmes 5 valeurs) ; `ClientStatus`/`UserStatus` (ACTIVE, SUSPENDED, REVOKED). |
| (b) Core | `DeliveryPersonStatus` (PENDING, APPROVED, SUSPENDED, REJECTED) dans `DeliveryPerson` (`approve()`, `suspend()` seulement) ; actor-core `ActorStatus` (ACTIVE, INACTIVE, SUSPENDED, BANNED) et `KycStatus` (PENDING, UNDER_REVIEW, VERIFIED, REJECTED, FLAGGED). |
| (c) À ajouter | `DeliveryPersonStatus.REVOKED` ; méthodes `reject()` et `revoke()` avec la même machine à états que le Go ; colonnes inchangées (`status` texte). |
| (d) Reste Go | `ClientStatus`/`UserStatus` : lus depuis `ActorStatus` (ACTIVE/SUSPENDED/BANNED ; REVOKED → BANNED). |
| (e) Migration | `gofp_freelancers.status` → `tnt_delivery_persons.status` par UPDATE sur l'id (invariant H9) ; relais : statut porté par l'opérateur (profil relais). Les enums Go sont supprimés une fois les services branchés. |
| (f) API | Routes d'agrément conservées (`/api/freelancers/{id}`, admin relais). Valeur `REVOKED` toujours émise. Classe : aucune. |
| (g) Risques | `GofpFreelancerProjectionService` crée aujourd'hui le `DeliveryPerson` en APPROVED d'office (TODO métier en attente) : ne pas changer ce comportement dans ce chantier. |
| (h) Tests | Test de la machine à états (toutes transitions licites/illicites) côté core ; IT admin : PENDING→APPROVED→REVOKED reflété dans `tnt_delivery_persons`. |

### 3.9 Évaluations (lot L3)

| | |
|---|---|
| (a) Go | `Evaluation` (`evaluations` : delivery_id, evaluator_id, evaluated_id, rating INT 1-5, comment, type ex. `CLIENT_RATING_DELIVERY_PERSON`, unicité (delivery_id, type) ; FK d'origine vers `persons`/`deliveries`). `EvaluationApplicationService` : `predictRating` (analyse de sentiment), puis `updateGlobalRating` recalcule la moyenne et l'écrit dans `gofp_relay_points`, `gofp_freelancers` ou `gofp_clients` (l.92 pour le relais). Routes `/api/v1/evaluations` (POST, GET `/person/{personId}`). |
| (b) Core | tp-core `ThirdPartyRating` (missionId, score 1.0-5.0, commentaire, unicité mission+évaluateur ; met à jour la moyenne de `TntClientProfile`), table `tnt_tp_ratings`, `RatingService` (`@RequirePermission actor:write`, `RatingService.java:44` ; contrôle d'unicité l.47 ; lecture `actor:read` l.81). actor-core `IRateActorUseCase` / `ActorRatingService.rateActor(RateActorCommand(tenantId, actorId, actorType, score, ratedByActorId))` met à jour `ActorRating` du profil mais ne persiste pas chaque note. |
| (c) À ajouter | tp-core : colonne `rating_context VARCHAR(40)` (type d'évaluation) sur `tnt_tp_ratings` ; `subject_kind VARCHAR(20)` (CLIENT/DELIVERY_PERSON/RELAY_OPERATOR) pour couvrir la cible relais ; entier toléré (score DOUBLE accepte 1-5). Noms exacts des colonnes de la cible (évalué) : non vérifié. |
| (d) Reste Go | `predictRating` (analyse de sentiment du commentaire) : calcule la note, puis délègue. |
| (e) Migration | `evaluations` → `tnt_tp_ratings` (id conservé, `mission_id = delivery_id`, évaluateur/évalué convertis en `actorId` : via `gofp_clients.core_user_id`, `gofp_freelancers.core_user_id`, opérateur du relais). Les moyennes stockées sur gofp (`rating`) sont retirées en clôture ; la moyenne lue vient de `ActorRating` / `averageRating`. |
| (f) API | `/api/v1/evaluations` conservé (POST, GET). `rating` renvoyé en entier : arrondi de la valeur core, ou décimal si le DTO l'accepte. Classe : addition (champ décimal optionnel). |
| (g) Risques | Permissions (H11). Doublons : contrainte d'unicité core (mission+évaluateur) plus stricte que (delivery_id, type) : détecter les conflits à la migration. Évalué = relais : sans `subject_kind` la note se perd. |
| (h) Tests | IT : note client→livreur persistée, moyenne mise à jour, unicité ; test de migration avec doublons ; test permission CLIENT/FREELANCER avec le vrai registre. |

### 3.10 Profils : utilisateur, client, freelancer (lot L3)

| | |
|---|---|
| (a) Go | `gofp_users` (id aléatoire, `core_user_id` unique = userId Kernel, noms, e-mail unique (placeholder `user+<id>@gofp.provisioned`), téléphone, cni_number, nui, `password_hash`, profile_photo_url, status, is_active, role, address_id) ; `gofp_clients` (core_client_id, core_user_id, status, loyalty_status, total_orders, rating, default_delivery_address_id) ; `gofp_freelancers` (core_freelancer_id, core_user_id, commercial_name/register, taxpayer_number, siret, status, is_active, rating, total/failed/remaining_deliveries, commission_rate, subscription_id, lat/lng, availability_status AVAILABLE/PAUSED, residence_address_id, operational_base_address_id). `GofpUserProvisioningService` crée le placeholder ; `GofpFreelancerProjectionService` (128 lignes). |
| (b) Core | actor-core (schéma `tnt_actor`) : `client_profiles`, `freelancer_profiles`, `relay_operator_profiles`, `deliverer_profiles` ; `TntActorProfile` (id, tenantId, actorId, actorType, actorStatus, kycStatus, currentLocation, rating, badges, preferredLanguage, photoId) ; use cases `ICreateClientProfileUseCase`, `IFindClientUseCase` (findByActorId, findById, findMine), `IFindFreelancerUseCase`, `IFindMyProfilesUseCase`, `IResolveActorIdentityUseCase.resolve/resolveCurrent` (→ `ActorIdentitySummary` : displayName, phone, email, prénom, nom, photoId), `IManageProfilePhotoUseCase`. delivery-core `DeliveryPerson` (`tnt_delivery_persons`, `DeliveryPerson.projection` l.110 : quota initial 100). tp-core `TntClientProfile` : non utilisé hors tp-core (grep). |
| (c) À ajouter | actor-core `FreelancerProfile` : `commercial_name`, `commercial_register`, `taxpayer_number`, `registration_number` (SIRET générique), `availability_mode VARCHAR(20)` (AVAILABLE/PAUSED), coordonnées de base opérationnelle via `currentLocation` existant. `ClientProfile` : `default_address_id UUID`. `UpdateActorIdentityCommand` couvre déjà prénom/nom/téléphone ; ajouter `national_id` (CNI) et `tax_id` (NUI) génériques (Kernel : non vérifié ; si le Kernel les porte, passer par lui). `DeliveryPerson` : `commission_rate` retiré (voir paiements). |
| (d) Reste Go | Abonnement (`subscription_id`, `SubscriptionType`), quota consommé (`remaining/failed/total_deliveries` : déjà dans `DeliveryPerson`, donc retiré), position GPS (portée par `currentLocation`/`updateLocation` du core). La ligne `gofp_freelancers` ne garde que l'abonnement et les références d'adresses du produit. |
| (e) Id + migration | Cible : `actorId` partout. `gofp_users.id` → `core_user_id` (id aléatoire à remplacer). Pour toute table référençant `gofp_users.id` : réutiliser le motif générique de `GOFP-SQL/20261114_relocate_guest_person.sql` (suppression temporaire des FK, UPDATE de l'id, recréation, idempotent). `gofp_clients.id` → id de `client_profiles` ; `gofp_freelancers.id` conservé (déjà = core_freelancer_id pour les lignes projetées, `GofpFreelancerProjectionService.java:31`) ; les lignes divergentes (H9) sont réécrites. Diagnostic préalable : `SELECT count(*) FROM gofp_freelancers WHERE id <> core_freelancer_id`. Les e-mails placeholder `user+<id>@gofp.provisioned` ne sont pas migrés : l'identité vient du Kernel (`IResolveActorIdentityUseCase`). |
| (f) API | Routes `/api/users`, `/api/clients`, `/api/v1/gofp/clients|users`, `/api/freelancers/{id}` conservées (les routes `/freelancers/{id}` utilisent l'id de profil, ne pas le changer en actorId). `AuthController` inchangé. Routes locales de mot de passe : suppression si H8 confirmée (rupture justifiée). |
| (g) Risques | Ids réécrits dans de nombreuses tables (liste complète des FK : non vérifiée, la dériver de `pg_constraint` à la migration) ; auto-approbation par `projection` ; `@RequirePermission` sur les use cases profil ; unicité d'e-mail ; photo : 100 % Kernel (déjà en place, ne pas réintroduire `profile_photo_url`). |
| (h) Tests | `Gdd3ProfileMigrationIntegrationTest` (ids hétérogènes → ids cohérents, FK intactes) ; IT création de compte → profils client/freelancer → lecture ; test de non-régression du double profil par compte (Go + Freelancer) ; MockWebServer pour le Kernel (identité). |

### 3.11 Véhicules du freelancer (lot L3)

| | |
|---|---|
| (a) Go | `FreelancerVehicle` (`gofreelancer_vehicles` : freelancer_id, `core_vehicle_id`, front/back_photo_url, color_hex, trunk_length/width/height/unit, vehicle_type, plate_number, photo_urls (séparées par retour à la ligne)). `FreelancerVehicleApplicationService` (92 lignes), `VehicleRegistrationAdapter` (POST HTTP `${tnt.resource-core.base-url}/api/v1/vehicles`, `VehicleRegistrationAdapter.java:58` ; retombe sur un UUID aléatoire, l.66), `CourierVehicleService` (218 lignes), `CourierVehicleController` (`/api/v1/gofp/freelancer-profiles/me/vehicle`, `/photos`), `CourierCardService` (160 lignes). |
| (b) Core | resource-core `FreelancerVehicle` (`tnt_freelancer_vehicles` : vehicleId, ownerOrgId, type, brand, model, plateNumber, maxCapacityKg, volumeM3, fuelType, docs, active, currentMissionId ; plafond de 3 par organisation, `FreelancerVehicle.java:85`) ; use cases `AddFreelancerVehicleUseCase` (commande `AddFreelancerVehicleCommand(ownerOrgId, type, brand, model, plateNumber, maxCapacityKg, volumeM3, fuelType, fuelConsumptionLPer100km, registrationDocRef, insuranceDocRef)`), `ListFreelancerFleetUseCase`, `AssignFreelancerVehicleToMissionUseCase`, `DeactivateFreelancerVehicleUseCase`. Routes réelles : `/api/resources/freelancer-orgs/{orgId}/fleet/vehicles` (`FreelancerFleetController.java:26,42`) et `/api/resources/vehicles` (`VehicleController.java:55`). La route appelée par l'adaptateur Go (`/api/v1/vehicles`) ne correspond à aucune de ces routes lues : non vérifié, probablement mort. |
| (c) À ajouter | resource-core : propriétaire individuel `owner_actor_id UUID` (nullable, alternatif à `freelancer_org_id`), `photo_refs TEXT[]` (≤ 4), `color_hex VARCHAR(7)`, `cargo_length_cm/width/height NUMERIC`, `cargo_dimension_unit VARCHAR(5)`, `category VARCHAR(30)`. |
| (d) Reste Go | `CourierCardService` (carte livreur : composition produit à partir des données core). |
| (e) Id + migration | Cible : `gofreelancer_vehicles.id` = `tnt_freelancer_vehicles.id`. Pour chaque ligne : si `core_vehicle_id` existe dans `tnt_freelancer_vehicles` → conserver ; sinon (H10) créer la ligne core avec l'id Go, la plaque et les attributs. INSERT avec `ON CONFLICT (plate_number)` (contrainte d'unicité core). Table Go supprimée en clôture. |
| (f) API | Routes `/me/vehicle` conservées. Aucune. |
| (g) Risques | Unicité de plaque ; plafond de 3 ; véhicule sans plaque ; `FreelancerVehicleEventConsumer` (delivery-core) sur `tnt.vehicle.assigned_to_mission` : garder les événements inchangés. |
| (h) Tests | IT : ajout via use case réel (plus de repli UUID aléatoire : échec explicite) ; migration avec `core_vehicle_id` fictif ; test d'API contre `/api/resources/...` (corriger l'URL de l'adaptateur ou supprimer l'adaptateur). |

### 3.12 Paiements (lot L4)

| | |
|---|---|
| (a) Go | `Payment` (`payments` : delivery_id, amount, currency, payment_method, status, transaction_reference, paid_at, commission_amount, net_amount) : entité jamais utilisée par un service (seul `PaymentRepository` dans l'adaptateur de persistance). Enums `PaymentMethod` (MOBILE_MONEY, ORANGE_MONEY, CASH), `PaymentStatus` (PENDING, PAID, FAILED, REFUNDED). Flux réel : `DeliveryPaymentApplicationService` (316 lignes ; `REFERENCE_TYPE="DELIVERY"`, `CURRENCY="XAF"`, `COMMISSION_RATE=0.05` à la ligne 46) et `DeliveryPaymentController` (`/api/v1/deliveries/{deliveryId}/payment` : `/receive-code`, `/challenge`, `/status`). |
| (b) Core | billing `WalletSettlement` (`billing.wallet_settlements`, unicité (tenant_id, reference_type, reference_id)) via `ISettlementUseCase.record(RecordSettlementCommand(tenantId, referenceType, referenceId, payerUserId, payeeUserId, amount, currency, method, status, kernelReference, commissionRate))`. Enums `SettlementMethod` (WALLET_TRANSFER, CASH, EXTERNAL_PROVIDER), `SettlementStatus` (PAID, CASH_COLLECTED, UNPAID), `CommissionStatus` (NONE, DUE, SETTLED, WAIVED). `reference_type` : DELIVERY | RELAY_DEPOSIT | MARKET_ORDER. |
| (c) À ajouter | Rien d'obligatoire. Option liée à H5 : lecture du taux depuis la politique de facturation. |
| (d) Reste Go | Défi/code de réception (`/receive-code`, `/challenge`), orchestration « livré mais impayé » (`DeliveredButUnpaidTest`). `PaymentMethod` Go : cartographie vers `SettlementMethod` à l'entrée. |
| (e) Migration | `payments` est vide ou inutilisée (à vérifier par comptage : non vérifié). Si des lignes existent : INSERT dans `wallet_settlements` avec `reference_type='DELIVERY'`, `reference_id=delivery_id`, `ON CONFLICT DO NOTHING`. Puis suppression de l'entité, du repository et de la table en clôture. |
| (f) API | Aucune. |
| (g) Risques | Taux de commission non uniforme (H5). Double règlement : la contrainte d'unicité core protège ; garder l'idempotence testée. |
| (h) Tests | Étendre `DeliveryPaymentFlowIntegrationTest` et `DeliveredButUnpaidTest` ; test de suppression (aucune référence à `PaymentRepository`) ; IT CASH → `CASH_COLLECTED`, hors CASH → `WALLET_TRANSFER` UNPAID. |

### 3.13 Notifications locales et SSE (lot L4)

| | |
|---|---|
| (a) Go | Tables `notifications` (person_id, type, title, message, status), `notification_annonces` (announcement_id), `notification_livraisons` (delivery_id) ; `NotificationService` (132 lignes) écrit la ligne de fil (`NotificationService.java:69,123`) puis envoie e-mail + push via notify-core et un événement Kafka `MatchingNotificationEvent`. Les dépôts annonce/livraison n'ont que leurs adaptateurs (mort probable). SSE : `NotificationStreamController` (`GET /api/notifications/stream/{freelancerId}`, `NotificationStreamController.java:44`, battement 15 s, `NotificationStreamAccessGuard`) au-dessus de sinks Reactor en mémoire (`NotificationStreamAdapter.java:22`, `ConcurrentHashMap`), alimenté par `MatchingNotificationConsumer`. Ancienne classe `NotificationStreamService`. Enums `NotificationType` (6), `NotificationStatus`. |
| (b) Core | tnt-notify-core : `ISendNotificationUseCase.send(tenantId, orgId, recipientId, targetDestination, NotificationModel, NotificationChannel)`, `ISearchNotificationsUseCase`, table `tnt_notifications` (destinataire_id, canal, contenu, statut, priorité), routes `/api/v1/notifications` (`recipient/{id}`, `pending`, `by-status`, préférences, modèles, rappels). `NotificationType` (MISSION_*, INCIDENT_*, INVOICE/PAYMENT…). realtime-core : `IBroadcastNotificationUseCase.broadcastToUser` (WebSocket/Redis) et `SseController` `/api/v1/realtime/sse` (`/tracking/{code}`, `/mission/{id}`, `/fleet/{orgId}`, `/tile/{geohash}`) : PAS de flux SSE de notifications par utilisateur. |
| (c) À ajouter | realtime-core : flux SSE par utilisateur (`GET /api/v1/realtime/sse/notifications`, abonné au destinataire du JWT) alimenté par notify-core (`broadcastToUser`). notify-core : types de notification Go manquants (nom générique, ex. `ANNOUNCEMENT_MATCHED`, `RESPONSE_RECEIVED`) et gabarits fr/en dans le catalogue i18n. |
| (d) Reste Go | `MatchingNotificationEvent` (événement produit Kafka de mise en relation) ; garde d'accès au flux (`NotificationStreamAccessGuard`) tant que la route Go existe. |
| (e) Migration | Les lignes `notifications` sont de l'historique de fil : migration optionnelle vers `tnt_notifications` (statut mappé) ; si personne ne lit le fil (non vérifié, à confirmer par recherche de lecteurs), on ne migre pas et on supprime en clôture. Tables `notification_annonces` / `notification_livraisons` : suppression directe. |
| (f) API | `GET /api/notifications/stream/{freelancerId}` : conservée, déléguée au flux realtime ; l'id de chemin est le profil freelancer, la garde reste. Classe : addition (nouvelle route realtime) ; rupture justifiée si le chemin Go est retiré plus tard (hors périmètre). |
| (g) Risques | Sinks en mémoire (perte à chaque redéploiement, pas multi-instance) : le flux realtime sur Redis corrige cela mais change la sémantique de reprise. Kernel d'envoi : points ouverts de la mémoire « résilience Kernel » (instances Resilience4j à déclarer pour tout nouveau client, pas d'`IllegalStateException` pour une erreur de configuration). |
| (h) Tests | IT SSE : abonnement, réception, battement, refus d'accès ; test de gabarits fr/en ; test d'archi : plus de table locale de notifications. |

### 3.14 Adresses (lot L4)

| | |
|---|---|
| (a) Go | `AddressEntity` (`addresses` : type, street, description (repère), district (quartier), city, region, country, postal_code, latitude, longitude), `person_addresses` (person_id, address_id, address_type), `AddressApplicationService` (166 lignes ; recherche locale `findByLandmarkContainingIgnoreCaseOrStreetContainingIgnoreCase` l.79, repli de géocodage via `IGeolocationPort` → `GeoCoreAdapter` → geo `IGeocodeUseCase`). Routes `/api/addresses` (POST, GET, `/search`, PUT, DELETE). Enum `AddressType` (PRIMARY, SECONDARY, HOME, WORK). |
| (b) Core | geo-core (schéma `tnt_geography`) n'a AUCUNE adresse persistée générique : seulement `AddressResult`, `IGeocodeUseCase` (geocode, reverseGeocode, findNearbyAddresses) et `IManagePointsOfInterestUseCase` (PoiType dont RELAY_POINT, LANDMARK). La livraison core embarque des VO `DeliveryAddress`. VO commun `com.yowyob.tiibntick.common.vo.Address`. |
| (c) À ajouter | geo-core : agrégat `Address` générique (table `tnt_geography.tnt_addresses` : id, tenant_id, street, landmark, district, city, region, country, postal_code, latitude, longitude, created_at, updated_at), `IManageAddressUseCase` (create, update, delete, findById, search) et table de liaison `tnt_address_links` (owner_id, address_id, label générique). |
| (d) Reste Go | `AddressType` comme libellé d'usage (valeur de lien). |
| (e) Id + migration | Les UUID d'adresse sont CONSERVÉS : `INSERT INTO tnt_geography.tnt_addresses (id, …) SELECT id, … FROM addresses ON CONFLICT (id) DO NOTHING ; INSERT INTO tnt_geography.tnt_address_links (owner_id, address_id, label) SELECT person_id, address_id, address_type FROM person_addresses`. Comme les ids ne bougent pas, les colonnes `*_address_id` des lots L2 et L3 n'ont rien à changer ; seules leurs FK physiques vers `addresses(id)` (non vérifiées) sont retirées en clôture. `person_id` est converti en `actorId` après L3 (dépendance : si L4 migre avant L3, convertir à l'étape L3 par un changeset L3). |
| (f) API | `/api/addresses` conservées ; mêmes DTO. Aucune. |
| (g) Risques | Doublon de recherche (la recherche locale est SQL ILIKE ; le core doit offrir une recherche équivalente) ; `NOT NULL` d'adresse sur annonce/besoin ; dépendance d'ordre avec L2 et L3. |
| (h) Tests | IT CRUD d'adresse via le core ; test de migration (ids conservés, liens recréés) ; test de recherche textuelle équivalente ; géocodage de repli (MockWebServer ou doublure). |

### 3.15 Points relais et dépôts (lot L4)

| | |
|---|---|
| (a) Go | `GofpRelayPoint` (`gofp_relay_points` : `core_relay_point_id` = id du `RelayHub` geo, core_freelancer_id, champs propriétaire dénormalisés, name, status, is_active, rating, total_deposits, subscription_id, deposits_used, max_deposits, storage_length/width/height/unit, postal_address_id, physical_access_address_id ; transitoires openingHours/pricingPolicy/visuels/propriétaire). `RelayDeposit` (`relay_deposits` : packet_id, client_id, logistics_id (= id du hub), storage_fee, penalty_fee, currency, status DELIVERED/RETRIEVED, created_at, retrieved_at), `@Deprecated` « miroir de synchro » (`RelayDepositService.java:50`). `RelayDepositService` (414 lignes) dépose dans l'inventaire (l.104), incrémente l'occupation geo, sauve le miroir ; au retrait vérifie l'OTP puis règle le tarif. `AdminRelayPointApplicationService`. |
| (b) Core | geo `RelayHub` (capacité, occupation, operatorActorId, statut ACTIVE/FULL/TEMPORARILY_CLOSED/PERMANENTLY_CLOSED, nodeId) via `IManageRelayHubUseCase.createHub` et `IFindNearbyHubsUseCase.findHub`/`updateHubOccupancy` ; `RelayHubPortAdapter` crée un `RoadNode` + `RelayHub`. org-core `HubRelaisEntity` (`tnt_hub_relais` : organization_id, tenant_id, name, max_parcel_capacity, geographic_point_wkt, opening_hours, operator_id). actor-core `RelayOperatorProfile` (hubId, openingHours, declaredCapacityParcels). inventory-core `HubPackageEntry` (`tnt_hub_package_entries` : hubId, packageId, trackingCode, storageLocation, depositedAt, pickedUpAt, depositedByActorId, pickedUpByActorId, recipientPhone, notified) avec `DepositHubPackageCommand(tenantId, hubId, packageId, trackingCode, storageLocation, depositedByActorId, recipientPhone)`. |
| (c) À ajouter | inventory-core `HubPackageEntry` / `tnt_hub_package_entries` : `owner_actor_id UUID` (client), `storage_fee NUMERIC(14,2)`, `penalty_fee NUMERIC(14,2)`, `fee_currency VARCHAR(3)`, `retrieval_status` si nécessaire (le statut déposé/retiré se déduit de `pickedUpAt`). geo `RelayHub` : dimensions de stockage `storage_length/width/height` + `storage_unit` (ou capacité volumique), `deposits_quota` (voir tarification). |
| (d) Reste Go | Photos du relais (`gofreelancer_relay_point_visuals`), horaires (`opening_hours` : produit), abonnement du relais (`relay_point_subscriptions`), tarification de stockage (`logistics_pricing` : price_per_day, grace_period_days, penalty_per_day). |
| (e) Id + migration | Relais : `gofp_relay_points.id` → `RelayHub.id` (= `core_relay_point_id`) : réécrire l'id de la ligne Go sur l'id du hub avec le même motif générique de repointage de FK (voir 3.10), puis référencer partout l'id du hub. Dépôts : INSERT dans `tnt_hub_package_entries` avec `packageId = parcel_id` (corrige la confusion packet/need, 3.2) et frais/pénalité/devise ; table `relay_deposits` supprimée en clôture. Statuts du relais : voir 3.8 (H2). |
| (f) API | Routes de dépôt/retrait et d'admin relais conservées. Aucune. |
| (g) Risques | Dépôts en cours (statut DELIVERED) à ne pas perdre ; occupation geo à recalculer (`updateHubOccupancy`) après migration ; l'ordre avec L1 pour `packetId`→`parcelId`. |
| (h) Tests | IT dépôt → retrait → règlement (OTP, frais, pénalité) ; `Gdd4RelayMigrationIntegrationTest` ; test d'occupation. |

### 3.16 Tarification et abonnements (lot L4)

| | |
|---|---|
| (a) Go | `delivery_person_pricing` (miroir), `FreelancerPricingPolicyApplicationService` (126 lignes), `FreelancerPriceQuoteService` (211 lignes, lit encore `legacyPricingRepository`), `pricing_catalog` (`PricingCatalogPort`, utilisé par `RelayPointSubscriptionApplicationService`), `logistics_pricing`, `subscriptions` (FREE 5 / 30 %, STANDARD 30 / 20 %, ADVANCE illimité / 10 %), `relay_point_subscriptions` (BASIC 50 / 15 %, STANDARD 200 / 10 %, PREMIUM illimité / 5 %). |
| (b) Core | billing-pricing : `BillingPolicy`, `IBillingPolicyUseCase`, `IPricingUseCase` ; `FreelancerGridPolicyMapper` (182 lignes, `GRID_TEMPLATE_CODE="FREELANCER-GRID"`). |
| (c) À ajouter | Rien d'obligatoire. |
| (d) Reste Go | Abonnements (produit). |
| (e) Migration | `FreelancerPriceQuoteService` lit la politique billing, plus le miroir ; `delivery_person_pricing` supprimé en clôture après vérification que chaque ligne a sa politique billing (comptage). |
| (f) API | Aucune. |
| (g) Risques | Le miroir et la politique peuvent diverger : mesurer avant de supprimer. |
| (h) Tests | Test de devis identique avant/après (jeu de cas) ; test de suppression du dépôt legacy. |

### 3.17 Code et tables mortes à retirer (réparties par lot)

| Élément | Preuve de mort (à reconfirmer par grep avant suppression) | Lot |
|---|---|---|
| `Payment` + `PaymentRepository` + table `payments` | entité jamais utilisée par un service | L4 |
| `notification_annonces`, `notification_livraisons` (+ dépôts) | seuls les adaptateurs les référencent | L4 |
| `VehicleCreateRequest`, `VehicleClass` | un seul fichier, DTO orphelin | L3 |
| `DeliveryUrgency` Go | DTO seulement, jamais persisté | L1 |
| `NotificationStreamService` (ancien) | doublon de `NotificationStreamAdapter` | L4 |
| Tables héritées `persons`, `clients`, `delivery_persons`, `logistics`, `packets`, `responses`, `users` (sans entité) | créées par le SQL initial ; `ensureLegacyUser` et `persistPacketIfPresent` y écrivent encore | L1 (packets), L2 (users), L3 (persons/clients/delivery_persons), L4 (logistics) ; DROP en clôture |
| `announcement_subscriptions` | remplacé par `tnt_announcement_responses` (réponses core) ; repli `switchIfEmpty(subscriptionRepository.findAllByAnnouncementId…)` à `AnnouncementApplicationService.java:363` et `findAllByFreelancerId` l.419 | L2 |
| `FreelancerVehicle` repli UUID aléatoire | `VehicleRegistrationAdapter.java:66` | L3 |

### 3.18 Ce qui reste côté Go (rappel des décisions)

Messages de négociation (`gofp_negotiation_messages`), invitations de besoin (`gofp_need_invitations`), codes de remise, favoris (`gofreelancer_user_bookmarks`), profils visuels (`gofreelancer_visual_profiles`) et photos de relais (`gofreelancer_relay_point_visuals`), horaires (`opening_hours`), abonnements, OTP, preuves de livraison. Toutes ces tables s'attachent à des ids core (annonce, livraison, acteur, hub).

---

## 4. Découpage en 4 lots indépendants (4 développeurs, 4 worktrees)

### 4.0 Préparation par le modérateur (« lot 0 », à faire AVANT de lancer les développeurs)

Un seul commit de squelette sur `feat/single-system-tenant`, que chaque worktree rebase avant de commencer. Il évite les conflits sur les fichiers partagés :

1. `GOFP-SQL/tnt-gofp-master.xml` : ajouter 4 lignes `<include>` vers `changes/gdd-l1.xml` … `changes/gdd-l4.xml` (+ un `gdd-final.xml` pour la clôture), tous créés vides (un `databaseChangeLog` sans changeset). Après cela, aucun lot ne touche `tnt-gofp-master.xml`. Rappel : le fichier se termine aujourd'hui par `c54-vehicle-card-and-required-type` (ligne 486), et il est inclus depuis `tnt-bootstrap/src/main/resources/db/changelog/tnt-core-master.yaml:211`.
2. Pour chaque module bas touché par plus d'un lot, ajouter dans son master un `include` d'un fichier par lot vide : `tnt-delivery-master.yaml` → `gdd-l1.yaml` et `gdd-l2.yaml` ; `tnt-actor-master.yaml` → `gdd-l3.yaml` ; `tnt-tp-master.yaml` → `gdd-l3.yaml` ; `tnt-resource-master.yaml` → `gdd-l3.yaml` ; `tnt-geo-master.yaml`, `tnt-inventory-master.yaml`, `tnt-notify-master.yaml` → `gdd-l4.yaml`. (Les noms exacts des masters sont ceux listés par la structure du dépôt ; vérifier leur existence : les masters `tnt-actor-master.yaml`, `tnt-delivery-master.yaml`, `tnt-geo-master.yaml`, `tnt-resource-master.yaml`, `tnt-notify-master.yaml`, `tnt-tp-master.yaml`, `tnt-inventory-master.yaml` ont été relevés ; la présence d'un master realtime : non vérifiée.)
3. `tnt-bootstrap/.../tnt-core-master.yaml` : AUCUN lot ne le modifie, puisque les fichiers de lot sont inclus par les masters de module déjà inclus. Si un nouveau module doit être inclus, le modérateur le fait dans un commit isolé.
4. `application.yml` : réserver une section par lot (clés regroupées sous `tnt.gdd.l1`, `tnt.gdd.l2`, …) uniquement si une configuration est nécessaire ; sinon ne pas toucher. Tout ajout de client HTTP nécessite les 3 instances Resilience4j (mémoire « résilience Kernel »).
5. Rappel des tests exclus de `mvn test` : les ITs `*IntegrationTest` se lancent avec `-Pintegration-tests verify -Dit.test=Classe` (Testcontainers, `postgis/postgis` requis ; `postgres:16-alpine` échoue sur PostGIS).

### 4.1 Lot L1 : livraison + colis

| | |
|---|---|
| Périmètre | 3.1, 3.2, 3.5 (enum et DTO), colonnes `deliveries` ; table `packets`. |
| Fichiers gofp possédés | `Delivery.java`, `CoreDeliveryProjection`, `CoreDeliveryStatusProjector`, `CoreMissionStatusProjectionConsumer`, `IDeliveryLifecyclePort` + `DeliveryLifecyclePortAdapter`, `DeliveryStatusApplicationService` (784 lignes), `DeliveryApplicationService` (564), `GofpDeliveryController`, `DeliveryPaymentController` (lecture seule du statut), `DeliveryUpdateDTO`, `DeliveryResponseDTO`, enum `DeliveryUrgency` Go, DTO/entité `PacketProof`, accès SQL `packets` dans `DeliveryNeedApplicationService` (voir « conflits »). |
| Modules bas touchés | delivery-core : `Delivery` (agrégat), `DeliveryEntity`, mappers, `Parcel`, `PackageSpecification`, `ParcelEntity`, `DeliveryCompletedEvent` (ajout `senderId`, H4), migrations `gdd-l1-*` ; consommateurs de l'événement (lecture seule). |
| Dépendances | Aucune en entrée. Fournit à L2 (`PackageSpecification` : designation, thickness) et à L4 (id Parcel pour `packetId`). |
| Ordre de fusion | 1er. |
| Conflits probables | `DeliveryNeedApplicationService` (L1 pour les lignes SQL `packets`, L2 pour le reste) : règle : L1 ne touche QUE les méthodes `persistPacketIfPresent` / `loadPacketMetrics` / `loadPacket` en les remplaçant par un `ParcelPort` ; L2 ne touche pas ces méthodes. `DeliveryStatusApplicationService` ↔ `RelayDepositService` (L4) : le paramètre `packetId` garde sa signature (UUID) ; L1 passe désormais l'id Parcel, L4 le lit comme Parcel (contrat). `DeliveryAnnouncementPortAdapter` (L2) lit `DeliveryUrgency` : L1 ne le modifie pas (vérifier l'import). |
| Critères d'acceptation vérifiables | (1) `grep -rn "deliveries.status\|setStatus(" GOFP` ne trouve plus d'écriture de statut hors migration ; (2) plus de `FROM packets` / `INSERT INTO packets` dans le module (`grep`) ; (3) `Gdd1DeliveryMigrationIntegrationTest` et `ParcelMigrationIntegrationTest` verts ; (4) `DeliveryStatusLifecycleConvergenceTest` + `DeliveryCoreConvergenceIntegrationTest` verts ; (5) enum Go `DeliveryUrgency` supprimée ; (6) tous les tests gofp (406 en lot 1) et delivery-core (68) verts ; (7) tableau avant/après des DTO dans le rapport. |

### 4.2 Lot L2 : annonces + besoins

| | |
|---|---|
| Périmètre | 3.3, 3.4, 3.5 (adaptateur), `announcement_subscriptions`. |
| Fichiers gofp possédés | `Announcement.java`, `AnnouncementApplicationService` (821), `DeliveryAnnouncementPortAdapter` (283), `IDeliveryAnnouncementPort`, `DeliveryNeed`, `DeliveryNeedStatus`, `DeliveryNeedApplicationService` (1025, sauf les 3 méthodes `packets` de L1), `NeedAnnouncementService`, `NeedInvitationService`, `RelayPointDirectoryService` (usage du statut), `AnnouncementSubscription*`, DTO d'annonce et de besoin. |
| Modules bas touchés | delivery-core : `DeliveryAnnouncement`, `DeliveryAnnouncementEntity`, `CreateDeliveryAnnouncementCommand`, `RecipientInfo`, `DeliveryAnnouncementService`, `DeliveryAnnouncementController`, migrations `gdd-l2-*`. |
| Dépendances | L1 (colis). Indépendant de L3/L4 sauf adresses (ids conservés, donc pas de blocage). |
| Ordre de fusion | 3e (après L1 et L3). |
| Conflits probables | `delivery-core` : `DeliveryAnnouncement*` appartient à L2, `Delivery*`/`Parcel*` à L1 : aucun fichier commun si la règle est respectée ; `tnt-delivery-master.yaml` (réglé par le squelette). |
| Critères d'acceptation | (1) Plus d'écriture dans `announcements` (hors migration) : `softMirrorLocal` et `enrichFromLocalMirror` supprimés ; (2) `DeliveryNeedStatus` n'est plus persisté (test de correspondance vert) ; (3) `announcement_subscriptions` plus lue (le repli `switchIfEmpty` retiré) ; (4) `Gdd2AnnouncementMigrationIntegrationTest` + `GoNeedAnnouncementMigrationIntegrationTest` (existant) verts ; (5) IT publication → réponse → sélection → livraison avec les champs ajoutés ; (6) `urgency` accepté en entrée, `NORMAL` toléré. |

### 4.3 Lot L3 : acteurs

| | |
|---|---|
| Périmètre | 3.6, 3.7, 3.8 (FreelancerStatus), 3.9, 3.10, 3.11. |
| Fichiers gofp possédés | `GofpUser`, `GofpClient`, `GofpFreelancer`, `GofpUserProvisioningService`, `GofpClientService`, `GofpFreelancerService`, `GofpFreelancerProjectionService`, `FreelancerQuotaService`, `Evaluation*`, `FreelancerVehicle*`, `VehicleRegistrationAdapter`, `CourierVehicleService`, `CourierVehicleController`, `CourierCardService`, `VehicleCreateRequest`, `VehicleClass`, `FreelancerStatus`, `ClientStatus`, `UserStatus`, `LoyaltyStatus`, `AuthController` / `PasswordSetupController` / `PasswordToken` (si H8). |
| Modules bas touchés | actor-core (`FreelancerProfile`, `ClientProfile`, commandes, migrations V5+), delivery-core (`DeliveryPerson`, `DeliveryPersonStatus` : fichiers distincts de L1/L2), tp-core (`ThirdPartyRating`, `LoyaltyService`, `DeliveryEventConsumer`), resource-core (`FreelancerVehicle`, commande), roles-core (`TntRole`, `TntPermission` : ajouts de permissions, H11), migrations `gdd-l3-*`. |
| Dépendances | L1 pour l'événement `senderId` (H4 : l'ajout vit dans delivery-core côté L1 ; L3 consomme). |
| Ordre de fusion | 2e (en parallèle de L1 possible ; fusion après L1). |
| Conflits probables | `TntRole` est partagé avec d'autres chantiers : une seule modification, par L3 ; `DeliveryPerson` (L3) et `Delivery` (L1) sont des fichiers distincts. Réécriture d'ids : couvre des tables de L2 (`announcements.client_id`, `delivery_needs.user_id` : déjà actorId, vérifier). |
| Critères d'acceptation | (1) `gofp_users`/`gofp_clients`/`gofp_freelancers` ne portent plus `loyalty_status`, `total_orders`, `rating`, `total/failed/remaining_deliveries`, `lat/lng` (retrait en clôture, lectures déjà basculées) ; (2) `Gdd3ProfileMigrationIntegrationTest` vert (ids cohérents, FK intactes) ; (3) `TntProfileRolePermissionsTest` couvre les nouvelles permissions ; (4) aucune `UUID.randomUUID()` de repli dans l'adaptateur véhicule ; (5) IT évaluation → moyenne → palier de fidélité ; (6) test du double profil par compte ; (7) `/api/freelancers/{id}` continue de prendre l'id de profil. |

### 4.4 Lot L4 : paiement, notifications, adresses, relais/dépôts, tarification

| | |
|---|---|
| Périmètre | 3.8 (RelayPointStatus), 3.12 à 3.16, 3.17 (parties L4). |
| Fichiers gofp possédés | `Payment`, `PaymentRepository`, `DeliveryPaymentApplicationService`, `NotificationService`, `NotificationStreamController`, `NotificationStreamAdapter`, `NotificationStreamService`, `MatchingNotificationConsumer`, `AddressEntity`, `AddressApplicationService`, `IGeolocationPort`/`GeoCoreAdapter`, `GofpRelayPoint`, `RelayDeposit`, `RelayDepositService` (414), `AdminRelayPointApplicationService`, `RelayPointSubscription*`, `FreelancerPricingPolicyApplicationService`, `FreelancerPriceQuoteService`, `FreelancerGridPolicyMapper`, `DatabasePricingCatalogAdapter`. |
| Modules bas touchés | geo-core (agrégat `Address`, `RelayHub`), inventory-core (`HubPackageEntry`, commande de dépôt), notify-core (types, gabarits), realtime-core (SSE par utilisateur), billing (lecture du taux), migrations `gdd-l4-*`. |
| Dépendances | L1 (id Parcel pour `packageId`) ; L3 pour la conversion `person_id`→`actorId` des liens d'adresse (voir 3.14). |
| Ordre de fusion | 4e (dernier). |
| Conflits probables | `RelayDepositService` ↔ `DeliveryStatusApplicationService` (contrat `packetId` = id Parcel) ; notify-core : gabarits fr/en partagés avec d'autres chantiers i18n ; `application.yml` (si un nouveau client HTTP). |
| Critères d'acceptation | (1) Entité `Payment` et ses tests supprimés, `DeliveryPaymentFlowIntegrationTest`/`DeliveredButUnpaidTest` verts ; (2) plus de tables `notifications`, `notification_annonces`, `notification_livraisons` lues ou écrites ; (3) flux SSE par utilisateur couvert par une IT ; (4) `tnt_addresses` peuplée avec les mêmes ids que `addresses` (requête de contrôle `count(*)` égale) ; (5) `relay_deposits` plus lue ni écrite, dépôts migrés dans `tnt_hub_package_entries` avec frais ; (6) `Gdd4*MigrationIntegrationTest` verts ; (7) devis de tarification identique avant/après. |

### 4.5 Ordre de fusion et dépendances (résumé)

L0 squelette → L1 → L3 → L2 → L4 → clôture (changeset de suppression, par le modérateur). L1 et L3 peuvent être développés en parallèle ; la fusion respecte l'ordre ci-dessus pour que L2 voie `PackageSpecification` et que L4 voie l'id Parcel et les ids d'acteur.

### 4.6 Changeset de clôture (modérateur, après L4)

Fichier `GOFP-SQL/changes/gdd-final.xml`, numéros 900+. Contenu : suppression des colonnes `deliveries.status/actual_*/freelancer_id/tarif/fenêtres` (H7), des tables `announcements`, `payments`, `notifications*`, `relay_deposits`, `gofreelancer_vehicles`, `evaluations`, `addresses`, `person_addresses`, `delivery_person_pricing`, `packets` et des tables héritées. Chaque suppression est précédée d'une précondition `sqlCheck` (comptage des lignes sans contrepartie core = 0, ou rapport signé).

**Réalisé le 2026-11-27 (décision D-154, « Oui, avec garde-fous »)** — `gdd-final-901` à `909` :
recensement `public.gdd_final_uncovered()` (901), porte globale HALT et retrait des clés étrangères vers les tables
supprimées (902), puis une précondition HALT par suppression : tables recopiées dans le cœur (903), colonnes H7 de
`deliveries` (904), colonnes héritées de `delivery_needs` (905), `password_hash` et agrégats des profils gofp (906),
`notifications`, `password_tokens`, `delivery_person_pricing` (907), tables d'identité héritées `users`, `persons`,
`clients`, `delivery_persons`, `logistics` (908), retrait du recensement (909). Le code Go qui les lisait a été
retiré avant (mots de passe locaux, reprise des grilles, `AdminBootstrapConfig`, `ensureLegacyUser`, repli L2 des
besoins). **Conservés volontairement** (données ou code encore vivants) : `gofreelancer_vehicles` (photos, couleur,
coffre : aucun champ du cœur), `announcement_subscriptions` (consommateur Kafka), compteurs et position de
`gofp_freelancers` (pas encore de cas d'usage de quota dans le cœur), `deliveries.duration` (hors H7), enums Go
`FreelancerStatus`/`ClientStatus`/`UserStatus`/`VehicleType` (typent des colonnes gofp vivantes).

---

## 5. Convention de nommage des changesets Liquibase

- `id` : `gdd-l<N>-<NNN>-<slug-kebab>` ; le numéro est unique et réservé par lot : L1 = 101-199, L2 = 201-299, L3 = 301-399, L4 = 401-499, clôture = 901-999. Exemple : `gdd-l3-304-actor-profile-id-realign`.
- `author` : `gdd-l<N>` ; `labels` : `gdd-l<N>` (permet le test de mise à jour « tout sauf le lot » du modèle `GoNeedAnnouncementMigrationIntegrationTest`, dont le label est `go-need-announcement` ; lignes 1-80 lues seulement, la suite de la classe n'a pas été lue).
- Fichiers : côté gofp `GOFP-SQL/changes/gdd-l<N>.xml` incluant des fichiers SQL `2026MMDD_gdd_l<N>_<NNN>_<slug>.sql` ; côté module bas `db/changelog/gdd-l<N>.yaml` (inclus par le squelette) contenant des fichiers `V<…>__gdd_l<N>_<NNN>_<slug>.sql` ou le style du module (actor-core : `migrations/V*.sql`).
- Les changesets sont append-only : jamais modifier un changeset publié ; correction = nouveau changeset. Pas de `validCheckSum ANY` sur les nouveaux changesets (les anciens en portent).
- Idempotence : `IF NOT EXISTS`, `ON CONFLICT DO NOTHING`, blocs `DO $$ … $$` re-jouables (modèle `20261114_relocate_guest_person.sql` : « once relocated … the block does nothing »).
- Colonnes ajoutées au core : nullables avec défaut, `ADD COLUMN IF NOT EXISTS` (compatibilité ascendante), pas de renommage destructif.
- Chaque migration de données a un test `Gdd<N>…MigrationIntegrationTest` (PostGIS Testcontainers, base fraîche par test, modèle : `createDatabase()` + `Liquibase(MASTER = "db/changelog/tnt-core-master.yaml")`).

Exemples d'esquisses SQL (à adapter par le développeur ; noms de colonnes core à confirmer sur le schéma réel) :

```sql
-- gdd-l1-101 : colonnes Parcel (delivery-core)
ALTER TABLE tnt_parcels ADD COLUMN IF NOT EXISTS designation   VARCHAR(255);
ALTER TABLE tnt_parcels ADD COLUMN IF NOT EXISTS thickness_cm  DOUBLE PRECISION;

-- gdd-l1-102 : packets -> tnt_parcels, ids conservés
INSERT INTO tnt_parcels (id, weight_kg, width_cm, height_cm, length_cm, thickness_cm,
                         fragile, perishable, description, designation, photo_url,
                         created_at, updated_at, version)
SELECT p.id, p.weight, p.width, p.height, p.length, p.thickness,
       COALESCE(p.is_fragile, false), COALESCE(p.is_perishable, false),
       p.description, COALESCE(p.designation, p.title), p.photo_url,
       now(), now(), 0
  FROM packets p
ON CONFLICT (id) DO NOTHING;

-- gdd-l1-103 : besoin -> colis (plus de FK vers packets)
ALTER TABLE delivery_needs DROP CONSTRAINT IF EXISTS delivery_needs_packet_id_fkey;
ALTER TABLE delivery_needs ADD COLUMN IF NOT EXISTS parcel_id UUID;
UPDATE delivery_needs SET parcel_id = packet_id WHERE parcel_id IS NULL;

-- gdd-l1-110 : colonnes livraison core (accord, fenêtres)
ALTER TABLE tnt_deliveries ADD COLUMN IF NOT EXISTS agreed_amount        NUMERIC(14,2);
ALTER TABLE tnt_deliveries ADD COLUMN IF NOT EXISTS agreed_currency      VARCHAR(3);
ALTER TABLE tnt_deliveries ADD COLUMN IF NOT EXISTS pickup_window_start  TIMESTAMPTZ;
ALTER TABLE tnt_deliveries ADD COLUMN IF NOT EXISTS pickup_window_end    TIMESTAMPTZ;
ALTER TABLE tnt_deliveries ADD COLUMN IF NOT EXISTS delivery_window_start TIMESTAMPTZ;
ALTER TABLE tnt_deliveries ADD COLUMN IF NOT EXISTS delivery_window_end   TIMESTAMPTZ;
ALTER TABLE tnt_deliveries ADD COLUMN IF NOT EXISTS source_announcement_id UUID;

UPDATE tnt_deliveries d
   SET agreed_amount = g.tarif, source_announcement_id = g.announcement_id,
       pickup_window_start = g.pickup_min_time, pickup_window_end = g.pickup_max_time,
       delivery_window_start = g.delivery_min_time, delivery_window_end = g.delivery_max_time
  FROM deliveries g
 WHERE g.id = d.id AND d.agreed_amount IS NULL;
```

```sql
-- gdd-l2-201 : annonce core, colonnes ajoutées
ALTER TABLE tnt_delivery_announcements ADD COLUMN IF NOT EXISTS required_vehicle_type VARCHAR(40);
ALTER TABLE tnt_delivery_announcements ADD COLUMN IF NOT EXISTS payment_method        VARCHAR(30);
ALTER TABLE tnt_delivery_announcements ADD COLUMN IF NOT EXISTS destination_hub_id    UUID;
ALTER TABLE tnt_delivery_announcements ADD COLUMN IF NOT EXISTS origin_ref_type       VARCHAR(30);
ALTER TABLE tnt_delivery_announcements ADD COLUMN IF NOT EXISTS origin_ref_id         UUID;
-- (autres colonnes de 3.3 sur le même modèle)

-- gdd-l2-202 : backfill depuis le miroir
UPDATE tnt_delivery_announcements a
   SET required_vehicle_type = l.required_vehicle_type,
       payment_method        = l.payment_method,
       destination_hub_id    = l.destination_relay_point_id,
       origin_ref_type       = CASE WHEN l.delivery_need_id IS NOT NULL THEN 'DELIVERY_NEED' END,
       origin_ref_id         = l.delivery_need_id
  FROM announcements l
 WHERE l.id = a.id AND a.origin_ref_id IS NULL;
```

```sql
-- gdd-l3-301 : repointage d'ids (modèle 20261114_relocate_guest_person.sql :
-- lister les FK de gofp_users(id) via pg_constraint, les supprimer, UPDATE, les recréer).
-- Diagnostic préalable à joindre au rapport :
--   SELECT count(*) FROM gofp_users       WHERE id <> core_user_id;
--   SELECT count(*) FROM gofp_freelancers WHERE id <> core_freelancer_id;
-- gdd-l3-310 : statut d'agrément
UPDATE tnt_delivery_persons p SET status = f.status
  FROM gofp_freelancers f WHERE f.id = p.id AND f.status IN ('REJECTED','REVOKED','SUSPENDED');
-- gdd-l3-320 : notes
INSERT INTO tnt_tp_ratings (id, mission_id, rating_context, score, comment /* + colonnes à confirmer */)
SELECT e.id, e.delivery_id, e.type, e.rating, e.comment
  FROM evaluations e
ON CONFLICT DO NOTHING;
```

```sql
-- gdd-l4-401 : adresses, ids conservés (geo-core)
INSERT INTO tnt_geography.tnt_addresses (id, street, landmark, district, city, region, country,
                                         postal_code, latitude, longitude, created_at, updated_at)
SELECT id, street, description, district, city, region, country, postal_code, latitude, longitude,
       now(), now()
  FROM addresses
ON CONFLICT (id) DO NOTHING;

-- gdd-l4-410 : dépôts -> inventaire (packageId = id Parcel, frais portés par l'entrée)
ALTER TABLE tnt_hub_package_entries ADD COLUMN IF NOT EXISTS owner_actor_id UUID;
ALTER TABLE tnt_hub_package_entries ADD COLUMN IF NOT EXISTS storage_fee    NUMERIC(14,2);
ALTER TABLE tnt_hub_package_entries ADD COLUMN IF NOT EXISTS penalty_fee    NUMERIC(14,2);
ALTER TABLE tnt_hub_package_entries ADD COLUMN IF NOT EXISTS fee_currency   VARCHAR(3);
```

---

## 6. Format du rapport destiné aux autres développeurs

Chaque lot AJOUTE une section datée aux trois fichiers existants de `docs/audits/remediation/gofp-dedup/` (ne pas réécrire le contenu des lots précédents) :

- `journal-technique.md` (tableau lot / contenu / preuve) : une ligne par lot, `| L<N> | <ce qui a été supprimé/ajouté, nombre de fichiers> | <commande de test + nombre de tests verts, chiffres réels> |`. Y ajouter la liste des changesets du lot (ids), le diagnostic de migration (comptes avant/après, lignes orphelines) et les limites (ITs non exécutés = dit explicitement).
- `mobile-bff.md` : section « Lot L<N> » avec uniquement l'impact sur les clients mobile/BFF : classe d'impact (aucune / addition / rupture justifiée) et, pour toute rupture, un bloc « avant / après » (requête et réponse JSON, valeurs d'enum, codes HTTP). Exemples attendus : valeur `NORMAL` → `STANDARD` ; niveaux de fidélité.
- `agency-et-modules.md` : section « Lot L<N> » avec l'impact sur les modules voisins : nouvelles colonnes/ports ajoutés au core (nom générique, type, colonne, module), permissions ajoutées, événements modifiés (additions seulement), changesets du lot.

Gabarit court à reprendre dans chacun des trois fichiers :

```
## Lot L<N> — <titre> (<date>, commit <hash>)
- Quoi : ...
- Ajouts au core (module / nom / type / colonne) : ...
- Impact API : none | addition | rupture justifiée (avant/après)
- Changesets : gdd-l<N>-xxx ...
- Preuves : commande + résultats réels (tests verts / non exécutés)
- Restes / hypothèses non levées (H<n>) : ...
```

Règles : le README du dossier (28 lignes : règle et tableau des décisions) n'est modifié que par le modérateur ; les messages de commit sont en anglais, sans trailer co-auteur ; aucun push (les commandes sont données au modérateur).

---

## 7. Plan de test transverse

- Chaque lot : tests unitaires + au moins une IT par flux touché (Testcontainers/MockWebServer) ; ne jamais déclarer « vérifié » sans exécution (mémoire « Toujours des tests d'intégration »).
- Commande type : `mvn -pl <module> verify -Pintegration-tests -Dit.test=<Classe>` ; contexte complet : Postgres PostGIS (5433) + Redis + Kafka locaux (`TiiBnTickApplicationTest`).
- Échecs préexistants à ne pas attribuer aux lots : Redis injoignable (`TiiBnTickApplicationTest`, `PublicPathsSecurityTest`), `LayeringArchitectureTest.applicationMustNotDependOnAdapterInWeb` (violation généralisée dans gofp), `SpringdocProdExposureTest`, ITs `*OutboxIntegrationTest` (« No qualifying bean OutboxPollerPort »), `TemplateKeyI18nRegressionTest` flaky, et deux tests bootstrap laissés rouges par le chantier i18n.
- Modèle des ITs de migration : `GoNeedAnnouncementMigrationIntegrationTest` (base de données créée à neuf, migration de tout sauf le changeset étiqueté, insertion de lignes, application du nouveau changeset, vérification).
- `GofpProjectionTablesSeedGuardTest` : à relire par chaque lot qui supprime une table de projection (il peut garder la liste des tables de projection).

---

## 8. Politique d'impact sur l'API

| Classe | Définition | Obligation |
|---|---|---|
| none | Routes, DTO, valeurs et codes HTTP inchangés | Mentionner dans `mobile-bff.md` seulement si une zone sensible a bougé |
| addition | Champ optionnel, valeur d'enum acceptée en plus, route nouvelle | Décrire dans `mobile-bff.md` (champ, défaut) |
| rupture justifiée | Valeur sortante, route ou sémantique modifiée parce que le core l'impose | Bloc avant/après obligatoire (requête/réponse), justification en une phrase, accord du modérateur AVANT le code |

Ruptures déjà identifiées : valeur `NORMAL` d'urgence (H6) ; seuils de fidélité (H3) ; routes locales de mot de passe (H8, si confirmé). Toute autre rupture proposée en cours de route passe par le modérateur.

---

## 9. CHECKLIST du modérateur (revue de chaque lot)

Compléter avec « OK / KO / non vérifié » et une preuve (commande, sortie, fichier:ligne).

**A. Périmètre et règles**
1. Le lot ne touche que ses fichiers (comparer `git diff --name-only` à la liste du lot, section 4). Aucun fichier d'un autre lot.
2. Aucune modification de `tnt-core-master.yaml`, de `tnt-gofp-master.xml`, ni des masters de module (le squelette suffit). `application.yml` : diff vide ou limité à des clés du lot.
3. Aucun changeset publié n'est modifié ; les nouveaux respectent la convention (id, auteur, label, plage de numéros, idempotence, pas de `validCheckSum ANY`).
4. Commits en anglais, sans trailer co-auteur, sur la branche attendue, aucun push.
5. Aucune lecture ni citation du dossier `tiibntick-platforms`.

**B. Délégation et dédoublonnage**
6. Les enums/entités visés n'existent plus côté Go (grep nul) ; aucun enum de substitution déguisé.
7. Les noms ajoutés au core sont génériques (aucun « gofp », « go », « freelancer » dans un nom de colonne/classe qui ne soit pas du domaine core).
8. Les ajouts au core sont additifs et optionnels (aucune colonne NOT NULL sans défaut, aucun renommage).
9. Les ids de tables jouent comme prévu : mêmes UUID quand ils sont déjà égaux, repointage idempotent sinon ; diagnostic de migration joint (comptes, orphelins).
10. Aucun `UUID.randomUUID()` de repli qui masque une erreur d'appel (cas `VehicleRegistrationAdapter.java:66`).
11. L'appel d'un use case à `@RequirePermission` a été testé AVEC un vrai jeton de rôle CLIENT et FREELANCER (H11) ; la permission ajoutée figure dans `TntRole` et dans `TntProfileRolePermissionsTest`.
12. Le contexte de tenant est celui du JWT/contexte courant (H12), jamais une valeur codée en dur.

**C. API**
13. Chaque route et chaque DTO existant est inchangé, ou classé dans `mobile-bff.md` (none/addition/rupture justifiée avec avant/après).
14. Les routes `/freelancers/{id}` utilisent toujours l'id de profil ; le double profil par compte (Go + Freelancer) fonctionne.
15. Le vocabulaire de statut sortant est identique ou documenté ; aucune valeur d'enum core « inconnue » du mobile n'est émise sans accord.

**D. Tests (écrits ET exécutés)**
16. Tests unitaires du lot verts, nombre de tests donné avec la commande.
17. ITs d'intégration exécutées (Testcontainers/MockWebServer) ; si Docker est indisponible, le rapport le dit.
18. Test de migration `Gdd<N>…MigrationIntegrationTest` : base remplie avant, migration, vérifications de comptes et d'ids, relance idempotente.
19. Non-régression : tests existants de référence verts (`DeliveryCoreConvergenceIntegrationTest`, `DeliveryStatusLifecycleConvergenceTest`, `CoreDeliveryStatusProjectorTest`, `GofpProjectionTablesSeedGuardTest`, `GoNeedAnnouncementMigrationIntegrationTest`, `DeliveryPaymentFlowIntegrationTest`, `DeliveredButUnpaidTest`, `TntProfileRolePermissionsTest`).
20. Les échecs rouges restants figurent dans la liste des échecs préexistants (section 7) ; tout autre rouge est une régression.
21. Le contexte Spring complet démarre (câblage et Liquibase) après le lot.

**E. Rapport**
22. Les trois fichiers `gofp-dedup/{journal-technique,mobile-bff,agency-et-modules}.md` ont reçu la section du lot, au gabarit de la section 6, avec des chiffres réels.
23. Chaque hypothèse H1-H12 touchée par le lot est reprise avec la décision appliquée (ou « non tranchée »).
24. Les mentions « non vérifié » sont explicites et listées ; rien n'est déclaré vérifié sans exécution.
25. Les commandes de push à lancer par l'utilisateur sont fournies à la fin du rapport (jamais exécutées).

**F. Spécifique à chaque lot**
- L1 : aucune écriture de `deliveries.status` ; `packets` plus lue ni écrite ; l'id Parcel circule besoin → annonce → livraison → dépôt ; `UNKNOWN_DRIVER` et `ensureInTransit` conservés ou remplacés par une règle équivalente testée ; événement `DeliveryCompletedEvent` enrichi sans casser ses consommateurs.
- L2 : plus d'écriture dans `announcements` ; le statut du besoin est dérivé (table de correspondance figée par test) ; Go = FIXED_PRICE, Freelancer = QUOTE_REQUEST préservés ; `announcement_subscriptions` retiré du chemin de lecture.
- L3 : invariant d'ids de profil vérifié sur les données (H9) ; seuils de fidélité (H3) tranchés ; permissions ouvertes (H11) ; véhicules sans repli fictif ; photo de profil toujours 100 % Kernel.
- L4 : dépôts en cours migrés sans perte ; occupation du hub cohérente ; commission inchangée (H5) ; flux SSE par utilisateur testé ; adresses migrées avec les mêmes ids ; dépendance L3 pour `person_id` respectée.
- Clôture : toutes les suppressions précédées d'une précondition de comptage ; sauvegarde/rapport signé des lignes orphelines.

---

## 10. Points « non vérifié » regroupés

- Effet à l'exécution du crédit de fidélité tp-core (sérialisation de l'événement, H4).
- Forme de la réponse de `VehicleRegistrationAdapter` et existence de la route `/api/v1/vehicles` (aucune route de ce nom relevée dans resource-core : `/api/resources/vehicles` et `/api/resources/freelancer-orgs/{orgId}/fleet/vehicles` seulement).
- FK encore présentes sur les tables héritées (`persons`, `logistics`, `users`) et liste exacte des FK pointant `gofp_users(id)` (à dériver de `pg_constraint`).
- Lecteurs éventuels du fil de notifications local.
- Sens de `thirdPartyId` côté Kernel ; noms exacts des colonnes de `tnt_tp_ratings` ; unités des dimensions du Parcel.
- Colonnes NOT NULL d'adresse encore actives sur le miroir annonce/besoin.
- Existence d'un master Liquibase dédié à realtime-core ; import réel de `DeliveryUrgency` dans `DeliveryAnnouncementPortAdapter`.
- Contenu exact de `GoNeedAnnouncementMigrationIntegrationTest` au-delà de la ligne 80 (non lu).
