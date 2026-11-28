# Audit — câblage Kafka et asynchrone (TiiBnTick Core)

Branche `wt/kafka-async-audit`. Audit statique et preuve par exécution, menés le 2026-11-24 et le 2026-11-25.

## 1. Méthode et banc d'essai

- **Inventaire statique** (scripts d'analyse des sources). Il recense les `@KafkaListener` (topics, `groupId`, `containerFactory`), les producteurs (`KafkaTemplate`, `PublishEventUseCase`/outbox du yow-event-kernel), les `NewTopic` de `TntKafkaTopicsConfig`, les `@Scheduled`/`@SchedulerLock` et le Redis pub/sub.
- **Banc jetable** :
  - broker KRaft `apache/kafka:3.8.0` (`localhost:19092`) ;
  - PostgreSQL (`localhost:15433`) ;
  - Redis (`localhost:16379`).
- **Test d'intégration à contexte complet** : `tnt-bootstrap/src/test/java/com/yowyob/tiibntick/bootstrap/messaging/KafkaAsyncWiringTest.java`.
  - Il démarre tout `tnt-bootstrap` contre ce banc et publie par les **vrais adaptateurs producteurs**. Il vérifie ensuite l'effet chez les consommateurs : ports aval simulés par `@MockitoBean`/`@MockitoSpyBean`, ou lecture du topic de sortie.
  - Il n'est actif que si `TNT_IT_KAFKA_BOOTSTRAP` est défini (`@EnabledIfEnvironmentVariable`). Il est sauté sinon, donc la CI sans broker reste verte.

```bash
TNT_IT_KAFKA_BOOTSTRAP=localhost:19092 TNT_IT_DB_PORT=15433 TNT_IT_REDIS_PORT=16379 \
mvn -o test -pl tnt-bootstrap -am -Dtest=KafkaAsyncWiringTest -Dsurefire.failIfNoSpecifiedTests=false
```

Variables facultatives : `TNT_IT_DB_HOST/NAME/USER/PASSWORD` et `TNT_IT_REDIS_HOST`.

## 2. Résultats d'exécution

| Exécution | Code | Résultat |
|---|---|---|
| Ligne de base, 12 tests | avant correctifs | **4 réussis, 7 échecs, 1 erreur** |
| Après correctifs, 12 tests | `19cf0152` | **12/12 réussis** |
| Suite étendue, 18 tests | `900770e7` | **18/18 réussis** (59 s, hors démarrage du contexte) |
| Preuve rouge GPS, géofence et SLA | ancien `IncidentEventConsumer` | **3/3 échecs** (`Invalid UUID string: null`, enveloppe non lue) |
| `EventPayloadsTest` (unitaire) | | 4/4 |
| `IncidentClosedCourierTest` (unitaire) | | 3/3 |
| Suites unitaires des 12 modules touchés | `1b334318` | **1 093 tests, 0 échec** (common, roles, actor, delivery, realtime, incident, notify, sync, GOFP, market, agency-assignment, accounting) |
| `tnt-bootstrap` (Kafka, topics, architecture, outbox) + amont filtré | final | 54 tests, **1 échec préexistant** : `LayeringArchitectureTest.applicationMustNotDependOnAdapterInWeb`, connu et hors périmètre ; `KafkaAsyncWiringTest` 18/18 |

Échecs de la ligne de base, chacun correspondant à un défaut ci-dessous :

- `gofpSubscriptionAttemptIsConsumed` et `gofpMatchingNotificationIsConsumed` : `MessageConversionException: Cannot convert from [B to SubscriptionAttemptEvent` (D2).
- `missionStatusChangedReachesConsumers` : enveloppe imbriquée non lue (D3).
- `slaBreachOpensIncident` : enveloppe non lue, `agencyId` obligatoire (D3).
- `freelancerOrgAssignedNotifiesOrg` : enveloppe non lue (D3).
- `noGroupCollision` : `tiibntick-core` partagé sur `tnt.delivery.mission.status.changed` et `tnt.incident.closed` (D1).
- `everyConsumedTopicIsDeclared` et `everyProducedTopicIsDeclared` : 65 topics sans `NewTopic` (D4).

Topologie observée au démarrage, sur la ligne de base : 94 topics, 21 groupes de consommateurs et 86 affectations de partitions. Le groupe `tiibntick-core` regroupait à lui seul environ 34 membres.

## 3. Flux : producteur → topic → consommateur → preuve

| Flux | Producteur (réel) | Topic | Consommateur(s) | Prouvé par exécution |
|---|---|---|---|---|
| Changement de statut de mission | `KafkaDeliveryEventPublisher` (outbox) | `tnt.delivery.mission.status.changed` | realtime `MissionStatusEventConsumer`, GOFP `CoreMissionStatusProjectionConsumer`, agency `DeliveryStatusConsumer` (+ market, sync) | **oui** : `missionStatusChangedReachesConsumers` (projecteur GOFP, notifier, `syncFromCoreStatus` agence, cache de suivi realtime) |
| Rupture de SLA → incident | `KafkaDeliveryEventPublisher` | `tnt.delivery.mission.sla.breached` | incident `IncidentEventConsumer` | **oui** : `slaBreachOpensIncident` |
| Mission assignée à une org freelance → notification | `KafkaDeliveryEventPublisher` | `tnt.delivery.freelancer_org.assigned` | notify `FreelancerOrgKafkaEventConsumer` (+ accounting) | **oui** : `freelancerOrgAssignedNotifiesOrg` |
| KYC org freelance approuvé → notification | `TntAdministrationEventPublisherAdapter` | `tnt.admin.freelancer_org.kyc_approved` | notify `FreelancerOrgKafkaEventConsumer` | **oui** : `kycApprovedNotifiesOrg` |
| Permissions modifiées → invalidation du cache | `PermissionChangeEventPublisher` (direct) | `tnt.roles.permission-changed` | roles `PermissionCacheInvalidationListener` | **oui** : `permissionChangeInvalidatesCache` |
| Outbox kernel | `PublishEventUseCase` | n'importe quel topic | — | **oui** : `outboxRelaysToKafka` (en-têtes `X-Yow-*`, clé, payload) |
| GOFP notification de matching | GOFP `KafkaEventPublisher` (outbox) | `gofp.matching.notifications` | `MatchingNotificationConsumer` | **oui** : `gofpMatchingNotificationIsConsumed` |
| GOFP tentative d'abonnement | GOFP `KafkaEventPublisher` | `gofp.subscription.attempts` | `SubscriptionConfirmationConsumer` | **oui** : `gofpSubscriptionAttemptIsConsumed` |
| GPS temps réel → Link | `KafkaRealtimeEventPublisher` (direct) | `tnt.realtime.gps.position.updated` → `tnt.link.tracking.position.latest` | link `GpsPositionKafkaConsumer` | **oui** : `gpsPositionReachesLink` (instantané relu sur le topic compacté) |
| Anomalie GPS → incident | `KafkaRealtimeEventPublisher` | `tnt.realtime.gps.position.updated` | incident `IncidentEventConsumer` | **oui** : `gpsAnomalyOpensIncident` |
| Géofence DANGER_ZONE → incident | `KafkaRealtimeEventPublisher` (outbox) | `tnt.realtime.geofence.triggered` | incident `IncidentEventConsumer` | **oui** : `dangerZoneGeofenceOpensIncident` |
| Incident résolu → reprise de la livraison | `IncidentKafkaEventPublisher` | `tnt.incident.resolved` | delivery `IncidentEventConsumer` | **oui** : `incidentResolvedReachesDelivery` |
| Incident clos → compteur d'incidents du livreur | `IncidentKafkaEventPublisher` | `tnt.incident.closed` | actor `IncidentEventConsumer` | **oui** : `incidentClosedReachesActor` (après D5) |
| Incident escaladé en litige → gel du wallet | `IncidentKafkaEventPublisher` | `tnt.incident.escalated.to.dispute` | wallet `WalletBillingEventConsumer` (+ dispute) | **oui** : `incidentEscalatedToDisputeHoldsSettlement` |
| Commission calculée → wallet et comptabilité | **aucun producteur interne** | `tnt.billing.wallet.commission.calculated` | wallet, accounting | **non** : rien à publier côté Core (voir R1) |
| Paiement confirmé, split exécuté | **aucun producteur interne** | `tnt.billing.wallet.payment.confirmed`, `…split_executed` | accounting, invoice, market, payout | **non** (R1) |
| Redis pub/sub temps réel | n'importe quelle instance (`tnt:rt:topic:*`) | Redis | `RedisTopicMessageListener` | **partiel** : `PUBSUB NUMPAT = 1` pendant le test ; un `PUBLISH` sonde a été reçu (log `Malformed wire message … no separator`) ; pas de session WebSocket/SSE de bout en bout |
| Tâches planifiées + ShedLock | 12 `@Scheduled` verrouillés | — | table `shedlock` | **oui** : 10 des 12 verrous posés en base pendant les exécutions (`locked_by` = hôte) ; manquent `media-cleanup-expired-files` (planifié à 2 h) et `yow-event-outbox-poll` (bean non instancié, R2) |

## 4. Défauts corrigés

| # | Défaut | Commit |
|---|---|---|
| D2 | Trois listeners GOFP (`SubscriptionConfirmationConsumer`, `MatchingNotificationConsumer`, `MatchingUseCase`) n'indiquaient aucune `containerFactory`. Ils tournaient donc sur la fabrique Boot par défaut (`ByteArrayDeserializer`, sans convertisseur), et chaque message échouait (`MessageConversionException`). Ajout de `gofpJsonKafkaListenerContainerFactory` (String + `StringJsonMessageConverter` sur `tntObjectMapper`). | `096a9727` |
| D3 | `KafkaDeliveryEventPublisher` publie `{eventType, aggregateId, tenantId, occurredAt, payload:{…}}`, alors que tous les consommateurs lisaient les champs à la racine. Realtime (rejet `UnrecognizedProperty`), incident, projection GOFP, agence, notify, accounting et market ignoraient ou rejetaient donc les événements de livraison. Ajout de `EventPayloads.unwrap` (tnt-common-core), qui accepte les deux formes, appliqué à ces consommateurs. Les consommateurs GPS et géofence de l'incident lisent maintenant les vrais champs (`delivererId`, `coordinates.latitude/longitude`, `trigger.*`), et `agencyId` y devient optionnel. Le producteur n'est pas modifié, pour ne pas casser les clients externes de sync. | `eec3dea0` |
| D1 | Les listeners déclarés avec `${spring.kafka.consumer.group-id:…}` tombaient tous dans le groupe global `tiibntick-core`. Deux listeners sur un même topic se partageaient alors ses partitions, et chacun ne voyait qu'une partie des événements (delivery et actor sur `tnt.incident.closed`, realtime et autres sur `mission.status.changed`). Chaque module a désormais sa propre clé `tnt.kafka.consumer.groups.<module>`. Le listener d'invalidation du cache de permissions utilise un groupe **par instance** (`auto.offset.reset=latest`), car en multi-instance un seul nœud invalidait. | `181f49e0` |
| D4 | 65 topics produits ou consommés n'avaient aucun bean `NewTopic`. En staging/prod, avec `spring.kafka.admin.auto-create=false`, ils dépendaient de l'auto-création côté broker. Ils sont déclarés dans `TntKafkaTopicsConfig`. | `19cf0152` |
| D5 | Le consommateur actor de `tnt.incident.closed` lit `driverActorId`, que `IncidentClosedEvent` ne portait pas : le compteur d'incidents du livreur n'était jamais incrémenté. Le champ est renseigné avec le livreur déclarant, ou le chauffeur enregistré par les détecteurs GPS ou géofence. L'acteur système et les déclarants non livreurs n'en désignent aucun. | `1b334318` |
| D6 | `docs/RUN_LOCAL.md` indiquait `KAFKA_BOOTSTRAP_SERVERS`, alors que `application.yml` lit `KAFKA_BOOTSTRAP`. | `b3930f64` |
| — | Tests : `KafkaAsyncWiringTest` (12 puis 18 cas). | `61b2dba2`, `900770e7` |

## 5. Défauts non corrigés et risques résiduels

- **R1 — Topics wallet sans producteur interne** *(traité en partie le 2026-11-26, voir §8.1 ; `split_executed` supprimé avec son écoute comptable au lot L5 partie 3, 2026-11-27)* : `tnt.billing.wallet.commission.calculated`, `…payment.confirmed` et `…split_executed`, ainsi que `tnt.billing.compensation.paid` et `tnt.billing.template.applied`. Leurs consommateurs (accounting, invoice, market, payout, notify, dispute) sont câblés mais ne reçoivent jamais rien du Core. Cause : migration du wallet vers le Kernel (voir `kernel-wallet-migration.md`). Seul le Kernel ou un pont peut les alimenter : c'est une décision d'architecture, hors du périmètre de cet audit.
- **R2 — « Outbox » sans relais** *(traité le 2026-11-26, voir §8.2)* : l'auto-configuration du kernel commente « Outbox poller — decommissioned 2026-09-18 ». `EventPublisherService.publish` persiste l'enveloppe **puis publie directement** ; en cas d'échec Kafka, l'enveloppe passe `FAILED` et n'est **jamais republiée automatiquement**. Il n'existe que le rejeu manuel (`ReplayEventService`, dead letter). La garantie de livraison « au moins une fois » d'un vrai outbox transactionnel n'existe donc pas. La ShedLock `yow-event-outbox-poll` n'apparaît d'ailleurs jamais dans la table `shedlock`. Rétablir le poller est un choix d'architecture à arbitrer.
- **R3 — Champs métier absents des événements de livraison** : les consommateurs sales (`orderId`), invoice (`sellingPrice`, `senderThirdPartyId`), tp (`senderThirdPartyId`) et market (`marketOrderId`) attendent des champs que `MissionStatusChangedEvent` / `DeliveryCompletedEvent` ne portent pas. Market ignore désormais proprement l'événement (log debug) au lieu de lever une `NullPointerException`. Les autres restent sans effet. Correction : enrichir le domaine delivery ; une décision produit est nécessaire.
- **R4 — 33 topics produits par chaînes littérales**, hors du référentiel `TntTopics` et sans `NewTopic` :
  - `tnt.resource.*` (8) ;
  - `tnt.trust.*` (17) ;
  - `tnt.actor.freelancer.*` (4) ;
  - `tnt.freelancer_org.kyc.upgraded` et `tnt.freelancer_org.suspended` ;
  - `tnt.realtime.sessions.active`.

  Aucun consommateur interne ne les lit. Avec `auto-create=false` et un broker sans auto-création, l'envoi bloquerait jusqu'à `max.block.ms`. Remède : constantes `TntTopics` et `NewTopic`.
- **R5 — Trust désactivé** (`tnt.trust.enabled=false`) : `yow.trust.events.committed` est consommé mais rien ne le produit tant que trust est désactivé.
- **R6 — Changement de groupes (D1) au déploiement** *(levé le 2026-11-26, voir §8.4)* : les nouveaux groupes démarrent à `earliest` et rejouent l'historique des topics concernés. Les consommateurs touchés sont idempotents ou tolérants (projections, notifications), mais **les notifications freelancer-org peuvent être renvoyées une fois**. À annoncer, ou à neutraliser en positionnant les offsets des nouveaux groupes avant la bascule (`kafka-consumer-groups --reset-offsets --to-latest`).
- **R7 — Augmentation de partitions** : déclarer un `NewTopic` pour un topic existant à 1 partition le fait passer à 3 (`KafkaAdmin` : « increasing if the broker supports it »). Les consommateurs déjà abonnés ne voient les nouvelles partitions qu'au rafraîchissement des métadonnées (`metadata.max.age.ms`, 5 min). Observé lors d'une exécution : 2 tests GOFP en échec à cause de cette transition, verts aux exécutions suivantes. L'ordre par clé n'est pas garanti pendant la transition.
- **R8 — Groupes par instance (roles)** : chaque redémarrage crée un groupe `tnt-roles-cache-<uuid>`. Les groupes orphelins expirent selon `offsets.retention.minutes` (7 jours par défaut).
- **R9 — Analyse statique** : les producteurs dont le topic vient d'une méthode `kafkaTopic()` d'un événement (realtime ETA ou présence) ne sont pas détectés par l'analyse (repère ¹ dans le tableau). Le GPS et la géofence ont été prouvés par exécution.
- **R10 — Le `application.yml` de `tnt-billing-wallet`** lit `KAFKA_BOOTSTRAP_SERVERS`. Il est masqué par celui de `tnt-bootstrap` (premier sur le classpath), donc sans effet à l'exécution, mais la configuration est trompeuse.

## 6. Planification et Redis

- 14 méthodes `@Scheduled`, dont 12 avec `@SchedulerLock`. Le fournisseur `R2dbcLockProvider` est déclaré dans `TntSchedulerLockConfig`.
- `yow-event-outbox-poll` porte un verrou mais n'est pas instancié (R2). `media-cleanup-expired-files` est planifié à 2 h et n'a donc pas tourné pendant les essais.
- Méthodes non verrouillées, volontairement locales à l'instance : `RealtimeMaintenanceScheduler.sweepStaleSessions` (sessions WebSocket locales) et `TntRoleSyncOutboxHealthIndicator.refreshGauges` (jauges locales).
- Les autres classes signalées par l'analyse sont des configurations ou des interfaces (`SyncCoreConfig`, `RealtimeCoreConfig`, `OutboxPollerPort`).
- Redis pub/sub : le motif `tnt:rt:topic:*` est souscrit au démarrage, et un message publié a été reçu par l'application (voir le tableau des flux).

## 7. Inventaire des topics (après correctifs)

Généré par analyse statique des sources : 169 lignes. Répartition :

- 46 topics câblés (producteur et consommateur internes) ;
- 92 sortants uniquement ;
- 7 consommés sans producteur interne (dont `tnt.realtime.gps.position.updated`, faux positif corrigé à la main ¹) ;
- 24 déclarés mais inutilisés.

¹ Producteur dont le topic est porté par l'événement (`event.kafkaTopic()`), non détecté par l'analyse et corrigé à la main.

| Topic | Producteurs | Consommateurs | NewTopic | Statut |
|---|---|---|---|---|
| `gofp.announcement.candidates.ranked` | MatchingUseCase.java | - | oui | sortant (aucun consommateur interne) |
| `gofp.announcement.published` | KafkaDeliveryEventPublisher.java, KafkaEventPublisher.java | MatchingUseCase.java | oui | câblé |
| `gofp.freelancer.created` | KafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `gofp.matching.notifications` | KafkaEventPublisher.java | MatchingNotificationConsumer.java | oui | câblé |
| `gofp.subscription.attempts` | KafkaEventPublisher.java | SubscriptionConfirmationConsumer.java | oui | câblé |
| `tnt.accounting.journal-entry.posted` | AccountingEventPublisherAdapter.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.accounting.period.closed` | AccountingEventPublisherAdapter.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.actor.badge.earned` | KafkaActorEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.actor.freelancer.associated` | KafkaActorEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.actor.freelancer.dissociated` | KafkaActorEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.actor.freelancer.org.linked` | KafkaActorEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.actor.freelancer.org.unlinked` | KafkaActorEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.actor.kyc.validated` | KafkaActorEventPublisher.java, KafkaEventPublisher.java | ActorKycConsumer.java | oui | câblé |
| `tnt.actor.location.updated` | KafkaActorEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.actor.mission.assigned` | KafkaActorEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.actor.profile.updated` | EntityChangedEventConsumer.java, KafkaActorEventPublisher.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.actor.reputation.changed` | - | - | oui | déclaré, inutilisé |
| `tnt.actor.status.changed` | KafkaActorEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.admin.freelancer_org.blacklisted` | FreelancerOrgAdminService.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.admin.freelancer_org.kyc_approved` | FreelancerOrgAdminService.java | FreelancerOrgKafkaEventConsumer.java | oui | câblé |
| `tnt.admin.freelancer_org.kyc_rejected` | FreelancerOrgAdminService.java | FreelancerOrgKafkaEventConsumer.java | oui | câblé |
| `tnt.admin.freelancer_org.suspended` | FreelancerOrgAdminService.java | FreelancerOrgKafkaEventConsumer.java | oui | câblé |
| `tnt.admin.freelancer_org.unsuspended` | FreelancerOrgAdminService.java | FreelancerOrgKafkaEventConsumer.java | oui | câblé |
| `tnt.administration.events` | TntAdministrationEventPublisherAdapter.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.agency.contract.events` | KafkaAgencyEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.agency.events` | KafkaAgencyEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.agency.mission.request` | KafkaAgencyEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.agency.staff.events` | KafkaAgencyEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.billing.compensation.initiated` | BillingCompensationAdapter.java | WalletBillingEventConsumer.java | oui | câblé |
| `tnt.billing.compensation.paid` | - | DisputeEventConsumer.java | oui | **consommé sans producteur interne** |
| `tnt.billing.custom_template.saved` | - | - | oui | déclaré, inutilisé |
| `tnt.billing.invoice.created` | - | - | oui | déclaré, inutilisé |
| `tnt.billing.invoice.events` | KafkaInvoiceEventPublisher.java | InvoiceEventReportConsumer.java | oui | câblé |
| `tnt.billing.invoice.paid` | KafkaInvoiceEventPublisher.java | BillingEventAccountingConsumer.java | oui | câblé |
| `tnt.billing.payment.failed` | - | - | oui | déclaré, inutilisé |
| `tnt.billing.template.applied` | - | FreelancerOrgKafkaEventConsumer.java | oui | **consommé sans producteur interne** |
| `tnt.billing.wallet.commission.calculated` | - | BillingEventAccountingConsumer.java, WalletBillingEventConsumer.java | oui | **consommé sans producteur interne** |
| `tnt.billing.wallet.credited` | - | - | oui | déclaré, inutilisé |
| `tnt.billing.wallet.debited` | - | - | oui | déclaré, inutilisé |
| `tnt.billing.wallet.payment.confirmed` | - | BillingEventAccountingConsumer.java, InvoiceBillingEventConsumer.java, MarketKafkaConsumer.java, WalletPayoutConsumer.java | oui | **consommé sans producteur interne** |
| `tnt.billing.wallet.split_executed` | - | BillingEventAccountingConsumer.java | oui | **consommé sans producteur interne** |
| `tnt.delivery.announcement.published` | KafkaDeliveryEventPublisher.java | MatchingUseCase.java | oui | câblé |
| `tnt.delivery.announcement.response.selected` | KafkaDeliveryEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.delivery.events` | KafkaDeliveryEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.delivery.freelancer_org.assigned` | KafkaDeliveryEventPublisher.java | BillingEventAccountingConsumer.java, FreelancerOrgKafkaEventConsumer.java | oui | câblé |
| `tnt.delivery.hub-deposit.created` | - | - | oui | déclaré, inutilisé |
| `tnt.delivery.hub-deposit.picked-up` | - | - | oui | déclaré, inutilisé |
| `tnt.delivery.mission.completed` | KafkaDeliveryEventPublisher.java | DeliveryEventConsumer.java, DeliveryEventSalesConsumer.java, InvoiceBillingEventConsumer.java | oui | câblé |
| `tnt.delivery.mission.created` | KafkaDeliveryEventPublisher.java | DeliveryStatusConsumer.java | oui | câblé |
| `tnt.delivery.mission.failed` | KafkaDeliveryEventPublisher.java | DeliveryEventSalesConsumer.java | oui | câblé |
| `tnt.delivery.mission.started` | KafkaDeliveryEventPublisher.java | DeliveryEventSalesConsumer.java | oui | câblé |
| `tnt.delivery.mission.status.changed` | EntityChangedEventConsumer.java, KafkaDeliveryEventPublisher.java | CoreMissionStatusProjectionConsumer.java, DeliveryStatusConsumer.java, EntityChangedEventConsumer.java, IncidentEventConsumer.java, MarketKafkaConsumer.java, MissionStatusEventConsumer.java | oui | câblé |
| `tnt.delivery.package.delivered` | KafkaDeliveryEventPublisher.java | DeliveryStatusConsumer.java | oui | câblé |
| `tnt.delivery.package.dispute.released` | DeliveryStatusAdapter.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.delivery.package.disputed` | DeliveryStatusAdapter.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.delivery.package.picked-up` | - | - | oui | déclaré, inutilisé |
| `tnt.delivery.package.updated` | EntityChangedEventConsumer.java, KafkaDeliveryEventPublisher.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.dispute.closed` | DisputeKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.dispute.compensation-paid` | - | - | oui | déclaré, inutilisé |
| `tnt.dispute.compensation.processed` | DisputeKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.dispute.escalated` | DisputeKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.dispute.evidence-added` | - | - | oui | déclaré, inutilisé |
| `tnt.dispute.evidence.submitted` | DisputeKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.dispute.opened` | DisputeKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.dispute.refund-initiated` | - | - | oui | déclaré, inutilisé |
| `tnt.dispute.resolved` | - | - | oui | déclaré, inutilisé |
| `tnt.dispute.ruled` | DisputeKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.dispute.status.changed` | DisputeKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.dlq` | LinkKafkaConsumerConfig.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.freelancer_org.created` | FreelancerOrgEventPublisherAdapter.java | FreelancerOrgEventConsumer.java | oui | câblé |
| `tnt.freelancer_org.kyc.upgraded` | FreelancerOrgEventPublisherAdapter.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.freelancer_org.sub_deliverer.associated` | FreelancerOrgEventPublisherAdapter.java | FreelancerOrgEventConsumer.java | oui | câblé |
| `tnt.freelancer_org.sub_deliverer.revoked` | FreelancerOrgEventPublisherAdapter.java | FreelancerOrgEventConsumer.java | oui | câblé |
| `tnt.freelancer_org.suspended` | FreelancerOrgEventPublisherAdapter.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.freelancer_org.verified` | FreelancerOrgEventPublisherAdapter.java | FreelancerOrgEventConsumer.java | oui | câblé |
| `tnt.geo.alert.created` | EntityChangedEventConsumer.java, KafkaGeoEventPublisher.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.geo.gps.position-updated` | - | - | oui | déclaré, inutilisé |
| `tnt.geo.node.events` | KafkaGeoEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.geo.traffic.events` | KafkaGeoEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.geo.zone.events` | KafkaGeoEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.incident.cancelled` | IncidentKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.incident.closed` | IncidentEventConsumer.java, IncidentKafkaEventPublisher.java | IncidentEventConsumer.java | oui | câblé |
| `tnt.incident.created` | IncidentKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.incident.driver.assigned` | IncidentKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.incident.escalated` | IncidentKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.incident.escalated.to.dispute` | IncidentKafkaEventPublisher.java | DisputeEventConsumer.java, WalletBillingEventConsumer.java | oui | câblé |
| `tnt.incident.handover.completed` | IncidentKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.incident.interagency.completed` | IncidentKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.incident.interagency.requested` | IncidentKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.incident.resolved` | IncidentEventConsumer.java, IncidentKafkaEventPublisher.java | IncidentEventConsumer.java | oui | câblé |
| `tnt.incident.status.changed` | IncidentKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.incident.triaged` | IncidentKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.inventory.hub.package.deposited` | InventoryKafkaEventPublisher.java | InventoryHubConsumer.java | oui | câblé |
| `tnt.inventory.hub.package.pickedup` | InventoryKafkaEventPublisher.java | InventoryHubConsumer.java | oui | câblé |
| `tnt.inventory.stock.low` | InventoryKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.link.tracking.position.latest` | GpsPositionKafkaConsumer.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.market.` | EntityChangedEventConsumer.java, MarketKafkaEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.market.listing.approved` | EntityChangedEventConsumer.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.market.listing.published` | EntityChangedEventConsumer.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.market.listing.rejected` | EntityChangedEventConsumer.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.market.merchant.contract.signed` | EntityChangedEventConsumer.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.market.order.completed` | EntityChangedEventConsumer.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.market.order.created` | EntityChangedEventConsumer.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.market.order.paid` | EntityChangedEventConsumer.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.market.provider.review.published` | EntityChangedEventConsumer.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.market.quote.request.created` | EntityChangedEventConsumer.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.market.quote.response.submitted` | EntityChangedEventConsumer.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.media.file.deleted` | - | - | oui | déclaré, inutilisé |
| `tnt.media.file.uploaded` | - | - | oui | déclaré, inutilisé |
| `tnt.notify.notification.delivered` | - | - | oui | déclaré, inutilisé |
| `tnt.notify.notification.failed` | - | - | oui | déclaré, inutilisé |
| `tnt.notify.notification.requested` | - | - | oui | déclaré, inutilisé |
| `tnt.organization.hub.updated` | EntityChangedEventConsumer.java, HubEventPublisherAdapter.java | EntityChangedEventConsumer.java | oui | câblé |
| `tnt.outbox.events` | - | - | oui | déclaré, inutilisé |
| `tnt.product.created` | ProductKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.product.offer.published` | ProductKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.realtime.eta.updated` | - | - | oui | déclaré, inutilisé |
| `tnt.realtime.geofence.triggered` | KafkaRealtimeEventPublisher.java (GeofenceTriggerEvent, via outbox) ¹, EntityChangedEventConsumer.java | EntityChangedEventConsumer.java, IncidentEventConsumer.java | oui | câblé |
| `tnt.realtime.gps.position.updated` | KafkaRealtimeEventPublisher.java (GpsPositionUpdatedEvent, envoi direct) ¹ | GpsPositionKafkaConsumer.java, IncidentEventConsumer.java | oui | câblé |
| `tnt.realtime.presence.changed` | - | - | oui | déclaré, inutilisé |
| `tnt.realtime.sessions.active` | SessionApplicationService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.resource.equipment.assigned` | ResourceKafkaEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.resource.freelancer.vehicle.registered` | ResourceKafkaEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.resource.maintenance.alert` | ResourceKafkaEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.resource.vehicle.assigned` | ResourceKafkaEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.resource.vehicle.location` | ResourceKafkaEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.resource.vehicle.maintenance` | ResourceKafkaEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.resource.vehicle.registered` | ResourceKafkaEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.resource.vehicle.retired` | ResourceKafkaEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.resource.vehicle.unassigned` | ResourceKafkaEventPublisher.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.roles.permission-changed` | PermissionChangeEventPublisher.java | PermissionCacheInvalidationListener.java | oui | câblé |
| `tnt.route.eta.updated` | KafkaRouteEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.route.reroute.events` | KafkaRouteEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.route.tour.events` | KafkaRouteEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.route.vrp.events` | KafkaRouteEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.sales.order.cancelled` | SalesEventPublisherAdapter.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.sales.order.confirmed` | SalesEventPublisherAdapter.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.sales.order.delivered` | SalesEventPublisherAdapter.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.sales.order.dispatched` | SalesEventPublisherAdapter.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.sync.conflict.detected` | - | - | oui | déclaré, inutilisé |
| `tnt.sync.delta.requested` | - | - | oui | déclaré, inutilisé |
| `tnt.sync.entity-changed.dlq` | SyncKafkaConfig.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.tp.client.profile.events` | KafkaTntTpEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.tp.kyc.events` | KafkaTntTpEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.tp.loyalty.events` | KafkaTntTpEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.tp.phone.alias.events` | KafkaTntTpEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.tp.rating.events` | KafkaTntTpEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.tp.unknown.events` | KafkaTntTpEventPublisher.java | - | oui | sortant (aucun consommateur interne) |
| `tnt.trust.badge.awarded` | BadgeChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.billing.policy.anchored` | BillingPolicyChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.custody.transfer.recorded` | DeliveryProofChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.dao.rule.activated` | DaoRuleChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.delivery.proof.recorded` | DeliveryProofChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.did.issued` | DIDManagerService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.did.issued.freelancer_org` | DIDManagerService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.did.revoked` | DIDManagerService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.dispute.evidence.anchored` | DisputeEvidenceChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.event.publish.error` | LogisticEventPublisherService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.event.published` | LogisticEventPublisherService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.geofence.crossing.recorded` | GeofenceChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.mission.cancelled` | MissionChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.mission.completed` | MissionChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.mission.created` | MissionChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.payment.anchored` | PaymentChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.trust.pol.recorded` | PolChainService.java | - | **non** | sortant (aucun consommateur interne) |
| `tnt.vehicle.assigned_to_mission` | ResourceKafkaEventPublisher.java | FreelancerVehicleEventConsumer.java | oui | câblé |
| `tnt.vehicle.released_from_mission` | ResourceKafkaEventPublisher.java | FreelancerVehicleEventConsumer.java | oui | câblé |
| `yow.trust.events` | KafkaTrustEventPublisherAdapter.java | - | oui | sortant (aucun consommateur interne) |
| `yow.trust.events.committed` | - | TrustCommittedEventConsumer.java | oui | **consommé sans producteur interne** |

## 8. Décisions du 2026-11-26 et mise en œuvre

Quatre décisions ont été arrêtées le 2026-11-26 :

- **R1** : le Core publie lui-même les événements wallet.
- **R2** : relance automatique de l'outbox.
- **Doublons** : tous les consommateurs deviennent idempotents.
- **Départ des groupes** : `earliest` partout, sans script de bascule.

Mise en œuvre sur la branche `wt/kafka-fiabilite`. La preuve de bout en bout est
`KafkaAsyncWiringTest`, exécuté sur un vrai broker et une vraie base : 26 cas, dont 8 nouveaux.

### 8.1 R1 — le Core publie les événements wallet

**Principe.** `SettlementService.record` (tnt-billing-wallet) est le point de passage de toute
opération wallet réussie côté Kernel :

- paiement de livraison GOFP (`DeliveryPaymentApplicationService:125`) ;
- dépôt en point relais (`RelayDepositService:341`) ;
- statut de livraison (`DeliveryStatusApplicationService:569/577`) ;
- commande market (`MarketOrderApplicationService:254`).

Après l'enregistrement, il compare l'état avant et l'état après (`publishTransitions`,
`SettlementService.java:79`). Il publie ensuite, **via l'outbox du yow-event-kernel** (et donc
avec la relance du §8.2), le ou les événements suivants :

