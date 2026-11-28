# Pour Agency, Link, Market et les autres modules `coreBackend`

> Ajouts faits dans les modules inférieurs, réutilisables par tous. Mis à jour à chaque lot.

## `tnt-delivery-core`

- `DeliveryStatus`, `ResponseStatus`, `AnnouncementStatus` : ajout de `getValue()` et `fromValue(String)` (lecture
  insensible à la casse, `IllegalArgumentException` si inconnu). Utilisez-les plutôt que de recopier l'énumération.

## `tnt-notify-core`

- Les modèles reçoivent automatiquement `{{recipientId}}`, `{{recipientAddress}}`, `{{recipientLanguage}}` ; une valeur
  donnée par l'appelant sous le même nom l'emporte.
- Le client Kernel envoie l'organisation de notification avec **son propre tenant** quand l'appelant ne connaît que son
  tenant métier.

## Lot L4 — paiement, notifications locales, adresses, relais/dépôts, tarification

- Paiement : aucun ajout au cœur. Le règlement continue de passer par `ISettlementUseCase` (`billing.wallet_settlements`).
- `tnt-realtime-core` : flux SSE **par utilisateur** `GET /api/v1/realtime/sse/notifications` (`UserNotificationSseController`),
  abonné au destinataire du jeton (aucun paramètre ne désigne un autre utilisateur), événement `notification`, commentaire
  `heartbeat` toutes les 15 s, 401 sans jeton. Port d'entrée `IStreamUserNotificationsUseCase.streamNotificationsOf(userId)`
  (lecture), pendant de `IBroadcastNotificationUseCase.broadcastToUser` (écriture). Transport = canal Redis du topic
  `/topic/user/{userId}` : multi-instances, pas de rejeu des notifications émises hors connexion. Aucune table, aucun changeset.
- `tnt-notify-core` : types `NotificationType.ANNOUNCEMENT_MATCHED` et `RESPONSE_RECEIVED` (génériques, domaine marketplace). Aucun
  gabarit nouveau : les textes fr/en de la mise en relation sont ceux déjà semés (`notify.gofp.announcement.available`, i18n-005).
- `tnt-geo-core` : **carnet d'adresses générique**. Tables `tnt_geography.tnt_addresses` (id, tenant_id nullable = visible de tous
  les tenants, street, landmark, district, city, region, country, postal_code, latitude, longitude, created_at, updated_at) et
  `tnt_geography.tnt_address_links` (id, owner_id, address_id → cascade, label libre ; unicité (owner, adresse, label)). Port
  `IManageAddressUseCase` (CRUD, `search` texte sur rue/repère, liens). Changeset `gdd-l4-401-geo-addresses-schema`.
- `tnt-inventory-core` : `HubPackageEntry` / `tnt_hub_package_entries` gagnent `owner_actor_id UUID` (la personne qui retire
  le colis), `storage_fee NUMERIC(14,2)`, `penalty_fee NUMERIC(14,2)`, `fee_currency VARCHAR(3)` (nullables, changeset
  `gdd-l4-403-hub-entry-owner-and-fees`). `DepositHubPackageCommand` a 3 composantes de plus (`ownerActorId, storageFee,
  feeCurrency`) ; l'ancien constructeur à 7 arguments est conservé. Nouveau port `FindHubPackagesUseCase` (`findEntry`,
  `findEntriesOfHub`, `findEntriesOfOwner`). Correctif : `pickupPackage` normalise le code de suivi (majuscules, espaces) comme
  le dépôt le stocke — un appelant passant un UUID en minuscules ne trouvait pas l'entrée. Statut déposé/retiré = `pickedUpAt`.
