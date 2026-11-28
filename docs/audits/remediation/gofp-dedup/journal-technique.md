# Journal technique du dédoublonnage

| Lot | Contenu | Preuve |
|---|---|---|
| 1 | `DeliveryStatus`, `ResponseStatus`, `AnnouncementStatus` de Go-Freelancer supprimés au profit de ceux de `tnt-delivery-core` (+ `getValue`/`fromValue` ajoutés au cœur) ; 48 fichiers réécrits | delivery-core 68 tests, Go-Freelancer 406 tests verts |

## Lot L4 — paiement, notifications locales, adresses, relais/dépôts, tarification (2026-11-24)

| Étape | Contenu | Preuve |
|---|---|---|
| L4-1 paiement | Entité `Payment`, `PaymentRepository` (port + R2DBC + adaptateur) et enum `PaymentStatus` Go supprimés (aucune référence hors de ces fichiers, `grep` à vide). `PaymentMethod` Go conservé : il type encore `Subscription` / `RelayPointSubscription` (produit Go). La table `payments` n'est plus lue ni écrite ; elle n'est pas supprimée (changeset de clôture, H7). | `mvn -o test-compile` Go + modules bas : OK |
| L4-2 notifications | `Notification`, `NotificationAnnonce`, `NotificationLivraison` (entités), leurs 3 dépôts R2DBC + 3 ports + 3 adaptateurs, enums `NotificationType`/`NotificationStatus` Go et l'ancienne `NotificationStreamService` supprimés (15 fichiers). `NotificationService` n'écrit plus de ligne de fil : e-mail et push via `sendLocalized` (catalogue i18n, langue du compte) + événement Kafka `MatchingNotificationEvent` conservé. `NotificationStreamAdapter` délègue au flux realtime par utilisateur (Redis) au lieu de sinks en mémoire ; clé = id utilisateur Kernel du profil freelancer (id déjà utilisateur toléré). notify-core : types `ANNOUNCEMENT_MATCHED`, `RESPONSE_RECEIVED` ajoutés. Fil local : aucun lecteur trouvé (`grep` : seul `NotificationService` utilisait `NotificationRepository`), donc **pas de migration** des lignes `notifications` ; tables supprimées en clôture. | realtime 8 tests unitaires + 2 ITs Redis Testcontainers (`UserNotificationStreamIntegrationTest`) ; Go : `NotificationService*`, `NoLocalNotificationTableTest`, `NotificationStream*` (contrat SSE octet par octet inchangé), `RelayDepositServiceTest` : 32 tests verts |
| L4-3 adresses | geo-core : agrégat `Address` + `AddressLink`, `IManageAddressUseCase` (create/update/delete/findById/findAll/search + link/unlink/findLinksByOwner/findLinksByAddress), `AddressService`, `R2dbcAddressRepository` (SQL, upsert, recherche ILIKE sur rue + repère avec `%`/`_` littéraux), tables `tnt_geography.tnt_addresses` / `tnt_address_links` (changeset `gdd-l4-401-geo-addresses-schema`). Go : `AddressApplicationService` délègue au cœur ; `AddressEntity` n'est plus une entité (modèle de lecture, nom gardé pour ne pas toucher aux fichiers L1/L2) ; `AddressReactiveRepository` devient une interface de lecture (`findById`) implémentée par `GeoCoreAddressReader` ; `PersonAddress` + dépôts supprimés (code mort, et sa colonne `address_type` n'existe dans aucun changeset). Le type d'usage (`AddressType`) = libellé de lien possédé par l'appelant authentifié. Migration Go `gdd-l4-410-addresses-to-core` (ids conservés, texte vide → NULL, coordonnées incomplètes/hors bornes → NULL) et `gdd-l4-411-address-links-to-core` (owner_id = person_id tel quel : conversion vers actorId **non faite**, dépend de L3/H1). | geo : `R2dbcAddressRepositoryIntegrationTest` 5/5 (PostGIS Testcontainers) ; Go : `AddressSearchTest` 7/7 + `RelayPointAccessControllerTest` 9, `DeliveryApplicationServiceTest` 15, `CourierVehicleJourneyTest` 7, `NeedAnnouncementLinkTest` 15 verts ; `Gdd4AddressMigrationIntegrationTest` 1/1 : `count(tnt_addresses) = count(addresses)` (graines + 4 lignes), mêmes ids, liens = `count(person_addresses)`, rejeu idempotent |
| L4-5 dépôts relais | `relay_deposits` n'est plus lue ni écrite : le dépôt **est** l'entrée d'inventaire `HubPackageEntry` (même id, propriétaire = client, frais/pénalité/devise portés par l'entrée). `RelayDeposit` devient une vue sans table (`RelayDepositViews.of`) ; port `RelayDepositRepository` réduit à la lecture (`findById`, `findByRelayPointId`, `findByClientId`) et implémenté par `InventoryRelayDepositReader` (sur `FindHubPackagesUseCase`) ; `RelayDepositRepositoryAdapter` + dépôt Spring Data supprimés. `RelayDepositService` : dépôt = `depositPackage` avec propriétaire + frais (2 décimales, XAF), retrait = `pickupPackage(trackingCode de l'entrée)` puis relecture de l'entrée. Changesets Go `gdd-l4-412-relay-deposits-to-inventory` (dépôt déjà reflété dans l'inventaire → complète l'entrée existante, et `package_id` ← `relay_deposits.packet_id` ; sinon INSERT avec le même id, tenant du hub geo, code de suivi = id du colis, ou id du dépôt si ce code est déjà pris ; hub inconnu de geo → non copié, NOTICE) et `gdd-l4-413-relay-hub-occupancy` (occupation des hubs Go-Freelancer = entrées actives, règle de `RelayHub.updateOccupancy` : FULL si = capacité sinon ACTIVE, hubs fermés : statut conservé, hub en surcharge : laissé tel quel + NOTICE). | Go : `RelayDepositServiceTest` 11/11 (dont dépôt avec propriétaire/frais, règlement du frais de l'entrée au retrait, lecture en vue), `RelayDepositRulesTest` 6/6, `RelayPointAccessControllerTest` 9/9 ; inventory : `HubInventoryApplicationServiceTest` 8/8 ; `Gdd4RelayMigrationIntegrationTest` 1/1 (PostGIS) : dépôt reflété complété sans doublon, id de besoin corrigé en id de colis, dépôt en cours copié avec le même id, dépôt retiré copié retiré, hub inconnu non copié, 0 dépôt en cours perdu, occupation 3/3 FULL, rejeu idempotent |
| L4-6 paiements | Changeset Go `gdd-l4-414-payments-to-settlements` (3.12 e) : les lignes `payments` **PAID** (s'il y en a) deviennent des `billing.wallet_settlements` (même id, `reference_type='DELIVERY'`, `reference_id=delivery_id`, tenant de la livraison core de même id, CASH → `CASH`/`CASH_COLLECTED`, autre moyen → `WALLET_TRANSFER`/`UNPAID` (3.12 h), taux 5 % (H5), commission et statut de commission calculés comme `SettlementService` : NONE tant qu'impayé, DUE une fois encaissé). Non copiées (NOTICE) : lignes non PAID, livraison inconnue de `tnt_deliveries`, livraison déjà réglée (l'unicité core protège du double règlement). Le taux reste la constante `DeliveryPaymentApplicationService.COMMISSION_RATE = 0.05` (H5 : inchangé ; point d'extension non implémenté, voir points non tranchés). | `Gdd4PaymentMigrationIntegrationTest` 1/1 (PostGIS) : CASH → CASH_COLLECTED commission 50/1000 DUE ; ORANGE_MONEY → WALLET_TRANSFER UNPAID commission NONE, `kernel_reference` repris ; PENDING / livraison inconnue / déjà réglée : non copiés ; règlement existant intact ; rejeu idempotent |
| L4-7 commission (H5) | Point d'extension seulement : port `CommissionRatePort.deliveryCommissionRate(tenantId, deliveryId)`, adaptateur par défaut `FixedCommissionRateAdapter` (5 %, la constante `COMMISSION_RATE`). `DeliveryPaymentApplicationService` lit le taux par ce port (setter optionnel ; vide/erreur → 5 %, le transfert Kernel ayant déjà eu lieu). Le chemin espèces de `DeliveryStatusApplicationService` (fichier non possédé par L4) lit encore la constante directement. | `DeliveryPaymentFlowIntegrationTest` 10/10 (dont nouveau test : défaut 5 %, taux 10 % via le port → commission 300, port en erreur → 150), `DeliveredButUnpaidTest` 4/4 |
| L4-8 capacité relais (décision du modérateur après L1) | `GofpRelayPointR2dbcRepository.findAllWithSufficientCapacity` ne lit plus `relay_deposits` ni `packets` : volume occupé = colis du cœur (`tnt_parcels`, dimensions en **cm**, ÷ 1e6 → m³) des entrées d'inventaire non retirées du hub (`tnt_hub_package_entries.package_id` = id Parcel) ; volume du hangar inchangé (unité du relais). Requête exposée en constante `SUFFICIENT_CAPACITY_SQL` pour la tester. Reste une lecture de `packets` hors L4 : `DeliveryRepository` (Go) l.47 (requête de capacité véhicule, fichier L1). | `Gdd4RelayMigrationIntegrationTest` 2/2 (PostGIS ; nouveau test : hangar 100×100×100 cm = 1 m³, colis actif 50×50×100 cm = 0,25 m³, colis retiré ignoré → retenu pour 0,75 m³, exclu pour 0,76 m³) |
| L4-9 re-clé inventaire (revue L1, C5) | `gdd-l4-412` re-clé en plus **toute** entrée `tnt_hub_package_entries` encore indexée sur un besoin (`package_id` = `delivery_needs.id`, ou `tracking_code` = id du besoin) sur l'id du colis du cœur (`delivery_needs.packet_id`, = id Parcel depuis `gdd-l1-104/106`) ; le code de suivi devient l'id du colis s'il était l'id du besoin et que ce code est libre (une seule entrée par colis le prend), sinon il est gardé. Idempotent. Le retrait n'appelle plus `pickupPackage(packetId)` mais `pickupPackage(code de suivi de l'entrée)` : il fonctionne avant comme après la re-clé. | `Gdd4RelayMigrationIntegrationTest` 2/2 (cas ajouté : entrée ancienne au besoin sans ligne `relay_deposits` → `package_id` et `tracking_code` = id du colis ; aucune entrée restante au besoin ; occupation 4/4 FULL) ; porte `LiquibaseMasterChangelogIntegrationTest` 3/3 |
| L4-10 tarification (décision utilisateur : option b) | Reprise unique au démarrage `LegacyPricingBackfillService` (déclenchée par `LegacyPricingBackfillRunner` sur `ApplicationReadyEvent`, en arrière-plan : le démarrage n'est jamais bloqué ; erreurs journalisées + métriques `gofp.pricing.legacy.backfill.runs{outcome=done/skipped/failed}` et `.policies{status=active/draft/failed}`). Comptage 3.16 (g) journalisé AVANT et APRÈS (grilles héritées, avec / sans politique de grille). Une grille dont le propriétaire a déjà une politique `FREELANCER-GRID` (tout statut) est ignorée ; sinon politique créée dans le tenant système avec les mêmes valeurs et activée — laissée DRAFT si le propriétaire a déjà une autre politique ACTIVE valide (l'activer la rendrait la plus récente et changerait ses devis ; comptée). Exécution unique entre instances : table `gofp_startup_jobs` (changeset `gdd-l4-416-startup-jobs`, clé primaire = nom du travail, prise atomique `INSERT … ON CONFLICT … WHERE status='FAILED' OR RUNNING périmé (30 min)`) ; DONE n'est jamais rejoué, FAILED est repris au démarrage suivant (idempotent). `FreelancerPriceQuoteService` ne lit plus `delivery_person_pricing` (politique active la plus récente, sinon barème plateforme) ; `FreelancerPricingPolicyApplicationService` ne lit ni n'écrit plus le miroir ; sans tenant dans le contexte, la grille est créée dans le tenant système (règle de `TenantContextHolder`) au lieu de rester dans la table héritée. Port `FreelancerPricingRepository` réduit à `findAll()` (lecture pour la reprise). Fenêtre connue : entre le démarrage et la fin de la reprise, un livreur dont la grille n'est encore qu'héritée reçoit le barème plateforme. | Go : `LegacyPricingBackfillServiceTest` 5/5 (grille héritée → politique ACTIVE tenant système, 4 devis identiques montant + détail + devise ; 2e démarrage sans effet ; politique de grille existante (archivée) ignorée ; autre politique active → DRAFT, devis inchangé ; échec → FAILED puis repris ; travail tenu ailleurs → rien), `FreelancerBillingPricingTest` 12/12, `CourierVehicleJourneyTest` 7/7, `FreelancerPricingPersistenceIntegrationTest` 3/3 ; `Gdd4PricingBackfillIntegrationTest` 1/1 (PostGIS, master Liquibase complet, vrais `BillingPolicyService` / `PricingEngineService` / verrou R2DBC : 4 devis identiques avant/après, ligne `gofp_startup_jobs` DONE, 2e exécution sans effet) |
| L4-11 fusion L1 + commission espèces (revue) | `feat/single-system-tenant` (L1, 3b2955c3) fusionnée : sections des trois documents gardées toutes deux. Tests L1 de `RelayDepositServiceTest` (`markAsRetrieved_depositKeyedOnTheParcel_findsTheDelivery`, ajout de 3eefe75c) réécrits : le dépôt est lu par `findHubPackagesUseCase.findEntry` (plus de `relayDepositRepository.findById`). `DeliveryPaymentFlowIntegrationTest` (modifié des deux côtés) : fusion automatique, vert. Chemin espèces de `DeliveryStatusApplicationService` : le taux vient de `CommissionRatePort` (setter optionnel, vide/erreur/absent → 5 %), comme le chemin portefeuille. `KafkaAsyncWiringTest` (bootstrap) ne compilait plus sur la branche fusionnée (il simulait `CoreDeliveryStatusProjector`, supprimé par L1) : la simulation est retirée et le test vérifie désormais `DeliveryEventNotifier.onMissionStatusChanged` (seul effet restant du consommateur Go) ; non exécuté ici (courtier Kafka). | Go : 473 tests verts (dont `DeliveredButUnpaidTest` 5/5 avec le nouveau cas « taux 12 % via le port », `RelayDepositServiceTest`, `DeliveryPaymentFlowIntegrationTest`) |
| L4-12 FK adresses (revue B1, bloquant) | Changeset `gdd-l4-415-drop-address-foreign-keys` : supprime **toutes** les clés étrangères vers `public.addresses`, quels que soient table et nom générés (boucle `pg_constraint`, modèle `gdd-l1-105`) : `delivery_needs` (pickup/delivery, RESTRICT), `announcements`, `gofp_users`/`gofp_clients`/`gofp_freelancers`/`gofp_relay_points`, `person_addresses`, etc. Les ids d'adresse viennent désormais du carnet du cœur (`tnt_addresses`, mêmes ids pour l'existant). Table `addresses` et ses lignes conservées (H7). Rejouable. | `Gdd4AddressMigrationIntegrationTest.recordsReferToCoreAddresses` (PostGIS, master complet) : les FK existent avant, aucune après ; un besoin est créé avec deux adresses présentes **uniquement** dans `tnt_addresses`, de même pour chaque ancienne table référente ; rien n'est écrit dans `addresses` |
| L4-13 corrections de migration (revue B2–B5) | Changesets non publiés corrigés sur place (branche non fusionnée). **B2** `gdd-l4-414` : un paiement PAID reste payé — CASH → `CASH`/`CASH_COLLECTED` (l'état « payé » des espèces dans le modèle de règlement, celui qu'écrit `SettlementService` pour une remise en espèces), `WALLET`/`KERNEL_WALLET`/`WALLET_TRANSFER` → `WALLET_TRANSFER`/`PAID`, tout autre moyen (MOBILE_MONEY, ORANGE_MONEY, carte…) → `EXTERNAL_PROVIDER`/`PAID` ; `paid_at = payments.paid_at` (date de création s'il manque), espèces comprises ; commission DUE si > 0 (règle de `SettlementService` pour de l'argent reçu). **B3** `gdd-l4-411` : libellé du lien = `addresses.type` (jointure `addresses`, vide → NULL) au lieu d'une colonne `person_addresses.address_type` inexistante. **B4** `gdd-l4-401` (geo) : `street`, `landmark`, `district`, `city`, `region`, `country`, `postal_code` et `tnt_address_links.label` en `TEXT` (les colonnes source sont des VARCHAR sans longueur). **B5** `gdd-l4-412` : seconde NOTICE comptant les dépôts dont le hub est connu mais qui n'ont ni entrée d'inventaire ni rapprochement (conflit d'insertion, code de suivi pris). | `Gdd4PaymentMigrationIntegrationTest` 1/1 (CASH → CASH_COLLECTED DUE `paid_at` repris ; ORANGE_MONEY → EXTERNAL_PROVIDER PAID DUE `paid_at` repris, `kernel_reference` repris ; kernel_wallet sans `paid_at` → WALLET_TRANSFER PAID, `paid_at` = création ; PENDING / livraison inconnue / déjà réglée non copiés ; rejeu idempotent) ; `Gdd4AddressMigrationIntegrationTest.addressesAndLinksAreMigrated` (lien sur adresse par défaut → label PRIMARY, adresse SECONDARY → label SECONDARY ; rue de 700+ caractères, repère, quartier > 255 et code postal > 50 recopiés entiers) ; `Gdd4RelayMigrationIntegrationTest` vert |

**Lot L4 — points non tranchés et limites (à arbitrer)**

- **Résolu (L4-10, option b retenue)** — Tarification (3.16 e, critère 7) : `FreelancerPriceQuoteService` lisait **encore** `delivery_person_pricing` en repli, et
  `FreelancerPricingPolicyApplicationService` écrit encore le miroir. Le couper sans perte demande une politique billing pour
  chaque ligne héritée ; or ces lignes n'ont pas de tenant (le code les laisse « jusqu'à une mise à jour avec tenant ») et le
  tenant système n'est pas connu de Liquibase. Options : (a) paramètre Liquibase du tenant système + changeset SQL qui crée
  les politiques ; (b) reprise Java unique au démarrage (`newPolicy` + `createPolicy` dans le tenant système), puis coupure ;
  (c) création paresseuse au premier devis. Recommandation : (b), avec le comptage de 3.16 (g) avant coupure.
- Alignement `gofp_relay_points.id` → id du `RelayHub` (3.15 e) : non fait. La création (`GofpRelayPointService.createOrUpdate`,
  fichier non attribué à L4) tire encore un id aléatoire, et les colonnes à repointer sont dans des tables d'autres lots
  (`delivery_needs.target_relay_point_id`, `announcements.destination_relay_point_id`, `opening_hours.relay_point_id`).
  Le code résout déjà les deux ids. Recommandation : changeset de repointage + `rp.setId(rp.getCoreRelayPointId())` à la
  création, dans un lot qui possède ces fichiers.
- `RelayHub` geo : dimensions de stockage / `deposits_quota` (3.15 c) non ajoutés : le document laisse le choix
  « dimensions + unité **ou** capacité volumique », et le quota relève de l'abonnement du relais qui reste côté Go (3.15 d).
- Statut du relais (H2) : `DeliveryPersonStatus` + `REVOKED` non ajoutés ici (même ajout attendu de L3 pour les livreurs :
  conflit certain). À faire une seule fois, dans L3.
- **Résolu (L4-11)** — Commission : le chemin espèces de `DeliveryStatusApplicationService` lisait la constante 5 % au lieu de
  `CommissionRatePort`.
- **Résolu (L4-13, B2)** — Paiements hérités non CASH migrés en `WALLET_TRANSFER/UNPAID` (lecture littérale de 3.12 h) : une ligne PAID non espèces
  redeviendrait « à payer ». La table est réputée vide ; vérifier par comptage avant déploiement.
- Libellés du flux SSE de mise en relation (`title`/`message` en français, contrat mobile) conservés.
- Liens d'adresse : `owner_id` = `person_id` hérité (conversion vers actorId : L3/H1).
- Occupation geo : hub dont les entrées actives dépassent la capacité laissé tel quel (NOTICE `gdd-l4-413`).

### Lot L4 — fusion avec L1-L3 et la fiabilité Kafka (2026-11-26)
- Conflits : `README.md` (lien `validations-administrateur.md` et section « Reports acceptés » gardés) et
  `KafkaAsyncWiringTest` (les deux côtés retiraient le projecteur supprimé par L1 ; l'assertion la plus stricte,
  code de suivi égal à celui de l'événement, est gardée).
- Correctif de test révélé par la fusion : la configuration imbriquée de `Gdd4PricingBackfillIntegrationTest`
  était une `@Configuration` du paquet `com.yowyob.tiibntick`, donc ramassée par le scan de `TiiBnTickApplication`
  (pas de `TypeExcludeFilter`) : son bean `pricingEngine` empêchait le démarrage de tous les tests à contexte
  complet de tnt-bootstrap (`KafkaAsyncWiringTest`, `TiiBnTickApplicationTest`…). Elle n'est plus annotée et
  n'est enregistrée que par `@SpringBootTest(classes = …)`.
- Point resté ouvert après L3 : les liens d'adresse migrés (`gdd-l4-411`) gardent `owner_id = person_id` hérité ;
  L3 a réaligné `gofp_users` / `gofp_clients` / `gofp_freelancers` mais pas `persons`, alors que le code lit les liens
  par l'utilisateur du jeton. Même remarque pour le client des dépôts relais hérités. Conversion à décider.

| L1 | Livraison et colis. Cœur : 6 classes ajoutées (`ParcelUseCase`, `ParcelService`, `ParcelRepository` + adaptateur, `RegisterParcelCommand`, `RecordAgreedTermsCommand`), 16 modifiées, 5 classes de test ajoutées et 2 étendues. Go : `CoreDeliveryProjection`, `CoreDeliveryStatusProjector` (+ test, 6 tests), enum `DeliveryUrgency` supprimés ; `CoreMissionStatusProjectionConsumer` renommé `CoreMissionStatusConsumer` ; `ParcelPort` + `ParcelPortAdapter`, `ParcelDetails`, `CoreDeliveryAssembler`, `DeliveryUrgencyInputDeserializer` ajoutés ; 50 fichiers Go touchés au total (code + tests) | `mvn -o test -pl logistics/tnt-delivery-core,coreBackend/tnt-go-freelancer-point-back-core` : delivery-core 87/87, Go-Freelancer 431/431 (406 − 6 du projecteur supprimé + 31 nouveaux). ITs (`-Pintegration-tests`, PostGIS `tnt-pg-lot1`) : `Gdd1DeliveryMigrationIntegrationTest` 2/2, `ParcelMigrationIntegrationTest` 2/2, `LiquibaseMasterChangelogIntegrationTest` 3/3 |
| L2 | Annonce Go = annonce du cœur uniquement : miroir `announcements` et repli `announcement_subscriptions` retirés du code ; compléments (expéditeur, e-mail destinataire, préférences, distance/durée, relais, signature, frais) portés par le cœur ; besoin relié par référence d'origine ; statut/livraison/livreur du besoin dérivés ; `delivery_needs.packet_id` → `parcel_id` | delivery-core 73, Go-Freelancer 444, link-back 4 tests verts ; ITs Go 2+7+9 et bootstrap `LiquibaseMasterChangelogIntegrationTest` 3/3, `Gdd2AnnouncementMigrationIntegrationTest` 1/1, `Gdd2NeedMigrationIntegrationTest` 1/1, `GoNeedAnnouncementMigrationIntegrationTest` 1/1 |

### Lot L1 — détail (2026-11-25, commits 19ad95e3, 251e434d, 174f691a)

- Quoi : la ligne `deliveries` est une extension de la livraison du cœur (même id). Statut, livreur, heures
  réelles, prix et fenêtres sont lus dans le cœur (champs transitoires remplis par
  `GofpDeliveryRepositoryAdapter`) et ne sont plus jamais écrits côté Go. Les requêtes par livreur ou par
  statut, les compteurs et le volume occupé du coffre sont répondus par le cœur. Les trois méthodes `packets`
  de `DeliveryNeedApplicationService` passent par `ParcelPort`. L'id du colis suit le besoin, l'annonce, la
  livraison et le dépôt relais. `UNKNOWN_DRIVER` et `ensureInTransit` sont conservés.
- Ajouts au core : voir `agency-et-modules.md`, section Lot L1.
- Impact API : addition (voir `mobile-bff.md`, section Lot L1).
- Changesets : `gdd-l1-101`, `-102`, `-110` (delivery-core, `gdd-l1-delivery.yaml`) ; `gdd-l1-103` à `-107`
  (Go, `changes/gdd-l1.xml` + `20261125_gdd_l1_1NN_*.sql`). Auteur et label `gdd-l1`, rejouables, sans
  `validCheckSum ANY`. Aucune colonne supprimée (H7). Les numéros Go commencent à 103 car 101, 102 et 110 sont
  pris côté delivery-core (l'esquisse du document cible numérotait la copie `packets` en 102).
- Diagnostic de migration (vérifié par les ITs sur des données construites, pas sur une copie de production) :
  - `packets` → `tnt_parcels` : tous les paquets, orphelins compris, gardent leur id ; un colis déjà présent
    dans le cœur n'est pas écrasé ; un poids ou une dimension absent ou ≤ 0 devient 1 kg / 10 cm ; une
    épaisseur ≤ 0 devient NULL ; `designation = COALESCE(designation, title)`.
  - FK `delivery_needs.packet_id` et `relay_deposits.packet_id` vers `packets` : 2 avant, 0 après.
  - `relay_deposits` : un dépôt enregistré avec un id de besoin prend l'id du colis de ce besoin ; si le besoin
    n'a pas de colis, il garde l'id du besoin (le retrait le retrouve toujours par besoin). La FK vers `packets`
    rendait ces lignes impossibles sans contournement : en production, 106 ne devrait rien modifier.
  - `deliveries` → `tnt_deliveries` : prix (si ≥ 0), devise de l'annonce du cœur (si ≤ 3 caractères), fenêtres
    lues en UTC ; seules les lignes dont l'id existe dans le cœur sont touchées ; une valeur déjà présente dans
    le cœur n'est jamais écrasée. Les lignes Go sans contrepartie cœur restent telles quelles ; requête de
    comptage : `SELECT count(*) FROM deliveries g WHERE NOT EXISTS (SELECT 1 FROM tnt_deliveries d WHERE d.id = g.id)`
    (1 ligne sur le jeu de test ; non mesuré en production).
- Limites : aucune migration exécutée sur une copie de la base de production. La lecture des fenêtres
  `TIMESTAMP` comme UTC suppose que l'application les écrivait en UTC (non vérifié en production).

### Lot L1 — corrections après revue (2026-11-25)

- **C1, retrait au relais.** Un dépôt est indexé sur l'id du colis de la livraison du cœur. Le retrait
  retrouve désormais la livraison par `tnt_deliveries.parcel_id`, à travers delivery-core
  (`DeliveryQueryUseCase.findDeliveriesByParcelIdAnyTenant`, port Go `findStatesByParcelAnyTenant`), sans SQL
  Go sur les tables du cœur. Il garde en second recours la jointure par `delivery_needs.packet_id`, pour les
  dépôts re-clés par `gdd-l1-106`. Cela couvre un besoin créé sans colis (le cœur a créé son propre colis) et
  un besoin publié avant L1 (le cœur a copié le colis du besoin sous un autre id).
- **C2, prix perdu.** `recordAgreedTerms` est appelé avant l'écriture de la ligne Go et son erreur est
  propagée : la sélection échoue, aucune ligne Go ni code de remise n'est créé. Le port
  `IDeliveryLifecyclePort` est obligatoire dans `AnnouncementApplicationService` (constructeur). Limite : la
  sélection déjà faite dans le cœur n'est pas annulée (pas de transaction distribuée).
- **C3, unité du volume du coffre.** `sumOccupiedVolumeM3ByFreelancerId` lisait `packets` en mètres
  (`SUM(length * width * height)`) ; il lit maintenant les dimensions du colis du cœur en cm et divise par
  1e6. Les volumes occupés sont donc 1 000 000 fois plus petits qu'avant pour les mêmes colis. Consommateurs
  touchés : `MatchingUseCase:368`, `SubscriptionConfirmationConsumer:236`.
- **C4, régression connue entre la fusion de L1 et celle de L4.** `GofpRelayPointR2dbcRepository.java:51`
  (fichier L4, requête `findAllWithSufficientCapacity`) joint encore `packets`. Les dépôts créés après L1 sont
  indexés sur des ids de colis du cœur, absents de `packets` : ils n'entrent plus dans le calcul de capacité
  des points relais, qui surestime donc la place libre. Les dépôts re-clés par `gdd-l1-106` sur un colis
  migré retrouvent leur ligne `packets` et sont comptés avec les dimensions lues en mètres. **Corrigé par L4**
  (réécriture de la requête sur `tnt_parcels` en cm / 1e6, ou sur `tnt_hub_package_entries`).
- **N+1 (dette, non bloquant).** Les listes (`findAllByFreelancerId`, `findAllByStatus`,
  `findActiveByFreelancerId`, volume occupé) sont déjà groupées : un appel au cœur, puis un seul `findAllById`
  Go. `hydrated()` fait un appel au cœur par ligne, mais seules les lectures d'une ligne l'utilisent
  (`findById`, `findByAnnouncementId`, `findByDeliveryNeedId`, second recours de `findByParcelId`), et aucun
  appelant ne les enchaîne en boucle aujourd'hui. Si une boucle apparaît, ajouter une lecture par lot d'ids
  dans delivery-core.
- **M1 (2026-11-26) — élection rejouable.** Si l'annonce est déjà `ASSIGNED` sur le même `responseId` avec
  `createdDeliveryId`, `assignResponse` ne rappelle plus `selectResponse` et rejoue la suite de façon
  idempotente : prix et créneaux inscrits dans le cœur s'ils n'y sont pas encore (déjà présents → rien n'est
  réécrit, les créneaux ne glissent pas), ligne Go créée si absente, codes de remise créés s'ils sont absents.
  L'échec d'inscription du prix renvoie une 503 `AGREED_TERMS_NOT_RECORDED` qui invite à refaire la même
  sélection. Tests : échec du prix puis nouvel essai qui réussit (un seul `selectResponse`), rejeu d'une
  sélection terminée sans aucune écriture, réponse différente = pas un rejeu.
- **Garde-fou inter-tenants.** `ParcelLookupCallersArchitectureTest` (ArchUnit, tnt-bootstrap) interdit
  tout appelant de `findDeliveriesByParcelIdAnyTenant`, `findStatesByParcelAnyTenant`, `findByParcelIdNoTenant`
  et du port Go `DeliveryRepository.findByParcelId` hors du chemin de retrait relais
  (`RelayDepositService` → `GofpDeliveryRepositoryAdapter` → `DeliveryLifecyclePortAdapter` →
  `DeliveryQueryService` → `DeliveryRepositoryAdapter`). La javadoc de chacune de ces méthodes renvoie à ce test.
- **Régression L1 corrigée dans tnt-bootstrap.** `KafkaAsyncWiringTest` simulait encore
  `CoreDeliveryStatusProjector`, supprimé par L1 : les sources de test de tnt-bootstrap ne compilaient plus.
  Le test vérifie maintenant que `CoreMissionStatusConsumer` appelle `DeliveryEventNotifier.onMissionStatusChanged`.
  Résultat : 18/18 sur un vrai broker Kafka, avec PostgreSQL et Redis réels.


## Lot L2 — annonces et besoins de livraison (2026-11-25, commits d71d7516, b43af5dd, 798a7ef3)
- Quoi : suppression du double stockage de l'annonce (architecture-cible §3.3, §3.4) ; le besoin de livraison
  ne garde que ses données produit + `announcement_id` / `parcel_id`.
- Changesets (label `gdd-l2`) :
  - `gdd-l2-201-announcement-details` (delivery-core) : 14 colonnes optionnelles sur `tnt_delivery_announcements`.
  - `gdd-l2-202-backfill-announcement-details` (gofp) : recopie miroir → cœur, même id, `COALESCE` (n'écrase
    jamais), noms repliés, `delivery_need_id` → `origin_ref_type='DELIVERY_NEED'` / `origin_ref_id`.
    Préconditions `MARK_RAN` (tables/colonnes absentes : base reconstruite par label).
  - `gdd-l2-203-announcement-origin-unique` (delivery-core) : index unique partiel `uq_tnt_ann_origin_ref`
    + `idx_tnt_announcement_responses_person`. delivery-master passe avant gofp-master : 203 s'exécute donc
    avant 202 ; le miroir ne contient qu'une annonce par besoin (`uq_announcements_delivery_need_id`, lot C-43),
    le backfill ne peut donc pas violer l'index.
  - `gdd-l2-204-announcements-relax-not-null` (gofp) : `pickup_address_id` / `delivery_address_id` du miroir
    nullables (table plus écrite).
  - `gdd-l2-210-delivery-needs-core-refs` (gofp) : `announcement_id`, `parcel_id` (+ copie de `packet_id`,
    décision du modérateur après L1), `status` nullable, index `idx_delivery_needs_announcement_id`.
  - `gdd-l2-211-delivery-needs-announcement-backfill` (gofp) : `announcement_id` des besoins déjà publiés.
- Diagnostic à joindre au rapport de migration (lignes du miroir sans annonce du cœur, laissées telles quelles,
  supprimées avec la table en clôture) :
  `SELECT count(*) FROM announcements l WHERE NOT EXISTS (SELECT 1 FROM tnt_delivery_announcements a WHERE a.id = l.id);`
- Statut dérivé du besoin (`DeliveryNeedStatusPolicy`, figé par `DeliveryNeedStatusPolicyTest`, 20 cas) :
  sans annonce / DRAFT / PUBLISHED / IN_NEGOTIATION → PENDING ; annonce CANCELLED → CANCELLED ; COMPLETED →
  COMPLETED ; ASSIGNED + livraison absente ou CREATED → ASSIGNED, DELIVERED → COMPLETED, CANCELLED → CANCELLED,
  autre statut de livraison → IN_PROGRESS. `ACCEPTED` n'est jamais produit.
- Preuves : tests unitaires (reactor `-am`) delivery-core 73/73, Go-Freelancer 444/444, link-back 4/4 ;
  ITs Go réelles (Postgres Testcontainers) `AnnouncementCoreRoundTripIntegrationTest` 2/2,
  `DeliveryCoreConvergenceIntegrationTest` 7/7, `DeliveryPaymentFlowIntegrationTest` 9/9 ; ITs bootstrap `LiquibaseMasterChangelogIntegrationTest` 3/3, `Gdd2AnnouncementMigrationIntegrationTest` 1/1, `Gdd2NeedMigrationIntegrationTest` 1/1, `GoNeedAnnouncementMigrationIntegrationTest` 1/1.
- Restes / limites :
  - Clôture : supprimer les tables `announcements`, `announcement_subscriptions` et les colonnes
    `delivery_needs.packet_id`, `status`, `delivery_id`, `assigned_freelancer_id` ; supprimer
    `ensureLegacyUser` avec la table `users` (§3.4 e), conservé ici.
  - `SubscriptionConfirmationConsumer` (Kafka) lit/écrit encore `announcement_subscriptions` : orphelin, laissé
    en place (pas de décision dans l'architecture cible).
  - Façade `IAnnouncementRepository` : hors requête HTTP (Kafka), repli sur `SYSTEM_TENANT` comme le reste du module.
  - Prénom composé découpé au premier espace ; `destination_hub_id` reçoit des ids de points relais Go (à
    revoir avec L4) ; désignation / épaisseur de `PackageSpecification` non câblées (L1).

### Lot L2 — fusion de L1 et corrections après revue (2026-11-26)
- Fusion de `feat/single-system-tenant` (L1) : 12 fichiers en conflit, résolus selon la consigne B2. Gardé de L1 :
  `ParcelPort`, la reprise du colis existant (`withParcelId`, `existingParcel` dans `toCreateCommand`), la lecture
  des livraisons par le cœur (`DeliveryRepository` Go, `sumOccupiedVolumeM3ByFreelancerId`), le port
  `deliveryLifecycle` obligatoire et le prix enregistré dans le cœur avant la ligne Go (C2). Gardé de L2 :
  `parcel_id` (et `findByParcelId` lit `n.parcel_id`), `AnnouncementExtras`, la référence d'origine,
  `sender` / `details`. Ma requête de volume qui joignait `packets` est supprimée.
- B1 : repli sur les colonnes héritées. `DeliveryNeed` lit `status`, `delivery_id`, `assigned_freelancer_id` en
  lecture seule (`storedStatus`, `storedDeliveryId`, `storedAssignedFreelancerId`, `@ReadOnlyProperty` : jamais
  dans un INSERT ni un UPDATE). `DeliveryNeedStateService` : si le cœur n'a pas de livraison pour le besoin (pas
  d'annonce, ou annonce sans livraison créée) et que la dérivation contredit un statut stocké `ASSIGNED` /
  `IN_PROGRESS` / `COMPLETED`, les valeurs stockées sont rendues ; le livreur stocké (`gofp_freelancers.id`) est
  traduit en id du cœur s'il est connu. Dès que le cœur a une livraison, la dérivation gagne.
  Preuves : `DeliveryNeedLegacyStateTest` (7 cas), `DeliveryNeedLegacyColumnsIntegrationTest` (Postgres réel :
  colonnes lues, jamais écrites ; `parcel_id` écrit, `packet_id` jamais).
- Republication d'un besoin : `NeedAnnouncementService.publishFor` consulte `findByOrigin` avant de publier et rend
  l'annonce existante ; si une publication concurrente gagne la course, l'index `uq_tnt_ann_origin_ref` lève une
  `DataIntegrityViolationException` (traduite par Spring R2DBC, vérifié sur Postgres) et l'annonce gagnante est
  relue. Limite : dans une transaction PostgreSQL déjà en erreur, la relecture échoue aussi (l'erreur remonte).
  Preuves : `NeedAnnouncementLinkTest` (3 cas), `AnnouncementCoreRoundTripIntegrationTest`.
- m1 : `updateAnnouncement` du cœur exige `announcement:update` (détenue par `CLIENT`) ;
  `DeliveryAnnouncementPermissionsTest` (3 cas).
- m2 : lecture d'une annonce hors requête HTTP. `CoreAnnouncementReadAdapter`, `DeliveryNeedStateService` et
  `NeedAnnouncementService` lisent l'annonce dans le tenant courant (`TenantContextHolder.currentTenantId()`).
  Depuis un consommateur Kafka (pas de contexte de requête), ce tenant est le tenant système (repli
  `SYSTEM_TENANT`, comme dans le reste du module) : une annonce publiée dans un autre tenant n'y est pas trouvée.
  Cela reste juste tant que la plateforme tourne en tenant unique (branche `feat/single-system-tenant`) ; en
  multi-tenant, il faudra transporter le tenant dans l'événement.
- Commits : 8d6fb3dd (fusion L1), 1bcb6639 (B1), cb0be3b9 (republication), d6029f8e (m1), cec55e95 (docs),
  053c485b (fusion des 2 commits L1 suivants, dont le correctif de `KafkaAsyncWiringTest`).
- Preuves (après les deux fusions, reactor `-am`) : tests unitaires Go-Freelancer 511/511, delivery-core 95/95,
  link-back 4/4 ; ITs Go (Postgres Testcontainers) `AnnouncementCoreRoundTripIntegrationTest` 2/2,
  `DeliveryNeedLegacyColumnsIntegrationTest` 1/1, `DeliveryCoreConvergenceIntegrationTest` 7/7,
  `DeliveryPaymentFlowIntegrationTest` 9/9, `DeliveryProofPersistenceIntegrationTest` 3/3,
  `NegotiationChatPersistenceIntegrationTest` 4/4, `FreelancerPricingPersistenceIntegrationTest` 3/3,
  `GofpEventPublisherPortAdapterOutboxIntegrationTest` 1/1 ; ITs bootstrap `LiquibaseMasterChangelogIntegrationTest`
  3/3, `Gdd1DeliveryMigrationIntegrationTest` 2/2, `Gdd2AnnouncementMigrationIntegrationTest` 1/1,
  `Gdd2NeedMigrationIntegrationTest` 1/1, `GoNeedAnnouncementMigrationIntegrationTest` 1/1.
- Reste hors lot : `GofpRelayPointR2dbcRepository` joint encore `packets` (`rd.packet_id`, dépôts relais, lot L4).

## Lot L3 — acteurs (journal)

| Commit | Contenu | Preuve |
|---|---|---|
| permissions | `rating:create`, `rating:read`, `loyalty:read` ajoutées à `TntPermission` et aux rôles (H11) | roles-core : 263 tests, 0 échec |
| delivery-core | `DeliveryPersonStatus.REVOKED` + machine à états, `reject/revoke/transitionTo`, `LogisticsType` SCOOTER/TRICYCLE/BUS, `LogisticsVehicleTypeMapping` | `mvn -o test -pl logistics/tnt-delivery-core` : 107 tests, 0 échec |
| tp-core | colonnes de note `rating_context`/`subject_kind` (gdd-l3-301), permissions `rating:*`/`loyalty:read`, note sans profil tp, crédit de fidélité système, lecture du corps delivery-core (H4) | `mvn -o test -pl identity/tnt-tp-core` : 42 tests, 0 échec |
| actor-core | `ActorRatingService` : permission `rating:create`, cas CLIENT ajouté | `mvn -o test -pl identity/tnt-actor-core` : 95 tests, 0 échec |
| Go-Freelancer | délégation synchrone : évaluations → `RatingService` + `IRateActorUseCase` ; fidélité/note client lues du core, `record-order` → `LoyaltyService.earn`, `PATCH loyalty` → 409 ; statut freelancer → `DeliveryPerson.transitionTo`, statut/note relus du core ; code véhicule mort supprimé ; dépendance `tnt-tp-core` | `mvn -o test -pl coreBackend/tnt-go-freelancer-point-back-core -am -Dtest='com.yowyob.tiibntick.core.gofreelancer.**'` : 434 tests, 0 échec (dont 11 nouveaux : `EvaluationCoreDelegationTest` 4, `GofpClientCoreLoyaltyTest` 4, `GofpFreelancerCoreOverlayTest` 3 ; `GofpFreelancerStatusTransitionTest` 18) |
| migrations | `changes/gdd-l3.xml` : 302 évaluations → `tnt_tp_ratings` (actorIds, doublons (mission, évaluateur) comptés, le plus ancien gardé), 303 comptes de fidélité `total_orders * 10` (complétés sans double comptage), 310/311/312 ids `gofp_users`→actorId, `gofp_clients`→id de `client_profiles`, `gofp_freelancers`→`core_freelancer_id` (FK lues de `pg_constraint`, supprimées, réécrites, recréées), 320 statut d'agrément → `tnt_delivery_persons`. Ordre imposé : 302 avant les réécritures d'ids (`evaluations.*_id` portent les ids Go et une FK vers `persons`, non repointables) | `Gdd3ProfileMigrationIntegrationTest` 1/1 vert (ids hétérogènes → cohérents, FK identiques et validées, doublon/inconnu non copiés, 10 commandes → 100 pts BRONZE, 60 → 600 SILVER, scripts rejoués sans effet) ; `LiquibaseMasterChangelogIntegrationTest` 3/3 vert. Exécutés via `test-compile failsafe:integration-test` (disque plein : le `repackage` du jar bootstrap échoue) avec `-Dliquibase.duplicateFileMode=WARN` (master vu deux fois, `src` et `target/classes`, faute de jar) |
| double profil | évaluation adressée par actorId d'un compte à deux profils (client + freelancer) : le côté est choisi par le `type` (`*_TO_DP`/`*DELIVERY_PERSON` → freelancer, `*_TO_CLIENT` → client) au lieu de toujours prendre le profil client | `EvaluationCoreDelegationTest` 8/8 (4 cas double profil) ; module Go : 438 tests, 0 échec |

### Lot L3 — points non tranchés (arrêtés, à décider)

| Point | Constat (fichier) | Options | Recommandation |
|---|---|---|---|
| Moyennes existantes | La note affichée vient désormais de `ActorRating` (`client_profiles`/`freelancer_profiles.rating_*`), que la migration 302 ne recalcule pas : un profil noté avant migration s'affiche sans note | (a) changeset 3xx recalculant `rating_score/rating_total` depuis `tnt_tp_ratings` (CLIENT, DELIVERY_PERSON) ; (b) accepter la remise à zéro | (a), non fait faute de décision dans 3.9 (e) |
| `VehicleType` resource-core | 3.6 (c) dit « rien à ajouter », mais SCOOTER et BUS n'y existent pas ; BUS est une catégorie vivante du parcours livreur (`VehicleCategory`) | ajouter SCOOTER, BUS à resource `VehicleType` ; ou correspondance vide (actuelle, `LogisticsVehicleTypeMapping`) | ajouter (ajout générique) |
| Véhicule livreur → resource-core (3.11) | `FreelancerVehicle` exige `ownerOrgId` NOT NULL et `maxCapacityKg > 0` (inconnu du parcours livreur), aucun cas d'usage de mise à jour ; normaliser `vehicle_type` en MOTORCYCLE casserait `VehicleCategory.parse` | ajouter `owner_actor_id` nullable, rendre `max_capacity_kg` facultatif, cas d'usage `Update…` ; ou garder `gofreelancer_vehicles` | décision métier sur la capacité ; non fait (aucune migration H10) |
| Champs pro du freelancer (3.10 c) | `commercial_name`, `commercial_register`, `taxpayer_number`, `registration_number`, `availability_mode`, `client_profiles.default_address_id` non ajoutés ; `national_id`/`tax_id` : présence au Kernel non vérifiée | ajout actor-core + migration ; ou via Kernel | vérifier le Kernel d'abord |
| Statut client/utilisateur | `PATCH /api/v1/gofp/clients/{id}/status` reste local : aucun cas d'usage actor-core n'écrit `ActorStatus` d'un client | ajouter `IUpdateActorStatusUseCase` générique | ajouter (lot suivant) |
| Compteurs et position du freelancer (critère 1) | `total/failed/remaining_deliveries`, `latitude/longitude_gps` toujours lus/écrits dans `gofp_freelancers` : `DeliveryPerson` n'a pas de méthode de consommation de quota, et le branchement position touche le matching (L2) | ajouter `DeliveryPerson.consumeQuota/recordFailure` + `updateLocation` côté core | à faire avec L2 |
| Tarif des nouveaux `LogisticsType` | `DeliveryCostPolicy` : SCOOTER/TRICYCLE/BUS au tarif moto (comme `null`) | tarif dédié | décision tarifaire |
| Suppression des enums Go | `FreelancerStatus`, `ClientStatus`, `UserStatus`, `VehicleType` (Go) encore utilisés par des fichiers d'autres lots (ex. `DeliveryNeedApplicationService:472`) | les remplacer lors de la fusion / clôture | modérateur |
| Crédit automatique de fidélité | `DeliveryEventConsumer` (tp-core) lit `senderId` : ajouté par L1 (H4) ; sans lui aucun crédit | — | dépend de la fusion L1 |
| `PATCH …/loyalty` | 409 : pas d'équivalent core (palier dérivé des points) | politique de seuils paramétrable (H3) | conserver 409 |
| IT critère 5 (évaluation → moyenne → palier) | couvert par tests unitaires (`EvaluationCoreDelegationTest`, `GofpClientCoreLoyaltyTest`) et l'IT de migration, pas par une IT de bout en bout sur base réelle | IT WebFlux + Testcontainers | à écrire |

### Lot L3 — corrections de la revue (C1-C5, points 1 et 11, B)

| Point | Contenu | Preuve |
|---|---|---|
| C1 | ids à la création = ids core (utilisateur = actorId, client = id de profil client trouvé ou créé par actor-core, freelancer = `coreFreelancerId`) ; livreur delivery-core créé au même id, PENDING | `GofpUserCreateOrUpdateTest`, `GofpUserProvisioningServiceTest`, `GofpClientCreateOrUpdateTest` (+3), `GofpFreelancerCreateOrUpdateTest` (+3) |
| C2 | 312 réécrit aussi `tnt_announcement_responses.delivery_person_id`, `tnt_deliveries.delivery_person_id` (garde information_schema, compteur par colonne) | `Gdd3ProfileMigrationIntegrationTest` |
| C3 | index unique (mission, évaluateur, genre) au lieu de (mission, évaluateur) ; contrôle applicatif identique ; 302 garde `CLIENT_TO_DP` + `CLIENT_TO_RP` d'une livraison | `RatingServiceTest` (+1), IT de migration |
| C4 | `record-order` no-op 200 déprécié ; crédit de fin de livraison idempotent par `external_ref = missionId` ; `senderId` = actorId vérifié (jeton sur besoin et annonce) | `LoyaltyServiceTest` (+2), `DeliveryEventConsumerTest` (+1), `GofpClientCoreLoyaltyTest` |
| C5 | évaluateur = acteur du jeton (personnel excepté), `evaluatorId` étranger → 403 | `EvaluationCoreDelegationTest` (+6) |
| 1 | 304 : moyennes `ActorRating` (CLIENT, DELIVERY_PERSON) et note des points relais recalculées depuis `tnt_tp_ratings` | IT de migration |
| 11 | IT de bout en bout évaluation → moyenne → palier : contrôleurs Go réels, services et dépôts R2DBC réels sur PostGIS (schéma du master Liquibase), aspect de permissions réel ; 3 notes, 409, 403, moyennes 3/2 et 5/1, 50 livraisons + 1 rejouée → 500 points SILVER, client Go lu avec note 3.0, SILVER, 50 commandes | `EvaluationLoyaltyEndToEndIntegrationTest` 1/1 |
| défaut trouvé par l'IT 11 | `DeliveryEventConsumer.onMissionCompleted` lançait `handle(...).subscribe()` sans attendre : deux livraisons du même client lisaient et sauvaient le même compte en parallèle, un crédit était perdu (51 messages → 1 crédit). Traitement désormais un message à la fois sur le fil du listener (`block`, 30 s) | IT 11 |
| B | agrément par un administrateur : `DeliveryPerson.projection` et la projection Go créent PENDING ; 320 ne rétrograde personne (testé : un livreur APPROVED, PENDING côté Go, reste APPROVED) | `GofpFreelancerProjectionServiceTest`, `DeliveryPersonStatusTransitionTest`, IT de migration |
| A | véhicules : resource-core étendu (SCOOTER/BUS, organisation optionnelle + propriétaire acteur, capacité toujours obligatoire, mise à jour) ; Go délègue `PUT /me/vehicle` à resource-core de façon synchrone, capacité obligatoire (400) ; migration 331 (références fictives remises à NULL, rien de recréé faute de capacité) | `FreelancerVehicleTest` (+4), `FreelancerFleetApplicationServiceTest` (+5), `CourierVehicleWebTest` (+4), IT de migration |
| H10 (décision utilisateur confirmée, 2026-11-26) | véhicules existants sans capacité : le livreur la saisit à son prochain enregistrement (`PUT /me/vehicle`, capacité obligatoire) ; la migration 331 ne recrée rien et n'invente aucune capacité par défaut | décision documentée, aucun code ajouté (comportement déjà en place) |

### Lot L3 — fusion avec L2 et la fiabilité Kafka (2026-11-26)
- Conflits : documentation seulement (`agency-et-modules.md`, `mobile-bff.md`, `journal-technique.md`), sections
  L2 puis L3 conservées.
- Ajustements de tests rendus nécessaires par la combinaison des lots (aucun code de production modifié) :
  - `KafkaAsyncWiringTest.duplicateMissionCompletedCreditsLoyaltyOnce` vérifie `LoyaltyService.creditCompletedDelivery`
    (chemin système idempotent de L3) au lieu de `earn` ; le doublon est toujours écarté (1 seul crédit par mission).
  - `AnnouncementCoreRoundTripIntegrationTest` (L2) agrée le livreur projeté avant sa réponse : depuis L3 B, une
    projection crée un livreur `PENDING`, qui ne peut pas répondre.

## Décisions du 2026-11-26 après le lot « gofp-correctifs-proprietaires » (registre D-134, D-135)

- Liens d'adresse migrés sans correspondance sûre vers un acteur du cœur : supprimés par un nouveau changeset (D-134).
- Révocation homogène : livreurs et points relais (cœur et Go-Freelancer) révocables seulement depuis approuvé ou
  suspendu (D-135).

## Décisions du 2026-11-26 sur l'agrément des points relais (registre D-138 à D-140)

- D-138 : statut d'agrément séparé sur le hub du cœur (PENDING, APPROVED, SUSPENDED, REJECTED, REVOKED), qui fait foi ;
  le relais Go le recopie ; l'état d'exploitation reste à part.
- D-139 : suspension seulement depuis APPROVED, réactivation seulement depuis SUSPENDED, 409 sinon, idempotent sur
  l'état courant.
- D-140 : relais suspendu = plus de nouveaux dépôts ni de sélection comme destination ; colis en garde retirables
  jusqu'à la fin de garde, puis retour.

## Décisions du 2026-11-26 sur les routes d'agrément et le périmètre Agency (registre D-141 à D-144)

- D-141 routes d'agrément dans le cœur (`relay:approve`, administrateur TiiBnTick) ; D-142 `PATCH …/status` aligné sur
  la permission d'administrateur ; D-143 relais côté Agency laissés à la plateforme Agency (une agence n'a pas de point
  relais) ; D-144 destination relais d'un besoin vérifiée dans le cœur.
- D-145 : relais en pause refusé comme destination (409). D-146 : relais plein refusé comme destination (pas seulement au
  dépôt).

## Correctifs après intégration : propriétaires hérités (D-128), relais révoqué (D-129), harnais e2e — 2026-11-26

| Étape | Contenu | Preuve |
|---|---|---|
| D-128 propriétaires → acteur du cœur | Nouveau changeset Go `gdd-l4-417-legacy-owners-to-actor-ids` (411 et 412 inchangés). Table de correspondance construite uniquement à partir des correspondances L3 : `gofp_users.id → core_user_id` (310), `gofp_clients.id → core_client_id` (311), `gofp_freelancers.id → core_user_id` (312), profils `tnt_actor.*_profiles.id → actor_id`, `persons.id → gofp_users.core_user_id` via `persons.user_id`. Un id qui mène à deux acteurs est **ambigu** et n'est pas converti. Liens d'adresse (seulement ceux venus de `person_addresses`) : `owner_id` converti ; un lien qui doublerait un lien existant (même acteur, adresse, libellé) n'est pas converti (compté). Entrées d'inventaire (seulement celles venues de `relay_deposits`, même id ou même hub et même client) : `owner_actor_id` et `picked_up_by_actor_id` convertis. Lignes sans correspondance : **gardées telles quelles**, comptées (`RAISE NOTICE`). Paiements (414) : pas de colonne propriétaire (payer/payee NULL), rien à convertir. Rejouable. | `Gdd4OwnerActorMigrationIntegrationTest` 1/1 (PostGIS, master complet, état « avant » par l'étiquette `!gdd-l4-owners`) : personne liée à un utilisateur, client, freelancer, profil, acteur déjà correct, inconnu, ambigu, doublon ; le vrai `AddressService.findLinksByOwner` et le vrai `HubInventoryApplicationService.findEntriesOfOwner` rendent à l'acteur ses adresses et dépôts ; rejeu sans effet. `Gdd4AddressMigrationIntegrationTest` 2/2 et `Gdd4RelayMigrationIntegrationTest` 2/2 toujours verts |
| D-129 relais révoqué (cœur) | geo-core : `HubStatus.REVOKED`, `RelayHub.revoke`, colonnes + contraintes (`gdd-l4-418-relay-hub-revocation`), `RelayHubRevocationService`, route `POST /api/v1/geo/tenants/{tenantId}/hubs/{hubId}/revoke` (`relay:revoke`), 409 via `GeoExceptionHandler`. Colis en garde par `IRelayHubCustodyPort` (inventory : `HubCustodyAdapter`) ; effets produit par `IRelayHubRevocationListener`. roles-core : `relay:revoke` à aucun rôle (TNT_ADMIN seulement). | geo : `RelayHubRevocationTest` 18/18 (toutes les transitions autorisées et refusées), `RelayHubRevocationServiceTest` 6/6, `RelayHubAdminControllerTest` 4/4, `R2dbcRelayHubRevocationIntegrationTest` 3/3 (PostGIS) ; inventory : `HubCustodyAdapterIntegrationTest` 1/1 (PostGIS) ; roles : `TntRelayRevokePermissionTest` 12/12 |
| D-129 relais révoqué (Go-Freelancer) | `PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/revoke` : motif obligatoire, `relay:revoke`, délègue à `IRevokeRelayHubUseCase` (hub inconnu du cœur ou déjà révoqué : alignement local). `APPROVED`/`SUSPENDED` → `REVOKED` seulement (comme `DeliveryPersonStatus`) ; `REVOKED` bloque valider, suspendre, activer, re-révoquer. `GofpRelayRevocationApplier` : relais `REVOKED` inactif, profil d'opérateur désactivé, notification à l'acteur de l'opérateur (profil d'opérateur, sinon `core_user_id` du freelancer : jamais un id de profil), courriel. Nouveau message `notify.gofp.relay_status.revoked_reason` (changeset i18n `gdd-l4-419-i18n-relay-revoked-reason`, fr/en, et 5 packs JSON). | Go : `RelayPointRevocationTest` 14/14 ; notify : `GofpLocalizedMessagesTest` 3/3 (graine = packs) ; module Go complet 554/554 |
| Harnais e2e | `scripts/e2e/e2e-freelancer-courier.sh` : étape `APPROBATION` (projection déclenchée, PENDING vérifié en lecture, agrément par `PATCH /api/v1/gofp/freelancer-profiles/{id}/status?status=APPROVED` avec `TNT_E2E_ADMIN_TOKEN` ou un jeton factice en bypass-dev, APPROVED vérifié dans `gofp_freelancers` et `tnt_delivery_persons`). Aucune écriture SQL dans les tables de projection (`GofpProjectionTablesSeedGuardTest`). | `bash -n` OK ; exécution complète impossible ici (BFF et Kernel OTP requis) |

**Points non tranchés (arrêtés, à décider)**

- D-128 : sur une base neuve, les 8 liens d'adresse de la graine de test (`20260219_seed_test_data.sql`) restent sans
  correspondance (personnes de graine sans `user_id`). Rapprocher `persons` et `gofp_users` par l'adresse e-mail
  serait possible mais n'est pas une correspondance L3 : non fait (option à décider). Les anciens ids `gofp_users`
  remplacés par 310 ne sont plus résolubles. Les lignes orphelines restent illisibles par leur propriétaire.
- D-129 : retour ou transfert des colis en garde, événement Kafka de révocation et avertissement des propriétaires :
  à brancher avec le cycle de vie du colis (lot L2 du chantier événements ; `TntTopics` appartient au lot L0).
- `AdminRelayPointController` : `/suspend` et `/activate` répondent 200 sans appeler le service (constat, non corrigé).
- Ancien code de `AdminRelayPointApplicationService` (notifications d'activation, suspension, validation) : envoie à
  `rp.getCoreFreelancerId()`, un id de profil et non un acteur (H1) — non modifié dans ce lot.
- Un hub d'agence révoqué ne déclenche aucune notification produit (aucun écouteur Agency).

### Suite : décisions D-134 (orphelins supprimés) et D-135 (révocation : approuvé ou suspendu), bogues H1 — 2026-11-26

| Étape | Contenu | Preuve |
|---|---|---|
| D-134 | Changeset Go `gdd-l4-420-delete-orphan-address-links` (destructif, décision D-134 citée dans le changeset ; 417 inchangé) : supprime les liens `tnt_address_links` venus de `person_addresses` dont le propriétaire n'est pas un acteur connu par les correspondances de 417 (sans correspondance, ambigus, ou laissés par 417 parce qu'ils auraient doublé le lien de l'acteur) ; `NOTICE` du nombre supprimé par motif. Adresses (`tnt_addresses`) et dépôts relais jamais supprimés ; dépôts orphelins comptés (`NOTICE`). Étiquettes `gdd-l4,gdd-l4-owners,gdd-l4-orphans` (jamais exécuté avant 417 par un `!gdd-l4-owners`). `Gdd4AddressMigrationIntegrationTest` vérifie la copie 410/411 sans 420 (`!gdd-l4-orphans`). Sur une base neuve, les 8 liens de la graine de test sont supprimés. | `Gdd4OwnerActorMigrationIntegrationTest` 1/1 (PostGIS, master complet ; NOTICE : 17 liens, 11 supprimés = 9 sans correspondance + 1 ambigu + 1 doublon, 6 gardés ; 1 dépôt orphelin gardé ; adresses et dépôts intacts ; l'acteur relit ses liens par `AddressService` ; rejeu sans effet), `Gdd4AddressMigrationIntegrationTest` 2/2, `Gdd4RelayMigrationIntegrationTest` 2/2, `LiquibaseMasterChangelogIntegrationTest` 3/3 |
| D-135 | Relais Go : déjà `APPROVED`/`SUSPENDED` → `REVOKED` ; message de refus précisé (un `PENDING` se rejette). Hub du cœur : arrêté, correspondance `HubStatus` à valider (proposition : `ACTIVE`/`FULL` = approuvé, `TEMPORARILY_CLOSED` = suspendu, `PERMANENTLY_CLOSED` refusé). | — |
| Bogues | `/suspend` et `/activate` branchés sur le service, avec la machine des livreurs (`APPROVED` ↔ `SUSPENDED`, même statut = sans effet, autre statut = 409 : plus d'activation d'un relais `PENDING` sans KYC). Notifications de statut et opérateur du hub provisionné : acteur de l'opérateur (profil d'opérateur, sinon `core_user_id` du freelancer), plus jamais l'id de profil. Javadoc de `GofpFreelancerProjectionService` : route d'agrément corrigée. | `RelayPointSuspensionAndRecipientTest` 13/13, `RelayPointRevocationTest` 14/14, module Go complet 567/567 |

### Suite : décisions D-138 (agrément du hub du cœur), D-139 (suspendre / réactiver), D-140 (effet sur les dépôts) — 2026-11-26

| Étape | Contenu | Preuve |
|---|---|---|
| D-138 cœur | geo-core : `HubApprovalStatus` (`PENDING`, `APPROVED`, `SUSPENDED`, `REJECTED`, `REVOKED`, machine des livreurs), séparé de `HubStatus` (ACTIVE, FULL, TEMPORARILY_CLOSED, PERMANENTLY_CLOSED : `REVOKED` en est retiré). `RelayHub` : `approve`, `reject`, `suspend`, `reactivate`, `revoke` (seulement depuis `APPROVED`/`SUSPENDED`, D-135) ; un hub neuf naît `PENDING`. `IManageHubApprovalUseCase` (`RelayHubApprovalService` : redemander l'état courant ne fait rien, refus = `RelayHubStateException`, 409). Aucune route d'agrément exposée par le cœur. Réponse de `POST .../hubs/{hubId}/revoke` : `approvalStatus` et `operatingStatus`. | `HubApprovalStatusTest` 55/55 (25 couples sur l'énumération, 25 sur le hub, 5 états), `RelayHubRevocationTest` 25/25, `RelayHubRevocationServiceTest` 7/7, `RelayHubAdminControllerTest` 4/4, `R2dbcRelayHubRevocationIntegrationTest` 5/5 (PostGIS), module geo 129/129 |
| D-138 migration | `gdd-l4-421-relay-hub-approval-status` (geo, étiquettes `gdd-l4,gdd-l4-approval`) : colonne `approval_status` NOT NULL, défaut `PENDING`, contraintes ; hubs existants `APPROVED`, hubs `REVOKED` de 418 → agrément `REVOKED` et exploitation `PERMANENTLY_CLOSED` ; hubs fermés comptés (`NOTICE`). `gdd-l4-422-relay-hub-approval-from-relay-points` (Go) : le hub d'un relais Go prend le statut du relais (date de révocation = dernière mise à jour du relais, motif non inventé) ; relais aux statuts différents ou inconnus et relais sans hub comptés, hub laissé tel quel. | `Gdd4HubApprovalMigrationIntegrationTest` 1/1 (PostGIS, état « avant » par `!gdd-l4-approval` : 5 statuts Go, statut inconnu, relais sans hub, hub d'agence, hub fermé, hub révoqué en 418, hub neuf `PENDING`, rejeu sans effet) ; `LiquibaseMasterChangelogIntegrationTest` 3/3 ; autres `Gdd4*` 7/7 |
| D-138/D-139 Go | Le relais recopie l'agrément : `/suspend`, `/activate`, la validation et `PATCH /api/v1/gofp/relay-point-profiles/{id}/status` (désormais acheminé par `AdminRelayPointUseCase.changeStatus`, plus d'écriture brute du statut) changent d'abord le hub du cœur, puis la ligne locale ; refus du cœur = 409 sans écriture ; hub inconnu du cœur = machine locale seule. Validation : seulement depuis `PENDING` (redemander = sans effet) ; l'approbation provisionne puis approuve le hub. `/revoke` : si le cœur refuse parce que son hub n'est ni approuvé ni suspendu, 409 (plus d'alignement local) ; alignement local seulement si le hub est déjà révoqué. | `RelayPointSuspensionAndRecipientTest` 22/22, `RelayPointRevocationTest` 15/15, `GofpRelayPointCreateOrUpdateTest` 2/2 |
| D-140 | Inventaire : `depositPackage` refuse (409) un dépôt dans un hub non `APPROVED` (`ICheckHubAcceptsDepositsUseCase`, hub inconnu de geo non gouverné) ; retraits jamais refusés. Geo : recherches de hubs proches et `findAllActive` limitées aux hubs `APPROVED` ; `RelayHub.addParcel` refusé hors `APPROVED` ; l'occupation ne rouvre plus un hub fermé. Go : un besoin dont le relais de destination n'est pas `APPROVED` et ouvert est refusé (409) ; les listes de relais proposés filtraient déjà `APPROVED` et actif. Point d'accroche du retour en fin de garde (lot L2) : `FindOverduePackagesUseCase.findOverduePackages`. | `HubInventoryApplicationServiceTest` 10/10, `NeedAnnouncementLinkTest` 23/23, module inventory 30/30, module Go 600/600 (dont 2 classes d'IT du module) |

**Points non tranchés (arrêtés, à décider)**

- Permission d'éventuelles routes d'agrément du cœur (non créées) : candidates `relay:revoke` (administrateur seul) ou
  une nouvelle permission `relay:approve` donnée à aucun rôle. Recommandation : n'en créer que si Agency en a besoin.
- `PATCH /api/v1/gofp/relay-point-profiles/{id}/status` reste ouvert au « personnel » (`requireStaff`) alors que
  `/suspend` et `/activate` demandent `gofp-admin:manage` : aligner les permissions est à décider.
- Agrément initial d'un hub créé hors Go (Agency) : un hub neuf naît `PENDING` ; aucun code actuel ne crée de hub
  géo pour Agency (Agency enregistre ses hubs dans organization-core, `tnt_hub_relais`), donc sans effet aujourd'hui.
- Hubs révoqués sous 418 : état d'exploitation fixé à `PERMANENTLY_CLOSED` (l'état d'avant est perdu) ; une
  révocation nouvelle garde l'état d'exploitation.
- Un besoin dont la destination est inconnue de Go n'est pas vérifié (comportement antérieur gardé).

### Suite : décisions D-141 (routes d'agrément du cœur), D-142, D-143 (Agency), D-144 (destination) — 2026-11-26

| Étape | Contenu | Preuve |
|---|---|---|
| D-141 | Routes du cœur `POST /api/v1/geo/tenants/{tenantId}/hubs/{hubId}/approve`, `/reject` (motif obligatoire), `/suspend` (motif facultatif), `/reactivate`, permission `relay:approve` (`TntPermission.RELAY_APPROVE`, donnée à aucun rôle : `TNT_ADMIN` seulement). La révocation garde `relay:revoke` (pas de fusion : les deux restent réservées à l'administrateur, la révocation, définitive, reste une permission à part). `IRelayHubApprovalListener` rétabli : `GofpRelayApprovalApplier` recopie sur le relais Go un changement fait par une route du cœur en rejouant le flux Go (statut, profil d'opérateur, KYC, notification) ; un changement fait par une route Go porte une clé de contexte Reactor et l'écouteur s'abstient (une seule notification). | `RelayHubApprovalRoutesHttpTest` 14/14 (aspect de permission réel, registre des rôles réel : 403 pour les 10 autres rôles sur les 5 routes, transitions, 400, 404, 409), `RelayHubAdminControllerTest` 4/4, `TntRelayApprovePermissionTest` 12/12, `RelayPointSuspensionAndRecipientTest$CoreRoute` 3/3 (vrai service d'agrément du cœur + vrai flux Go) |
| D-142 | `PATCH /api/v1/gofp/relay-point-profiles/{id}/status` : `@RequirePermission(gofp-admin, manage)` au lieu de `requireStaff`. | `RelayPointAccessControllerTest` 9/9 (aspect réel ; `SUPPORT_AGENT` → 403, `TNT_ADMIN` → 200) |
| D-143 | Agency : rien à faire ; commentaires et docs qui rattachaient Agency à l'agrément du cœur retirés (`HubApprovalStatus`, `ICheckHubAcceptsDepositsUseCase`, commentaire de tête de `gdd-l4-421`, `agency-et-modules.md`). | — |
| D-144 | `DeliveryNeedApplicationService.requireProposableTarget` : la destination (id de relais Go ou de hub) est cherchée dans le registre des hubs du cœur (`IRelayHubPort.findHub`) : inconnue → 404 ; hub non `APPROVED` ou fermé, relais Go non `APPROVED` ou en pause → 409 ; hub plein accepté (remplacé par D-146). | `NeedAnnouncementLinkTest` 31/31 |
| D-145, D-146 | Relais en pause : refus 409 confirmé (inchangé). Hub plein (`FULL`, ou `currentOccupancy >= capacity`) : refusé comme destination (409) dans `requireProposableTarget`, et retiré des listes suggérées : `GofpRelayPointService.findActiveApproved` / `findWithSufficientCapacity` ne gardent que les relais dont le hub (`IRelayHubPort.findHub`) est `isAvailable()` avec une place libre ; relais sans hub ou hub introuvable retirés aussi. Le contrôle au dépôt (`acceptsDeposits`, capacité) est inchangé. Requête SQL de capacité inchangée. | `NeedAnnouncementLinkTest` 32/32, `GofpRelayPointCreateOrUpdateTest` 3/3, module Go 613/613, `TiiBnTickApplicationTest` 1/1, `KafkaAsyncWiringTest` 26/26, `Gdd4RelayMigrationIntegrationTest` 2/2 |

Les points non tranchés de la section précédente sur les routes d'agrément du cœur, la permission de
`PATCH .../status` et Agency sont réglés par D-141, D-142 et D-143.

## Clôture `gdd-final` (décision D-154 « Oui, avec garde-fous », 2026-11-27)

### Code Go retiré d'abord (plus aucun lecteur des objets supprimés)

| Objet supprimé | Code retiré |
|---|---|
| `gofp_users.password_hash`, `password_tokens`, routes `/api/auth/setup-password`, `/request-password-reset` (D-054) | `PasswordSetupController`, `SetPasswordRequest`, `SetPasswordValidator`, `PasswordSetupUseCase`, `PasswordSetupApplicationService`, `TokenService`, `PasswordToken` (+ port, dépôt R2DBC, adaptateur), `PasswordHasherService` (champ inutilisé de `ClientApplicationService`), champ `GofpUser.passwordHash`, chemins publics de `GofpSecurityConfig` |
| `delivery_person_pricing` | `FreelancerPricingPolicy`, `FreelancerPricingRepository` (+ R2DBC, adaptateur), `LegacyPricingBackfillService`, `LegacyPricingBackfillRunner`, `FreelancerPricingBackfillRules`, `FreelancerGridPolicyMapper.toGrid(FreelancerPricingPolicy)` ; tests `LegacyPricingBackfillServiceTest`, `FreelancerPricingPersistenceIntegrationTest`, `Gdd4PricingBackfillIntegrationTest` |
| `persons` (admin local réécrit à chaque démarrage) | `AdminBootstrapConfig` (mot de passe local `admin123` par défaut, aucun lecteur) |
| `users` (FK de `delivery_needs.user_id`) | `DeliveryNeedApplicationService.ensureLegacyUser` et le `DatabaseClient` devenu inutile (7 tests adaptés) |
| `delivery_needs.status`, `delivery_id`, `assigned_freelancer_id`, `packet_id` | repli L2 (B1) de `DeliveryNeedStateService` et champs `stored*` de `DeliveryNeed` ; tests `DeliveryNeedLegacyStateTest`, `DeliveryNeedLegacyColumnsIntegrationTest` |

Les champs `@Transient` (statut, livreur, tarif, fenêtres, heures réelles de `Delivery` ; fidélité, commandes, note
de `GofpClient` ; note de `GofpFreelancer`) étaient déjà lus dans le cœur : seules leurs colonnes tombent.

### Changesets `gdd-final-901` à `909` (`GOFP-SQL/changes/gdd-final.xml`)

- **901** : fonctions `public.gdd_final_uncovered()` (une ligne par élément, nombre de lignes héritées sans
  contrepartie dans le cœur) et aides (`gdd_final_fixture`, `gdd_final_known_actor`, `gdd_final_person_covered`,
  `gdd_final_logistics_covered`). Contreparties : même id dans `tnt_delivery_announcements`,
  `tnt_announcement_responses`, `tnt_parcels`, `tnt_tp_ratings`, `billing.wallet_settlements` (paiements PAID),
  `tnt_hub_package_entries` (id, colis ou code), `tnt_geography` ; politique `FREELANCER-GRID` du propriétaire ;
  livraison du cœur de même id ; annonce du cœur avec livraison créée pour un besoin assigné avant L2 ; changesets
  `gdd-l3-303/304` appliqués ; acteur connu (gofp_* ou profils `tnt_actor`) pour `users`, `persons`, `clients`,
  `delivery_persons` ; relais, véhicule ou plaque connus pour `logistics` ; aucune ligne vivante (`comments`,
  `subscriptions`, `freelancer_eligibility`, `logistics_pricing`, …) pointant vers un id hérité inconnu.
  Ignorés : données de démonstration du changeset 040 (ids fixes), client invité (033), ancien admin local (…0002).
- **902** : porte globale (somme = 0, sinon HALT), puis retrait de toutes les clés étrangères vers les tables
  supprimées, quel que soit leur nom.
- **903 à 908** : une précondition HALT par suppression, sur les éléments concernés (voir la cible §4.6).
- **909** : retrait des fonctions de recensement.
- Conservés (données ou code vivants) : `gofreelancer_vehicles`, `announcement_subscriptions`, compteurs et position
  de `gofp_freelancers`, `deliveries.duration`, enums Go `FreelancerStatus`/`ClientStatus`/`UserStatus`/`VehicleType`.
- Diagnostic en production si le démarrage s'arrête : `SELECT * FROM public.gdd_final_uncovered() WHERE uncovered > 0;`
  (rien n'est supprimé tant que le total n'est pas nul ; décision manuelle, spec admin §6.4).

### Scripts e2e

`e2e-freelancer-courier.sh` : plus d'écriture dans `announcements` ; C10/C11 semés dans le cœur (colis, annonce,
livraison) + ligne d'extension `deliveries (id, announcement_id)` ; tarif lu dans `tnt_deliveries.agreed_amount`.
`e2e-presence-loop.sh` : plus de table `users` ni de repli `11111111-…` ; le besoin assigné est semé par son annonce
du cœur (`origin_ref_type = DELIVERY_NEED`) et la réponse retenue. Les ITs de migration des lots précédents excluent
le label `gdd-final` (elles écrivent dans les tables héritées).

### Preuves

- Unitaires Go-Freelancer (reactor `-am`) : 665/665.
- `GddFinalClosureMigrationIntegrationTest` 2/2 (base migrée par la version précédente avec fixtures : tables et
  colonnes supprimées, tables vivantes et lignes conservées, relance sans effet ; une personne orpheline → HALT,
  rien de supprimé, diagnostic = 1, puis rattachement → clôture passe).
- `LiquibaseMasterChangelogIntegrationTest` 3/3 (base neuve : la clôture passe sur les seules fixtures).
- 23 ITs de migration des lots précédents vertes (`Gdd1..4*`, `GoNeedAnnouncement`, `Relay*`, `Parcel`,
  `MarketSchemaAlignment`, `EvaluationLoyaltyEndToEnd`).
- Base l7 réelle (migrée par la version précédente) : les 9 changesets passés au démarrage ; `TntApplicationFullStackIT`
  7/7, `CourierReplacementFullStackIT` 1/1, `KafkaAsyncWiringTest` 39/39, `TntArchitectureTest` 1/1 sur le schéma clos.
- Application lancée en local sur la base l7 close (Kernel coupé, dev-bypass) : `e2e-presence-loop.sh` **tout passé**
  (livreur du besoin lu dans la réponse retenue du cœur, suivi GPS 200, présence Redis, mutations M1-M5).
  `e2e-freelancer-courier.sh` et `e2e-freelancer-flow.sh` demandent le BFF (:3001) et une connexion OTP/MFA réelle
  au Kernel : non exécutés sans le mainteneur (préflight « BFF inaccessible ») ; syntaxe (`bash -n`) et seeds réécrits
  sur les colonnes du schéma clos vérifiés.

## Fusion de `main` (15 commits, 276ccb93..1e8bbb0e) — 2026-11-27

Consigne : la branche reste la référence, `main` est intégré sans casser son travail ; ce qui pose problème est
soumis au mainteneur (D-157, D-158).

- **Conflits résolus (14 fichiers)** : la branche garde son modèle (annonces/livraisons/colis du cœur, pas de miroir
  local `announcements`, états D1-D6). Repris de `main` : `tenantOf` (annonce et livraison lues/écrites dans leur
  tenant), événements in-app (C-36) aux mêmes endroits, contacts du besoin (C-56) portés par l'annonce du cœur
  (`recipientName/Phone`, `senderName/Phone` via `AnnouncementExtras`), livreur d'un autre tenant
  (`courierOf`), réglages des consommateurs Kafka à côté des réglages L0/G.8.
- **Changelog** : `c56-need-contacts` et `c36-in-app-notifications` placés avant les inclusions `gdd-*`, pour qu'une base
  neuve suive l'ordre de la production. La clôture `gdd-final` est inchangée : elle supprime `notifications`, la boîte
  in-app vivant désormais dans le Kernel (D-157).
- **Boîte in-app (D-157)** : `tnt-notify-core` gagne `IInAppInboxUseCase`/`IInAppInboxPort`/`KernelInAppInboxAdapter`
  (canal `IN_APP` ; envoi avec le compte de service, lecture `/inbox` et marquage avec le jeton de l'utilisateur) ; le
  module Go garde l'API de `main` (`InAppNotificationService`, `GofpNotificationController`) sur ce port, sans table
  (entité `Notification`, dépôt R2DBC et adaptateur de `main` retirés, garde `NoLocalNotificationTableTest` intacte).
  Idempotence : `event_bus.processed_events` (marque posée seulement après acceptation du Kernel — défaut trouvé par
  le test : un envoi raté était marqué). Contrats à prouver en direct : K16-K18.
- **Approbation automatique des relais (C-57, D-158)** : `approveOnRegistration` passe par le chemin D-138 (hub du
  cœur provisionné puis agréé, le relais recopie) ; vrai par défaut.
- **Tarifs** : les tests C-56 de `main` réécrits sur la grille D-058 de la branche (minimum puis suppléments).
- **Tests de `main` corrigés** : `PublicPathsSecurityIntegrationTest` ouvre `/v3/api-docs` comme en staging (fermé
  par défaut depuis d453f4b0, il échouait aussi sur `main`) ; `LiquibaseMasterChangelogIntegrationTest` tient compte
  de la clé `fk_notification_person` retirée par c36.

Preuves (2026-11-27) :

- Unitaires, réacteur complet jusqu'au bootstrap : tout vert sauf `LayeringArchitectureTest` (1 145 violations
  anciennes Agency/Go, déjà rouge avant la fusion). Les 12 échecs « Redis 6379 » et `SpringdocProdExposureTest` ont
  disparu grâce à `main` (tests renommés en `*IntegrationTest`, Swagger fermé).
- `KernelInAppInboxAdapterTest` 6/6 (WireMock : chemins, canal, en-têtes tenant/organisation, jeton compte de service
  vs utilisateur, erreurs propagées) ; `InAppNotificationsTest` 17/17 ; `RelayAutoApprovalTest` 4/4 ;
  `FreelancerBillingPricingTest` 21/21.
- ITs Liquibase et migrations : porte `LiquibaseMasterChangelogIntegrationTest` 3/3, `GddFinalClosureMigrationIntegrationTest`
  2/2, 16 ITs de migration des lots ; ITs de modules (PostGIS réel) : 11 classes, 44 tests verts.
- Conteneurs l7 recréés (disparus au redémarrage de la machine) : `TntApplicationFullStackIT` 7/7,
  `CourierReplacementFullStackIT` 1/1, `KafkaAsyncWiringTest` 39/39, `KafkaBlockingConsumersWiringTest` 8/8,
  `DeliveryThresholdsWiringTest` 1/1, `TiiBnTickApplicationIntegrationTest` 1/1, `PublicPathsSecurityIntegrationTest`
  10/10, `WalletAutoProvisioningWiringIntegrationTest` 1/1 (profil `test` pointé sur l7 par variables
  `SPRING_*`), `TntKafkaConsumerTuningTest` 10/10, `NoProfileHardeningTest` 3/3. Aucun rapport ne mentionne le Kernel
  de production.