| Transition | Topic | Contenu |
|---|---|---|
| le règlement devient `PAID` | `tnt.billing.wallet.payment.confirmed` | `paymentId` (= id du règlement), `paymentRef` (référence Kernel), `payerUserId`, `payeeUserId`, `amount`, `currency`, `method`, `paidAt`, `referenceType`/`referenceId`, `missionId` si DELIVERY |
| la commission plateforme devient `DUE` (ou son montant change) | `tnt.billing.wallet.commission.calculated` | `commissionType=PLATFORM_FEE`, `missionId`, `delivererId`/`payeeUserId`, `commission`, `commissionRate`, `amount`, `currency`, `commissionStatus` |

**Format.** Le JSON est plat, avec `eventId`, `tenantId` et `organizationId` (= tenant), comme
le lisent les consommateurs existants. La clé de partition est la référence métier. Aucun
second événement n'est émis pour un règlement déjà PAID, ni pour une commission inchangée.

**Échec de publication.** Une mise en file en échec ne fait jamais échouer le règlement :
elle est seulement journalisée (`SETTLEMENT_EVENT_NOT_PUBLISHED`). Le constructeur à un
argument reste disponible pour les tests GOFP qui instancient le service à la main. Ces tests
n'ont pas été modifiés.

**Champs volontairement absents, et tolérances ajoutées.**

