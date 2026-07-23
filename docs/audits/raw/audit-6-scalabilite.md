# Audit n°6 — Scalabilité — TiiBnTick Core

- **Date** : 2026-07-17
- **Auditeur** : Performance/Platform Engineer (audit outillé, preuves fichier:ligne)
- **Périmètre** : dépôt `tiibntick-core` (~34 modules Maven, ~2 956 fichiers Java), application unique déployable `tnt-bootstrap` — **y compris `coreBackend/`** (tnt-agency-back-core, tnt-go-freelancer-point-back-core, tnt-link-back-core, tnt-market-back-core), avec analyse approfondie de `tnt-link-back-core` (carte temps réel, cible : millions d'utilisateurs simultanés)
- **Objectif client** : capacité cible de plusieurs centaines de millions d'utilisateurs

---

## 1. Résumé exécutif

TiiBnTick Core est un **monolithe modulaire réactif** (Spring Boot 4 WebFlux, R2DBC/PostgreSQL+PostGIS, Kafka, Redis réactif, MinIO, OR-Tools). L'architecture interne est de bonne qualité (hexagonale, outbox pattern avec `FOR UPDATE SKIP LOCKED`, verrouillage optimiste sur les wallets, cache Redis à deux niveaux pour le routage, URLs présignées MinIO). **Le code est visiblement pensé pour un futur scale-out** — mais il n'y est pas encore prêt.

**Verdict : l'application ne peut PAS être déployée en plusieurs instances aujourd'hui sans corruption fonctionnelle**, à cause de trois défauts bloquants : (1) un générateur de références de litiges basé sur un `AtomicInteger` statique par JVM qui produit des doublons garantis en multi-instances, (2) ~10 jobs `@Scheduled` sans verrou distribué (aucun ShedLock dans le dépôt) qui s'exécuteraient en double, (3) des dépôts de données **en mémoire** utilisés en production (réconciliation financière du wallet, affectations de rôles RBAC en mode `LOCAL` par défaut).

Même en mono-instance, le plafond de charge est bas : **une seule instance PostgreSQL partagée par ~30 schémas**, pools R2DBC de 10 connexions par factory, **aucune pagination** sur les endpoints de listing, **aucun partitionnement** de tables, **aucun read replica**, et des appels HTTP vers le Kernel Yowyob **sans timeout ni circuit breaker** (le Resilience4j présent ne couvre que le module trust). L'objectif « centaines de millions d'utilisateurs » exige un chantier structurel : la base de données cassera la première, bien avant le monolithe lui-même.

**Alerte spécifique `tnt-link-back-core`** (exigence : millions d'utilisateurs simultanés sur une carte temps réel) : le module ne contient **aucun mécanisme de push** — pas de WebSocket, pas de SSE, pas de sink, pas d'adapter Kafka ni Redis (il ne dépend même pas de `tnt-realtime-core`). La « carte temps réel » ne peut fonctionner que par **polling REST** de requêtes bounding-box exécutées **sans PostGIS** (colonnes lat/lon en `DOUBLE PRECISION`, index B-tree inadapté, table des alertes sans aucun index géo). À 1 M d'utilisateurs rafraîchissant leur viewport toutes les 5 s, cela représente ~200 000 requêtes DB/s sur une instance PostgreSQL dont le pool primaire fait 10 connexions : **le design actuel est hors d'échelle par plusieurs ordres de grandeur** (§5.8).

**Problèmes recensés : 27** — dont **6 Critiques**, **10 Élevés**, **7 Moyens**, **4 Faibles**.

---

## 2. État actuel de l'architecture

### 2.1 Topologie de déploiement

- **Une seule application déployable** : `tnt-bootstrap` assemble ~34 modules-bibliothèques (CLAUDE.md, racine du dépôt).
- **Infrastructure locale** (`tnt-bootstrap/docker-compose.yml`) : 1 PostgreSQL/PostGIS (l.48), 1 Redis (l.71), 1 Kafka KRaft mono-broker (l.89), 1 MinIO (l.125), Elasticsearch, Prometheus, Grafana, Zipkin — **tous en instance unique, sans réplication ni cluster**.
- **Aucun manifeste Kubernetes, HPA, ou configuration de load balancer** dans le dépôt (seul indice : commentaire « TLS terminated at Nginx/load balancer level », `application.yml:527`).
- **Dockerfile** (`tnt-bootstrap/Dockerfile`) : bonnes pratiques (jar en couches, utilisateur non-root, `MaxRAMPercentage=75`, ZGC générationnel, healthcheck liveness) — le conteneur est correctement préparé.

### 2.2 La « pyramide » de base de données

`tnt-bootstrap/src/main/java/com/yowyob/tiibntick/bootstrap/config/TntDataSourceConfig.java` définit **8 `ConnectionFactory` R2DBC** (kernel + core + 6 plateformes) qui, en mode monolithe, **pointent toutes vers la même instance PostgreSQL** (l.36-38, l.160-167). Pool par factory : `DB_POOL_MAX_SIZE` **défaut 10** (l.64), soit un plafond théorique de 80 connexions. Le pool `spring.r2dbc` (max 30, `application.yml:26-30`) est **inactif** — commentaire explicite l.17-21 : « This starter-managed factory/pool is NOT what the app actually uses ».

### 2.3 Points structurels

- ~160 tables (`createTable`) réparties dans les changelogs Liquibase des modules ; **388 `createIndex`** au total — bonne couverture d'indexation.
- Outbox pattern transactionnel dans `foundation/yow-event-kernel` (poller 1 s, batch 50).
- Realtime : WebSocket/SSE adossés à **Redis pub/sub** pour le broadcast inter-instances.
- Kernel Yowyob consommé **exclusivement en HTTP** (migration terminée) via des `WebClient` centralisés dans `KernelBridgeConfig`.

---

## 3. Goulots d'étranglement — classés dans l'ordre où ils casseront

| # | Goulot | Seuil estimé de rupture | Preuve principale |
|---|--------|------------------------|-------------------|
| 1 | **PostgreSQL unique + pools de 10 connexions** : 30 schémas, requêtes OLTP + géo PostGIS + audit + outbox pollé chaque seconde sur la même instance | Premier à saturer : ~quelques centaines de req/s soutenues ; le pool primaire de 10 connexions fera des files d'attente `AcquirePending` bien avant | `TntDataSourceConfig.java:63-64`, `docker-compose.yml:48` |
| 2 | **Carte Link « temps réel » = polling REST sur bounding-box sans PostGIS** : chaque viewport utilisateur = 1+ requête DB ; aucune voie de push, aucun fan-out par zone | Casse dès les premiers dizaines de milliers d'utilisateurs actifs sur la carte (bien avant le million visé) : 10⁴ users × 0,2 Hz = 2 000 scans géo/s sur le goulot #1 | `NetworkNodeR2dbcRepository.java:17-20`, `003-create-network-nodes.sql:19`, §5.8 |
| 3 | **Endpoints de listing non paginés** : `Flux` illimités sur des tables à forte volumétrie (deliveries, nearby Link) | Dès ~10⁵–10⁶ lignes par tenant : latence et pression mémoire (sérialisation de dizaines de Mo par requête) | `DeliveryController.java:72-97`, `R2dbcDeliveryRepository.java:24-31` |
| 4 | **Appels Kernel sans timeout ni circuit breaker** : un Kernel lent bloque indéfiniment les requêtes en attente et épuise les connexions Netty | À n'importe quel moment — dégradation en cascade dès que le Kernel ralentit | `KernelBridgeConfig.java:57-69` |
| 5 | **Scale-out impossible** (séquence de litiges par JVM, schedulers dupliqués, état en mémoire) : la seule réponse à la charge est verticale | Bloque toute augmentation d'instances → plafond = 1 machine | `DisputeReference.java:21`, §5.1 |
| 6 | **Contention wallet sans retry** sur verrou optimiste : les crédits de commission convergent vers le wallet de l'organisation à chaque livraison | Dès quelques débits/crédits concurrents sur un même wallet « chaud » → erreurs 5xx de paiement | `WalletEntity.java:72`, `WalletService.java:80-115` |
| 7 | **OR-Tools CPU-bound** sur `boundedElastic` sans limite de concurrence + matrice de distances A* O(n²) recalculée par requête | Quelques optimisations VRP simultanées saturent tous les cœurs et affament les event loops Netty | `VrpSolverService.java:39-43` |
| 8 | **Kafka mono-broker, 3 partitions, `max.in.flight=1`** : débit producteur sérialisé par partition | Ingestion GPS/événements à grande échelle (>quelques milliers msg/s) | `application.yml:46-50, 314-315` |
| 9 | **Redis unique** : pub/sub realtime (WS/SSE), presence, caches, idempotence — tout sur un nœud | Fan-out temps réel massif ; panne = perte totale du realtime | `docker-compose.yml:71` |
| 10 | **MinIO unique sans CDN** : les médias (preuves de livraison, photos) sortent d'un seul nœud | Trafic média à l'échelle nationale | `docker-compose.yml:125`, `application.yml:263-272` |

---

## 4. Points positifs (à préserver)

| Point | Preuve |
|-------|--------|
| **Outbox pattern multi-instances sûr** : `SELECT … FOR UPDATE SKIP LOCKED` + garde `AtomicBoolean` | `R2dbcOutboxEntryRepository.java:37-47`, `OutboxPollerService.java:104-116` |
| **Trust retry queue multi-replica safe** — même technique SKIP LOCKED, documentée pour le multi-réplicas | `TrustRetryQueueRepositoryAdapter.java:108-120` |
| **Verrouillage optimiste** `@Version` sur wallets et factures | `WalletEntity.java:72`, `InvoiceEntity.java:72` |
| **Broadcast realtime distribué** : WS et SSE adossés à Redis pub/sub — les clients peuvent se connecter à n'importe quelle instance | `FluxSseEmitter.java:19-25`, `RedisBackedWebSocketBroadcaster` |
| **Cache réseau routier à 2 niveaux** L1 local 30 s / L2 Redis 5 min avec dégradation gracieuse | `RoadNetworkProviderAdapter.java:20-58` |
| **VRP exécuté hors event loop** via `subscribeOn(Schedulers.boundedElastic())` | `VrpSolverService.java:42` |
| **Presque aucun `.block()`** : 4 occurrences seulement dans le code de production (voir §5.5) ; commentaire vigilant dans `TntStartupRunner.java:82` |
| **Indexation généreuse** : 388 index pour ~160 tables ; deliveries indexées sur (tenant, sender), (tenant, dp), (tenant, status), tracking_code | `005-create-deliveries.yaml:64-86` |
| **Idempotence distribuée sur Redis** pour wallet et événements | `RedisIdempotencyStore.java`, `RedisEventIdempotencyStore.java` |
| **Resilience4j complet sur le module trust** : 2 circuit breakers + timelimiter + retry, readiness qui exclut délibérément le trust gateway | `application.yml:160-186, 116-121` |
| **URLs présignées MinIO** (TTL 3600 s) — le téléchargement de médias ne transite pas par la JVM | `application.yml:271` |
| **Dockerfile production-grade** : layered JAR, non-root, ZGC, `MaxRAMPercentage` | `tnt-bootstrap/Dockerfile` |
| **Kafka producteur idempotent, acks=all, isolation read_committed** — cohérence forte des événements | `application.yml:46-58` |

---

## 5. Problèmes détectés

### Tableau de synthèse

| ID | Problème | Criticité | Localisation |
|----|----------|-----------|--------------|
| S1 | Séquence de référence de litige en `AtomicInteger` statique par JVM → doublons garantis en multi-instances | **Critique** | `DisputeReference.java:21,44-49` |
| S2 | ~10 jobs `@Scheduled` sans verrou distribué (aucun ShedLock dans le dépôt) | **Critique** | voir §5.1 |
| S3 | Réconciliation financière du wallet stockée en `ConcurrentHashMap` (`@Repository` en mémoire) | **Critique** | `InMemoryReconciliationRepository.java:24-27` |
| S4 | Appels HTTP Kernel sans timeout, retry ni circuit breaker (auth, rôles, organisation, notifications) | **Critique** | `KernelBridgeConfig.java:57-118` |
| S5 | Affectations de rôles RBAC en mémoire (mode `LOCAL` par défaut + fallback in-memory actif) | **Critique** | `TntRolesAutoConfiguration.java:71-84`, `application.yml:260` |
| S6 | PostgreSQL unique pour ~30 schémas, pool 10/factory, ni réplicas ni sharding | **Élevé** | `TntDataSourceConfig.java:59-64` |
| S7 | Endpoints de listing sans pagination (Flux non bornés, requêtes sans LIMIT) | **Élevé** | `DeliveryController.java:72-97` |
| S8 | Aucune table partitionnée (deliveries, transactions wallet, audit, outbox) | **Élevé** | grep `PARTITION BY` = 0 résultat |
| S9 | Pas de retry sur échec de verrou optimiste wallet ; wallet d'organisation = point de contention | **Élevé** | `WalletService.java:80-115,239-291` |
| S10 | État GPS en mémoire non borné et par instance (détection d'anomalies cassée en scale-out + fuite mémoire) | **Élevé** | `GpsPingProcessor.java:71-77` |
| S11 | OR-Tools CPU-bound sans limitation de concurrence ; matrice de distances A* O(n²) par requête | **Élevé** | `VrpSolverService.java:39-43,45-66` |
| S12 | Kafka mono-broker, replication-factor 1 (défaut), 3 partitions, `max.in.flight=1` | **Élevé** | `application.yml:46-50,314-315`, `docker-compose.yml:89` |
| S13 | Redis unique : pub/sub realtime + caches + idempotence sans HA | **Élevé** | `docker-compose.yml:71` |
| S14 | `.block()` dans l'aspect de validation tenant et dans un consumer Kafka | **Moyen** | `TenantValidationAspect.java:74`, `DisputeEventConsumer.java:101` |
| S15 | État geofence en mémoire par instance | **Moyen** | `GeofenceMonitorService.java:43` |
| S16 | Caches locaux (permissions 300 s, clients plateforme 45 s) → fenêtre d'incohérence inter-instances | **Moyen** | `PermissionCache.java:30`, `application.yml:222,253` |
| S17 | 80/83 usages de `WebClient` sans timeout explicite | **Moyen** | grep §5.4 |
| S18 | Réseau routier complet chargé en mémoire JVM par tenant (L1) — empreinte mémoire non bornée multi-tenants | **Moyen** | `RoadNetworkProviderAdapter.java:61` |
| S19 | Pas de protection anti-stampede sur les caches Redis (expiration L2 → reconstructions DB simultanées) | **Moyen** | `RoadNetworkProviderAdapter.java:85-89` |
| S20 | `findAll()` non borné sur les templates de facturation | **Faible** | `ListTemplatesUseCase.java:69` |
| S21 | Liquibase exécuté au démarrage de chaque instance (sérialisé par `DATABASECHANGELOGLOCK` — ralentit les rolling deploys) | **Faible** | `application.yml:33-38` |
| S22 | Backpressure quasi absente : un seul `onBackpressureBuffer(256)` dans tout le dépôt | **Faible** | `TntStompWebSocketHandler.java:104` |
| S23 | Carte Link « temps réel » sans aucun canal de push (ni WS, ni SSE, ni Kafka, ni Redis) → modèle 100 % polling DB | **Critique** | `coreBackend/tnt-link-back-core` (module entier), §5.8 |
| S24 | Requêtes géo Link sans PostGIS : lat/lon `DOUBLE PRECISION`, index B-tree composite inadapté ; `network_alerts` sans aucun index géo | **Élevé** | `003-create-network-nodes.sql:12-19`, `002-create-network-alerts.sql:10-11` |
| S25 | Endpoints `/nearby` Link sans LIMIT ni tuilage — Flux non borné sur un viewport arbitraire | **Élevé** | `NetworkNodeController.java:119-128`, `NetworkAlertController.java:100-108` |
| S26 | `updateLocation` Link : ≥3 requêtes DB par ping (lecture nœud + zones DAO contenantes + save), aucun événement émis → aucun fan-out possible | **Moyen** | `NetworkNodeApplicationService.java:61-77` |
| S27 | Batch `by-ref/batch` en N+1 : un `flatMap` par id au lieu d'un `IN (...)` | **Faible** | `NetworkNodeController.java:109-116` |

---

### 5.1 Statelessness — l'application n'est pas scale-out ready

#### S1 — CRITIQUE : références de litiges dupliquées en multi-instances

`logistics/tnt-dispute-core/.../domain/model/DisputeReference.java:21` :

```java
private static final AtomicInteger SEQUENCE = new AtomicInteger(0);
...
public static void initSequence(int maxSequence) { SEQUENCE.set(maxSequence); }
public static DisputeReference generate() { ... SEQUENCE.incrementAndGet(); ... }
```

La séquence est **par JVM**, initialisée une fois au démarrage depuis le max persisté. Avec 2 instances démarrées au même moment, les deux partent du même max → **chaque litige créé produit la même référence sur les deux instances** (violation de contrainte unique au mieux, doublon client au pire). Le format `DSP-YYYYMM-NNNNN` est utilisé pour la communication client et les documents légaux (javadoc l.12-14).
**Impact** : bloque à lui seul le passage à N>1 instances pour le module dispute.
**Recommandation** : séquence PostgreSQL (`CREATE SEQUENCE ... ; nextval()`) ou table de compteurs avec `UPDATE ... RETURNING` — coût : 1 aller-retour DB par création de litige, négligeable.

#### S2 — CRITIQUE : jobs @Scheduled sans verrou distribué

`grep -rn shedlock` → **0 résultat dans tout le dépôt**. 15 classes portent `@Scheduled` ; deux seulement sont multi-instances safe (outbox et trust retry queue via `SKIP LOCKED`). Les autres s'exécuteraient **en double sur chaque instance** :

| Job | Localisation | Effet en double exécution |
|-----|-------------|---------------------------|
| Relance factures en retard | `OverdueInvoiceScheduler.java:26` (toutes les heures) | doubles notifications de relance, doubles pénalités éventuelles |
| SLA litiges (escalade) | `DisputeSLAScheduler.java:53,71` | doubles escalades / notifications |
| Monitoring SLA incidents | `IncidentSlaMonitorScheduler.java:36` (5 min) | doubles alertes |
| Auto-escalade incidents | `IncidentAutoEscalationScheduler.java` | doubles escalades |
| Nettoyage fichiers médias | `MediaFileCleanupScheduler.java` (cron 02:00) | course sur suppressions |
| Maintenance realtime / sync | `RealtimeMaintenanceScheduler.java`, `SyncMaintenanceScheduler.java` | travail redondant |
| Réconciliation wallet | `ReconciliationService.java` | double traitement financier (voir S3) |
| Expiration colis hub | `HubParcelExpiryJob.java` | doubles événements d'expiration |

**Recommandation** : ShedLock (support R2DBC) ou généraliser le pattern `FOR UPDATE SKIP LOCKED` déjà maîtrisé dans le dépôt (`TrustRetryQueueRepositoryAdapter.java:108-120` est le modèle à copier).

#### S3 — CRITIQUE : réconciliation financière en mémoire

`billing/tnt-billing-wallet/.../InMemoryReconciliationRepository.java:24-27` :

```java
@Repository
public class InMemoryReconciliationRepository implements IReconciliationRepository {
    private final Map<UUID, ReconciliationRecord> store = new ConcurrentHashMap<>();
```

C'est le bean actif (annoté `@Repository`, aucune alternative R2DBC trouvée). Les enregistrements de réconciliation de paiement (MoMo/Orange/Stripe, cf. `MtnMoMoProperties.java`, `StripeProperties.java`) sont **perdus à chaque redémarrage et invisibles des autres instances**. Combiné au scheduler `ReconciliationService` (S2), le multi-instances produit des réconciliations incohérentes sur des flux d'argent réel.
**Recommandation** : adapter R2DBC persistant (table `tnt_wallet.reconciliation_records`), avant toute mise en production du paiement.

#### S5 — CRITIQUE : RBAC en mémoire en mode LOCAL

`application.yml:260` : `mode: ${TNT_ROLES_PERMISSION_MODE:LOCAL}` — la résolution de permissions se fait « from local role assignments ». Or `TntRolesAutoConfiguration.java:71-84` fournit `InMemoryUserRoleAssignmentRepository` / `InMemoryRoleRepository` en fallback `@ConditionalOnMissingBean`, et aucun adapter persistant n'implémente ces ports dans le dépôt. Conséquences : les affectations de rôles **disparaissent au redémarrage** et **divergent entre instances** (un utilisateur promu sur l'instance A reste sans rôle sur l'instance B).
**Recommandation** : adapter R2DBC ou passage au mode `REMOTE`/`HYBRID` dès que l'endpoint Kernel existe ; a minima, refuser le démarrage en profil prod si le fallback in-memory est actif.

#### S10/S15 — ÉLEVÉ/MOYEN : état realtime par instance et non borné

`GpsPingProcessor.java:71-77` :

```java
private final Map<String, GPSStreamEntry> lastPositions = new ConcurrentHashMap<>();
private final Map<String, LocalDateTime> stopStartTimes = new ConcurrentHashMap<>();
```

et `GeofenceMonitorService.java:43` (`activeZoneMembership`). Deux problèmes :
1. **Multi-instances** : si les pings GPS d'un même livreur sont load-balancés entre instances, la détection d'outliers, d'arrêt prolongé (>10 min) et d'entrée/sortie de geofence devient aléatoire — chaque instance ne voit qu'une fraction des pings (faux positifs « trajectory anomaly » consommés par tnt-incident-core).
2. **Fuite mémoire** : aucune éviction — 1 entrée par livreur jamais supprimée. À 1 M de livreurs actifs cumulés : ~centaines de Mo de heap perdus.

À noter : le module a déjà `RedisPresenceRepository`/`RedisSessionRepository` — la plomberie Redis existe.
**Recommandation** : déplacer last-position/stop-start/zone-membership dans Redis (hash par livreur, TTL glissant), ou garantir l'affinité livreur→instance (sticky partitioning Kafka) et borner les maps (Caffeine + TTL).

**Point conforme** : `WebSocketSessionRegistry.java:23-24` est volontairement local (« Multi-instance broadcasting is handled by Redis pub-sub ») — ce design-là est correct.

### 5.2 Base de données

#### S6 — ÉLEVÉ : instance unique, pools sous-dimensionnés, pas de réplicas

- Les 8 factories partagent la même instance (`TntDataSourceConfig.java:160-167`, commentaire l.59-62). `DB_POOL_MAX_SIZE` défaut **10** (l.64) : la factory `@Primary` qui sert ~30 modules ne dispose que de 10 connexions. En WebFlux, 10 connexions saturées = toutes les requêtes suivantes en file d'acquisition → latence en escalier puis timeouts.
- **Aucun read replica** (aucune configuration de routage lecture/écriture), **aucun sharding**.
- Chiffrage : une instance PostgreSQL bien réglée tient ~10⁴ tx/s simples ; avec PostGIS, l'outbox pollé chaque seconde, l'audit et 30 schémas OLTP, le plafond réaliste est très inférieur. Pour des centaines de millions d'utilisateurs (même 1 % actifs/jour = quelques milliers de req/s soutenues), **c'est le premier mur**.

**Recommandation** : (1) porter `DB_POOL_MAX_SIZE` à un dimensionnement calculé (cores DB × 2-4 réparties par criticité de factory) ; (2) read replicas + routage des requêtes de lecture ; (3) préparer la sortie des modules à plus fort débit (delivery, realtime, wallet) vers leurs propres instances — la « pyramide » à 8 factories est déjà conçue pour ça, il faut l'activer.

#### S7 — ÉLEVÉ : absence de pagination

`DeliveryController.java:72-97` expose `Flux<DeliveryDetailResponse>` sans paramètre de page ; les requêtes sous-jacentes n'ont **pas de LIMIT** (`R2dbcDeliveryRepository.java:24-31` : `SELECT * FROM tnt_deliveries WHERE tenant_id = :tenantId AND status = :status ORDER BY created_at DESC`). `listByStatus` pour un gros tenant retournera l'intégralité de la table filtrée. Seuls 12 fichiers dans tout le dépôt mentionnent `Pageable`/`PageRequest`. Idem `ListTemplatesUseCase.java:69` (`findAll()`, S20).
**Impact chiffré** : 1 M de livraisons `PENDING` × ~1 Ko sérialisé ≈ 1 Go transféré par appel ; même en streaming réactif, la DB exécute le tri complet.
**Recommandation** : pagination keyset obligatoire (`WHERE created_at < :cursor ORDER BY created_at DESC LIMIT :n`) sur tous les endpoints de listing ; l'imposer par convention de revue.

#### S8 — ÉLEVÉ : aucun partitionnement

`grep "PARTITION BY"` → 0 résultat sur 200 fichiers de changelog. Tables à croissance illimitée concernées : `tnt_deliveries`, transactions wallet, `client_audit_logs` (gateway), outbox/event store (`yow-event-kernel`), pings/positions. À 10⁸ utilisateurs, `tnt_deliveries` dépasse 10⁹ lignes/an : les index ne tiennent plus en RAM, le vacuum devient un problème permanent.
**Recommandation** : partitionnement natif PostgreSQL par plage temporelle (mois) sur deliveries/transactions/audit/outbox + politique de rétention/archivage (l'outbox `PROCESSED` peut être purgé agressivement).

#### S21 — FAIBLE : Liquibase à chaque démarrage

`application.yml:33-38` : chaque instance exécute Liquibase au boot. Le `DATABASECHANGELOGLOCK` standard sérialise correctement (pas de corruption), mais un rolling deploy de N instances sérialise N validations de changelog et un crash pendant la migration laisse le lock posé.
**Recommandation** : exécuter les migrations dans un job séparé (init-container/CI) et `spring.liquibase.enabled=false` sur les instances applicatives en prod.

### 5.3 Cache

- **Usage Redis sain et distribué** là où c'est critique : presence, zones geofence, sessions, broadcast (realtime), réseau routier L2, idempotence wallet/événements, localisation véhicules (`VehicleLocationRedisAdapter.java`).
- **S16 — MOYEN** : deux caches purement locaux — `PermissionCache` (Caffeine, TTL 300 s, `PermissionCache.java:30`, `application.yml:253`) et le cache clients plateforme (TTL 45 s, `application.yml:222`). En multi-instances : une révocation de permission/API-key reste effective jusqu'à 5 min/45 s selon l'instance frappée. Acceptable si documenté (le TTL-only est un choix explicité l.221), mais à 100 instances la fenêtre d'incohérence devient un vrai sujet de sécurité.
- **S18 — MOYEN** : le L1 du réseau routier garde le graphe complet par tenant en heap (`RoadNetworkProviderAdapter.java:61`) sans plafond de nombre d'entrées (TTL 30 s seulement). Un réseau urbain dense = dizaines de Mo ; multiplié par les tenants actifs simultanément → pression GC.
- **S19 — MOYEN** : pas de protection anti-stampede : à l'expiration du L2 Redis (5 min), toutes les instances qui reçoivent une requête VRP reconstruisent le graphe depuis la DB en parallèle (requête lourde full-scan des nœuds/arcs).
**Recommandation** : verrou de reconstruction (`SETNX` court) ou jitter sur TTL + `Caffeine.maximumWeight` pour L1.

### 5.4 Verrous, contention et résilience HTTP

#### S9 — ÉLEVÉ : wallet — verrou optimiste sans retry, wallet chaud

Le domaine est correct (`WalletEntity.java:72` `@Version` ; idempotency-key Redis). Mais `WalletService` (l.80-115) fait `walletRepository.save(wallet)` sans **aucun** `retryWhen` (grep `retry|OptimisticLock` dans le service → 0 résultat). Une collision de version remonte en erreur brute vers l'appelant. Or `splitPayment`/`creditCommission` (l.239-291) créditent **le wallet de l'organisation à chaque livraison terminée** : pour une grosse agence (10³ livraisons/h), c'est un point de contention garanti — les échecs optimistes deviendront fréquents précisément aux heures de pointe.
**Recommandation** : (1) `retryWhen(Retry.backoff(3, …).filter(OptimisticLockingFailureException.class))` autour des opérations wallet ; (2) à terme, modèle ledger append-only (le solde = somme des transactions, matérialisé périodiquement) qui supprime la contention d'UPDATE sur une ligne unique.

#### S4/S17 — CRITIQUE/MOYEN : WebClients sans timeout ni circuit breaker

`KernelBridgeConfig.java:57-69` construit `kernelWebClient` (et `kernelOrganizationWebClient`, `kernelTpWebClient`) avec baseUrl + headers + logging — **aucun `responseTimeout`, aucun `ReadTimeoutHandler`, aucun retry, aucun circuit breaker**. Ces clients portent : provisioning RBAC, résolution d'acteurs, organisation, tiers, notifications (mode `tnt.notify.kernel.enabled=true` par défaut, `application.yml:282`), onboarding.
Dans tout le dépôt, seuls **3 endroits** configurent des timeouts HTTP : `WebClientGeoConfig.java:42-61`, `TntTrustAutoConfiguration.java:97`, `TrustClientConfig.java:40-43` — sur **83 fichiers** utilisant `WebClient`. Le Resilience4j de `application.yml:160-186` ne couvre que le trust gateway.
**Scénario de panne** : le Kernel ralentit (GC, réseau) → chaque requête TiiBnTick qui l'appelle reste suspendue indéfiniment → les requêtes s'accumulent, la mémoire des buffers Netty gonfle, les health checks passent encore (readiness ne teste que r2dbc/redis/kafka, l.121) → l'instance devient un zombie qui accepte du trafic sans le servir.
**Recommandation** : timeout global sur le `WebClient.Builder` partagé (connect 2 s / response 5 s), retry idempotent limité, circuit breaker Resilience4j par famille d'appels Kernel (le pattern existe déjà pour trust — le généraliser), et fallback dégradé pour la résolution d'acteur (contexte minimal plutôt qu'échec).

#### S14 — MOYEN : `.block()` résiduels

- `TenantValidationAspect.java:74` : la branche « méthodes non réactives » fait `.block()` dans un aspect AOP. Si une méthode `@TenantScoped` non réactive est atteinte depuis une chaîne WebFlux, c'est un blocage d'event loop (et `Mono.block()` y lève une exception en runtime). Commentaire admis : « not ideal in reactive context, but supported for tests/batch ».
- `DisputeEventConsumer.java:101` : `.block()` dans un consumer Kafka — s'exécute sur le thread listener (pas l'event loop), tolérable mais sérialise le débit de consommation du topic.
- `TntAdministrationApplicationService.java:305` : `mono.block()` dans un service applicatif.
**Recommandation** : supprimer la branche bloquante de l'aspect (lever `UnsupportedOperationException` hors profil test) ; convertir le consumer en reactor-kafka ou `CompletableFuture`.

### 5.5 Réactif et CPU-bound — OR-Tools

#### S11 — ÉLEVÉ

`VrpSolverService.java:39-43` isole bien le solveur : `Mono.fromCallable(() -> solveBlocking(...)).subscribeOn(Schedulers.boundedElastic())`. Deux limites :
1. `boundedElastic` est calibré pour du **blocking I/O** (plafond par défaut 10 × cœurs threads). 10 requêtes VRP simultanées sur une machine 4 cœurs = 10 threads CPU-bound qui se disputent 4 cœurs **en concurrence avec les event loops Netty** → latence de toute l'application pendant les calculs.
2. `buildDistanceMatrix` (l.60 et suivantes) exécute un pathfinding A* pour chaque paire de nœuds — O(n²) recherches sur le graphe routier complet, **recalculé à chaque requête** (pas de cache de matrice). Pour 30 livraisons ≈ 61 nœuds ≈ 3 700 A*.
**Recommandation** : scheduler dédié `Schedulers.newParallel("vrp", cores-1)` ou sémaphore réactif (`Mono.fromCallable(...).transformDeferred(BulkheadOperator.of(...))` — Resilience4j bulkhead) limitant à N solveurs simultanés ; cache des matrices de distances par (tenant, ensemble de nœuds arrondi) ; à terme, extraire le solveur dans un worker séparé consommant une file Kafka.

### 5.6 Files, asynchrone, backpressure

- **S12 — ÉLEVÉ** : `max.in.flight.requests.per.connection: 1` (`application.yml:50`) + `acks=all` sérialise l'envoi producteur (≈ quelques centaines de msg/s par partition selon la latence broker). 3 partitions par défaut, 6 en prod (l.314, 555) ; replication-factor **1** par défaut (l.315) = perte de données à la moindre panne broker. Kafka lui-même est mono-broker en compose (`docker-compose.yml:89`).
  **Recommandation** : idempotence Kafka autorise `max.in.flight=5` sans réordonnancement — le passer à 5 ; partitionnement par clé métier (tenantId/delivererId) avec 24-48 partitions sur les topics chauds (GPS, events) ; cluster 3 brokers, RF 3 en prod.
- **S22 — FAIBLE** : un seul opérateur de backpressure explicite dans le dépôt (`TntStompWebSocketHandler.java:104`, buffer 256). Les flux SSE Redis (`FluxSseEmitter.java:49-60`) n'ont ni buffer borné ni stratégie de drop : un client SSE lent accumule dans la mémoire du listener container. Redis pub/sub n'a par ailleurs **aucune persistance** : toute déconnexion = messages perdus (acceptable pour de la position temps réel, à documenter).
- **Positif** : la chaîne événementielle métier passe par l'outbox transactionnel — le travail lourd est déjà asynchrone by design.

### 5.7 coreBackend — tnt-link-back-core : la carte « temps réel » est un polling de base de données

Le module `coreBackend/tnt-link-back-core` (carte réseau TiiBnTick Link : nœuds, alertes, balises, suivi de colis) est celui dont l'exigence produit est la plus extrême — « millions d'utilisateurs simultanés sur une carte temps réel ». C'est aussi, en l'état, **le module le moins prêt pour cette échelle**.

#### S23 — CRITIQUE : aucun canal de push

Inventaire exhaustif du module (114 fichiers Java) : **zéro** WebSocket, SSE, `Sinks`, adapter Kafka ou Redis. La configuration du module se limite à `LinkBackR2dbcConfig.java` (R2DBC uniquement), et son `pom.xml` ne référence ni `tnt-realtime-core` ni `tnt-notify-core` (dépendances : common, auth, roles, actor, organization, geo, route, delivery, incident — `coreBackend/tnt-link-back-core/pom.xml:37-75`). Toute l'API est du REST requête/réponse :

- `GET /nearby` (nœuds) — `NetworkNodeController.java:119-128` ;
- `GET /nearby` (alertes, rayon 5 km) — `NetworkAlertController.java:100-108` ;
- `GET /track/{trackingCode}` — `ParcelTrackingController.java:34-35`, qui délègue à `deliveryQueryUseCase.findByTrackingCode` (`TrackParcelApplicationService.java:25-27`).

Conséquence : la seule façon pour un client d'avoir une carte « vivante » est de **repoller ces endpoints en boucle**. Chiffrage : 1 M d'utilisateurs simultanés × 1 rafraîchissement/5 s = **200 000 requêtes/s**, chacune traversant l'auth JWT, l'event loop et **le pool PostgreSQL primaire de 10 connexions** (goulot #1). Même à 10 000 utilisateurs (0,1 % de la cible), c'est déjà 2 000 scans géo/s — hors de portée de l'instance unique actuelle. L'ironie architecturale : `tnt-realtime-core` contient déjà exactement l'infrastructure nécessaire (SSE/WS adossés à Redis pub/sub, `FluxSseEmitter.java:19-25`) — Link ne s'y raccorde pas.

**Recommandation** : (1) brancher Link sur `tnt-realtime-core` : topics de broadcast par tuile géographique (geohash de précision 5-6), le client s'abonne en SSE/WS aux tuiles de son viewport ; (2) publier les mises à jour de nœuds/alertes en événements (Kafka → Redis pub/sub) au lieu de laisser les clients repoller ; (3) pour l'état initial du viewport, servir des snapshots par tuile pré-agrégés en cache Redis (TTL quelques secondes) plutôt que la DB.

#### S24 — ÉLEVÉ : géo-requêtes sans PostGIS sur une plateforme qui embarque PostGIS

`003-create-network-nodes.sql:12-13` stocke `latitude`/`longitude` en `DOUBLE PRECISION` ; l'« index géo » est un B-tree composite (l.19) :

```sql
CREATE INDEX ... idx_network_nodes_bbox ON tnt_link.network_nodes (tenant_id, latitude, longitude);
```

Un B-tree composite ne sert efficacement qu'un préfixe : le planner fait un range-scan sur `(tenant_id, latitude)` puis **filtre `longitude` ligne à ligne**. Pour une bande de latitude à l'échelle d'un pays, chaque `/nearby` (`NetworkNodeR2dbcRepository.java:17-20`) parcourt tous les nœuds de la bande. Pire : `network_alerts` n'a **aucun index** sur `(tenant_id, status, latitude, longitude)` (`002-create-network-alerts.sql` — aucun `CREATE INDEX` dans le fichier) → la requête `findByTenantIdAndStatusWithinBoundingBox` (`NetworkAlertR2dbcRepository.java:17-21`) fait un **scan séquentiel complet à chaque appel**, alors que c'est l'endpoint destiné à être pollé par tous les clients de la carte. Le tout sur une image `postgis/postgis:17-3.5` (`docker-compose.yml:49`) dont les capacités (type `geometry`, index GIST, `ST_DWithin`) sont déjà utilisées par `tnt-geo-core` mais ignorées ici.
**Recommandation** : colonne `geometry(Point,4326)` + index GIST + `ST_Within`/`ST_DWithin`, ou a minima ajout d'une colonne geohash indexée pour le tuilage. Coût : une migration Liquibase, gain : ×100-1000 sur les scans géo.

#### S25 — ÉLEVÉ : `/nearby` non borné

Aucun `LIMIT`, aucun plafond de surface de bounding-box, aucune pagination : un client qui demande la carte du pays entier (`minLat=-90&maxLat=90...`) reçoit **tous les nœuds du tenant** en un seul `Flux`. À des millions de nœuds enregistrés, c'est un déni de service involontaire à un seul appel.
**Recommandation** : plafonner la surface du viewport, `LIMIT` serveur (p. ex. 500 nœuds, clustering côté serveur au-delà), tuilage.

#### S26 — MOYEN : coût par ping de localisation et absence d'événement

`NetworkNodeApplicationService.java:61-77` (`updateLocation`) : chaque ping de position d'un nœud fait ≥3 allers-retours DB — lecture du nœud, `findContaining` sur les zones DAO (test point-in-polygon), sauvegarde avec `@Version` (contention optimiste si plusieurs pings du même nœud arrivent en parallèle, `003-create-network-nodes.sql:16`). Et **aucun événement n'est publié** : les autres utilisateurs ne peuvent voir le mouvement qu'en repollant (cercle vicieux avec S23).
**Recommandation** : écrire la position chaude dans Redis (comme `VehicleLocationRedisAdapter` le fait déjà côté resource-core), persister en DB par échantillonnage, publier un événement par tuile.

#### S27 — FAIBLE : N+1 sur le batch

`NetworkNodeController.java:109-116` : `by-ref/batch` exécute un `flatMap(refId -> findByRefId(...))` — N requêtes pour N ids au lieu d'un `WHERE ref_id IN (...)`. Contre-productif pour un endpoint créé précisément pour « one round trip ».

**Note positive Link** : le leaderboard est correctement borné (`findTopRanked ... LIMIT :limit`, `NetworkNodeR2dbcRepository.java:22-24`) et indexé (`005-create-leaderboard-index.sql`), les entités portent `version BIGINT` (verrou optimiste), et les autres backends `coreBackend/` (agency, go-freelancer-point, market) consomment Kafka via les groupes dédiés (`application.yml:330-334`) — le pattern événementiel existe dans la couche, Link est l'exception.

### 5.8 Déploiement

- **S13 — ÉLEVÉ / SPOF généralisés** : en l'état des artefacts fournis (docker-compose uniquement), chaque brique est un SPOF : PostgreSQL (l.48), Redis (l.71), Kafka (l.89), MinIO (l.125), et l'application elle-même (1 conteneur, l.216, profil `app`). Aucun manifeste K8s/HPA, aucun `deploy.replicas`.
- **Positif** : Dockerfile prêt pour l'orchestration (healthcheck liveness l.57-59, `MaxRAMPercentage=75` l.66, graceful shutdown `application.yml:96`).
- **Médias** : URLs présignées = bon offload, mais pas de CDN devant MinIO ni de réplication MinIO (mode single node).

---

## 6. Recommandations priorisées

### P0 — Bloquant avant tout scale-out (semaines 1-3)
1. **S1** : remplacer `DisputeReference.SEQUENCE` par une séquence PostgreSQL.
2. **S2** : ShedLock R2DBC (ou pattern SKIP LOCKED maison) sur les ~10 schedulers non protégés.
3. **S3** : persister la réconciliation wallet en R2DBC — aucune donnée financière en `ConcurrentHashMap`.
4. **S5** : adapter persistant pour les rôles/affectations RBAC ; fail-fast au boot prod si le fallback in-memory est actif.
5. **S4** : timeouts (connect 2 s / response 5 s) sur tous les WebClients Kernel + circuit breakers Resilience4j (copier le pattern trust).

### P1 — Avant montée en charge significative (mois 1-2)
6. **S7/S20/S25** : pagination keyset sur tous les endpoints de listing ; plafonner la surface et le nombre de résultats des `/nearby` Link.
6bis. **S24** : migrer les colonnes géo de Link vers `geometry` + index GIST (et indexer `network_alerts`) — migration Liquibase à faible risque, gain immédiat ×100+.
7. **S9** : retry avec backoff sur les échecs optimistes wallet ; planifier le modèle ledger.
8. **S6** : dimensionner `DB_POOL_MAX_SIZE` ; mettre en place 1-2 read replicas et router les lectures.
9. **S10/S15** : état GPS/geofence dans Redis (TTL glissant) ou sticky partitioning + éviction locale.
10. **S11** : bulkhead sur le solveur VRP + cache des matrices de distances.
11. **S12** : `max.in.flight=5`, plus de partitions sur les topics chauds, cluster Kafka RF 3.

### P2 — Trajectoire « centaines de millions » (mois 3-6)
12. **S8** : partitionnement temporel deliveries/transactions/audit/outbox + rétention.
13. Activer la « pyramide » DB : sortir delivery/realtime/wallet vers des instances PostgreSQL dédiées (les 8 factories existent déjà pour ça).
14. **S13** : Kubernetes + HPA, Redis Sentinel/Cluster, MinIO distribué ou S3 managé + CDN, migrations Liquibase hors instances (S21).
15. **S16/S19** : invalidation par pub/sub des caches sécurité ; anti-stampede sur les caches Redis.
16. Extraire les workloads divergents (VRP CPU-bound, ingestion GPS haut débit) en services dédiés — le découpage hexagonal par module rend cette extraction peu coûteuse.
17. **S23/S26** : refonte du modèle temps réel de Link — abonnement SSE/WS par tuile geohash via `tnt-realtime-core`, positions chaudes dans Redis, événements de mise à jour par tuile, snapshots de viewport en cache. C'est le prérequis absolu de l'objectif « millions d'utilisateurs simultanés sur la carte » ; le modèle polling actuel ne franchira jamais 10⁴ utilisateurs.

---

## 7. Conclusion

Le socle est sérieux : réactif de bout en bout presque sans `.block()`, outbox multi-instances exemplaire, realtime déjà pensé pour le broadcast distribué, indexation soignée, Dockerfile production-grade. Les auteurs connaissent les patterns de scalabilité — la preuve : ils les ont appliqués aux deux endroits les plus difficiles (outbox, trust retry queue).

Mais l'application est aujourd'hui **verrouillée en mono-instance** par cinq défauts critiques (séquence de litiges par JVM, schedulers dupliqués, réconciliation et RBAC en mémoire, appels Kernel sans timeout), et son plafond mono-instance est lui-même bas (PostgreSQL unique, pools de 10, pas de pagination). S'y ajoute un sixième défaut critique de conception : **la carte « temps réel » de TiiBnTick Link n'a aucun canal de push** — elle repose entièrement sur du polling REST de requêtes géographiques non indexées, un modèle qui plafonne trois à quatre ordres de grandeur sous l'objectif « millions d'utilisateurs simultanés », alors même que l'infrastructure nécessaire (SSE/WS sur Redis pub/sub) existe déjà dans `tnt-realtime-core`.

Le chemin vers « des centaines de millions d'utilisateurs » est clair et incrémental : corriger les P0 (quelques semaines) débloque le scale-out horizontal de l'application ; les P1 (pagination, PostGIS/GIST pour Link, réplicas de lecture, retry wallet) repoussent le mur de la base de données ; les P2 (partitionnement, pyramide DB effective, refonte push de la carte Link, extraction des workloads VRP/GPS) constituent la trajectoire réaliste vers l'échelle cible. Sans les P0, ajouter une deuxième instance **corromprait des données** (références dupliquées, doubles traitements financiers) — c'est la conclusion la plus importante de cet audit.
