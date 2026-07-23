# Chantier C · Audit n°3 · P5 — Inventaire des flux événementiels à migrer

**Statut (2026-07-23) : TERMINÉ — 27/27 publishers migrés (25 de l'inventaire initial
+ 2 trouvés en cours de route).** Voir §9 pour le récapitulatif final et les preuves.
Le reste de ce document (§1-§8) est l'historique de l'inventaire et du pilote, conservé
tel quel pour traçabilité.

## 1. Rappel du constat d'audit

> P5 — 100 % des flux événementiels contournent le kernel : fire-and-forget sans
> outbox, perte d'événements silencieuse. `KafkaDeliveryEventPublisher.java:64`
> (`.onErrorComplete()`) ; `KafkaAgencyEventPublisher` ("failures are swallowed") ;
> ~24 modules.

P1-P4 (schéma Liquibase, activation de l'auto-configuration, `EventPublisherService`,
`OutboxPollerService`) sont réhabilités et vérifiés (2026-07-18, ADR Option A). P5 —
faire migrer les publishers métier vers cette infrastructure — **n'a pas commencé**.

## 2. Confirmation architecturale : le contournement est total, pas partiel

Recherche de toute utilisation de l'API d'outbox (`PublishEventUseCase`,
`PublishEventBatchUseCase`, `EventPublisherService`, ou tout package
`com.yowyob.kernel.event.application.*`) en dehors de `yow-event-kernel` lui-même :
**zéro résultat.** Chaque module publie directement via son propre
`KafkaTemplate<String, String>` qualifié (ex. `@Qualifier("invoiceKafkaTemplate")`),
sans passer par l'outbox. Le contournement est donc bien structurel et complet, pas
un résidu partiel — confirmé indépendamment du texte de l'audit.

## 3. Constat annexe urgent, indépendant de la migration P5 elle-même