- **`invoiceId` n'est pas émis.** `WalletPayoutConsumer` (agency-commission) lit `invoiceId`
  comme un id de commission agence. Un règlement de livraison n'en a pas. Côté invoice,
  `InvoiceBillingEventConsumer.onPaymentConfirmed` ignore désormais un événement sans
  `invoiceId` (log debug).
- **`orderId` n'est pas émis.** `MarketKafkaConsumer.onPaymentConfirmed:65` appellerait
  `confirmOrder` sur une commande déjà PAID. Il ignore désormais un événement sans `orderId`.
- **Commission côté wallet.** `WalletBillingEventConsumer.onCommissionCalculated` traite une
  commission `PLATFORM_FEE` par un simple log INFO. Elle déclenchait auparavant l'erreur
  `COMMISSION_CREDIT_NOT_SUPPORTED`, car le Core ne crédite rien : la commission est déjà
  retenue par le Kernel.

**Écarts sémantiques relevés (non décidés, à arbitrer).**

1. `BillingEventAccountingConsumer.onCommissionCalculated` (lignes 75-100) passe une **charge
   de commission livreur** (débit 651000 / crédit 481000). Or la commission du Core est un
   **produit de la plateforme**, payé par le bénéficiaire. Options :
   - (a) brancher sur `commissionType == PLATFORM_FEE` vers un compte de produit (706xxx) ;
   - (b) cesser de comptabiliser cet événement côté Core.

   Recommandation : (a), après validation par l'équipe comptable. En l'état, le test
   bout-en-bout prouve l'acheminement, pas la justesse comptable.
