# Journal technique — événements par agrégat

Une section par lot : quoi, pourquoi, preuve, points non tranchés. Référence des lots :
`docs/architecture/chantiers/cible-evenements-colis-livraison.md` §6.

## Lot L0 — `evt-socle-evenements` (socle commun, 2026-11-26)

| Étape | Contenu | Preuve |
|---|---|---|
| L0-1 enveloppe, masquage, listener bloquant | `tnt-common-core`, paquet `com.yowyob.tiibntick.common.kafka` : `TntAggregateEvent<S>` (enveloppe §3.4 : `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `aggregateType`, `aggregateId`, `aggregateVersion`, `tenantId`, `correlationId`, `causationId`, `trigger`, `actor`, `previousState`, `payload` = état complet, `details` ; validation à la construction : type mécanique `<Agrégat><PascalCase(état)>` (§1.0) préfixé par l'agrégat, UUID, `trigger` en MAJUSCULES_SOULIGNÉES, versions ≥ 1) ; `TntAggregateType` (5 agrégats → topic, préfixe, `eventTypeFor`) ; `TntEventActor` ; `TntEventJson` (ISO-8601 UTC, nuls écrits) ; `TntEventMasking` (adresse → quartier/ville/pays + coordonnées arrondies HALF_UP à `tnt.kafka.masking.coordinate-decimals` = **2** (QO-17), refusé hors 1–3 ; `displayName` → « Awa N. » ; montants en chaîne) ; `ReactiveListenerSupport` (`await(Mono\|Flux)` : bloque le fil du conteneur jusqu'à la fin, délai `tnt.kafka.listener.timeout` = 30 s → `ListenerTimeoutException`, refuse un fil non bloquant, erreur relancée telle quelle) ; `TntEventSchemas`, `TntPiiSentinels` ; schéma `resources/kafka/schemas/v1/envelope.schema.json` (draft 2020-12, `$defs/maskedAddress`) ; constantes `TntTopics.PARCEL_EVENTS`, `INCIDENT_EVENTS`, `DISPUTE_EVENTS`, `ANNOUNCEMENT_EVENTS`. Dépendance de test `com.networknt:json-schema-validator` 1.5.1 (gérée à la racine). | `tnt-common-core` : 110 tests verts, dont `TntAggregateEventTest` 36 (validation, sérialisation **validée contre le schéma**, aller-retour), `TntEventMaskingTest` 22 (arrondi 2 décimales, aucun sentinelle PII dans le JSON, par agrégat), `ReactiveListenerSupportTest` 8 (attente réelle, délai, erreur, fil non bloquant refusé) |
| L0-2 id d'événement et DLQ commune | `yow-event-kernel` : `DomainEventEnvelope.Builder.eventId(String)` (id d'événement = id d'enveloppe : aucune colonne nouvelle, conservé par le relais et le rejeu) ; `KafkaEventPublisher.buildRecord` ajoute `X-Yow-Event-Id` (le filtre anti-doublon `KafkaEventIds` le lit déjà, après `X-Yow-Envelope-Id`). `TntKafkaErrorHandlingPostProcessor` (BPP, déclaré par `YowEventKernelAutoConfiguration`) : sur toute fabrique de conteneurs **sans** gestionnaire d'erreurs, pose un `DefaultErrorHandler` (`tnt.kafka.retry.max-attempts` = 4, attente exponentielle 1 s ×2) dont le récupérateur journalise `KAFKA_RECORD_DEAD_LETTERED`, incrémente `yow.event.consumer.dead_lettered.total{topic,group}` et publie sur `tnt.dlq` (même clé, valeur octets/chaîne/JSON, en-têtes d'origine + `kafka_dlt-*`). Les fabriques sync et link gardent leur gestionnaire (bascule L8). Le filtre `IdempotentRecordInterceptor` n'est pas dupliqué : il marque traité après le succès du listener, donc après la fin du travail attendu. | `yow-event-kernel` : 74 tests verts, dont `KafkaEventPublisherHeadersTest` 2, `TntKafkaErrorHandlingPostProcessorTest` 6 |
| L0-3 bootstrap | `TntKafkaTopicsConfig` : 5 `NewTopic` d'agrégat (`cleanup.policy=delete`, `retention.ms` = `tnt.kafka.aggregate-retention-ms` = 30 jours ; remplace l'ancien `NewTopic` de `tnt.delivery.events` sans rétention) ; `tnt.dlq` et les topics « strictement nécessaires » du §3 (`payment.confirmed`, `commission.calculated`, `compensation.paid`) étaient déjà déclarés. `TntKafkaConfig` : beans `tntEventMasking`, `reactiveListenerSupport` (`@ConditionalOnMissingBean`). `application.yml` : bloc `tnt.kafka.{aggregate-retention-ms, retry.max-attempts, listener.timeout, masking.coordinate-decimals}` (variables `TNT_KAFKA_*`). `pom.xml` du bootstrap : profil `integration-tests` avec `classesDirectory = target/classes` pour failsafe (le jar est réempaqueté par Spring Boot : un *IT qui démarre l'application ne trouvait pas `@SpringBootConfiguration`). | `TntKafkaAggregateTopicsTest` 3/3 (topics, constante DLQ du noyau = `TntTopics.DLQ`, beans) |
| L0-4 règle ArchUnit gelée | `tnt-bootstrap/src/test/.../architecture/TntArchitectureTest` (créé : il n'existait pas) : `FreezingArchRule` « aucun `@KafkaListener`/`@KafkaHandler` n'appelle `subscribe()` ni n'avale son erreur (`onErrorResume(… Mono.empty())`, `onErrorComplete()`), directement ou via une méthode/lambda de la même classe ». Ligne de base enregistrée dans `tnt-bootstrap/archunit_store` (fichier `03b0f146-…` + `stored.rules`) : **67 violations** (43 `subscribe`, 24 erreurs avalées). Toute violation nouvelle échoue ; chaque lot L2–L8 qui corrige un listener retire sa ligne (l'archive se vide d'elle-même). | `TntArchitectureTest` 1/1 |
| L0-5 application complète | `TntApplicationFullStackIT` (failsafe, activé par `TNT_IT_KAFKA_BOOTSTRAP`) : application entière sur Kafka 3.8.0, PostGIS 18-3.6 et Redis 7 réels ; topics présents sur le courtier (5 agrégats : 3 partitions, `delete`, 30 jours ; `tnt.dlq`) ; toutes les fabriques (hors sync/link) ont le gestionnaire commun ; événement `ParcelPickedUp` publié par l'outbox → vrai `@KafkaListener` sur la fabrique Spring Boot qui attend son travail réactif (`ReactiveListenerSupport`) → effet observé ; enregistrement valide contre `envelope.schema.json`, clé = id du colis, en-têtes `X-Yow-*` (Event-Id = `eventId` du corps), aucun sentinelle PII, `event_bus.processed_events` marqué ; travail en échec : enveloppe seulement persistée, reprise par le **relais** → 4 tentatives → `tnt.dlq` (en-têtes `kafka_dlt-original-topic`, groupe, exception, `X-Yow-Event-Id` d'origine), non marqué traité, enveloppe `PUBLISHED` ; travail qui dépasse `tnt.kafka.listener.timeout` → 4 tentatives → `tnt.dlq` (`ListenerTimeoutException`). | `TntApplicationFullStackIT` 6/6 (Kafka `apache/kafka:3.8.0`, `postgis/postgis:18-3.6`, `redis:7-alpine`) ; non-régression : `KafkaAsyncWiringTest` 26/26 (même infrastructure), `TiiBnTickApplicationTest` 1/1 |

### Lot L0 — points non tranchés et écarts (à arbitrer)

- **`$id` du schéma** — la spécification (§3.6, l. 633) écrit `"$id": "tnt/kafka/v1/envelope"` ; ce `$id` relatif est refusé
  par le validateur JSON Schema 2020-12 (`InvalidSchema`). Écrit `urn:tnt:kafka:v1:envelope`. Options : (a) garder l'URN
  (recommandé) ; (b) URL absolue `https://schemas.tiibntick…/kafka/v1/envelope.json` si un hébergement est prévu (L9).
- **Nombre de violations gelées** — la spécification (§6, lot L0) annonce 26 violations, §3.10/§9.2 « 39 méthodes ». La
  règle telle qu'écrite (§3.10 point 3 : `subscribe` **ou** erreur avalée, directement ou via méthode/lambda de la même
  classe) en relève 67 (43 + 24). Aucune violation n'a été retirée pour coller au chiffre. Les écarts viennent des
  `.subscribe()` atteints via un appel interne et des `onErrorResume(… Mono.empty())`, que l'inventaire ne comptait pas.
- **`TntArchitectureTest`** — cité par la spécification comme existant ; il n'existait pas : créé sous ce nom.
- **Fichiers d'impacts** — la spécification §7 nomme `impacts-*.md` ; nommés `mobile-bff.md`, `agency-et-modules.md`,
  `journal-technique.md` (consignes du chantier, modèle `../gofp-dedup/`), correspondance dans `README.md`.
- **Erreur avalée par `try/catch`** — non détectée par la règle (ex. `tp-core` `DeliveryEventConsumer` attrape
  `Exception`) ; la spécification ne le demande pas. Les lots L2–L8 le corrigent en réécrivant les listeners.
- **Rétention d'un topic existant** — Spring Kafka ne modifie pas un topic déjà créé : sur un cluster existant,
  `tnt.delivery.events` garde sa rétention (commande d'exploitation dans le spec d'administration §5.5).
- Schémas de `payload`/`details` par agrégat et sentinelles par champ réel : lots L2–L4 (L0 fournit le mécanisme et un
  test générique).

## Lot L1 `evt-seuils-plateforme`

### Schéma (`tnt-administration-core`, `db/changelog/changes/evt-l1-administration.sql`)

- `evt-l1-001-platform-delivery-thresholds` : table `administration.tnt_delivery_thresholds`, une seule ligne
  (`id = 'PLATFORM'`, contrainte CHECK), créée avec les défauts décidés (3 tentatives, 7 jours de garde, rappel J-1,
  3 retours échoués, alertes 15/0 min, délais par urgence 48/24/8 h, ETA +30 min, GPS 10 min, arrêt 20 min) ;
  bornes relais indépendant nulles à la création ; `version` pour le verrou optimiste.