`YowEventKernelAutoConfiguration` est maintenant réellement importée dans
`TntCoreConfig` (`tnt-bootstrap/.../TntCoreConfig.java:46`, commentaire "Chantier C ·
Audit n°3 P1: reactivated 2026-07-18") et porte `@ComponentScan(basePackages =
"com.yowyob.kernel.event")` + `@EnableScheduling` — ce qui signifie que
**`OutboxPollerService.scheduledPoll()` tourne réellement en production
aujourd'hui**, à `@Scheduled(fixedDelayString = "${yow.event.outbox.poll-interval-ms:1000}")`.

Or son Javadoc affirme toujours :

> **Not ShedLock-guarded** (...) : this whole module is never actually wired into the
> running application (...). This bean is never instantiated today, so adding a lock
> here would exercise a code path that doesn't run.

Ce commentaire est **devenu faux** au moment même de la réactivation P1 et n'a pas été
mis à jour en conséquence : le poller tourne maintenant sans verrou distribué en
multi-instances (le même problème que Chantier D · S2 a corrigé partout ailleurs —
`TntSchedulerLockConfig` est déjà importé dans `TntCoreConfig.java:36` et déjà utilisé
ailleurs, ex. `MediaFileCleanupScheduler`). Tant que P5 n'a migré aucun publisher,
l'impact est nul en pratique (rien n'alimente encore l'outbox), mais **dès le premier
module migré, ce sera une régression S2 silencieuse** si non corrigé avant.

**Recommandation : traiter ce point isolément, avant ou en tête de la migration P5**
(petit correctif indépendant : `@SchedulerLock` sur `scheduledPoll()`, même pattern que
`MediaFileCleanupScheduler` ; mise à jour du Javadoc devenu obsolète), plutôt que de le
découvrir en production après le premier module migré.

## 4. Inventaire des publishers Kafka directs (candidats à la migration)

25 classes d'adaptateur trouvées (implémentation directe `KafkaTemplate`, aucune ne
passe par l'outbox) :

| # | Module | Fichier | Statut |
|---|---|---|---|
| 1 | billing/tnt-billing-invoice | `adapter/out/messaging/KafkaInvoiceEventPublisher.java` | ✅ migré — `b90b6884` |
| 2 | billing/tnt-billing-templates | `adapter/outbound/messaging/KafkaTemplateEventPublisher.java` | ✅ migré — `b261cbed` |
| 3 | billing/tnt-billing-wallet | `adapter/out/kafka/WalletKafkaPublisher.java` | ✅ migré — `520b304d` |
| 4 | business/tnt-accounting-core | `adapter/out/persistence/adapter/AccountingEventPublisherAdapter.java` | ✅ migré — `d4c47174` |
| 5 | business/tnt-inventory-core | `adapter/out/persistence/adapter/InventoryKafkaEventPublisher.java` | ✅ migré — `7f6bb96f` |
| 6 | business/tnt-product-core | `adapter/out/persistence/adapter/ProductKafkaEventPublisher.java` | ✅ migré — `d399c47b` |
| 7 | business/tnt-resource-core | `adapter/out/cache/ResourceKafkaEventPublisher.java` | ✅ migré — `09d23408` |
| 8 | business/tnt-sales-core | `adapter/out/persistence/adapter/SalesEventPublisherAdapter.java` | ✅ migré — `3d99c68e` |
| 9 | coreBackend/tnt-agency-back-core/tnt-agency-compliance-core | `adapter/out/messaging/ClaimsKafkaPublisher.java` | ✅ migré — `fec9e016` |
| 10 | coreBackend/tnt-agency-back-core/tnt-agency-eventing-core | `adapter/out/messaging/KafkaAgencyEventPublisher.java` | ✅ migré — `cfd814ee` |
| 11 | coreBackend/tnt-go-freelancer-point-back-core | `adapter/out/event/KafkaGofpEventPublisher.java` | ✅ migré — `94fc8f9e` |
| 12 | coreBackend/tnt-market-back-core | `adapter/out/messaging/MarketKafkaEventPublisher.java` | ✅ migré — `12eeb1bc` |
| 13 | identity/tnt-actor-core | `adapter/out/messaging/KafkaActorEventPublisher.java` | ✅ migré — `e56169b6` |
| 14 | identity/tnt-administration-core | `adapter/out/persistence/adapter/TntAdministrationEventPublisherAdapter.java` | ✅ migré — `8420ba4c` |
| 15 | identity/tnt-organization-core | `infrastructure/adapter/out/messaging/FreelancerOrgEventPublisherAdapter.java` | ✅ migré — `b055f22b` |
| 16 | identity/tnt-tp-core | `adapter/out/messaging/KafkaTntTpEventPublisher.java` | ✅ migré — `e735b6b4` |
| 17 | logistics/tnt-delivery-core | `adapter/out/messaging/KafkaDeliveryEventPublisher.java` — **`.onErrorComplete()` confirmé ligne 65** | ✅ migré — `6dd0dc12` (2026-07-20, pilote), voir §8 |
| 18 | logistics/tnt-dispute-core | `infrastructure/adapter/out/messaging/DisputeKafkaEventPublisher.java` | ✅ migré — `199fa71a` |
| 19 | logistics/tnt-geo-core | `adapter/out/messaging/KafkaGeoEventPublisher.java` | ✅ migré — `2be073ca` |
| 20 | logistics/tnt-incident-core | `adapter/kafka/IncidentKafkaEventPublisher.java` | ✅ migré — `9895ade7` |
| 21 | logistics/tnt-notify-core | `infrastructure/messaging/KafkaNotificationPublisherAdapter.java` | ✅ migré — `f4954ad9` |
| 22 | logistics/tnt-realtime-core | `adapter/out/kafka/KafkaRealtimeEventPublisher.java` | ⚠️ **partiel, volontaire** — `d3e82578` : seul `GeofenceTriggerEvent` migré (déclenche un état métier durable) ; GPS/ETA/connect-disconnect restent en direct — télémétrie éphémère haute fréquence, voir §9 |
| 23 | logistics/tnt-route-core | `adapter/out/messaging/KafkaRouteEventPublisher.java` | ⚠️ **partiel, volontaire** — `4470d38c` : 3/4 événements migrés ; `publishEtaUpdated` reste en direct (même raison télémétrie + `tenantId` jamais renseigné par l'appelant), voir §9 |
| 24 | logistics/tnt-sync-core | `adapter/out/kafka/KafkaSyncEventPublisher.java` | ✅ migré — `8d970649` |
| 25 | trust/tnt-trust-core | `adapter/out/messaging/KafkaTrustEventPublisherAdapter.java` (+ `application/service/LogisticEventPublisherService.java`) | ✅ migré — `0e6efc41` |
| 26 | logistics/tnt-dispute-core | `infrastructure/adapter/out/delivery/DeliveryStatusAdapter.java` — **hors liste initiale**, trouvé lors de la vérification finale (port séparé de `IDisputeEventPublisher`) | ✅ migré — `460a925a` |
| 27 | logistics/tnt-dispute-core | `infrastructure/adapter/out/billing/BillingCompensationAdapter.java` — **hors liste initiale**, même découverte | ✅ migré — `460a925a` |

Sur ces 25, seules 3 occurrences de `.onErrorComplete()` (avalement silencieux total)
subsistent littéralement dans tout le dépôt : `KafkaDeliveryEventPublisher.java:65`
(citée par l'audit), et `RoadNetworkProviderAdapter.java:100,153` dans
`tnt-route-core` — mais ce dernier n'est **pas** un publisher d'événements, c'est un
appel sortant vers un fournisseur de réseau routier externe, hors périmètre P5.
Les 24 autres publishers listés utilisent des variantes (`onErrorResume` loggé,
`.subscribe()` sans gestion d'erreur explicite — équivalent en pratique à un
fire-and-forget puisque rien ne journalise l'échec dans un état durable rejouable).
**Le point commun structurel — et donc le vrai périmètre P5 — n'est pas le style de
gestion d'erreur (variable d'un module à l'autre), c'est l'absence totale
d'intégration avec l'outbox : même un publisher qui logue proprement ses erreurs
aujourd'hui ne les rend pas rejouables.**

## 5. Ce que "migrer" signifie concrètement

Remplacer, dans chaque adaptateur, l'appel direct `kafkaTemplate.send(topic, key,
payload)` par un appel à `PublishEventUseCase.publish(...)` (ou
`PublishEventBatchUseCase` pour les cas qui publient plusieurs événements en une
opération) — qui écrit l'enveloppe (`DomainEventEnvelope`) + une entrée
`OutboxEntry` dans la **même transaction R2DBC** que l'opération métier, puis laisse
`OutboxPollerService` livrer réellement à Kafka en tâche de fond, avec retry/DLQ
déjà implémentés (`application/service/OutboxPollerService.java`, voir §3 pour le
point ShedLock à corriger avant tout).

Les interfaces de port existantes (`IDisputeEventPublisher`,
`IWalletEventPublisher`, `DeliveryEventPublisher`, etc. — déjà listées au §4)
n'ont pas besoin de changer de signature côté appelant : seul le corps de
l'implémentation change (délègue à `PublishEventUseCase` au lieu du
`KafkaTemplate` direct). C'est ce qui rend la migration module-par-module sûre :
chaque module reste indépendant, testable et déployable séparément.

## 6. Complexités à anticiper (pas encore résolues, à traiter au fil de la migration)

- **Formats d'enveloppe hétérogènes** : l'audit note "trois enveloppes
  incompatibles" en circulation avant P5 — chaque module aura probablement une
  petite divergence de mapping vers `DomainEventEnvelope` (tenantId, topic,
  clé de partition) à vérifier au cas par cas, pas un mapping uniforme copier-coller.
- **`TntTopics`** (`tnt-common-core`, centralisé par Chantier C · Audit n°5 P-03/P-12)
  couvre déjà les topics des modules touchés par P6/P-01/P-02 — à réutiliser tel
  quel comme `kafkaTopic` de l'enveloppe, pas de nouveau littéral à introduire.
- **Idempotence côté consommateurs** : migrer le producteur vers l'outbox ne change
  rien côté consommateurs Kafka existants (même topic, même format de payload une
  fois publié) — mais l'outbox garantit désormais *at-least-once* là où c'était
  *at-most-once* (silencieux) avant ; certains consommateurs qui supposaient
  implicitement l'absence de doublons devront être vérifiés.
- **Volume** : 25 adaptateurs, chacun avec ses tests d'intégration à adapter/ajouter
  (suivre le pattern déjà utilisé pour P6/P-02 : test Kafka embarqué `spring-kafka-test`
  prouvant réception réelle plutôt qu'un mock).

## 7. Séquencement recommandé (à valider)

Ne pas migrer les 25 modules en un seul commit. Proposition :

1. Corriger le ShedLock du poller (§3) — isolé, rapide, prérequis de sécurité.
2. Module pilote à fort enjeu et déjà bien couvert par des tests d'intégration —
   `tnt-delivery-core` (`KafkaDeliveryEventPublisher`) est un bon candidat : c'est
   le seul déjà cité nommément par l'audit et il a déjà un test d'intégration Kafka
   embarqué de P6 (`MissionStatusChangedTopicIntegrationTest`) à étendre plutôt que
   créer de zéro.
3. Valider le pilote en conditions réelles (poll interval, comportement multi-instances
   avec le ShedLock, DLQ) avant de dérouler sur les 24 autres.
4. Dérouler par domaine (billing, business, identity, logistics, coreBackend,
   trust) pour garder chaque commit revuable indépendamment.

## 8. Pilote `tnt-delivery-core` — statut (2026-07-20)

**Fait.** `KafkaDeliveryEventPublisher` délègue maintenant à `PublishEventUseCase`/
`PublishEventBatchUseCase` au lieu d'appeler `KafkaTemplate` directement ; format du
message Kafka inchangé (même enveloppe JSON `{eventType, aggregateId, tenantId,
occurredAt, payload}`), aucun changement requis côté consommateurs.
`DeliveryLifecycleService`/`DeliveryAnnouncementService` : les méthodes qui
sauvegardent puis publient sont désormais `@Transactional` (même schéma que
`tnt-dispute-core`/wallet/agency-back-core), pour que l'écriture métier et
l'enveloppe/entrée outbox valident ensemble. `deliveryKafkaProducer` (bean mort après
la bascule) supprimé du module et de son fallback `tnt-bootstrap`.

**La validation du pilote a payé** — trois bugs réels, latents depuis la
réactivation P1-P4 (2026-07-18), et jamais couverts par un test auparavant
(`find ... -iname "*OutboxPoller*"` ne remontait aucun fichier de test), ont été
trouvés et corrigés dans `OutboxPollerService` (`yow-event-kernel`), tous les trois
préexistants et indépendants de ce module :

1. **Enveloppes jamais marquées `PUBLISHED`.** `commitSuccess(entry, envelope)` était
   passé en argument Java "eager" à `.then(...)`, donc `envelope.getStatus()`/
   `getVersion()` étaient lus à l'assemblage de la chaîne réactive — avant la mutation
   par `doOnSuccess` — au lieu d'après. La clause de verrouillage optimiste
   `WHERE version = :version - 1` ne correspondait donc jamais à la ligne réelle : la
   mise à jour n'affectait silencieusement aucune ligne. Conséquence en production :
   chaque enveloppe restait `PENDING` pour toujours, et `fetchPendingBatch`
   la re-sélectionnait (et la re-publiait sur Kafka) à **chaque cycle de poll**
   (1s par défaut) — un flood de doublons dès le premier module migré. Corrigé via
   `Mono.defer(...)`.
2. **`switchIfEmpty` déclenché même en cas de succès.** `publishAndCommit`/
   `handlePublishFailure` retournent `Mono<Void>` (n'émettent jamais d'élément, même
   en cas de succès), donc `processEntry`'s `switchIfEmpty(handleMissingEnvelope(...))`
   se déclenchait systématiquement, y compris pour une enveloppe trouvée et traitée
   avec succès — un log `WARN` trompeur ("No envelope found... Marking as processed")
   et une écriture DB redondante à chaque entrée, à chaque cycle.
3. **`poll()` retournait toujours 0.** `.flatMap(this::processEntry).count()` compte
   les éléments émis par un `Flux<Void>`, toujours nul quel que soit le nombre
   d'entrées réellement traitées — la métrique/log de poll ne reflétait jamais la
   réalité.

Preuve : `OutboxPollerServiceTest` (nouveau, `yow-event-kernel`, aucun test n'existait
avant pour cette classe) verrouille la version/statut post-mutation et le
déclenchement exactement une fois du chemin "enveloppe absente". Bout en bout :
`KafkaDeliveryEventPublisherOutboxIntegrationTest` (`tnt-delivery-core`, Postgres
Testcontainer réel pour le schéma `event_bus`, broker Kafka embarqué) — `publish()`
enfile en `PENDING` (rien sur Kafka), un cycle de poll relaie sur le topic/format
exact attendu par les consommateurs, l'enveloppe passe à `PUBLISHED`. `mvn -pl
logistics/tnt-delivery-core -am clean verify -Pintegration-tests` et
`mvn -pl foundation/yow-event-kernel -am clean test` : verts.

**Pilote validé — la parallélisation sur les 24 modules restants peut démarrer.**