2. `BillingEventAccountingConsumer.onPaymentConfirmed:114` impute toute méthode autre
   qu'`ORANGE_MONEY` au compte 521100 « MTN MoMo ». Cela vaut aussi pour `WALLET_TRANSFER` et
   `CASH`. Recommandation : une table de correspondance par `SettlementMethod` (caisse 571
   pour CASH, compte wallet dédié pour WALLET_TRANSFER).

**Topics restés sans producteur (documenté, rien de naturel à brancher).**

- `tnt.billing.wallet.split_executed` : le Kernel ne sait pas répartir un paiement (demande
  R2 du Kernel, voir `kernel-wallet-migration.md`). Aucun service du Core n'exécute de
  partage. Son consommateur reste câblé : `BillingEventAccountingConsumer.onWalletSplitExecuted:190`.
- `tnt.billing.compensation.paid` : le Kernel n'offre pas de crédit de serveur à serveur.
  L'indemnisation d'un litige reste en erreur `COMPENSATION_NOT_SUPPORTED`
  (`WalletBillingEventConsumer:105`), si bien qu'aucun paiement d'indemnisation n'est jamais
  confirmé. Le consommateur dispute reste câblé.
- `tnt.billing.template.applied` **a en fait un producteur** : `KafkaTemplateEventPublisher`,
  appelé par `ApplyTemplateUseCase:127` via l'outbox. L'analyse initiale l'a manqué, parce
  que le topic y est la constante `TemplateAppliedEvent.TOPIC`. Le consommateur notify lisait
  `actorId`/`policyId`, alors que l'événement porte `ownerActorId`/`createdPolicyId` :
  corrigé, les deux noms sont acceptés (`FreelancerOrgKafkaEventConsumer:225-227`).