- `evt-l1-002-organization-delivery-thresholds` : table `administration.tnt_organization_delivery_thresholds`, clé
  `organization_id` (id d'organisation Kernel), mêmes colonnes nullables (NULL = valeur plateforme).
- `evt-l1-003-independent-relay-storage-default-bounds` (décision D-130) : bornes de garde d'un relais indépendant
  3 à 30 jours par défaut. Remplit seulement les bornes nulles (une borne déjà réglée par l'administrateur est
  gardée), pose `DEFAULT 3` / `DEFAULT 30` et `NOT NULL`. Non destructif.
- DDL idempotente (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`), aucun changement destructif.

### Événement `DeliveryThresholdsChanged`

- Topic `tnt.administration.events` (conservé), via l'outbox (`TntAdministrationEventPublisher`), même transaction que
  l'écriture. Clé = tenant de l'auteur (tenant système à défaut), en-tête `X-Yow-Event-Type: DeliveryThresholdsChanged`.
- Corps : `{eventType, module: "administration", tenantId, aggregateId, payload, occurredAt}` ; `aggregateId` = id
  d'organisation, ou un UUID fixe pour la plateforme
  (`UUID.nameUUIDFromBytes("tnt.administration.delivery-thresholds.PLATFORM")`).
- `payload` : `scope`, `change`, `organizationId`, `version`, `updatedBy`, `updatedAt`, `thresholds`,
  `previousThresholds`, et pour la plateforme `independentRelayStorageMinDays` / `MaxDays`.
- Consommateur attendu : delivery-core (lot L2, invalidation du cache des seuils).

### Contrat modifié : `relayPointMaxStorageHours`

- Retiré de `UpdateTntPlatformOptionsRequest` et `TntPlatformOptionsResponse`
  (`GET|PUT /api/v1/admin/settings/tnt-platform-options`). Un client qui l'envoie encore est accepté (`@JsonIgnoreProperties`, champ ignoré, pas de 400) ; la
  valeur existante est conservée.
- Retrait non destructif : colonne, entité et domaine `TntPlatformOptions` inchangés. La durée de garde se règle
  désormais par `relayStorageDays` (jours) sur `/api/v1/admin/settings/delivery-thresholds`.

### Permissions

- `tnt:platform:admin` (constante `TntPermission.TNT_PLATFORM_ADMIN`, protégée, portée par `*` de `TNT_ADMIN` seulement).
- `organization:delivery-settings:read|write` ajoutées à `TntPermission`, `TntRole` et `TntPermissionCatalog`
  (catalogue : 92 entrées au lieu de 90).

### Décisions appliquées (registre D-130 à D-133)

- D-130 : bornes QO-29 = 3 à 30 jours par défaut, toujours renseignées ; `RelayStorageBounds.DEFAULT`, plus de repli
  1–60 (qui reste seulement la plage de saisie). Le `PUT` plateforme exige désormais les deux bornes (400 sinon).
- D-131 : plages de saisie gardées (tentatives 1–10, retours échoués 1–10, garde 1–60 j, minutes 0 ou 1–1440, heures
  1–720).
- D-132 : délais sans fenêtre gardés (48/24/8 h), réglables et surchargeables par une agence.
- D-133 : surcharge par une agence libre, sans autorisation de l'administrateur (qui voit et peut supprimer).

## Lot L7 `evt-consommateurs-bloquants` (consommateurs bloquants, 2026-11-26)

Principe (spec §3.10, décision G.8) : un listener attend la fin de son travail sur le thread du conteneur
(`ReactiveListenerSupport.await`, délai `tnt.kafka.listener.timeout`), n'acquitte qu'après succès, et laisse remonter
toute erreur au conteneur : 4 tentatives (1 s, ×2) puis `tnt.dlq`. Fin des `.subscribe()` détachés et des
`onErrorResume(e -> Mono.empty())` qui perdaient les échecs.

| Classe (module) | Avant | Après |
|---|---|---|
| `WalletPayoutConsumer` (agency-commission) | `subscribe()`, erreur avalée, acquitté | bloquant ; paiement sans `invoiceId` ou `invoiceId` non UUID ignoré ; commission inconnue ignorée (`CommissionService.confirmWalletPayment` ne lève plus `COMMISSION_NOT_FOUND` : le wallet confirme tous les paiements de la plateforme) |
| `ActorKycConsumer` (agency-workforce) | idem | bloquant ; `actorId`/`tenantId` manquant → file morte |
| `MarketKafkaConsumer` (market-back) | `subscribe()` ×4, erreurs loguées | bloquant ; transition déjà atteinte = sans effet (rejeu), sinon garde de domaine → file morte. Constat : aucun producteur n'envoie `orderId` sur `payment.confirmed` (paiement Market via `processPayment`) ; branche gardée telle quelle |
| `FreelancerOrgEventConsumer` (actor) | `subscribe()` ×4 | bloquant ; champ manquant → file morte ; liaison idempotente côté service |
| `FreelancerVehicleEventConsumer` (delivery) | `subscribe()`, erreur avalée | bloquant ; `missionId` manquant ou livraison inconnue → file morte |
| `FreelancerOrgKafkaEventConsumer` (notify) | `subscribe()` interne, erreurs avalées | bloquant ; envoi en échec → file morte (au moins une fois : une relance peut renvoyer un canal déjà envoyé) |
| `SubscriptionConfirmationConsumer` (gofp) | `subscribe()`, candidat en erreur ignoré | bloquant ; seul un doublon d'inscription (`DuplicateKeyException`) est absorbé ; toute autre erreur → file morte |
| `HubParcelExpiryJob` (agency-org) | `subscribe()` dans un `@Scheduled` + ShedLock (verrou relâché tout de suite) | attend son travail dans la fenêtre du verrou (50 min max) ; échec levé au planificateur |
| `WalletBillingEventConsumer.onCommissionCalculated` (billing-wallet) | JSON illisible logué et perdu | JSON illisible → file morte |
| `MatchingNotificationConsumer` (gofp) | synchrone | vérifié : les erreurs remontent déjà |
| `PermissionCacheInvalidationListener` (roles) | erreur → invalidation totale | vérifié et conservé : l'effet sûr (vider tout le cache) est appliqué, rien n'est perdu |

**Non traité dans ce lot (décision de l'utilisateur du 2026-11-26 : « la blockchain de côté pour le moment ») :**
`TrustCommittedEventConsumer` (trust-core) reste tel quel ; sa violation reste gelée dans `archunit_store`.

Règle ArchUnit `TntArchitectureTest` : 28 violations retirées du gel (67 → 39) ; les 39 restantes relèvent des lots
L2–L6, L8 et du consommateur blockchain mis de côté.

Tests : unitaires des 8 classes touchées (34 tests) ; suites complètes des modules touchés et de leurs dépendances (2 432 tests hors test blockchain retiré,
0 échec) ; **vrai broker** `KafkaBlockingConsumersWiringTest` (8/8) : pour chaque consommateur, effet réel, effet unique
quand le même événement arrive deux fois (`X-Yow-Event-Id`), 4 tentatives puis `tnt.dlq` avec le groupe d'origine.

## Lot L2 `evt-etats-colis-livraison` (2026-11-26)

### Machines d'états (une table par entité, qui fait foi)

- `domain/statemachine/TransitionTable` (générique) ; `DeliveryStateMachine.TABLE` (D1–D21), `ParcelStateMachine.TABLE`
  (P1–P20), `AnnouncementStateMachine.TABLE` (A1–A7) et `RESPONSE_TABLE` (R1–R4) ; correspondance
  `DeliveryParcelCorrespondence.effectOf`. Tables imprimées depuis le code dans la spécification, §1.7.
- Supprimés : `domain/policy/DeliveryStateTransitionPolicy` (seconde table, divergente) et son test ;
  `DepositAtRelayPointCommand`, `ResumeFromRelayPointCommand`, `FailDeliveryCommand` renommés
  (`…RelayHub…`, `RecordFailedAttemptCommand`) ; `StartFinalLegCommand` ajouté.
- Chaque transition produit un `AggregateEvent` (`<Agrégat><ÉtatCible>`, instantané, `previousState`, `trigger`,
  `actor`, `causationId`) ; `DeliveryTransitionExecutor.saveAndPublish` (transactionnel) sauvegarde la livraison et son
  colis, applique A6/A7 à l'annonce, ouvre le brouillon de retour si le colis passe `RETURN_REQUESTED`, puis publie.
- Bug corrigé : la sélection d'une candidature (A4) ne produisait aucun événement d'agrégat.

### Schéma (`tnt-delivery-core`, `db/changelog/evt-l2-delivery.yaml`, inclus en dernier)

| Changeset | Effet |
|---|---|
| `evt-l2-001` | colonnes du colis : code de suivi, état, garde, litige, livraison courante, liste des livraisons, cause de retour… |
| `evt-l2-002` | index unique partiel `uq_tnt_parcels_tracking_code`, index `idx_tnt_parcels_status` |
| `evt-l2-003` | colonnes de la livraison : `kind`, parent, tentatives, motifs, garde au relais, retard, instants |
| `evt-l2-004` | colonnes de l'annonce : `kind`, `creator_role`, `outcome`, instants |
| `evt-l2-005` | précondition **HALT** : aucune ligne `TIMED_OUT`/`SLA_BREACHED` (à traiter à la main avant la mise à jour) |
| `evt-l2-006` | `AT_RELAY_POINT` → `AT_RELAY_HUB` (statut et état mémorisé) — [C bis] |
| `evt-l2-007` | `CREATED` avec livreur → `ASSIGNED` — [C] |
| `evt-l2-008` | `delivery_attempt_number := GREATEST(n − 1, 0)` (compteur d'échecs) — [C.2] |
| `evt-l2-009` | code du colis = code de sa livraison quand il n'y en a qu'un ; plusieurs codes distincts : rien n'est choisi |
| `evt-l2-010` | état du colis repris de sa dernière livraison (table §2.1) |
| `evt-l2-011` | `max_attempts = 3` par défaut |
| `evt-l2-012` | `recipient_name`/`recipient_phone` des annonces nullables (brouillon de retour sans contact connu, QO-4) |

GOFP : `db/changelog/changes/evt-l2.xml` — `evt-l2-001-relay-point-storage-days` (`gofp_relay_points.storage_days`).
Rattrapage applicatif : `ParcelTrackingCodeBackfill` (au démarrage, `tnt.delivery.tracking-code-backfill.enabled`)
donne un code neuf aux colis restés sans code et journalise le nombre de colis qui avaient plusieurs codes ; les
anciens codes de livraison restent résolus (`tnt_deliveries.tracking_code` lu en repli). Les nouvelles livraisons
n'écrivent plus `tnt_deliveries.tracking_code`.

### Code de suivi (D-136, D-137)

`TrackingCode.generate()` tire avec `SecureRandom` (même format). Suivi public : `PublicTrackingResponse` (masqué) et
`PublicTrackingRateLimiter` (fenêtre d'une minute par IP, Caffeine, par instance) sur delivery-core et Link. Le flux SSE
de realtime (`/realtime/sse/tracking/{code}`) exige une authentification : pas de limitation par IP, mais le topic de
suivi ne diffuse plus d'identifiants.

### Seuils (L1) et garde au relais

- `DeliveryThresholdsAdapter` (port `DeliveryThresholdsPort`) lit `ResolveDeliveryThresholdsUseCase`, met en cache
  (Caffeine) ; `DeliveryThresholdsChangedConsumer` vide le cache sur `DeliveryThresholdsChanged`
  (`tnt.administration.events`, groupe par instance `tnt-delivery-thresholds-cache-<uuid>`, `latest` — même exception
  documentée que le cache des permissions).
- `AgencyOrganizationPort` (agency-org-core) : `agencyId` → `kernel_organization_id`. `RelayOwnershipPort` : relais
  d'agence (agency-org-core) ou relais indépendant GOFP (`storage_days`). Échéance = min(demande, garde) (QO-30).
- `RelayStorageScheduler` (`tnt.delivery.relay-storage.cron`, toutes les heures, `@SchedulerLock`) : rappel puis D12.
- D-130 : `GofpRelayPointService.createOrUpdate` appelle `checkIndependentRelayStorageDays`.

### Double déclenchement du matching

`MatchingUseCase` n'écoute plus que `tnt.announcement.events` (`AnnouncementPublished`). L'alias
`gofp.announcement.published` produit par delivery-core et `EventPublisher.publishAnnouncementPublished` (sans appelant)
sont supprimés. Le listener reçoit désormais le `ConsumerRecord` brut : avec la fabrique
`gofpJsonKafkaListenerContainerFactory` (`StringJsonMessageConverter`), un paramètre `String` ne peut pas recevoir une
enveloppe JSON (objet) et chaque enregistrement partait en file morte après 4 essais (constaté sur le vrai broker ;
l'ancien listener avait la même signature sur les anciens topics). Preuve : `KafkaAsyncWiringTest.announcementPublishedTriggersMatchingOnce` (vrai broker : l'annonce est
lue une seule fois alors que l'ancien topic est encore écrit).

### Double écriture des anciens topics — à retirer d'un coup au lot L8 (G.5)

Chaque point est marqué `// TODO(L8)` dans le code :

| Où | Ancien événement | Ancien(s) topic(s) |
|---|---|---|
| `Delivery.create` | `DeliveryCreatedEvent` | `tnt.delivery.mission.created` |
| `Delivery.assignDeliveryPerson` | `DeliveryPersonAssignedEvent` | aucun depuis L2 (n'est plus écrit dans `tnt.delivery.events`) |
| `Delivery.cancel` | `DeliveryCancelledEvent` | aucun depuis L2 |
| `Delivery.confirmPickup` | `ParcelPickedUpEvent` | `tnt.delivery.mission.started`, `tnt.delivery.package.updated` |
| `Delivery.startTransit`, `resumeFromRelayHub` | `DeliveryInTransitEvent` | `tnt.delivery.package.updated` |
| `Delivery.depositAtRelayHub` | `ParcelAtRelayPointEvent` | `tnt.delivery.package.updated` |
| `Delivery.complete` | `DeliveryCompletedEvent` | `tnt.delivery.mission.completed`, `tnt.delivery.package.delivered` |
| `Delivery` (D10, D12, D20, D21) | `DeliveryFailedEvent` | `tnt.delivery.mission.failed` |
| `Delivery.pauseForIncident` / reprise | `DeliveryPausedByIncidentEvent`, `DeliveryResumedFromIncidentEvent` | aucun depuis L2 |
| `Delivery.assignToFreelancerOrg` | `FreelancerOrgAssignedEvent` | `tnt.delivery.freelancer_org.assigned` |
| toute transition (`Delivery.addMissionStatusChangedEvent`) | `MissionStatusChangedEvent` | `tnt.delivery.mission.status.changed`, `tnt.delivery.package.updated` |
| `DeliveryAnnouncement.publish` | `AnnouncementPublishedEvent` | `tnt.delivery.announcement.published` |
| `DeliveryAnnouncement.selectResponse` | `AnnouncementResponseSelectedEvent` | `tnt.delivery.announcement.response.selected` |
| `KafkaDeliveryEventPublisher.toEnvelopes` | routage de tous les anciens événements | — |

`gofp.announcement.published` n'a plus de producteur ni de lecteur ; le topic reste déclaré jusqu'à L8.

### Tests (lot L2)

Unitaires delivery-core : 771 (dont `TransitionTablesTest` 422, `DeliveryAggregateTransitionsTest` 185,
`ParcelAndAnnouncementAggregatesTest` 28, `DeliveryParcelCorrespondenceTest` 4, `AggregateEventSchemaTest` 2).
Schémas JSON §3.6 livrés par le lot : `parcel.payload.schema.json`, `delivery.payload.schema.json`,
`announcement.payload.schema.json` et `details/` (`DeliveryAttemptFailed`, `DeliveryFailed`, `DeliveryCancelled`,
`DeliveryAlertRaised`, `DeliveryAlertCleared`, `DeliveryEtaUpdated`, `ParcelReturnRequested`, `ParcelCustodyChanged`,
`ParcelLost`, `ParcelDamaged`, `AnnouncementResponseReceived`, `AnnouncementResponseWithdrawn`) sous
`foundation/tnt-common-core/src/main/resources/kafka/schemas/v1/` ; `AggregateEventSchemaTest` valide chaque
événement produit par six scénarios (enveloppe, payload, details) et refuse un état retiré ou une adresse non
masquée. Les champs `trackingCode`, `failedReturnCount` (colis) et `relayStorageExpiresAt` (livraison) s'ajoutent à
la liste du §3.6 (ajout permis, G.6). PostGIS réel :
`ParcelDeliveryStatesPostgisIntegrationTest` (5 : migration d'une base peuplée, arrêt HALT, rattrapage et unicité des
codes, relais → fin de garde → retour, de bout en bout). Kafka réel : `KafkaAsyncWiringTest` (annonce publiée →
`tnt.announcement.events` masqué → matching une seule fois ; `ParcelCreated` sur `tnt.parcel.events`), porte Liquibase
`LiquibaseMasterChangelogIntegrationTest`.

### Suites du lot L2 après fusion de feat (décisions D-147 à D-151, 2026-11-26)

- **Fusion** de `feat/single-system-tenant` (`8b2ea728`) : conflits résolus dans `GofpRelayPointService` (les deux
  apports gardés : bornes de `storageDays` D-130 + routage par la machine d'agrément et filtrage des relais proposés
  D-144/D-146) et dans `mobile-bff.md` (les deux sections).
- **Un seul chemin de retour en fin de garde** : D12 via `RelayStorageService`. `FindOverduePackagesUseCase.findOverduePackages`
  (inventory-core) reste une lecture seule et ne déclenche aucun retour.
- **Dépôt hors hub APPROVED refusé avant D7** : `DeliveryLifecycleService.depositAtRelayHub` appelle
  `ICheckHubAcceptsDepositsUseCase.requireAcceptsDeposits(hubId)` (geo-core, D-140) avant de charger la livraison ; un
  refus (`RelayHubStateException`) ne modifie rien. Le contrôle de l'inventaire (feat) reste en place.
- **Garantie de retour (D-147, D-151)** : port sortant `ReturnGuaranteePort` (`hold`, `release`, `capture`, idempotents
  par livraison), implémenté par le lot L5 (séquestre Kernel) ; `ReturnGuaranteeService` décide quand l'appeler :
  gel quand une livraison `OUTBOUND` vers un relais de destination reçoit son prix (`recordAgreedTerms`), dégel à toute
  autre fin, débit en D12. État conservé sur la livraison (changeset `evt-l2-013-delivery-return-guarantee` :
  `return_guarantee_status`, `return_guarantee_reason`, index partiel des cas `NOT_HANDLED`/`FAILED`) et dans
  `DeliveryDetailResponse`. Cas non traités, avec raison explicite et log `RETURN_GUARANTEE_NOT_HANDLED` :
  `SENDER_NOT_PAYING_BY_WALLET` (`CASH`), `PAYMENT_METHOD_UNKNOWN`, `NO_GUARANTEE_HELD`,
  `WALLET_GUARANTEE_NOT_CONNECTED` (tant que L5 n'a pas livré l'adaptateur), `WALLET_OPERATION_FAILED` (statut
  `FAILED`). Le brouillon de retour est toujours ouvert : jamais de retour bloqué en silence.
- **Payeur du retour = expéditeur** dans tous les cas (`ReturnDeliveryService`) ; le relais n'est plus jamais
  `creatorId` d'un brouillon.
- **Retour annulé avant enlèvement (D-148)** : `Parcel.returnDeliveryCancelledBeforePickup` → événement
  `ParcelReturnDeliveryCancelled` (schéma `details/ParcelReturnDeliveryCancelled.schema.json`) et nouveau brouillon.
- **Dépôt GOFP (D-149)** : `CoreDeliveryState.atDestinationRelay` (= `Delivery.isAtDestinationHub()`) ; ancrage
  « mission terminée » et paiement du livreur seulement si `true`.
- **Colis sans tenant (D-150)** : `Parcel` remplace un tenant absent par `SystemTenant.id()` à la création et sur tout
  événement ; `ParcelService` publie toujours `ParcelCreated`. Tests : `ReturnCancelledAndParcelTenantTest`,
  `ParcelServiceTest`, assertion « tenant non nul » sur tous les événements de `AggregateEventSchemaTest`.
- **QO-9** : GOFP expose `outcome` (`DELIVERED` / `FAILED`) sur une demande `COMPLETED`
  (`DeliveryNeedStatusPolicy.outcome`, `DeliveryNeedResponseDTO.outcome`).
- **Correctif — sélection d'une réponse** : `DeliveryAnnouncementService.selectResponse` enchaîne la sauvegarde de
  l'annonce puis celle de la livraison, qui écrivent toutes deux le colis. La seconde était assemblée avant que la
  première ne s'exécute (`then(save(...))` évalué tout de suite) et écrivait une version périmée du colis :
  `OptimisticLockingFailureException` sur `tnt_parcels`, donc toute sélection échouait. Révélé par
  `AnnouncementCoreRoundTripIntegrationTest` (GOFP, vrai PostGIS), jamais relancé pendant le lot. `ParcelWriter`,
  `DeliveryRepositoryAdapter.save`, `DeliveryAnnouncementRepositoryAdapter.save` et `DeliveryTransitionExecutor.publish`
  sont désormais paresseux (`Mono.defer`) ; test `ParcelWriterLazinessTest`.
- **`TODO(L3)`** : `DeliveryEventNotifier` (GOFP, notification `DELIVERY_LATE` à rebrancher sur
  `DeliveryAlertRaised(DELIVERY_LATE)`) et `IncidentEventConsumer.onMissionStatusChanged` (incident-core, ouverture
  d'incident SLA à rebrancher sur les événements SLA ; la branche `TIMED_OUT`/`SLA_BREACHED` ne se déclenche plus).

### Points non tranchés (lot L2)

1. Un brouillon de retour sans contact de l'expéditeur (livraison sans annonce) n'a pas de destinataire : le payeur le
   complète avant publication (changeset `evt-l2-012`).
2. Moyen de paiement « wallet » (D-147) : seul `CASH` est classé « hors wallet » ; un moyen absent est « non traité »
   (`PAYMENT_METHOD_UNKNOWN`) ; tout autre libellé (`MOBILE_MONEY`, `ORANGE_MONEY`…) est traité comme un paiement par
   wallet, comme le fait déjà le règlement GOFP (`WALLET_TRANSFER` hors `CASH`). Une livraison d'agence sans annonce
   est donc « non traitée ».
3. Garantie dégelée à **toute** fin de livraison autre que D12 (livrée, annulée, échouée pour une autre cause) :
   interprétation de D-147 (seul le retrait est cité).
4. Le gel est demandé quand le prix est connu (`recordAgreedTerms`), pas à la ligne D1 : à la création GOFP le prix
   n'est pas encore enregistré.
5. Une annulation de retour avant enlèvement compte comme un retour échoué (`failedReturnCount`, QO-8) : après
   `maxFailedReturns` annulations, plus de brouillon (signalement au support).

## Lot L3 `evt-incidents` (incidents et alertes de livraison, 2026-11-26)

Références : spécification §1.3 (lignes I1–I31), §3.5, §3.6 ; décisions [C.3], [D.1], D.2, QO-2, QO-12 à QO-15, QO-18,
QO-25 ; seuils D-131 à D-133.

### Machine d'états de l'incident (`IncidentStatus`, table qui fait foi)

- `DETECTED` supprimé : un incident naît `REPORTED`, toujours déclaré par le livreur affecté (garde 403 si
  `IMissionStatusPort.isAssignedCourier` répond non, 422 pour un type non déclarable, QO-12 :
  `IncidentType.isDeclarableByCourier()`, liste servie par `GET /api/v1/incidents/declarable-types`).
- `IncidentStatus.TRANSITIONS` porte les lignes I1–I31 ; I27 (escalade) est ajoutée à tout état `isEscalatable()`, I31
  (annulation) à tout état non terminal sauf `RESOLVED`. `Incident.moveTo` refuse toute autre transition
  (`IllegalStateException`, traduit en **409** par les services) et empile une `IncidentTransition` par changement.
- Bloquant (QO-2, `IncidentBlockingPolicy`) : forcé par le livreur (`blockingForced`), **ou** gravité `HIGH` /
  `CRITICAL` / `FATAL`, **ou** type bloquant (retraits, indisponibilité, urgence médicale, accident, arrestation,
  téléphone mort ; pannes moteur, freins, transmission, électrique, collision majeure, tonneau, feu, immersion).
- Déclaration : `REPORTED` (D13 sur la livraison), puis `ACKNOWLEDGED` et `TRIAGED` dans la même requête ; non
  bloquant → D14 (reprise, déclencheur `INCIDENT_NOT_BLOCKING`) ; bloquant → D15 (pause), gel du paiement, puis :
  livraison d'agence → `PENDING_AGENCY_ASSIGNMENT` (I5) ; sinon `AUTO_RESOLVING` (I4) et moteur.
- Moteur : remplaçant nécessaire → `REASSIGNING_DRIVER` ; aucun remplaçant → `TRANSFERRING_TO_HUB` si un relais est à
  moins de `hubTransferMaxKm` (QO-13, I9), sinon `AUTO_RESOLUTION_FAILED` → `PENDING_AGENCY_ASSIGNMENT` (I10, I11 :
  l'agence, ou le support TiiBnTick quand la livraison n'a pas d'agence) ; types `GEO_*` → `REROUTING` puis `RESOLVED`.
- Remplaçant accepté (`POST .../handover/accept`, I12/I16) ou désigné (I13) : si le livreur tient le colis →
  `AWAITING_HANDOVER` et D17 ; la passation confirmée des deux côtés résout l'incident (`HANDOVER_COMPLETED`, D18) ;
  passée `handoverTimeout` sans confirmation → retour à `REASSIGNING_DRIVER` et D19 (`IncidentLifecycleScheduler`).
- Résolution (`IncidentResolutionEffects`) : `deliveryOutcome` obligatoire pour un incident bloquant (400 sinon) ;
  `RESUMED` → D16 (reprise) ; `FAILED` → D20 (échec, `responsibleParty`) ; `DISPUTE` → rien sur la livraison. Le paiement
  est dégelé sauf pour `DISPUTE`. Annulation → reprise et dégel.
- Confirmation (QO-14, I23–I25) : `propose-resolution` (gestionnaire) → `PENDING_CONFIRMATION` ; le déclarant confirme
  (`RESOLVED`) ou conteste (retour à `AGENCY_HANDLING`) ; sans réponse après `confirmationDelay`, confirmation
  automatique (déclencheur `CONFIRMATION_TIMED_OUT`). Un livreur peut clore lui-même son incident **non bloquant**
  (I6, `resolve` sans permission de gestion).
- Escalade en litige (QO-15) : `RESOLVED` avec `deliveryOutcome = DISPUTE`, événement `IncidentEscalatedToDispute`.
- Code de référence : `TNT-INC-AAMMJJ-<10 hex>`, tiré avec `SecureRandom`.

### Événements `tnt.incident.events`

Chaque sauvegarde publie, dans la même transaction (`IncidentPersistenceAdapter.save` →
`IncidentAggregateEventOutboxPublisher`), les transitions empilées : clé = id de l'incident, `aggregateVersion` = suite
continue (colonne `event_sequence`), `causationId` chaîné à l'événement précédent. Payload masqué : pas de description,
coordonnées arrondies, position 0/0 → `null`. Schémas : `incident.payload.schema.json` et `details/` (`IncidentReported`,
`IncidentTriaged`, `IncidentEscalated`, `IncidentResolved`, `IncidentPendingConfirmation`, `IncidentCancelled`,
`IncidentEscalatedToDispute`, `IncidentHandoverConfirmed`, `IncidentHandlerAssigned`). Les anciens topics d'incident
restent écrits (`publishCreated`, `publishResolved`, marqués `TODO(L8)`).

### Alertes de livraison (delivery-core, [C.3], [D.1], D.2)

- Les détections ne créent plus d'incident : `DeliveryAlertRaised` / `DeliveryAlertCleared` sur `tnt.delivery.events`,
  une seule fois tant que l'alerte est active (colonne `active_alerts`), champ `activeAlerts` dans le payload.
- `DeliveryWatchService` : `PICKUP_LATE`, `DELIVERY_LATE`, `ETA_EXCEEDED`, `GPS_SIGNAL_LOST` (job
  `DeliveryLateScheduler`, ShedLock, `tnt.delivery.alerts.cron` toutes les 5 min, `batchSize` 500) ;
  `TRAJECTORY_DEVIATION`, `PROLONGED_STOP` et retour du GPS (`DeliveryWatchConsumer` sur
  `tnt.realtime.gps.position.updated`) ; `GEOFENCE_EXIT` (`tnt.realtime.geofence.triggered`). Une position seule est
  écrite au plus une fois par minute.
- **Seuils** : ceux de l'administration (lot L1, `DeliveryThreshold.*_MINUTES` / `*_HOURS`), avec la surcharge de
  l'organisation Agency de la livraison (D-133), lus par `DeliveryThresholdsPort.forAgency` (cache 60 s, invalidé par
  `DeliveryThresholdsChanged`) ; valeurs de la plateforme si l'administration est illisible. `DELIVERY_LATE` = fin de
  la fenêtre de livraison + grâce, à défaut création + délai de l'urgence (48 h standard, 24 h express, 8 h jour même,
  D-132). La première version du lot lisait des propriétés `tnt.delivery.alerts.*` avec d'autres valeurs (express 4 h,
  jour même 12 h, base = enlèvement) : corrigé avant commit, `DeliveryAlertProperties` ne règle plus que le job.
- `IncidentEventConsumer` (incident-core, ouverture d'incident sur GPS, géofence, SLA) supprimé.
- GOFP : `CoreDeliveryAlertConsumer` notifie le client et le livreur sur `DeliveryAlertRaised(DELIVERY_LATE)`
  (`DeliveryEventNotifier.onDeliveryLate`, gabarit `notify.gofp.delivery.late`) — fin des `TODO(L3)` du lot L2.

### Prise en charge par le support et client Agency

- `POST /api/v1/incidents/{id}/agency/handle` : `agencyId` devient facultatif. Sans lui, c'est le support TiiBnTick qui
  prend en charge un incident sans agence (I18 depuis la file `PENDING_AGENCY_ASSIGNMENT`) : acteur `SUPPORT`, aucune
  agence notifiée.
- `IncidentCoreClient` (agency compliance-core) : rôle `AGENCY_DRIVER` (l'ancien `DELIVERER` n'existe pas dans
  `ActorRole`, chaque signalement échouait en 400 et l'erreur était avalée) ; types déclarables seulement (vol →
  `PARCEL_TOTAL_LOSS`) ; retard, anomalie inconnue ou absence de livreur → aucun incident ; l'agence ne déclare plus à
  la place du livreur.

### Schéma

- incident (`db/changelog/evt-l3-incident.xml`, après 005) : `evt-l3-001` `DETECTED` → `REPORTED` ; `evt-l3-002`
  colonnes `blocking`, `blocking_forced_by_courier`, `delivery_outcome`, `responsible_party`, `resolution_note`,
  `resolution_proposed_at`, `event_sequence`, et `blocking = TRUE` pour les incidents ouverts existants (ils
  bloquaient tous, C6) ; `evt-l3-003` index partiel des propositions en attente.
- delivery (`db/changelog/evt-l3-delivery.yaml`) : `evt-l3-001-delivery-alerts` (`active_alerts`, `last_position_at`,
  index partiel des livraisons surveillées).

### Configuration (`tnt.incident.lifecycle.*`)

`enabled`, `cron` (`0 */5 * * * *`), `batch-size` (200), `confirmation-delay` (24 h), `handover-timeout` (2 h),
`hub-transfer-max-km` (5). Réglages de plateforme listés pour l'administration (spec admin §5.3).

### Tests (lot L3)

- Unitaires : incident-core 61 (dont `IncidentLifecycleL3Test` 27 : la table complète comparée à la spécification ;
  `IncidentReportingFlowL3Test` 6 ; `IncidentAggregateEventSchemaTest` ; `IncidentControllersSecurityTest`) ;
  delivery-core 793 (`DeliveryWatchServiceTest` 8, `AlertThresholdsMappingTest` 2, `AggregateEventSchemaTest`
  alertes) ; GOFP `CoreDeliveryAlertConsumerTest` 3 ; agency
  `IncidentCoreClientTest` 3 (MockWebServer).
- PostgreSQL + Kafka réels : `IncidentLifecycleL3IntegrationTest` (2 : une ligne `DETECTED` existante migrée en
  `REPORTED` bloquant ; un cycle complet sauvegardé étape par étape publie 8 événements ordonnés, clés et versions
  1..8, colonnes relues, requête de confirmation automatique) ; `IncidentKafkaEventPublisherOutboxIntegrationTest` (2).
- PostGIS réel : `ParcelDeliveryStatesPostgisIntegrationTest.alertsWatchEndToEnd` (alertes levées une fois,
  persistées, effacées par une position). Kafka, base et Redis réels : `KafkaAsyncWiringTest` 27/27 (GPS et géofence →
  `DeliveryWatchService`, géofence en double traitée une fois). Porte Liquibase `LiquibaseMasterChangelogIntegrationTest` 3/3.
- Correctif de test (antérieur au lot) : `announcementPublishedTriggersMatchingOnce` échouait depuis la fusion de L2
  avec la règle L7 (échec relancé puis DLQ) : l'annonce n'était pas en base, chaque passage échouait « not found » et
  était relancé (4 lectures). Le test fournit désormais l'annonce, comme `DeliveryAnnouncementService` qui la sauvegarde
  avant de publier.
- ArchUnit : 3 entrées gelées retirées (`IncidentEventConsumer`) ; les entrées incident restantes de
  `applicationMustNotDependOnAdapterInWeb` sont antérieures au lot (les nouveaux cas d'usage ne portent pas
  `@RequirePermission`, les contrôles sont dans les contrôleurs).

### Limites et points non tranchés (lot L3)

1. `TRANSFERRING_TO_HUB` n'a pas de signal « colis déposé au relais » : le gestionnaire résout l'incident à la main
   (I17 côté relais à brancher avec la réception du dépôt par l'inventaire).
2. `replacementDriverId` (désignation d'un remplaçant) est supposé être un id d'acteur Kernel.
3. `IncidentAutoEscalationScheduler` appelle un cas d'usage protégé par `@RequirePermission` hors de toute requête
   (comportement antérieur, non modifié).
4. Le payload `delivery` n'a pas de `trackingCode` : la notification « livraison en retard » GOFP est envoyée sans
   référence de colis.
5. `PROLONGED_STOP` est détecté par le flux realtime avec son propre seuil ; la valeur de l'administration
   (`prolongedStopMinutes`) n'est que reportée dans l'alerte.
6. Les délais `confirmationDelay`, `handoverTimeout` et `hubTransferMaxKm` sont des propriétés de l'application, pas
   encore des réglages d'administration ni surchargeables par une agence.
7. Agency : `delivererId` de la mission d'agence doit être l'acteur Kernel du livreur (ou son id de livreur dans le
   cœur) pour passer la garde « livreur affecté » ; le signalement hors ligne (`AgencyMissionOfflineOperationApplier`)
   n'a pas de jeton du livreur et sera refusé par le cœur (erreur journalisée, mission non bloquée).
8. Vol signalé par un livreur d'agence = `PARCEL_TOTAL_LOSS` (choix technique, aucun type « vol subi » déclarable).

## Lot L4 `evt-litiges` (litiges, effets sur le colis, `tnt.dispute.events`, 2026-11-26)

Références : spécification §1.4, §3.5, §4.3 ; décisions [B.6], [B.7], [E], QO-7, QO-16, QO-23, QO-24, C10, C15, C19.

### Machine d'états du litige (`DisputeStateMachine.ROWS`, table qui fait foi)

- LT1 à LT12 de la spécification, une ligne par transition (`row(id)`, `allows`, `targets`). Toute transition passe
  par `Dispute.moveTo(rowId, cible, trigger, acteur, détails)` : la ligne est vérifiée (409 sinon), une
  `DisputeTransition` est ajoutée aux transitions en attente et `eventSequence` augmente. Ajouter une pièce sans
  changer d'état produit `EVIDENCE_ADDED` (`DisputeEvidenceAdded`).
- `ResolutionType` passe à 8 valeurs (`TOTAL_LOSS_COMPENSATED`, `DAMAGE_COMPENSATED` ajoutés), avec
  `grantsCompensation()` et `parcelOutcome()`. LT7 exige un type indemnisant et un montant > 0 ; LT8 un type non
  indemnisant sans indemnité. La règle porte `responsibleParty` (`DisputeResponsibleParty`).
- `close` n'accepte que `ADMINISTRATIVE_CLOSURE` avec motif (LT12, correction de C10) ; `withdraw` vérifie que
  l'appelant est le demandeur (403) ; `expire` (LT11) remplace `closeDispute(EXPIRED)` dans `DisputeSLAScheduler`, qui
  n'expire plus un litige en médiation.

### Effets sur le colis (delivery-core, même transaction)

- Port `IDisputeParcelPort` (dispute-core), adapter `CoreParcelDisputeAdapter` → `ParcelDisputeUseCase`. Remplace
  `IDeliveryStatusPort` et `IBillingCompensationPort` (supprimés avec leurs adapters et leur test : ils publiaient sur
  des topics que personne ne lisait).
- Ouverture : le colis est résolu depuis `packageId` ou la livraison (`missionId`), recopié dans `packageId` ; P12
  avec gel de la livraison sauf `DELIVERY_DELAYED` (QO-7). Issue : à LT8 et LT9 selon `parcelOutcome()`, avec le
  montant et la partie responsable ; `RESTORE_PREVIOUS` à LT10, LT11 et LT12. L'issue n'est appliquée que si le colis
  porte encore ce litige (`openDisputeId`).
- Délai de dépôt sur un colis clos : `ParcelDisputeService.withinWindow` (depuis `statusChangedAt`, à défaut
  `updatedAt`), durée lue par `DisputeFilingWindowPort` → `ResolveDisputeFilingWindowUseCase` (administration : option
  du tenant, sinon du tenant système, sinon 7 jours).
- Parties d'un colis (`ParcelDisputeUseCase.disputeParties`) : expéditeurs des livraisons du colis, organisations
  freelance, acteurs Kernel des livreurs affectés, propriétaires du relais détenteur et du relais de destination ;
  `recipientActorIds` vide (l'acteur destinataire n'est pas stocké).

### Indemnisation (§4.3)

- `IDisputeCompensationPort.pay` est synchrone. Après LT7, le service paie ; succès → LT9 (`processCompensation`,
  issue du colis avec le montant) dans la même requête. Échec (`DisputeCompensationException`) : la règle est gardée,
  le litige reste `PENDING_COMPENSATION` et la réponse le montre ; nouvel essai par
  `POST /api/v1/disputes/{id}/compensation/pay` (`dispute:resolve`). Bénéficiaire : celui de l'indemnité, à défaut le
  demandeur.
- Adapter par défaut `UnavailableDisputeCompensationAdapter` (`@ConditionalOnMissingBean`) : toujours 503
  `COMPENSATION_UNAVAILABLE`. Le lot L5 fournit l'adapter du fonds Kernel ; 409 `COMPENSATION_FUND_INSUFFICIENT`
  est prévu.

### Événements `tnt.dispute.events`

- `DisputeAggregateEventOutboxPublisher` publie les transitions en attente dans l'outbox au moment de la sauvegarde,
  dans sa transaction : clé = id du litige, versions consécutives (`eventSequence - en attente + 1`…), type
  `TntAggregateType.DISPUTE.eventTypeFor(kind)`, payload masqué (ni description ni résumé), `parcelId`, acteur,
  `trigger`, détails. Schémas : `dispute.payload.schema.json` et `details/Dispute*.schema.json` (7 fichiers).
- Les anciens topics par type (`tnt.dispute.opened`, `.resolved`…) sont encore écrits après la validation, au mieux
  (`TODO(L8)`).

### Chemins système et consommateur

- `IDisputeSystemUseCase` (sans `@RequirePermission`, qui échoue hors requête) : `openFromIncident` (idempotent par
  `incidentId`, premier `parcelIds` du message comme colis), `confirmCompensationPaid`, `expire`. Avant, l'ouverture
  automatique, la confirmation de paiement et l'expiration échouaient en silence.
- `DisputeEventConsumer` (C15) : plus d'`onErrorResume` qui avalait les erreurs ; `ReactiveListenerSupport.awaitBounded`,
  acquittement seulement en cas de succès, JSON illisible relancé (relances puis DLQ). Un `doOnSuccess` qui lisait un
  litige `null` (incident déjà traité) est corrigé en `doOnNext`.

### Persistance (défauts antérieurs corrigés)

- `DisputeRepositoryAdapter.save` faisait un INSERT à chaque sauvegarde (aucune mise à jour possible) : il teste
  l'existence et marque l'entité non nouvelle. Les pièces, la chronologie et les commentaires n'étaient jamais
  enregistrés : pièces mises à jour ou insérées, nouveaux événements de chronologie et commentaires insérés
  (`Persistable` sur les trois entités).
- Les escalades étaient remplacées par `deleteAll()`, qui effaçait **celles de tous les litiges** : remplacé par
  `deleteByDisputeId`.
- `@Version` était posé sur `respondentOrgId` (une chaîne) : retiré ; plus de verrou optimiste sur le litige (à
  rétablir avec une vraie colonne de version).
- Changeset `evt-l4` (`010_evt_l4_dispute_lifecycle.sql`) : colonnes `responsible_party`, `incident_id`, `closed_at`,
  `event_sequence` (`NOT NULL DEFAULT 0`) ; `closed_at` rempli pour les litiges déjà terminaux.

### API

- `RuleDisputeRequest.responsibleParty` (ajout) ; `DisputeDetailResponse` + `responsibleParty`, `incidentId`,
  `closedAt` en fin d'enregistrement.
- Garde à l'ouverture (`DisputeController.ifClaimantMayOpen`) : un non-privilégié doit être partie au colis (ou à la
  mission si le litige ne porte pas sur un colis du Core, ou s'il est `AGENCY_MANAGER`) ; `SYSTEM` réservé aux
  privilégiés.

### Tests (lot L4)

- Unitaires : dispute-core 79 (`DisputeStateMachineL4Test` 11, `DisputeLifecycleL4ServiceTest` 7,
  `DisputeAggregateEventSchemaTest`, `DisputeClaimantGuardL4Test` 3, `DisputeEventConsumerL4Test` 2,
  `DisputeRestMapperL4Test`) ; delivery-core 794 (`ParcelDisputeFilingWindowTest`) ; administration-core 99
  (`DisputeFilingWindowServiceTest`).
- PostGIS et Kafka réels, `ParcelDisputeService` réel de delivery-core : `DisputeParcelL4IntegrationTest` (4) :
  - montée de version : un litige existant reçoit `event_sequence` 0, `closed_at` s'il est terminal ;
  - perte indemnisée de bout en bout : colis `UNDER_DISPUTE` (gel), pièce, chronologie et responsable relus en base,
    colis `LOST`, livraison `FAILED` (D21), 6 événements ordonnés (versions 1..6) sans donnée masquée,
    `ParcelUnderDispute` et `ParcelLost` sur `tnt.parcel.events` ;
  - fonds absent : `PENDING_COMPENSATION`, colis toujours `UNDER_DISPUTE`, clôture administrative refusée, nouvel
    essai payé → `DAMAGED` ;
  - retrait → colis rendu à `IN_TRANSIT` ; retard sans gel (QO-7) ; colis clos depuis 10 jours refusé sans ligne de
    litige ni ligne d'outbox, accepté à 2 jours.
- Porte Liquibase 3/3 ; `KafkaAsyncWiringTest` 27/27 ; `TntArchitectureTest` et `ParcelLookupCallersArchitectureTest`
  verts. ArchUnit : une entrée gelée retirée (`DisputeEventConsumer` n'avale plus ses erreurs) ;
  `applicationMustNotDependOnAdapterInWeb` échoue déjà avant ce lot (la base gelée ne contient pas les
  `@RequirePermission` de `DisputeCommandService` ni ceux d'agency/gofp) ; le nouveau `payCompensation` n'en ajoute
  pas (garde sur le contrôleur).

### Limites et points non tranchés (lot L4)

1. Un destinataire ne peut pas ouvrir lui-même un litige (aucun `recipientActorId` sur le colis) : le support ouvre en
   son nom.
2. Indemnisation : 503 jusqu'au lot L5 (fonds Kernel) ; `POST /{id}/compensation` (référence saisie à la main) est
   gardé pour un paiement constaté hors plateforme.
3. Double écriture des anciens topics de litige et lecture de `tnt.incident.escalated.to.dispute` jusqu'au lot L8.
4. `checkResponseSlaBreaches` (planificateur) modifie des litiges sans les sauvegarder (antérieur au lot).
5. Verrou optimiste du litige absent (l'ancien `@Version` était sur un mauvais champ).
6. `DisputeCommandService` est défini deux fois (`@Service` et `@Bean` de `TntDisputeCoreConfig`), antérieur au lot.
7. Market (`MarketOrderApplicationService`) ouvre un litige `MARKET_CLAIM` avec l'id de commande comme mission : sans
   colis du Core, aucun effet P12 (comportement voulu).

## Lot L5 `evt-paiement-sequestre` (argent : fonds d'indemnisation, séquestre, encaissements, 2026-11-26)

Références : spécification §4, décisions [H], QO-10, QO-11, QO-19, QO-22 à QO-24, QO-26, D-147 à D-151.

### Partie 1 — fonds d'indemnisation (§4.3)

- Contrat Kernel `settlement-controller` relevé dans `docs/kernel-api/openapi.json` : `KernelSettlementDtos`
  (`ShareDto`, `EscrowHoldRequest`, `EscrowResponse`, `ChallengeResponse`, `CashCollection*`, `FundResponse`,
  `Compensation*`) ; port `IKernelSettlementPort`, adapter `KernelSettlementAdapter`.
- Deux identités : les opérations confirmées par un code (défi et blocage du séquestre par le payeur, reversement
  d'espèces par le livreur) partent avec le jeton de l'appelant (`kernelPaymentWebClient`) ; les opérations de la
  plateforme (libération, remboursement, gel, fonds, encaissements) avec le compte de service, par un **nouveau**
  client `kernelSettlementWebClient` (instances Resilience4j déclarées, **aucun rejeu** : un POST expiré peut déjà
  avoir déplacé de l'argent ; `kernelServiceAccountWebClient` rejoue deux fois, ce qui convient aux rôles, pas à
  l'argent).
- `CompensationService` (port `ICompensationUseCase`) : miroir existant → rendu sans appeler le Kernel ; wallet du
  bénéficiaire par `GET /wallets/owner/{acteur}` (l'acteur Core est l'utilisateur Kernel), absent → 409
  `BENEFICIARY_WALLET_MISSING` ; solde du fonds insuffisant → 409 `COMPENSATION_FUND_INSUFFICIENT` ; refus du Kernel
  (4xx) → 409 `KERNEL_PAYMENT_REFUSED` ; Kernel injoignable → 503 `KERNEL_PAYMENT_UNAVAILABLE` ; sinon
  `POST /compensations` (référence `DISPUTE:<id>`).
- Le miroir `billing.wallet_compensations` (unique par litige, changeset `evt-l5-001`) et
  `tnt.billing.compensation.paid` sont écrits dans une **nouvelle** transaction : une fois le Kernel payé, ils
  survivent à l'échec de la transaction du litige, et l'événement permet au module litige de rattraper LT9.
  `confirmCompensationPaid` ne fait rien sur un litige déjà `COMPENSATED` (sinon chaque événement publié après un
  paiement synchrone serait relancé jusqu'à la DLQ).
- `KernelDisputeCompensationAdapter` implémente `IDisputeCompensationPort` (billing-wallet dépend désormais de
  dispute-core, couche inférieure) ; dispute-core le résout par `ObjectProvider` (repli 503 sans lui) au lieu d'un
  bean `@ConditionalOnMissingBean`, dont l'issue dépendait de l'ordre d'import des deux configurations.
- Supprimés : `tnt.billing.compensation.initiated` (constante, topic provisionné, listener
  `onCompensationInitiated` / marqueur `COMPENSATION_NOT_SUPPORTED`).
- Tests : `KernelSettlementAdapterTest` 4 (MockWebServer : chemins, corps, enveloppe, identité appelante),
  `CompensationServiceTest` 4 ; `DisputeCompensationFundIntegrationTest` 3 (PostgreSQL, Kafka et Kernel simulé :
  litige jugé → payé → `COMPENSATED` dans la même requête, miroir et référence relus en base,
  `compensation.paid` reçu sur Kafka, second paiement sans appel Kernel ; fonds insuffisant → `PENDING_COMPENSATION`
  puis relance payée ; miroir et enveloppe gardés après rollback de l'appelant). Porte Liquibase 3/3,
  `KafkaAsyncWiringTest` 27/27, `KernelResilienceConfigDeclaredTest`.
- Questions au Kernel (à vérifier en réel, code MFA requis) : le compte de service peut-il appeler
  `/compensation-fund` et `/compensations` ? `POST /compensations` est-il idempotent par `reference` (un paiement
  expiré côté Core puis rejoué par le support) ?

### Partie 2 — séquestre de la livraison et encaissements espèces (§4.1, §4.2, §4.5)

**Conception retenue (décision technique, pas de question au mainteneur).**
- Sélection payée (§4.1) : défi puis confirmation ; la confirmation bloque le séquestre avec le code du client, puis
  élit la candidature avec le `deliveryId` choisi à l'intention. Si l'élection échoue avant que la livraison existe,
  le séquestre est remboursé (`refundAbortedSelection`) ou l'encaissement annulé ; si la livraison existe déjà (échec
  d'une suite, ex. prix non enregistré), le paiement est gardé et une relance rejoue l'élection (journal
  `SELECTION_PAYMENT_NOT_UNDONE` si le remboursement échoue).
- Les élections faites sans le code du client (besoin Go, invitation, attribution historique) sont payées ensuite
  dans le même séquestre par `POST /api/v1/deliveries/{id}/payment/challenge` puis `POST …/payment`. Livraison déjà
  `DELIVERED` au moment du blocage → libération immédiate (`PAID_AFTER_DELIVERY`).
- Le règlement (`wallet_settlements`, `payment.confirmed`, `commission.calculated`) est écrit à la libération E4
  (`method = ESCROW`, `PAID`, commission `SETTLED`) ; en espèces à K2 (`CASH_COLLECTED`, commission `DUE`) puis
  commission `SETTLED` au reversement K4.
- La facture est émise à la confirmation du blocage (référence = id du séquestre). Limite : un remboursement demande
  un avoir manuel.

**Code.**
- Domaine billing-wallet : `EscrowShares`, `EscrowSharesPolicy` (PLATFORM = prix × taux arrondi `HALF_UP` au franc,
  MERCHANT donné, COURIER = reste, refus si négatif), `EscrowOutcomePolicy` (actions RELEASE/REFUND/FREEZE/UNFREEZE/
  NONE selon la fin de livraison et la partie responsable, QO-10, QO-23, QO-24), `PaymentIntent`, `EscrowMirror`,
  `CashCollectionMirror`.
- Persistance : `PaymentMirrorRepositoryAdapter` (`DatabaseClient`) sur `billing.wallet_payment_intents`
  (`delivery_id` unique), `billing.wallet_escrows` (unique livraison + objet), `billing.wallet_cash_collections`
  (changeset `evt-l5-002`) ; `billing.wallet_holds` supprimée (`evt-l5-003`, décision H.1).
- `EscrowService` (`IEscrowUseCase`) : intention (wallets payeur/livreur par `GET /wallets/owner`, 409
  `WALLET_MISSING`), défi Kernel avec le jeton du client, blocage (403 si l'appelant n'est pas le payeur, 409 intention
  consommée/expirée/CASH, reprise idempotente par `GET /escrows?subjectType=DELIVERY&subjectId=…`), transitions
  plateforme idempotentes (état cible déjà atteint → rien ; refus 4xx du Kernel relu par `GET /escrows/{id}`).
- `CashCollectionService` (`ICashCollectionUseCase`) : ouverture à la confirmation CASH, `collected` à la remise,
  annulation tant que `OPEN`, défi et reversement par le livreur (403 sinon), liste de ses reversements en attente.
- `PaymentLifecycleService` + `WalletPaymentLifecycleConsumer` : `tnt.delivery.events` (`DeliveryDelivered`,
  `DeliveryAtRelayHub` avec `details.atDestination`, `DeliveryCancelled`, `DeliveryFailed`) et `tnt.dispute.events`
  (`DisputeOpen` → gel `DISPUTE:<id>` ; retrait/expiration → dégel ; jugement/indemnisation → issue).
- `IncidentPaymentFreezeAdapter` réécrit sur le séquestre (`INCIDENT:<motif>`), sans lever un gel de litige.
- GOFP : `SelectionPaymentService` + `SelectionPaymentController` (`/api/announcements/{aid}/responses/{rid}/
  selection/challenge|confirm`), `DeliveryPaymentApplicationService` réécrit sur le séquestre, `CashCollectionController`
  (`/api/v1/gofp/cash-collections/mine`, `/{deliveryId}/remit/challenge`, `/{deliveryId}/remit`), erreurs en
  ProblemDetail `urn:tiibntick:payment:<code>` ; receive-code supprimé (endpoint, colonnes `evt-l5-001`, champs) ; la
  garde de prix regarde l'existence d'une intention.
- delivery-core : `Delivery.create(id, …)`, `SelectAnnouncementResponseCommand.deliveryId`, D7 `atDestination`.
- Configuration : `tnt.payments.platform-wallet-id` (`TNT_PAYMENTS_PLATFORM_WALLET_ID`), vide → 503
  `PLATFORM_WALLET_NOT_CONFIGURED`.

**Tests.** `EscrowPoliciesTest` 4, `WalletPaymentLifecycleConsumerTest` 3, `SelectionPaymentServiceTest` 7,
`DeliveryAccessRulesTest` (prix figé dès qu'une intention existe), `DeliveryPaymentFlowIntegrationTest` 10 réécrit
(contrôleur réel derrière `@RequirePermission`, Kernel simulé distinguant le jeton du client du compte de service :
défi et blocage avec le jeton du client, facture avec l'id du séquestre, double paiement 409 sans appel Kernel, mauvais
code → 409 `KERNEL_PAYMENT_REFUSED` sans rien enregistrer, libération à la remise et règlement `ESCROW`/`SETTLED`,
paiement après remise libéré aussitôt, gel/dégel d'incident sans lever un gel de litige, CASH refusé, gardes de rôle
et de permission) ; `EscrowLifecycleIntegrationTest` 4 (PostgreSQL, Kafka et Kernel simulé : `DeliveryDelivered`
publié sur Kafka → libération, règlement relu en base, `payment.confirmed` et `commission.calculated` reçus,
rejeu sans seconde libération ; `DisputeOpen` → gel puis `DisputeCompensated` → remboursement ; espèces ouvertes,
encaissées à la remise, reversées par le livreur ; intention non utilisée réutilisée) ; `KafkaAsyncWiringTest` 27/27
(contexte complet sur Kafka, PostGIS et Redis réels, dont `disputeOpenFreezesEscrow`) ; porte Liquibase 3/3
(nouvelles tables, `wallet_holds` et `receive_code` absents). Suite unitaire complète verte hors échecs préexistants de
`tnt-bootstrap` (Redis attendu sur 6379, `LayeringArchitectureTest` gelé, `SpringdocProdExposureTest`) ; aucune
nouvelle dépendance `application` → `adapter.in.web`.

**Limites.**
1. `payment.mode` du payload de livraison reste `NONE`.
2. Un dégel de litige ne vérifie pas s'il reste un incident escaladé ouvert.
3. Faute `RELAY`/`AGENCY` non tranchée → aucune action, journal `ESCROW_OUTCOME_UNDECIDED`.
4. Litige qui ordonne un retour : l'événement ne dit pas si l'aller a été fait → remboursement.
5. Facture émise au blocage ; remboursement → avoir manuel.
6. Échecs d'écriture après un mouvement Kernel journalisés (`PAYMENT_UNRECORDED`, `CASH_SETTLEMENT_UNRECORDED`,
   `CASH_COMMISSION_UNRECORDED`) : rapprochement manuel.
7. Questions au Kernel (K1 à K10) toujours ouvertes, `/decision` non appelé ; aucun essai réel (code MFA requis).
8. ~~Restent pour le lot : dettes QO-22/QO-26, garantie de retour sur séquestre, suppression de
   `tnt.billing.wallet.split_executed`~~ (faits en partie 3) ; prorata QO-11 (limite Kernel, voir partie 3).

### Partie 3 — dettes envers le fonds, retours payés par le fonds, garantie de retour (§4.3, §4.4, §4.5 ; QO-22, QO-26, D-147, D-151, D-152 ; 2026-11-27)

**Conception retenue.**
- **Dette (QO-22, QO-26)** : registre local `billing.wallet_debts` (changeset `evt-l5-004-wallet-debts`, référence
  unique). États `PENDING` (en attente du paiement par le fonds), `OPEN`, `REPAID`, `CANCELLED`. Recouvrement « comme
  un impayé d'espèces » : un encaissement Kernel `subjectType = DEBT`, `subjectId = id de la dette`, ouvert sur le
  wallet du débiteur avec une seule part `PLATFORM` = wallet du fonds pour le montant entier, marqué encaissé aussitôt ;
  le débiteur le reverse avec son code (`remit`) → `REPAID`. Débiteur inconnu (faute `RELAY`/`AGENCY`) → `OPEN` sans
  encaissement, l'administrateur désigne l'organisation débitrice puis le recouvrement s'ouvre.
- **Litige indemnisé (QO-26)** : `DisputeCompensated` avec `responsibleParty` `COURIER` → dette du livreur de la
  livraison (`intent.courierActorId`), `RELAY`/`AGENCY` → dette sans débiteur, `SENDER`/`RECIPIENT`/`PLATFORM` →
  aucune. Référence `DISPUTE:<disputeId>`, montant `compensationAmount` du payload.
- **Retour payé par le fonds (QO-22)** : intention `Mode.FUND` pour une annonce RETURN dont le colis parent a
  `returnCostPayer` `COURIER` ou `PLATFORM`, ou dont la garantie a été capturée (D-151). Rien n'est pris à
  l'expéditeur ; confirmation sans code (`paymentStatus = FUNDED`). À la `DeliveryDelivered` du retour, le fonds paie
  au livreur du retour la part livreur (`/compensations`, référence `RETURN:<deliveryId>`, pas d'événement
  `compensation.paid`) ; la dette `PENDING` du livreur fautif (créée à l'élection) s'ouvre ; retour annulé ou échoué →
  dette annulée.
- **Garantie de retour (D-147, D-151, D-152)** : à la sélection payée d'une livraison ALLER vers un point relais, un
  second séquestre (`subjectType = RETURN_GUARANTEE`, même montant, une part `PLATFORM` = wallet du fonds) est confirmé
  par un **second code** du client. Il est tenu avant le prix ; si le prix échoue ou si l'élection échoue avant la
  création de la livraison, il est remboursé. `KernelReturnGuaranteeAdapter` implémente `ReturnGuaranteePort` de
  delivery-core : `hold` constate le séquestre (`HELD`, ou `NOT_HANDLED`/`GUARANTEE_NOT_CONFIRMED` si la livraison a
  été élue sans lui), `release` le rembourse (colis retiré, fin sans retour), `capture` le libère au fonds (fin de
  garde), puis le retour est élu en mode `FUND`.
- **`tnt.billing.wallet.split_executed` supprimé** : constante, topic déclaré par le bootstrap et écoute comptable
  retirés (la répartition est faite par le Kernel dans le séquestre).

**Code.** billing-wallet : `WalletDebt`, `IDebtRepository`/`DebtRepositoryAdapter`, `IDebtUseCase`/`DebtService`,
`WalletDebtController` (`/api/v1/billing/debts` : `GET /mine`, `POST /{id}/repay/challenge`, `POST /{id}/repay` ;
administration `GET ?status&limit`, `POST /{id}/assign`, `POST /{id}/recovery`, `POST /{id}/write-off`) ;
`PaymentIntent.Mode.FUND`, `IEscrowUseCase.confirmFunded`, `guaranteeChallenge`/`holdGuarantee`/`applyGuarantee`/
`findGuarantee` ; `CompensationService.payReturnCourier` ; `PaymentLifecycleService` (retour FUND livré/abandonné,
`disputeCompensated`) ; `KernelReturnGuaranteeAdapter` (dépendance Maven vers `tnt-delivery-core`, sans cycle) ;
codes `DEBT_NOT_FOUND`, `DEBT_FORBIDDEN`, `DEBT_NOT_RECOVERABLE`. GOFP : `SelectionPaymentService` (mode FUND imposé,
`FUND` demandé → 400 `PAYMENT_MODE_INVALID`, dette `PENDING` à l'élection, second code de garantie, 400
`RETURN_GUARANTEE_CODE_REQUIRED`), DTO de sélection étendus (`guaranteeAmount`, `guaranteeChallengeToken`,
`guaranteeExpiresInSeconds`, `guaranteeCodePreview` ; `guaranteeChallengeToken`/`guaranteeCode` à la confirmation ;
`guaranteeStatus` en réponse).

**Tests.** `DebtAndFundedReturnTest` 8 (recouvrement ouvert et parts envoyées, idempotence par référence,
remboursement par le seul débiteur, dette d'organisation désignée par l'administrateur, relance et abandon, dettes
`PENDING`, retour FUND livré/abandonné, dettes de litige par partie responsable) ; `SelectionPaymentServiceTest` 11
(dont deux codes pour un relais, garantie remboursée si le prix échoue, retour financé par la garantie capturée) ;
`WalletPaymentLifecycleConsumerTest` 3 ; `EscrowLifecycleIntegrationTest` 7 (PostgreSQL, Kafka, Kernel simulé :
`DisputeCompensated` sur Kafka → dette `OPEN` avec encaissement `DEBT` vers le fonds → reversée `REPAID` ; retour FUND
livré → indemnité `RETURN:` au livreur et dette activée, rien bloqué chez l'expéditeur ; garantie tenue, constatée,
capturée de façon idempotente, sans règlement de livraison, ou remboursée) ; suites unitaires wallet, GOFP,
delivery-core, accounting vertes ; `KafkaAsyncWiringTest` 27/27 ; porte Liquibase 3/3 (`wallet_debts`).

**Limites.**
1. **K11** (à vérifier sur le Kernel réel) : un encaissement dont la seule part est le wallet du fonds, sans part
   `COURIER`, avec `subjectType = DEBT`, et son ouverture/encaissement par le compte de service.
2. **K12** : un séquestre `RETURN_GUARANTEE` sur le même `subjectId` que celui de la livraison, et un second défi en
   parallèle du premier.
3. La garantie couvre le prix de l'aller ; le fonds paie la part livreur du prix du retour, qui peut différer.
4. Un retour élu par l'attribution historique (sans sélection payée) n'a pas d'intention FUND : pas de paiement par
   le fonds (l'administrateur le règle).
5. Prorata QO-11 : le Kernel ne rembourse qu'en totalité (K6) ; pas de remboursement partiel.
6. Une dette n'est pas déduite automatiquement des gains du livreur ; aucune suspension automatique d'un débiteur
   (décision d'administrateur, spec §5.2).

## Lot L6 `evt-comptabilite-kernel` (livres tenus dans le Kernel, 2026-11-27)

**Conception (cible §4.6, décisions H.3, QO-20, QO-21).**
- Producteur des écritures : accounting-core, par des consommateurs **bloquants** (`ReactiveListenerSupport`) de
  `commission.calculated`, `compensation.paid` et `invoice.paid`. Un Kernel indisponible fait échouer l'enregistrement
  (relance du conteneur puis `tnt.dlq`) ; l'écriture est idempotente par fait.
- Livres : PLATFORM (organisation Kernel `tnt.accounting.kernel.platform-organization-id`,
  `TNT_ACCOUNTING_PLATFORM_ORGANIZATION_ID`) ou ORGANIZATION (émettrice d'une facture) ; l'organisation est envoyée en
  `X-Organization-Id` (K7). Identité : compte de service du Core, client `kernelSettlementWebClient` (sans rejeu de
  transport).
- Écritures (QO-20) : commission **SETTLED** → D 4671 / C 706 (plateforme) ; indemnité de litige ou retour payé par le
  fonds → D 6788 / C 4672 (plateforme) ; facture payée → D 4671 / C 411 (organisation émettrice). La part du livreur
  n'est écrite nulle part (H.3). Une commission `DUE` (espèces non reversées) est `SKIPPED` ; une facture sans
  organisation émettrice est `SKIPPED` (`NO_ISSUER_ORGANIZATION`).
- Correspondances locales (changeset `evt-l6-001-kernel-bookkeeping-links`) : `accounting.kernel_account_links`,
  `kernel_journal_links` (uniques par propriétaire et code), `kernel_entry_links` (`source_key` unique :
  `COMMISSION:<settlementId>`, `COMPENSATION:<compensationId>`, `INVOICE_PAID:<invoiceId>` ; états `PENDING`, `POSTED`,
  `VALIDATED`, `FAILED`, `SKIPPED` ; le fait complet est gardé en JSON pour être rejoué).
- Reprise sûre : une ligne laissée `PENDING`/`FAILED` cherche d'abord l'écriture du Kernel par sa référence
  (`GET /entries`) avant d'en créer une ; une écriture créée mais non validée est seulement validée.
- Comptes et journaux Kernel cherchés (`GET /accounts/search?query=`, `GET /journals`) puis créés (`POST /accounts`
  avec `externalId` = UUID nommé de `<propriétaire>:<id>:<code>`, `POST /journals` `TNT-COM`, `TNT-IND`, `TNT-VTE`),
  puis `POST /entries` et `POST /entries/{id}/validate`. Chemins hérités (`/ecritures`, `/comptes`) non utilisés.
- Journal local (QO-21) : plus écrit ; `POST /api/accounting/journal-entries` répond **410**
  `ACCOUNTING_JOURNAL_READ_ONLY` ; lecture inchangée ; suppression des tables après accord explicite du mainteneur.
- Supprimés : écoutes `payment.confirmed` (rien à écrire, H.3 ; aucune part MERCHANT n'existe encore) et
  `tnt.delivery.freelancer_org.assigned` (pas un fait comptable). `KernelAccountingPort.findJournalEntryById` pointe
  sur `/api/accounting-service/entries/{id}`.

**Changements producteurs liés.**
- billing-wallet : `compensation.paid` est aussi publié pour un retour payé par le fonds (`reason = RETURN_COST`,
  `returnDeliveryId`, `disputeId = null` ; les indemnités portent `reason = DISPUTE_COMPENSATION`) ; dispute-core
  ignore `RETURN_COST`. Une commission reversée en espèces (K4) republie `commission.calculated` avec
  `commissionStatus = SETTLED` (auparavant jamais republiée : la comptabilité ne l'aurait jamais vue).
- billing-invoice : `InvoicePaid` porte `issuerOrgType` et `issuerOrgId` (ajoutés à la fin).
- Administration : `GET /api/v1/accounting/kernel-entries?status=&limit=` et `POST /{id}/retry`
  (`accounting:admin`).

**Tests.** `KernelBookkeepingIntegrationTest` 5 (PostgreSQL réel + changelog accounting, Kafka embarqué, vrai
consommateur, Kernel simulé : commission SETTLED → écriture validée D 4671 / C 706 dans le livre plateforme
(`X-Organization-Id`), rejeu et seconde commission sans doublon ni recréation de comptes, journal local vide ;
commission DUE et facture sans émetteur `SKIPPED` sans appel ; facture dans le livre de l'organisation émettrice,
retour payé par le fonds D 6788 / C 4672 ; Kernel en panne → relance du conteneur ; réponse perdue → `FAILED`, rejeu
qui retrouve l'écriture par sa référence et la valide sans la recréer ; livre plateforme non réglé → `FAILED` puis
rejouable). `BookkeepingFactPolicyTest` 3, `SettlementEventPublicationTest` 7 (dont la republication SETTLED),
`EscrowLifecycleIntegrationTest` 7 (retour payé par le fonds publié `RETURN_COST`), `KafkaAsyncWiringTest` 27/27
(contexte complet : commission d'un séquestre écrite une fois dans le livre plateforme malgré un doublon), porte
Liquibase 3/3 (tables `kernel_*_links`).

**Limites.**
1. **K13** : `GET /entries` sans filtre (recherche par référence côté Core) ; volume à surveiller.
2. **K14** : valeurs de `accountType` (`ASSET`, `REVENUE`, `EXPENSE`) et du `type` de journal (`GENERAL`) supposées,
   non énumérées par l'OpenAPI.
3. Aucune part MERCHANT n'est produite aujourd'hui (la sélection GOFP n'en crée pas) : son écriture viendra avec la
   première part.
4. Remboursement d'une dette envers le fonds (L5 partie 3) non écrit (compte de produit non prévu par QO-20).
5. Factures émises sans organisation émettrice (livraisons) : non écrites, listées `SKIPPED`.
6. Aucun essai sur le Kernel réel (code MFA requis).

## Lot L8 `evt-bascule-consommateurs` (bascule des consommateurs, retrait des anciens topics, 2026-11-27)

**Principe (cible §3.7–§3.10, décision G.5).** Chaque consommateur lit désormais les cinq topics d'agrégat
(`tnt.parcel.events`, `tnt.delivery.events`, `tnt.announcement.events`, `tnt.incident.events`, `tnt.dispute.events`)
et choisit ce qui le concerne par `eventType` (et, au besoin, `previousState.status` → `payload.status`). Un
enregistrement sans `schemaVersion` (ancien format) est ignoré (§3.9). La double écriture `TODO(L8)` des lots L2–L4 est
retirée d'un coup, avec les anciens topics.

**Consommateurs basculés.**

| Classe (module) | Lisait | Lit désormais |
|---|---|---|
| `EntityChangedEventConsumer` (sync) | `tnt.delivery.mission.*`, `tnt.delivery.package.*`, anciens topics incident/litige | les 5 topics d'agrégat ; type indexé = `PARCEL`, `DELIVERY`, `ANNOUNCEMENT`, `INCIDENT`, `DISPUTE` (au lieu de `MISSION`/`PACKAGE`) ; opération : `CREATED` sans `previousState`, `STATUS_CHANGED` si l'état change, sinon `UPDATED` ; auteur = `actor.type:actor.id` |
| `MissionStatusEventConsumer` (realtime) | `tnt.delivery.mission.status.changed` | `tnt.delivery.events` → `/topic/delivery/{id}` (mêmes champs) ; `tnt.parcel.events` → `/topic/tracking/{code}` avec les **états du colis** ; la correspondance livraison → code vient des événements de colis (`currentDeliveryId`) |
| `InvoiceBillingEventConsumer` (billing-invoice) | `tnt.delivery.mission.completed` | `DeliveryDelivered` ; facture au prix convenu, client = expéditeur, émetteur = agence ou organisation freelance ; `generateOnceAsSystem` (une seule facture par livraison et tenant, rejeu sans doublon) |
| `DeliveryEventSalesConsumer` (sales) | `tnt.delivery.mission.*` | `DeliveryPickedUp`, `DeliveryDelivered`, `DeliveryFailed` ; `SalesApplicationService.followDelivery` (commande expédiée → en livraison → livrée, ou retour avec le motif d'échec) |
| `IncidentEventConsumer` (actor) | `tnt.incident.closed` | `IncidentClosed` de `tnt.incident.events` ; même règle « livreur impliqué » que l'ancien publieur |
| `DeliveryEventConsumer` (tp, fidélité) | `tnt.delivery.mission.completed` | `DeliveryDelivered` |
| `DeliveryStatusConsumer` (agency-assignment) | `tnt.delivery.mission.status.changed` / `.created` | transitions de `tnt.delivery.events` ; indices masqués (quartier, ville, nom affiché, relais) |
| `InventoryHubConsumer` (agency-org) | `tnt.inventory.hub.package.deposited` / `.pickedup` | `tnt.parcel.events` : entrée en `AT_RELAY_HUB` = dépôt (relais de la nouvelle garde), sortie = retrait (relais de la garde précédente) |
| `CoreMissionStatusConsumer` (gofp) | `tnt.delivery.mission.status.changed` | transitions de `tnt.delivery.events` ; code de suivi lu par `findStateAnyTenant` (vide si la livraison est inconnue) |
| `MarketKafkaConsumer` (market) | `tnt.delivery.mission.status.changed` | transitions de `tnt.delivery.events` (commandes trouvées par livraison) |
| `FreelancerOrgKafkaEventConsumer` (notify) | `tnt.delivery.freelancer_org.assigned` | `DeliveryFreelancerOrgAssigned` (`payload.freelancerOrgId`, `payload.courierId`, `details.role`) |
| `DisputeEventConsumer` (dispute) | `tnt.incident.escalated.to.dispute` | `IncidentEscalatedToDispute` de `tnt.incident.events` |

**Producteurs : ancien code retiré.**
- delivery-core : 14 classes d'événements (`DeliveryCreatedEvent`, `MissionStatusChangedEvent`,
  `FreelancerOrgAssignedEvent`, `AnnouncementPublishedEvent`…) et leurs ajouts dans `Delivery` / `DeliveryAnnouncement` ;
  `KafkaDeliveryEventPublisher` ne publie plus que les `AggregateEvent`.
- incident-core : `IncidentKafkaEventPublisher`, `IIncidentEventPublisher`, `IncidentDomainEvents` et les appels des
  services (signalement, gestion, auto-résolution, inter-agences, escalade, passation, tri). Les cas couverts
  (inter-agences, tri) sont des transitions de l'incident.
- dispute-core : les 7 événements, `IDisputeEventPublisher`, `DisputeKafkaEventPublisher` ; les méthodes de
  `Dispute` qui les rendaient (`assignMediator`, `addEvidence`, `rule`, `escalate`, `processCompensation`, `close`,
  `expire`) ne rendent plus rien (interne).
- inventory-core : `PackageDepositedEvent`, `PackagePickedUpEvent` et leurs topics ; `InventoryEventPublisherPort`
  garde seulement `publishStockLow`.
- sync-core : la file morte propre `tnt.sync.entity-changed.dlq` est supprimée ; la fabrique sync reçoit le
  gestionnaire commun (4 tentatives puis `tnt.dlq`).

**Topics retirés (42).** `TntTopics` passe de 149 à 107 noms ; les `NewTopic` correspondants sont retirés de
`TntKafkaTopicsConfig`, les propriétés `consumed.core-mission-*`, `core-package-delivered`, `core-inventory-*` de
`application.yml`. Liste : `tnt.delivery.mission.{created, started, completed, failed, status.changed}`,
`tnt.delivery.freelancer_org.assigned`, `tnt.delivery.package.{delivered, updated, picked-up, disputed,
dispute.released}`, `tnt.delivery.hub-deposit.{created, picked-up}`, `tnt.delivery.announcement.{published,
response.selected}`, `gofp.announcement.published`, `tnt.incident.{created, status.changed, triaged, driver.assigned,
handover.completed, resolved, closed, cancelled, escalated, escalated.to.dispute, interagency.requested,
interagency.completed}`, `tnt.dispute.{opened, status.changed, resolved, evidence.submitted, ruled, escalated,
compensation.processed, closed, evidence-added, refund-initiated, compensation-paid}`,
`tnt.inventory.hub.package.{deposited, pickedup}`, `tnt.sync.entity-changed.dlq`. Un cluster existant garde ces
topics (Spring Kafka ne supprime rien) : suppression par l'exploitation, spec d'administration §5.5.

**Défauts trouvés et corrigés en route.**
1. **Schéma Market** (`tnt-market-back-core`, changesets `008-align-*`, label `market-schema-008`) : les entités
   mappaient 82 colonnes et 5 colonnes `@Version` que les changelogs 001–007 n'avaient jamais créées
   (`column market_orders.mission_id does not exist`, puis `… version does not exist`) : aucune commande, demande de
   devis, offre, avis ou contrat ne pouvait être lu ni écrit. Ajouts seulement (`ADD COLUMN IF NOT EXISTS`) ; reprise
   `delivery_mission_id` → `mission_id` (si UUID) et `transaction_id` → `transaction_ref` ; primitifs Java en
   `NOT NULL DEFAULT` (`quantity` 1, `version` 1 = première version donnée par Spring Data) ; index
   `(mission_id, tenant_id)`. Les anciennes colonnes restent.
2. **Erreurs avalées** : `InventoryHubConsumer.refreshOccupancy` et `CoreMissionStatusConsumer` faisaient
   `onErrorResume(e -> Mono.empty())` ; retirés (traitements idempotents, notification dédupliquée par `eventId`).
3. **Banc de test Liquibase** : 8 tests de migration ajoutaient `src/main/resources` alors que `target/classes` était
   déjà sur le classpath (« Found 2 files with the path ») ; factorisé dans `BootstrapChangelogs.accessor()`.

**Tests.**
- Unitaires nouveaux (20) : `EntityChangedEventConsumerTest` 4, `InvoiceBillingEventConsumerTest` 3,
  `DeliveryEventSalesConsumerTest` 3, `IncidentEventConsumerTest` 3 (règle « livreur impliqué » reprise de
  l'ancien `IncidentClosedCourierTest`), `DeliveryStatusConsumerTest` 2, `InventoryHubConsumerTest` 3,
  `MissionStatusEventConsumerTest` 2 ; tests delivery, incident, dispute, inventory réécrits sur les événements d'agrégat.
- Suites unitaires des 18 modules touchés : 2 377 tests, 0 échec.
- Intégration (Kafka, PostGIS, Redis réels) : `KafkaDeliveryEventPublisherOutboxIntegrationTest` 2 (une seule
  publication `DeliveryAssigned` sur `tnt.delivery.events`), `IncidentKafkaEventPublisherOutboxIntegrationTest` 2,
  `InventoryKafkaEventPublisherOutboxIntegrationTest` 1, `HubCustodyAdapterIntegrationTest` 1,
  `DisputeParcelL4IntegrationTest` 4, `DisputeCompensationFundIntegrationTest` 3,
  `DeliveryCoreConvergenceIntegrationTest` 7, `KafkaAsyncWiringTest` 27/27 (application complète : prise en charge
  d'une vraie livraison → cache du code, notification GOFP, synchronisation agence ; organisation freelance notifiée ;
  incidents ; fidélité créditée une fois ; **aucun enregistrement en `tnt.dlq`** sur toute la classe),
  `EvaluationLoyaltyEndToEndIntegrationTest` 1, `Gdd4OwnerActorMigrationIntegrationTest` 1,
  `MarketSchemaAlignmentIntegrationTest` 1 (toutes les colonnes mappées par les 7 entités Market existent après mise à
  jour d'une base de la version précédente ; ancienne commande retrouvée par sa livraison ; insertion puis mise à jour
  versionnée), porte Liquibase 3/3, `TntArchitectureTest` 1/1.

**Limites.**
1. Un dépôt au relais fait directement dans l'inventaire (dépôt GOFP `RelayDepositService`, `HubParcelService` de
   l'agence, API REST) sans transition D7 de la livraison n'émet plus aucun événement Kafka : l'état du colis fait foi.
2. Le flux SSE de suivi diffuse les états du **colis** (rupture pour Mobile/BFF, voir `mobile-bff.md`).
3. ~~Les fichiers gelés d'ArchUnit gardent les lignes des anciennes méthodes supprimées~~ — fait plus bas (partie 2) :
   les 9 lignes corrigées ont été retirées du gel.

### L8, partie 2 — branchement total : annexe 9.1, preuves par consommateur, scénario complet, vocabulaire (2026-11-27)

**Annexe 9.1 appliquée (topics morts, littéraux conservés).** `TntTopics` passe de **107 à 101** noms (149 au
départ du lot ; un chiffre « 140 » donné en cours de route était faux).
- 22 constantes sans producteur ni consommateur retirées, avec leurs `NewTopic` (14 beans d'une ligne et
  `tntOutboxEvents`) et leurs entrées `TntModuleRegistry` (outbox → `tnt.dlq` seul ; actor → `ACTOR_PROFILE_UPDATED` ;
  geo sans `GPS_POSITION_UPDATED` ; realtime sans `PRESENCE_CHANGED` ; media sans topic).
- 16 constantes créées pour des littéraux encore produits (annexe : « constante + `NewTopic` ») :
  `tnt.notifications.sent|failed` (notify), `FREELANCER_ORG_SUSPENDED|KYC_UPGRADED` (organization),
  `ACTOR_FREELANCER_ASSOCIATED|DISSOCIATED|ORG_LINKED|ORG_UNLINKED` (actor), 8 topics `RESOURCE_*` (resource). Les
  producteurs (`KafkaActorEventPublisher`, `FreelancerOrgEventPublisherAdapter`, `NotifyProperties`,
  `ResourceKafkaEventPublisher`, `ResourceCoreProperties`, événements sync/realtime, `CustomTemplateSavedEvent`) et les
  `@KafkaListener` (notify, vehicle, permissions) citent la constante.
- **Erreurs de l'annexe corrigées** : 4 topics marqués « SUPPRIMÉ » sont produits et restent
  (`tnt.realtime.actor.connected|disconnected`, `tnt.sync.completed`, `tnt.billing.custom_template.saved`) ;
  `tnt.realtime.sessions.active` est une métrique Micrometer, pas un topic ; `EntityVersionChangedEvent` (sync) n'avait
  aucun émetteur : classe et branche du publieur supprimées.
- Market : `MarketKafkaEventPublisher` ne concatène plus un préfixe ; table `TOPICS` (10 classes d'événements → une
  constante `MARKET_*` chacune), une classe inconnue lève `IllegalArgumentException` au lieu de publier sur un nom
  inventé. `EntityChangedEventConsumer` (sync) écoute les mêmes constantes.
- `KafkaAsyncWiringTest.everyTopicConstantIsDeclared` : 101 constantes, toutes déclarées en `NewTopic`, aucun
  `NewTopic` hors constante (sauf `tnt.trust.*`, blockchain laissée de côté).

**Expiration au relais de l'agence.** `HubParcelExpiryJob` (et son test) supprimé : l'expiration est la réaction à
l'événement de colis `AT_RELAY_HUB → RETURN_REQUESTED` (`returnCause = NOT_COLLECTED_AT_RELAY`, effet de
`DeliveryFailed(NOT_COLLECTED_AT_RELAY)`). `InventoryHubConsumer` (adaptateur entrant) délègue à
`HubParcelExpiryService.expireDeposited(trackingCode, tenant, now)` (transactionnel ; colis déjà `EXPIRED` ou inconnu :
rien), ce qui supprime aussi l'accès direct de l'adaptateur entrant au dépôt. L'endpoint manuel
`POST /api/v1/tenants/{tenantId}/agency-registry/hubs/expired/process` reste ; les propriétés du job sont retirées.

**tp.** `DeliveryEventConsumer` (fidélité) : un client Go-Freelancer n'a pas de profil client tp-core ;
`incrementDeliveriesIfProfiled` termine vide au lieu de lever `TntThirdPartyNotFoundException` (qui partait en
`tnt.dlq` à chaque livraison GOFP).

**ArchUnit dégelé.** Les 9 lignes corrigées par le lot sont sorties du gel ; la règle « adaptateur entrant → adaptateur
sortant » ne garde que 3 lignes préexistantes d'`InventoryHubConsumer` (renommées par la nouvelle signature) ; la règle
de cycle garde la seule ligne `TrustCommittedEventConsumer` (blockchain hors périmètre). Le renommage §2.3 change une
ligne gelée de la règle « application → adapter.in.web » (`InvoiceService.listByMissionId(String)` →
`listByDeliveryId(String)`, même annotation `@RequirePermission`) : elle est regelée sous son nouveau nom. Cette règle
échouait déjà avant le lot (violations GOFP, agency-staff… jamais gelées) ; le lot n'y ajoute aucune violation.

**Preuves par consommateur (`KafkaAsyncWiringTest`, section 10, 12 tests, application complète, vrai Kafka).**
`DeliveryDelivered` → facture, ventes, fidélité, sync ; alerte de retard → GOFP ; colis non retiré → colis du relais
d'agence `EXPIRED` ; incident escaladé → litige ouvert ; indemnisation payée → litige et écriture comptable
`IND-<litige>` ; événements de facture → écriture `FAC-<facture>` et état `PAID` du rapport ; paiement confirmé →
facture, commission agence, commande Market ; seuils modifiés → cache livraison vidé ; véhicule affecté → livraison ;
organisation freelance → actor ; KYC validé → agence ; notifications restantes. Classe : 39/39.

**Scénario complet (`TntApplicationFullStackIT.deliveryLifecycleEndToEnd`).** Création → `ASSIGNED` → `PICKED_UP` →
`IN_TRANSIT` → `DELIVERED` par les vrais services (une transition par sauvegarde, comme en production) ; vérifie sur
Kafka la suite des états livraison et colis, le schéma (§3.2), l'absence de donnée personnelle (sentinelles PII,
téléphone ; nom affiché « Awa P. »), en-têtes = corps, versions croissantes sans doublon, cache de suivi et index sync
à `DELIVERED`, et **aucun enregistrement `tnt.dlq`** pour la livraison ou le colis pendant 20 s. Classe : 7/7.

**Vocabulaire unique (§2.3).**
- Ports renommés : `IMissionStatusPort` → `IDeliveryStatusPort` (`pauseDelivery`, `resumeDelivery`, `failDelivery`,
  `getDeliverySnapshot`, `DeliverySnapshot`), adaptateur `DeliveryStatusPortAdapter` ; `IMissionAssignmentPort` →
  `IDeliveryAssignmentPort` (`isDeliveryAssignedTo`), adaptateur GOFP `CoreDeliveryAssignmentAdapter` ;
  `IUpdatePresenceUseCase.assignDelivery|clearDelivery` ; `IKalmanStateRepository.findByDeliveryId|deleteByDeliveryId` ;
  `DeliveryEventNotifier.onDeliveryStatusChanged` ; dépôts de port `findByDeliveryId` / `existsByDeliveryId…`
  (invoice, market, sales, tp), `InvoiceUseCases.listByDeliveryId`, `releaseFromDelivery` (resource) ; paramètre et
  composante `missionId` → `deliveryId` dans tous les ports et commandes (`ComputeCostCommand`,
  `GenerateInvoiceCommand`, `RateThirdPartyCommand`, `EarnLoyaltyPointsCommand`, `AssignVehicleCommand`,
  `AssignFreelancerVehicleToMissionCommand`, ports compliance de l'agence…).
- **Non renommés, volontairement** : les contrats externes (champs JSON REST `missionId`, paramètres de requête,
  colonnes `mission_id`, requêtes dérivées Spring Data, topic `tnt.actor.mission.assigned` conservé sous QO-27,
  `tnt.vehicle.assigned_to_mission`), l'énumération persistée `NotificationType` (`MISSION_ASSIGNED`…, contrainte en
  base et valeur exposée), `GeofenceAnchorPayload` (blockchain hors périmètre) et les noms de classes de cas d'usage
  (`IAssignMissionToDelivererUseCase`…) : un renommage de ces éléments casse les clients et demande sa propre migration.
- Gabarits notify : `notify.freelancer_org.mission_assigned` → `notify.freelancer_org.delivery_assigned`,
  `notify.sub_deliverer.mission_assigned` → `notify.sub_deliverer.delivery_assigned`,
  `notify.gofp.delivery.at_relay_point` → `notify.gofp.delivery.at_relay_hub` (packs JSON et changeset
  `i18n-006-rename-delivery-vocabulary-keys` : une modification d'administrateur de l'ancienne clé est reportée sur la
  nouvelle, les textes de base complètent, l'ancienne clé est supprimée) ; textes « mission » → « livraison ».
  `DeliveryEventNotifier` n'accepte plus `AT_RELAY_POINT` (seuls les événements d'agrégat arrivent).

## Lot L9 `evt-guide-kafka-html` (guide Kafka local, 2026-11-27)

`docs/kafka/guide-kafka-tiibntick.html` (fichier du dépôt, jamais publié en ligne) est **généré** par
`docs/kafka/gen_kafka_guide.py` depuis le code fusionné : constantes et javadoc de `TntTopics`, `@KafkaListener` des
sources `src/main/java` (topics par constante, littéral ou propriété `${…}` résolue dans `application.yml` du bootstrap ;
groupe), références de publication hors listener, schémas `kafka/schemas/v1` inclus tels quels, table ancien → nouveau
lue dans la spécification §3.8. Relevé du 2026-11-27 : 101 topics, 66 liaisons listener → topic, 30 groupes, 35
schémas. Sections rédigées : connexion, cinq topics d'agrégat et leurs types, **comment distinguer les événements d'un
même topic** (`eventType`, `aggregateType`, transition `previousState.status → payload.status`, `details`,
`aggregateVersion`, `eventId`), en-têtes `X-Yow-*`, groupe et ACL lecture seule par plateforme externe (G.4), bonnes
pratiques, relance manuelle depuis `tnt.dlq` (`X-Yow-Replay: true`), suppression des anciens topics d'un cluster.
Limite : la colonne « producteurs » d'un topic hors agrégat liste les classes qui le référencent hors listener
(publication directe ou propriété), pas une preuve d'envoi ; les preuves d'envoi et de réception sont les tests
`KafkaAsyncWiringTest`.

## QO-11 / D-153 : livreur remplacé, payé par le fonds au prorata (2026-11-27)

**Décision.** D-153 (registre) : le séquestre Kernel ne modifie pas ses parts (aucune route, K15), il verse toute la
part `COURIER` au livreur nommé à la mise en séquestre. Le fonds d'indemnisation paie chaque remplaçant au prorata de
la distance ; le livreur d'origine doit la même somme au fonds (dette `COURIER_REPLACEMENT`, recouvrée comme les
autres dettes, D-115/D-120).

**Conception.**
- delivery-core : `Delivery.resumeAfterIncident(trigger, newCourier, newVehicle, handoverPoint)`. Quand le livreur
  change, les `details` de l'événement de reprise portent `replacedCourierId`, `replacementCourierId`,
  `replacedLegKm` (vol d'oiseau, du début de l'étape — ramassage ou remise précédente, colonnes `leg_start_*` — au
  point de remise) et `remainingKm` (du point de remise à l'adresse de livraison). Avant ramassage (D14/D16 vers un
  état où le livreur ne tient pas le colis) : `replacedLegKm = 0`. Point inconnu, ou (0, 0) : distances `null`.
- incident-core : après une remise confirmée, `IncidentResolutionEffects.afterResolved(…, lat, lng)` appelle
  `IDeliveryStatusPort.resumeAfterHandover` avec `IncidentDriverReplacement.handoverLatitude/Longitude`.
- billing-wallet : le consommateur `tnt-billing-wallet-delivery` enregistre chaque changement
  (`billing.wallet_courier_legs`, unique par `eventId`). À la libération du séquestre (`DeliveryDelivered`, ou
  `DeliveryAtRelayHub` au relais de destination), `ReplacementProrataPolicy` partage la part livreur : chaque livreur
  reçoit ses kilomètres (un livreur revenu cumule ses étapes), le dernier reçoit `remainingKm` ; si toutes les étapes
  sauf la dernière font 0 km, le dernier reçoit tout. Arrondi à l'unité, somme plafonnée à la part. Le fonds paie
  (`REPLACEMENT:<livraison>:<rang>`, idempotent par le miroir `wallet_compensations`), puis la dette s'ouvre
  (idempotente par référence). Fonds refusé (409) : journal `REPLACEMENT_FUND_PAYMENT_FAILED`, l'administrateur paie ;
  Kernel indisponible (503) : l'enregistrement échoue et est relancé (puis `tnt.dlq`). Partage indécidable : journal
  `REPLACEMENT_PRORATA_UNDECIDED`.
- `compensation.paid` porte la raison `COURIER_REPLACEMENT` et `deliveryId` ; accounting-core l'écrit en `REP-…` ;
  dispute-core l'ignore.

**Preuves.**
- Unitaires : `DeliveryCourierLegsTest` 5/5, `ReplacementProrataPolicyTest` 6/6, `ReplacementCourierPaymentTest` 4/4,
  `WalletPaymentLifecycleConsumerTest` 4/4 ; modules delivery, incident, dispute, wallet et accounting : verts.
- Intégration : `CourierReplacementFullStackIT` 1/1 — application entière sur PostGIS, Kafka et Redis réels : livraison
  ramassée par A, incident, remise à B au point connu (vrai `DeliveryStatusPortAdapter`), livraison par B ;
  l'événement de remise est valide (schéma) et porte les distances ; le vrai consommateur du wallet enregistre
  l'étape, libère le séquestre, le fonds paie B `REPLACEMENT:<id>:1` au prorata exact, la dette de A est `OPEN`,
  `compensation.paid` `COURIER_REPLACEMENT` est publié, rien en `tnt.dlq`. Le Kernel est un serveur HTTP local qui
  enregistre les appels (11 appels, tous locaux).
- Porte Liquibase 3/3 (deux changesets additifs), `KafkaAsyncWiringTest` 39/39, `TntArchitectureTest` 1/1,
  `TntApplicationFullStackIT` 7/7.

**Constat de configuration (préexistant, non corrigé).** `tnt.kernel.base-url`, `api-key` et `client-id` ne sont
liés aux variables `TNT_KERNEL_*` que dans le profil `prod` d'`application.yml`. Hors de ce profil, chaque client
Kernel prend la valeur par défaut de son `@Value`, c'est-à-dire l'URL de **production**, quelle que soit
`TNT_KERNEL_BASE_URL`. Conséquence observée : la première exécution de `CourierReplacementFullStackIT` a envoyé
4 `POST /api/payments/escrows/<id>/release` et 4 `GET` vers `kernel-core.yowyob.com`, avec la clé factice par défaut
`changeme-kernel-api-key`, sur un identifiant de séquestre aléatoire et inexistant. Aucun effet possible, mais un appel
réel. Le test fixe maintenant `tnt.kernel.*` vers son serveur local. La correction de `application.yml` (lier
`tnt.kernel.*` dans le document par défaut) change le Kernel atteint par toute exécution locale : elle est laissée au
mainteneur.

**Suite (2026-11-27) : aucun test du bootstrap ne joint plus le Kernel de production.** Un relevé des rapports de test
a montré que d'autres tests à contexte Spring (`TntApplicationFullStackIT`, `KafkaAsyncWiringTest`,
`KafkaBlockingConsumersWiringTest`, `DeliveryThresholdsWiringTest`, `PublicPathsSecurityTest`,
`TiiBnTickApplicationTest`) configuraient le pont Kernel sur `kernel-core.yowyob.com` et lisaient au démarrage son
JWKS public (`GET /.well-known/jwks.json`, lecture seule, aucune écriture). Correction côté tests seulement :
`tnt-bootstrap/src/test/resources/config/application.yml` (prioritaire sur `application.yml` du classpath) pointe
`tnt.kernel.*`, `tiibntick.kernel.organization.base-url`, le JWKS et, pour les tests du profil `prod`,
`TNT_KERNEL_BASE_URL` / `JWT_JWK_SET_URI`, vers `127.0.0.1:9` (connexion refusée). Les tests qui ont besoin d'un Kernel
démarrent leur bouchon (`CourierReplacementFullStackIT`). Contrôle : suite unitaire du bootstrap + deux ITs pleine
pile relancées, aucun rapport ne mentionne plus l'hôte de production ; échecs inchangés (les 14 préexistants : 12
contextes sans Redis sur 6379, `SpringdocProdExposureTest`, `LayeringArchitectureTest`). La configuration principale
reste inchangée (décision du mainteneur).

**Limites.**
1. K15 : la modification des parts est demandée au Kernel ; tant qu'elle n'existe pas, le fonds avance la part du
   remplaçant et la récupère par la dette.
2. Distances à vol d'oiseau, pas d'itinéraire routier.
3. Intention `CASH` : aucun partage automatique (le livreur qui encaisse reverse).
4. Remplacement avant ramassage par le chemin automatique (`IncidentAutoResolutionService.applyReplacement`, livreur
   sans colis) : couvert (0 km).


## D-159 à D-175 : registre des administrateurs de la plateforme (TNT_ADMIN), 2026-11-27

Conception et décisions : [registre-administrateurs-plateforme.md](../../../architecture/chantiers/registre-administrateurs-plateforme.md).

**Ce qui change.**
- `tnt-roles-core` : table `tnt_platform_role_grants` (changeset `platform-admins:005`, index unique partiel sur
  l'attribution active), service `PlatformAdminService` (SYSTEM_OWNER automatique, protection 409, tenant d'obtention =
  `tid` de l'attributeur, révocation de masse avec aperçu et jeton de confirmation de 5 min), cache de 60 s invalidé par
  `tnt.roles.permission-changed`, API `/api/v1/admin/platform-admins`, événement `tnt.roles.platform-admin-changed`.
  L'ancien `POST/DELETE /api/v1/admin/roles/assignments` envoie TNT_ADMIN au registre (D-171) ; les anciennes lignes
  TNT_ADMIN de `tnt_user_role_assignments` ne donnent plus rien (D-169).
- `tnt-bootstrap` `TntSecurityConfig` : seul endroit qui donne `ROLE_TNT_ADMIN` (propriétaire du tenant système ou
  registre, quel que soit le `tid`) ; les claims `roles`/`permissions` ne peuvent plus apporter TNT_ADMIN, `*` ni une
  permission réservée aux administrateurs (faille C4 fermée, aussi dans `TntJwtValidator` de tnt-auth-core). Le
  propriétaire n'a plus les 11 rôles métier (D-167). Garde `PlatformAdminSingleSourceTest`.
- Alertes : courriel au bénéficiaire et au propriétaire, message in-app au propriétaire (directive du 2026-11-27),
  chacun dans la langue de son compte ; `IInAppInboxUseCase.sendLocalized` ajouté à notify ; textes `i18n-007`.

**Preuves.** `PlatformAdminFullStackIT` sur l7 (PostGIS, Kafka, Redis réels ; seuls le décodeur de jeton et les deux
sorties vers le Kernel sont remplacés) : cycle complet vert, aucun appel à l'hôte Kernel de production. Porte
Liquibase et clôture `gdd-final` vertes. Suite unitaire `-pl tnt-bootstrap -am` : seul échec
`LayeringArchitectureTest`, préexistant (1 145 violations, inchangé).

**Limites.**
1. K19 et K20 non prouvés en réel (connexion MFA du mainteneur nécessaire).
2. Cache de 60 s : une révocation se voit tout de suite sur l'instance qui la fait et sur les autres dès réception
   de l'invalidation ; au pire 60 s si l'invalidation est perdue.
3. Points ouverts P-1 à P-4 à trancher.

### Suite : D-176 à D-179 (2026-11-27)

- D-176 : rôles d'agence/organisation du jeton confirmés par `tnt_user_role_assignments` dans le convertisseur JWT
  (fermé en cas d'erreur, cache 60 s invalidé par `tnt.roles.permission-changed`). Vérifié avant de couper : le
  propriétaire d'agence reçoit le code Kernel `AGENCY_ADMIN` (non développé) et le personnel provisionné passe par
  l'attribution du Core, donc le flux Agency n'est pas cassé.
- D-177 : transfert de propriété du tenant système à la connexion du nouveau propriétaire.
- D-178 : lecture inter-tenant des TNT_ADMIN par `TenantPathGuardWebFilter`, journal `tnt.audit.cross-tenant`.
- D-179 : migration `hasRole` → `@RequirePermission` notée comme dette.
- Preuves : `PlatformAdminFullStackIT` étendu, vert sur l7, aucun appel au Kernel de production ; suite unitaire :
  seul `LayeringArchitectureTest` (1 145, inchangé).