- Go-Freelancer, commission (H5) : port `CommissionRatePort` (taux de commission d'une livraison) ; défaut fixe 5 %. Pour lire
  le taux depuis billing-pricing, fournir un bean `@Primary` qui implémente ce port (décision métier non prise).
- Go-Freelancer : table `gofp_startup_jobs` (changeset `gdd-l4-416-startup-jobs`) et port `StartupJobLockPort` : registre
  « exécuter une fois » des travaux de démarrage. Premier usage : reprise des grilles `delivery_person_pricing` en politiques
  billing `FREELANCER-GRID` du tenant système. `delivery_person_pricing` n'est plus lue que par cette reprise (suppression en
  clôture après comptage « sans politique = 0 », journalisé à chaque démarrage qui l'exécute).

## Lot L1 — livraison et colis (2026-11-25, commits 19ad95e3, 251e434d)

- Quoi : la ligne Go `deliveries` devient une extension de la livraison du cœur (même id) ; les faits de la
  livraison ne sont écrits que dans `tnt-delivery-core`. Les colis Go sont des `Parcel` du cœur.
- Ajouts au core (`tnt-delivery-core`, tous génériques et additifs) :
  - `tnt_parcels.designation VARCHAR(255)`, `tnt_parcels.thickness_cm DOUBLE PRECISION` ; `tnt_parcels.description`
    élargie en `TEXT` ; `PackageSpecification` gagne `designation` et `thicknessCm` (constructeur à 7 arguments conservé).
  - `tnt_deliveries.agreed_amount NUMERIC(14,2)`, `agreed_currency VARCHAR(3)`, `pickup_window_start/end`,
    `delivery_window_start/end TIMESTAMPTZ` ; `Delivery.recordAgreedTerms(...)` et
    `DeliveryLifecycleUseCase.recordAgreedTerms(RecordAgreedTermsCommand)` (pas d'événement, pas de permission).
    `source_announcement_id` n'a PAS été ajouté : `tnt_deliveries.announcement_id` porte déjà l'annonce d'origine.
  - `ParcelUseCase` (`registerParcel`, `findParcelById`) + port `ParcelRepository`.
  - `CreateDeliveryAnnouncementCommand.existingParcel` (optionnel, constructeurs existants conservés) : une
    annonce peut porter un colis déjà enregistré au lieu d'en créer un.
  - `DeliveryQueryUseCase.findDeliveryByIdAnyTenant(UUID)` et `countDeliveriesByDeliveryPersonAndStatus(...)`.
- Permissions ajoutées : aucune. `recordAgreedTerms` et `ParcelUseCase` n'ont pas de `@RequirePermission` :
  ils sont appelés par le module Go derrière ses propres gardes.
- Événements : `DeliveryCompletedEvent` gagne `senderId` en dernier composant (H4, additif ; le constructeur à
  6 arguments reste, avec `senderId = null`).
- Côté Go : `CoreDeliveryProjection` et `CoreDeliveryStatusProjector` supprimés ; le consommateur Kafka
  s'appelle `CoreMissionStatusConsumer` et garde le group id `tnt-gofp-delivery-projection` (offsets conservés) ;
  il ne fait plus que notifier.
- Contrat avec L4 : le `packetId` passé à `RelayDepositService.createRelayDeposit` est désormais l'id du
  colis du cœur (avant : l'id du besoin). La signature ne change pas.
- Changesets : `gdd-l1-101-parcel-designation-thickness`, `gdd-l1-102-parcel-description-text`,
  `gdd-l1-110-delivery-agreed-terms` (delivery-core) ; `gdd-l1-103-deliveries-extension-nullable-terms`,
  `gdd-l1-104-packets-to-parcels`, `gdd-l1-105-drop-packet-foreign-keys`, `gdd-l1-106-relay-deposits-parcel-id`,
  `gdd-l1-107-deliveries-agreed-terms-to-core` (Go). Aucune colonne supprimée (H7).
## Lot L2 — annonces et besoins de livraison (2026-11-25, commits d71d7516, b43af5dd, 798a7ef3)
- Quoi : l'annonce du cœur porte désormais les compléments que Go-Freelancer gardait dans son miroir local
  `announcements` (supprimé du code, table conservée jusqu'à la clôture) ; un besoin Go est relié à son annonce
  par une référence d'origine générique ; le statut du besoin est dérivé du cœur.
- Ajouts au core (module `tnt-delivery-core`, tous nullables / optionnels) :

| Colonne `tnt_delivery_announcements` | Type | Porté par |
|---|---|---|
| `sender_name`, `sender_phone`, `sender_email` | VARCHAR(150) / (30) / (255) | `SenderInfo` (nouveau VO) |
| `recipient_email` | VARCHAR(255) | `RecipientInfo.email` (4e composante ; constructeur à 3 arguments conservé ; omis du JSON des événements quand nul) |
| `required_vehicle_type`, `payment_method`, `transport_method` | VARCHAR(40) / (30) / (30) | `AnnouncementDetails` (nouveau VO) |
| `distance_km`, `estimated_duration_min` | NUMERIC(10,3), INTEGER | `AnnouncementDetails` |
| `destination_hub_id` | UUID | `AnnouncementDetails` (relais de destination) |
| `handover_signature_ref` | VARCHAR(500) | `AnnouncementDetails` (remplace `signature_url`) |
| `service_fee` | NUMERIC(14,2) | `AnnouncementDetails` (remplace `logistics_price`) |
| `origin_ref_type`, `origin_ref_id` | VARCHAR(30), UUID | `AnnouncementDetails` ; index unique partiel `uq_tnt_ann_origin_ref` (une annonce par origine) |

  Index ajouté : `idx_tnt_announcement_responses_person (delivery_person_id)`.
- Ports / commandes ajoutés : `AnnouncementLookupUseCase` (`findAnnouncementByOrigin`, `findAnnouncementsByOrigins`
  en lot, `findAnnouncementsRespondedBy`) ; `DeliveryAnnouncementUseCase.updateAnnouncement(UpdateDeliveryAnnouncementCommand)`
  (montant/devise tant que l'annonce est ouverte, expéditeur, détails ; seul le client de l'annonce) ;
  `DeliveryAnnouncement.updateTerms` / `updateDetails`. `CreateDeliveryAnnouncementCommand` : composantes optionnelles
  `sender`, `details` ajoutées APRÈS `existingParcel` (même forme que L1 ; constructeurs à 11, 12 et 13 arguments
  conservés). REST du cœur (`CreateAnnouncementRequest` / `DeliveryAnnouncementResponse`) : champs optionnels en fin.
- Côté Go-Freelancer : `IDeliveryAnnouncementPort` gagne `update`, `findByOrigin(s)`, `findRespondedBy` ;
  `PublishAnnouncementPortCommand` / `AnnouncementSnapshot` gagnent `AnnouncementExtras` (après `parcelId` de L1) ;
  `IAnnouncementRepository` devient une vue en lecture seule du cœur (`CoreAnnouncementReadAdapter`) ;
  `DeliveryNeedStateService` + `DeliveryNeedStatusPolicy` dérivent statut / livraison / livreur d'un besoin.
- Permissions ajoutées : aucune. `updateAnnouncement` exige `announcement:update` (permission existante, déjà
  détenue par `CLIENT` ; tout rôle qui a `announcement:create` l'a aussi, test `DeliveryAnnouncementPermissionsTest`).
  Événements : aucun ajouté.
- Changesets : `gdd-l2-201-announcement-details`, `gdd-l2-203-announcement-origin-unique` (delivery-core,
  `gdd-l2-delivery.yaml`) ; `gdd-l2-202-backfill-announcement-details`, `gdd-l2-204-announcements-relax-not-null`,
  `gdd-l2-210-delivery-needs-core-refs`, `gdd-l2-211-delivery-needs-announcement-backfill` (gofp, `gdd-l2.xml`).
- Restes : voir `journal-technique.md` (Lot L2).

### Lot L2 — fusion de L1 et corrections après revue (2026-11-26)
- Fusion de `feat/single-system-tenant` (L1, 3b2955c3). `CreateDeliveryAnnouncementCommand` n'a qu'un ordre de
  composantes : `…, urgency, existingParcel, sender, details`. Le colis vient uniquement de `ParcelPort` /
  `ParcelUseCase` (L1) : plus aucune lecture ni écriture de la table `packets` par le besoin.
- m1 : `DeliveryAnnouncementService.updateAnnouncement` passe de `announcement:create` à `announcement:update`.
- Aucun changeset ajouté.

## Lot L3 — acteurs : profils, véhicules, évaluations, fidélité, statuts

### Permissions ajoutées (roles-core, H11)
- Quoi : permissions génériques `rating:create`, `rating:read`, `loyalty:read` (`TntPermission`), accordées dans `TntRole` :
  `CLIENT` (rating:create, rating:read, loyalty:read), `FREELANCER` (rating:create, rating:read). Les rôles qui évaluaient via
  `actor:write` ou lisaient la fidélité via `billing:read` les conservent : `AGENCY_MANAGER`, `BRANCH_MANAGER`, `ORG_ADMIN`
  (rating:create + loyalty:read), `SUPPORT_AGENT`, `MEDIATOR` (loyalty:read) ; `rating:read` suit tous les rôles porteurs de `actor:read`.
- Preuves : `TntProfileRolePermissionsTest` (vrai registre de rôles), roles-core 263 tests verts.

### delivery-core : statut d'agrément et types logistiques (H2, 3.6, 3.8)
- `DeliveryPersonStatus` : valeur `REVOKED` ajoutée ; machine à états portée par l'enum (`canTransitionTo`, `isTerminal`) :
  PENDING→APPROVED|REJECTED, APPROVED→SUSPENDED|REVOKED, SUSPENDED→APPROVED|REVOKED, REJECTED/REVOKED terminaux, même statut idempotent.
- `DeliveryPerson` : `reject()`, `revoke()`, `transitionTo(status)` ajoutés. Changement de comportement à connaître :
  `approve()` accepte maintenant SUSPENDED (levée de suspension) en plus de PENDING ; `suspend()` n'est plus inconditionnel
  (refusé hors APPROVED, `DeliveryDomainException`). Routes `/api/v1/tenants/{tenantId}/delivery-persons/{id}/approve|suspend` concernées.
- `LogisticsType` : `SCOOTER`, `TRICYCLE`, `BUS` ajoutés (colonne `logistics_type` VARCHAR(20) inchangée, aucun changeset).
  `DeliveryCostPolicy` leur applique le tarif de repli déjà utilisé pour un type inconnu (moto) : tarif propre non décidé.
- `LogisticsVehicleTypeMapping` (classe utilitaire, delivery-core) : correspondance `LogisticsType` ↔ noms de `VehicleType` de resource-core
  (BIKE↔BICYCLE/VELO/VELO_CARGO, MOTORBIKE↔MOTORCYCLE/MOTO, CAR↔CAR/VOITURE, VAN↔VAN/CAMIONNETTE, TRUCK, TRICYCLE).
  SCOOTER et BUS n'ont aucun équivalent dans resource-core (le document disait « rien à ajouter » : contredit par le code, point non tranché).
- Preuves : delivery-core 107 tests verts (dont `DeliveryPersonStatusTransitionTest`, `LogisticsVehicleTypeMappingTest`).

### tp-core : notes et fidélité (3.7, 3.9, H4, H11)
- `tnt_tp_ratings` : colonnes `rating_context VARCHAR(40)` et `subject_kind VARCHAR(20)` (CLIENT / DELIVERY_PERSON / RELAY_OPERATOR),
  nullables (changeset `gdd-l3-301-tp-rating-context-and-subject-kind`, fichier `gdd-l3-tp.yaml`). `ThirdPartyRating` et
  `RateThirdPartyCommand` portent les deux champs (constructeur/fabrique d'origine conservés).
- `RatingService.rate` exige désormais `rating:create` (au lieu de `actor:write`), `listRatings` exige `rating:read` (au lieu de `actor:read`).
  La note est enregistrée même si la partie notée n'a pas de profil client tp-core (livreur, opérateur relais, client Go) ;
  la moyenne de `TntClientProfile` n'est mise à jour que si ce profil existe.
- `LoyaltyService.getByThirdPartyId` exige `loyalty:read` (au lieu de `billing:read`). Nouveau `creditCompletedDelivery(cmd)` :
  chemin système sans `@RequirePermission`, réservé au consommateur Kafka (sans contexte de sécurité, `earn` y était toujours refusé).
  `updateLoyaltyTierOnProfile` rend vide (au lieu d'une erreur) quand le profil tp-core n'existe pas, conformément à sa javadoc.
- `DeliveryEventConsumer` (`tnt.delivery.mission.completed`) lit le corps produit par delivery-core
  (`{eventType, aggregateId, tenantId, occurredAt, payload:{…}}`) ou la forme plate historique : client = `senderId`
  (ajouté par L1 à `DeliveryCompletedEvent`) sinon `senderThirdPartyId` ; mission = `missionId` sinon `aggregateId` (id de livraison).
  Tant que L1 n'a pas fusionné `senderId`, aucun point n'est crédité pour les livraisons du cœur (message sans expéditeur).
- Unicité des notes (C3, changeset `gdd-l3-305-tp-rating-unique-per-subject-kind`) : la contrainte
  `uq_tnt_tp_rating_mission_rater` (mission, évaluateur) est remplacée par l'index unique
  `uq_tnt_tp_rating_mission_rater_kind` (mission, évaluateur, `COALESCE(subject_kind, '')`) ; `RatingService.rate`
  contrôle le même triplet (`existsByMissionIdAndRaterActorIdAndSubjectKind`). Un client note le livreur et le relais
  d'une même livraison ; une 2e note du même genre reste refusée.
- Crédit de fin de livraison idempotent (C4, changeset `gdd-l3-306-loyalty-transaction-unique-reference`) :
  `creditCompletedDelivery` écrit d'abord le mouvement dans `tnt_loyalty_transactions` (`external_ref = missionId`,
  index unique partiel `(loyalty_account_id, type, external_ref)`, `INSERT … ON CONFLICT DO NOTHING`) puis crédite ;
  un message rejoué ne crédite rien (résultat vide) et le consommateur ne recompte pas la livraison du profil tp-core.
  Constat : avant ce lot, aucun mouvement n'était persisté (l'adaptateur n'écrivait que le compte) ; seuls les crédits
  de fin de livraison le sont désormais (`LoyaltyAccountRepository.recordTransactionOnce`). `missionId` est exigé.
- `senderId` vérifié : sur le parcours « besoin » Go, c'est le `sub` du jeton (actorId) ; sur `POST` annonce,
  `AnnouncementAccessGuard.clientIdFor` impose l'appelant (staff excepté) : un actorId dans les deux cas.
- Preuves : tp-core 46 tests verts (`DeliveryEventConsumerTest`, `RatingServiceTest`, `LoyaltyServiceTest`).

### actor-core : moyenne de notes (3.9)
- `ActorRatingService.rateActor` exige `rating:create` (au lieu de `actor:write` ; tous les rôles porteurs de `actor:write` ont reçu
  `rating:create`, aucun accès perdu) et traite désormais `ActorType.CLIENT` (moyenne `ActorRating` de `client_profiles`).
  Effet de bord positif : `ProviderReviewApplicationService` (market) appelé par un CLIENT n'est plus refusé.
- Non fait dans ce lot (point arrêté) : colonnes professionnelles de `freelancer_profiles` (`commercial_name`, `commercial_register`,
  `taxpayer_number`, `registration_number`, `availability_mode`) et `client_profiles.default_address_id` (voir journal, points non tranchés).
- Preuves : actor-core 95 tests verts.

### Go-Freelancer : délégation synchrone au core (lot L3)
- Dépendance Maven ajoutée : `tnt-go-freelancer-point-back-core` → `tnt-tp-core` (L6-Bis → L2, sens autorisé).
- Évaluations : `EvaluationApplicationService` → `RatingService.rate` (tp-core) + `IRateActorUseCase` (actor-core) ;
  la table `evaluations` n'est plus écrite (dépôt Go supprimé). Analyse de sentiment conservée côté Go.
- Fidélité : `GofpClient.loyaltyStatus/totalOrders/rating` deviennent transitoires, lus de `LoyaltyService` et de
  `client_profiles` ; `LoyaltyStatus` (Go) et `computeLoyalty` supprimés.
- Statut freelancer : `GofpFreelancerService.updateStatus` applique `DeliveryPerson.transitionTo` (delivery-core) ;
  `GofpFreelancerRepositoryAdapter` complète chaque lecture (statut delivery-core, note actor-core) ; `FreelancerStatus`
  ne garde que le vocabulaire d'API et délègue sa machine à états à `DeliveryPersonStatus`.
- Véhicules : `VehicleRegistrationAdapter`, `IVehicleRegistrationPort`, `registerVehicle`, `RegisterFreelancerVehicleDTO`,
  `VehicleCreateRequest`, `VehicleClass` supprimés (critère 4 : plus de `UUID.randomUUID()` de repli).
- Migrations `changes/gdd-l3.xml` : 302 évaluations → `tnt_tp_ratings`, 303 comptes de fidélité, 304 moyennes
  recalculées (`ActorRating` des profils client/freelancer depuis `tnt_tp_ratings` par `subject_kind`, profils qui ont
  déjà une moyenne actor-core laissés et comptés ; `gofp_relay_points.rating` = moyenne des notes RELAY_OPERATOR du
  propriétaire), 310/311/312 ids (`gofp_users`, `gofp_clients`, `gofp_freelancers` ; 312 réécrit aussi
  `tnt_announcement_responses.delivery_person_id` et `tnt_deliveries.delivery_person_id`, C2), 320 statut d'agrément
  (copie REJECTED/REVOKED/SUSPENDED seulement : ne rétrograde personne). Diagnostics par `RAISE NOTICE`.
- Ids à la création (C1) : plus aucun id aléatoire. `gofp_users.id = coreUserId` (`GofpUserService`,
  `GofpUserProvisioningService`) ; `gofp_clients.id` = id du profil client actor-core (`coreClientId` s'il désigne un
  profil, sinon `ICreateClientProfileUseCase` trouve ou crée celui de l'acteur) ; `gofp_freelancers.id = coreFreelancerId`
  et `tnt_delivery_persons` créé au même id (PENDING) s'il manque.
- Évaluateur (C5) : l'acteur du jeton ; un `evaluatorId` qui ne le désigne pas est refusé (403), sauf personnel.
- `record-order` (C4) : no-op déprécié (200), le crédit vient du core seul.

### resource-core : véhicule personnel d'un livreur indépendant (décision utilisateur, lot L3 A)
- `VehicleType` : ajout de `SCOOTER` et `BUS` (`TRICYCLE` existait). Colonne `vehicle_type` en `VARCHAR(50)` : pas de DDL.
- `FreelancerVehicle` : organisation propriétaire (`ownerOrgId` / `freelancer_org_id`) **devenue optionnelle**, nouveau
  propriétaire acteur `ownerActorId` / `owner_actor_id` (l'un des deux obligatoire, contrainte
  `ck_tnt_fl_vehicles_owner`) ; changeset `gdd-l3-330-personal-vehicle-owner` dans `gdd-l3-resource.yaml`.
  **Champ à revoir** : le concept d'« organisation freelancer » (3 indépendants + un chef) pourrait être retiré, auquel
  cas l'acteur resterait le seul propriétaire et `freelancer_org_id` serait à supprimer.
- Capacité maximale `max_capacity_kg > 0` **toujours obligatoire**, véhicule personnel compris.
- Nouveaux cas d'usage : `AddPersonalVehicleUseCase` (création, 3 véhicules au plus par acteur, plaque unique toutes
  organisations confondues → `IllegalStateException`) et `UpdateFreelancerVehicleUseCase` (mise à jour type, marque,
  modèle, plaque, capacité, volume, carburant ; propriétaire, mission et documents conservés), exposé aussi en
  `PUT /api/resources/freelancer-orgs/{orgId}/fleet/vehicles/{vehicleId}`. `IllegalStateException` → 409 dans
  `ResourceExceptionHandler`.
- Go-Freelancer dépend maintenant de `tnt-resource-core` (L6-Bis → L4) : `CourierVehicleService.saveMyVehicle` écrit le
  véhicule core de façon synchrone (création ou mise à jour, reprise par plaque après une écriture partielle), puis la
  ligne locale (`core_vehicle_id`, photos, copie de lecture du type et de la plaque pour le matching).
  `gofreelancer_vehicles` disparaîtra à la clôture.
- Migration H10 `gdd-l3-331` (Go) : les `core_vehicle_id` fictifs (vers aucun véhicule core) sont remis à `NULL` et
  comptés. **Aucun véhicule n'est recréé** : aucune table Go ne porte de capacité, et la capacité est obligatoire ; les
  lignes restent en attente (comptées avec / sans plaque) jusqu'à la redéclaration par le livreur.
  **Décision utilisateur confirmée (H10, 2026-11-26)** : pour un véhicule existant sans capacité, le livreur la
  saisit à son prochain enregistrement (`PUT /me/vehicle`) ; aucune capacité par défaut n'est inventée.

### Agrément des livreurs par un administrateur (décision utilisateur, lot L3 B)
- `DeliveryPerson.projection` crée désormais un livreur **PENDING** (auparavant APPROVED d'office) ;
  `GofpFreelancerProjectionService` idem côté Go. L'agrément passe par la machine à états (`approve()`, route
  d'agrément Go `PATCH /api/v1/gofp/freelancers/{id}/status` ou delivery-core `…/delivery-persons/{id}/approve`).
  Les lignes existantes ne sont jamais modifiées par la projection ; la migration 320 ne rétrograde personne.
- À reprendre : `scripts/e2e/e2e-freelancer-courier.sh` comptait sur l'agrément d'office ; il lui faut une étape
  d'agrément par un administrateur avant l'abonnement du livreur (non modifié dans ce lot).

## Agrément des hubs relais dans le cœur (décision D-138, 2026-11-26)

Le hub relais du cœur (`tnt-geo-core`, `RelayHub`) reçoit un **statut d'agrément** distinct de son état d'exploitation :
`PENDING`, `APPROVED`, `SUSPENDED`, `REJECTED`, `REVOKED`. Il fait foi pour les hubs relais du cœur (Go-Freelancer, relais indépendants ; les relais côté Agency restent gérés par la plateforme Agency, D-143) :
un hub non `APPROVED` n'est pas proposé comme destination et refuse les nouveaux dépôts (D-140). Correction D-143 : Agency n'est pas concernée (une agence n'a pas de point relais).

## Routes d'agrément des relais dans le cœur et périmètre Agency (décisions D-141, D-143, 2026-11-26)

- D-141 : le cœur expose les routes d'agrément des hubs relais sous `relay:approve` (administrateur TiiBnTick) ; les
  routes Go délèguent au cœur.
- D-143 : une « agence » TiiBnTick Agency est une organisation commerciale avec ses antennes (siège, agences de ville
  ou de quartier, à la manière d'une compagnie de transport interurbain) ; **elle ne dispose pas de point relais**. Les
  hubs/relais enregistrés côté Agency (`tnt_hub_relais`, `agency_relay_hubs`) restent gérés par la plateforme Agency ;
  le Core ne les unifie pas avec les hubs relais du cœur et ne leur applique pas les règles d'agrément.

## Correctifs après intégration : propriétaires hérités (D-128) et révocation des relais (D-129) — 2026-11-26

- **geo-core** : `HubStatus.REVOKED` (terminal, `isTerminal()`, `canBeRevoked()`), `RelayHub.revoke(reason, admin)`
  (motif obligatoire, 1 000 caractères au plus ; rouvrir, fermer, ajouter un colis, mettre à jour l'occupation :
  `RelayHubStateException`, 409 ; un colis peut encore sortir). Colonnes `revocation_reason`, `revoked_at`,
  `revoked_by_actor_id` et contraintes (changeset `gdd-l4-418-relay-hub-revocation`). Cas d'usage
  `IRevokeRelayHubUseCase` (`RelayHubRevocationService`), route `POST /api/v1/geo/tenants/{tenantId}/hubs/{hubId}/revoke`.
  Deux ports sortants que les couches hautes implémentent : `IRelayHubCustodyPort` (colis en garde) et
  `IRelayHubRevocationListener` (effets produit). geo-core dépend désormais de `tnt-auth-core` et `tnt-roles-core`
  (couche L1, sens autorisé).
  Changement de comportement à connaître : `R2dbcRelayHubRepository.updateOccupancy` ne touche plus un hub `REVOKED`,
  et un hub `REVOKED` n'est pas « actif » (`findAllActive`).
- **inventory-core** : dépend de `tnt-geo-core` (L4 → L3) et fournit `HubCustodyAdapter` (entrées non retirées du hub).
- **roles-core** : permission `relay:revoke` (`TntPermission.RELAY_REVOKE`), donnée à aucun rôle canonique : seul
  `TNT_ADMIN` (`*`) la porte. Un hub d'agence se révoque donc aussi par l'administrateur de la plateforme seulement.
- **Go-Freelancer** : `GofpRelayRevocationApplier` (écouteur de révocation : relais local `REVOKED`, opérateur
  désactivé, notification + courriel) ; `AdminRelayPointUseCase.revokeRelayPoint(id, reason, adminActorId, loginUrl)`
  remplace `revokeRelayPoint(id, loginUrl)` et rend `RelayPointRevocation`. Un hub qui n'est pas un relais Go (hub
  d'agence) est ignoré par l'écouteur Go ; Agency n'a pas de point relais et n'est pas concernée (D-143).
- **Colis en garde** : listés (réponse + journal `WARN`), jamais déplacés. Retour ou transfert, événement Kafka et
  avertissement des propriétaires sont à brancher avec le cycle de vie du colis (lot L2 du chantier événements).
- **Migration D-128** : changeset Go `gdd-l4-417-legacy-owners-to-actor-ids` (voir journal technique).
- Correction : la route d'agrément Go citée plus haut est `PATCH /api/v1/gofp/freelancer-profiles/{id}/status`
  (`GofpFreelancerProfileController`), et non `/api/v1/gofp/freelancers/{id}/status`, qui n'existe pas.
- Fait : `scripts/e2e/e2e-freelancer-courier.sh` inclut l'étape d'agrément par un administrateur (étape `APPROBATION`).
- Suite D-134 / D-135 : changeset Go `gdd-l4-420-delete-orphan-address-links` (destructif, décidé : D-134) ; la
  révocation d'un relais Go est réservée à `APPROVED`/`SUSPENDED` (D-135) ; le hub du cœur n'est pas encore aligné
  (correspondance `HubStatus` à valider, voir la spec d'administration § 6.1). `AdminRelayPointApplicationService` :
  hub provisionné avec l'acteur du propriétaire comme opérateur et notifications à l'acteur de l'opérateur (H1), au
  lieu de l'id de profil freelancer.

### Agrément des hubs du cœur (D-138 à D-144) et Agency — 2026-11-26

Le statut d'agrément vit sur `tnt_geography.relay_hubs.approval_status` et vaut pour les points relais Go-Freelancer.
**Agency : rien à faire (D-143).** Une agence est une organisation commerciale à antennes, sans point relais ; ses
hubs (`agency_org.agency_relay_hubs`, `tnt_hub_relais` d'organization-core) restent gérés par la plateforme Agency,
avec leurs propres ouverture, fermeture, suspension et reprise. Ils ne sont pas dans `relay_hubs` : la garde de dépôt
D-140 de l'inventaire ne les trouve pas et ne les bloque pas, ce qui est voulu. Aucun module Agency n'a à lire ni à
écouter l'agrément du cœur.