### 8.2 R2 — relance automatique de l'outbox

**Service.** `OutboxRelayService` (yow-event-kernel) est planifié toutes les
`poll-interval-ms`, sous le verrou ShedLock `yow-event-outbox-relay` (une seule instance). Il
reprend trois cas :

- les enveloppes **FAILED** dont l'échéance est atteinte ;
- les **PENDING** plus vieilles que `pending-grace` (le processus est mort entre l'insertion
  et l'envoi) ;
- les **RETRYING** dont la réservation a expiré.

**Réservation.** Chaque enveloppe est d'abord réservée par une mise à jour optimiste
(`version`). Deux instances ne publient donc jamais la même enveloppe dans le même tour, même
si le verrou expire.

**Backoff.** L'échéance est portée par une nouvelle colonne, `next_attempt_at`, calculée
côté base (`NOW()`), ce qui évite tout décalage d'horloge. Le délai vaut
`initial-delay × multiplier^(n-1)`, plafonné à `max-delay`. Le premier renvoi d'un échec
direct a lieu au cycle suivant.

**DEAD.** Au bout de `max-attempts` tentatives (envoi direct initial compris), l'enveloppe
passe `DEAD`. Trois choses se produisent alors :

- une entrée `dead_letter_entries` est créée en `WAITING` (le rejeu manuel
  `ProcessDeadLetterUseCase`/`ReplayEventUseCase` est inchangé) ;
- un log ERROR `OUTBOX_ENVELOPE_DEAD` est émis ;
- les compteurs `yow.event.outbox.dead.total{topic,eventType}` et
  `yow.event.outbox.relay.total{topic,outcome}` sont incrémentés.

**Panne du broker.** Elle ne consomme aucune tentative. Une sonde `describeCluster` de 3 s
saute le cycle, et le premier envoi en timeout arrête le cycle en cours.

**Configuration.** Les clés `yow.event.outbox.relay.*`, avec leurs valeurs par défaut,
figurent dans `application.yml` (variables `YOW_EVENT_RELAY_*`).

**Liquibase.** Le nouveau changeset `006-add-envelope-relay-schedule.sql` (yow-event-kernel)
ajoute la colonne et un index partiel. Aucun changeset publié n'a été modifié. Le gate
`LiquibaseMasterChangelogIntegrationTest` est vert (3/3).

**Garantie : au moins une fois, sans ordre strict.**

- **Doublons possibles.** Un renvoi peut dupliquer un événement déjà arrivé au broker juste
  avant un plantage. Le §8.3 absorbe ces doublons.
- **Ordre non garanti.** Un événement relancé peut arriver après un événement plus récent de
  la même clé. Les consommateurs qui dépendent de l'ordre (statuts de mission) doivent
  rester tolérants. C'était déjà le cas avec le rejeu manuel.
- **Lignes antérieures à la migration.** Les lignes PENDING/FAILED existant avant la
  migration reçoivent `next_attempt_at = NOW()` : elles seront relancées. C'est acceptable,
  puisqu'il n'y a pas de données de production.

### 8.3 Doublons — tous les consommateurs idempotents

**Table.** `event_bus.processed_events` (changeset 007, yow-event-kernel, couche L0 : la règle
de couches est respectée). Sa clé primaire est **(groupe de consommateurs, topic, id
d'événement)**.

**Le topic dans la clé (écart volontaire).** La décision visait « groupe + id d'événement ».
Le topic y est ajouté parce que plusieurs producteurs publient un même objet événement, donc
avec le même `eventId` dans le JSON, sur deux topics : un topic spécifique et un topic
agrégé. Un groupe abonné aux deux doit traiter chaque topic. Sans le topic dans la clé, le
second message, légitime, serait écarté comme doublon. Les messages de l'outbox portent un
`X-Yow-Envelope-Id` distinct par enveloppe et ne sont pas concernés.

**Id d'événement** (`KafkaEventIds`), dans l'ordre de préférence :

1. l'en-tête `X-Yow-Envelope-Id` : c'est l'en-tête que l'outbox émet réellement. Il est
   identique à chaque renvoi. L'en-tête `X-Yow-Event-Id` prévu par la décision n'est émis par
   aucun producteur, mais il est accepté ;
2. l'en-tête `X-Yow-Event-Id` ;
3. l'en-tête `eventId` ;
4. le champ `eventId` à la racine du JSON ;
5. à défaut, `SHA-256(topic|partition|offset|horodatage)`. Cet id identifie l'enregistrement
   lui-même : il couvre la redistribution après un plantage ou un rebalancement, pas une
   seconde production. L'horodatage évite qu'un topic recréé (offsets repartant à 0) entre en
   collision avec l'ancien.

Un id de plus de 128 caractères est remplacé par son SHA-256.

**Filtre commun.**

- **Mise en place.** `IdempotentRecordInterceptor` est posé par un `BeanPostProcessor` sur
  **toutes** les fabriques de conteneurs (`AbstractKafkaListenerContainerFactory`) : celles
  des modules, celles de coreBackend (dont `gofpJsonKafkaListenerContainerFactory`) et celle
  de Spring Boot. Un intercepteur déjà présent est conservé derrière le filtre. Aucun
  `@KafkaListener` n'a été modifié.
- **Marquage.** Un événement est marqué traité **après** le succès du listener. Un échec ne le
  marque pas, ce qui laisse la porte ouverte à un nouvel essai.
- **Exceptions.** Les rejeux (`X-Yow-Replay=true`) ne sont jamais filtrés. Une table
  indisponible (timeout `lookup-timeout`) laisse passer l'événement (*fail-open*, log WARN) :
  la disponibilité prime, et le doublon reste possible.
- **Purge.** Elle est planifiée (`purge-cron`, ShedLock `yow-event-processed-purge`) avec une
  rétention configurable (14 jours par défaut).
- **Réglages.** `yow.event.consumer-idempotency.*`, dont `excluded-topics` pour retirer un
  topic du filtre.
- **Métrique.** `yow.event.consumer.duplicate.total{group,topic}`.

**Limite connue : listeners en *fire-and-forget*.** La plupart des listeners sont `void` et
lancent leur traitement réactif par `.subscribe()`. Pour eux, « succès » signifie « traitement
lancé sans exception synchrone ». Un échec asynchrone ultérieur est donc marqué traité et ne
sera pas retenté par une redistribution. Ce n'est pas une régression : il ne l'était déjà pas,
faute d'acquittement lié au résultat. Recommandation : faire bloquer ces listeners sur leur
`Mono` (thread du conteneur Kafka, avec un timeout) lors d'un chantier dédié. Fichiers
concernés : la majorité des `adapter/in/kafka/*Consumer`.

**Preuves.**

- Tests unitaires : `IdempotentRecordInterceptorTest` (7 cas) et `KafkaEventIdsTest` (6 cas).
- `KafkaAsyncWiringTest` : le même enregistrement (mêmes en-têtes) est envoyé deux fois, puis
  suivi d'un événement témoin sur la même partition. Résultat : **une seule** notification
  (KYC org), **un seul** crédit de fidélité (mission terminée), **un seul** incident (SLA
  dépassé) et **une seule** écriture comptable (paiement confirmé). Le test vérifie aussi que
  toutes les fabriques portent le filtre.

### 8.4 Départ des groupes

**Configuration vérifiée.**

- `spring.kafka.consumer.auto-offset-reset: earliest` dans `application.yml` (bootstrap).
- `auto.offset.reset=earliest` dans chaque `ConsumerFactory` de module, y compris celle de
  `gofpJsonKafkaListenerContainerFactory`.
- Le test `everyGroupStartsAtEarliest` contrôle la règle sur toutes les fabriques et sur
  toutes les surcharges par listener.

**Exception conservée (contradiction notée).** `PermissionCacheInvalidationListener:49`
(tnt-roles-core) force `auto.offset.reset=latest`. Son groupe est propre à chaque instance
(`tnt-roles-cache-<uuid>`) et nouveau à chaque démarrage. Avec `earliest`, chaque démarrage
rejouerait tout l'historique des invalidations de cache, sans effet utile (le cache est vide
au démarrage). Recommandation : garder `latest` pour ce groupe éphémère. Le test l'exclut
explicitement.

**Aucun script de bascule.** Le risque R6 (renvoi unique des notifications freelancer-org
lors du changement de groupes) disparaît : l'historique rejoué par un nouveau groupe porte
les mêmes ids d'enveloppe, et le filtre du §8.3 l'écarte pendant sa rétention. Au-delà, les
environnements de dev et de staging sont remis à zéro par leur propre déploiement (topics
recréés). Voir `docs/RUN_LOCAL.md` §4.4.

### 8.5 Ce qui reste

**Décisions métier ou comptables.**

- Les écarts comptables du §8.1 (charge ou produit de commission, compte par méthode de
  paiement).
- `split_executed` et `compensation.paid` : ils attendent les capacités du Kernel (répartition
  des paiements, crédit de serveur à serveur).
- La confirmation des paiements de commissions agence (`WalletPayoutConsumer`) et des
  commandes market ne passe pas par les événements de règlement. Ces flux gardent leur
  mécanisme propre.

**Chantiers techniques.**

- La limite *fire-and-forget* du §8.3.
- R3, R4, R7, R9 et R10 inchangés.
- R8 : couvert par le §8.4.

**Statut des risques.**

- **R1** : traité pour `payment.confirmed`, `commission.calculated` et `template.applied`.
- **R2** : traité.
- **R6** : levé.
