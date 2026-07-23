# Phase 2 — Architecture cible (trajectoire long terme)

**Statut global : 🟡 Planifié — non commencé**
**Horizon estimé : 3–18 mois, sous condition de traction ; à ne démarrer qu'après [Phase 0](phase-0-critical.html) et [Phase 1](phase-1-hardening.html)**
**Objectif :** poser la trajectoire vers des centaines de millions d'utilisateurs sans sur-investir avant que la charge réelle ne le justifie. Principe directeur (Audit n°7) : **rester monolithe modulaire** aussi longtemps que possible — extraire seulement ce que la charge mesurée justifie, en gardant les coutures hexagonales propres pour que l'extraction reste peu coûteuse le jour venu.

> Cases à cocher : mode d'emploi en tête de la [Phase 0](phase-0-critical.html).

## Vue d'ensemble

| Chantier | Nature |
|---|---|
| H — Infrastructure & exploitation | K8s, Kafka cluster, réplicas, DR |
| I — Partitionnement & extraction de données | Tables volumineuses, pyramide DB |
| J — Extraction microservices (conditionnelle) | Premiers candidats |
| K — Hygiène i18n & référentiels | Nettoyage, cohérence |
| L — Hygiène événementielle (yow-event-kernel) | Si Option A retenue en Phase 0 |
| M — Hygiène architecture & documentation | Dette résiduelle |

---

## Chantier H — Infrastructure & exploitation

- [ ] **[Audit n°6 · S13 / Audit n°7 · item16]** Kubernetes + HPA, Redis Sentinel/Cluster, MinIO distribué ou S3 managé + CDN pour les médias — élimine les SPOF actuels (Postgres, Redis, Kafka, MinIO tous mono-instance en docker-compose)
- [ ] **[Audit n°6 · S21]** Sortir l'exécution Liquibase du démarrage de chaque instance (verrou `DATABASECHANGELOGLOCK` sérialisant les rolling deploys) — migration hors ligne dédiée
- [ ] **[Audit n°5 · P2 item9]** Observabilité Kafka : enregistrer `MicrometerProducerListener`/`MicrometerConsumerListener` dans la factory commune (Phase 1) ; ajouter kafka-exporter (lag) à docker-compose + dashboard Grafana ; alerter sur lag et taux DLQ — couvre **P-10**
- [ ] **[Audit n°5 · P2 item10]** Sécurité Kafka staging/prod : SASL/SCRAM ou mTLS + ACLs par principal (un principal par plateforme backend, cohérent avec le modèle platform-client) — couvre **P-09**
- [ ] **[Audit n°5 · P2 item11]** Groupes et partitions : group-id explicite par module (supprimer le défaut global `tiibntick-core`), concurrency = nombre de partitions, revoir les clés tenantId (suffixe entité `tenantId:aggregateId`) — couvre **P-11, P-16**
- [ ] **[Audit n°7 · item15 / Audit n°6 · S13]** Kafka en cluster (3 brokers min, RF=3 partout), consumer groups dimensionnés par partition ; envisager le compactage pour les topics d'état
- [ ] **[Audit n°7 · item16]** Multi-AZ / DR : le monolithe stateless se réplique trivialement ; l'effort porte sur PostgreSQL (failover managé) et MinIO (erasure coding/réplication de site)
- [ ] **[Audit n°6 · S22]** Généraliser la gestion de backpressure (aujourd'hui un seul `onBackpressureBuffer(256)` dans tout le dépôt) — `TntStompWebSocketHandler.java:104`
- [ ] **[Audit n°6 · S16/S19]** Invalidation par pub/sub des caches sécurité (permissions 300 s, clients plateforme 45 s) ; protection anti-stampede sur les caches Redis (expiration L2 → reconstructions simultanées) — `PermissionCache.java:30`, `RoadNetworkProviderAdapter.java:85-89`
- [ ] **[Audit n°5 · P2 item13]** Exploiter systématiquement `X-Yow-Schema-Version` (aujourd'hui seul yow-event-kernel le pose) ; documenter chaque topic (payload, clé, producteur, consommateurs) dans `docs/kafka-topics.md` généré depuis le référentiel de Phase 0

## Chantier I — Partitionnement & extraction de données

- [ ] **[Audit n°6 · S8 / Audit n°7 · item14]** Partitionnement temporel/par `tenant_id` des tables volumineuses : deliveries, transactions wallet, audit logs, outbox — aucune table partitionnée aujourd'hui (`grep 'PARTITION BY'` = 0 résultat)
- [ ] **[Audit n°6 · reco 13]** Activer la « pyramide » de base de données : sortir delivery/realtime/wallet vers des instances PostgreSQL dédiées (les 8 factories de connexion existent déjà pour ça)
- [ ] **[Audit n°7 · item14]** Archivage froid vers MinIO/parquet des données partitionnées anciennes
- [ ] **[Audit n°7 · item12]** Cache Redis sur les lectures chaudes (policies pricing, zones géo), au-delà du read replica déjà mis en place en Phase 1

## Chantier J — Extraction microservices (conditionnelle à la traction réelle)

- [ ] **[Audit n°7 · item13]** Premiers candidats d'extraction, dans cet ordre : **`tnt-billing-wallet`** (frontière financière déjà nette, schéma dédié, idempotence en place, communication événementielle) puis **`tnt-realtime-core`**/passerelle Link (profil de charge connexions-longues radicalement différent du CRUD transactionnel) — prérequis : chantiers A (RLS), C (Kafka fiable), et contrats d'événements versionnés
- [ ] **[Audit n°6 · reco 16]** Extraire les workloads CPU-bound/haut-débit divergents (VRP OR-Tools, ingestion GPS) en services dédiés — le découpage hexagonal actuel rend cette extraction peu coûteuse
- [ ] **[Audit n°6 · S23/S26]** ~~Refonte temps réel Link~~ — **déjà traité en [Phase 1, chantier G](phase-1-hardening.html#chantier-g-link-bff-temps-reel-par-tuiles-geohash-priorite-n1-de-la-phase-1)**, ne pas dupliquer ; ce chantier J ne couvre que l'extraction de la passerelle temps réel en microservice séparé une fois le modèle tuiles/BFF stabilisé et sa charge mesurée

## Chantier K — Hygiène i18n & référentiels (Audit n°4, P2/P3)

- [ ] **[Audit n°4 · P2]** Localiser les handlers d'exceptions (clés `error.*` déjà présentes dans les packs) et les messages de validation Jakarta via un adaptateur `MessageSource` branché sur le kernel — couvre **P-9** ; étendre à `coreBackend/` (94 messages en dur : agency 38, market 31, link 18, gofp 7) — couvre **P-24**
- [ ] **[Audit n°4 · P2]** Internationaliser les JRXML (facture, reçus, manifeste) via `$R{}` + bundles, transmettre la locale dans `InvoicePdfAdapter`, brancher enfin `PriceFormatterUseCase` sur invoice/media — couvre **P-10, P-18**
- [ ] **[Audit n°4 · P2]** Supprimer le code mort (`FreelancerOrgNotificationTemplates` maps jamais branchées) et les dépendances Maven mortes vers `yow-i18n-kernel` (`tnt-delivery-core`, `tnt-billing-dsl`, `tnt-go-freelancer-point-back-core`) ; localiser le sujet d'email en dur — couvre **P-3, P-11, P-17**
- [ ] **[Audit n°4 · P3]** Réconcilier les référentiels : `SupportedLanguage`/`SupportedCurrency` ↔ `SolutionContext` (qui annonce `pcm_NG, fr_SN, en_KE, GHS` inexistants dans les enums) ↔ colonnes SQL ; ajouter `messages_en_US.json` ou retirer `EN_US` ; renommer `application.yaml` embarqué de la librairie en défauts programmatiques — couvre **P-13, P-14, P-15**
- [ ] **[Audit n°4 · P3]** ADR stratégie taux de change (service dédié ou intégration Kernel) avant toute expansion multi-pays ; autoriser les montants négatifs dans `LocalizedPrice` ou documenter l'usage avoirs — couvre **P-8, P-16**
- [ ] **[Audit n°4]** Colonnes devise hétérogènes (`CHAR(3)` à `VARCHAR(10)`) à uniformiser — couvre **P-19** ; champ `TranslationService.translationPort` à rendre `final` — couvre **P-20**

## Chantier L — Hygiène événementielle (si Option A retenue pour `yow-event-kernel` en Phase 0)

Sans objet si l'Option B (retrait du module) a été retenue en Phase 0 — cocher uniquement les items pertinents selon la décision actée dans `phase-0-critical.md`, chantier C.

- [ ] **[Audit n°3 · PR7]** Corriger les javadocs mensongères (« exactly-once », fixed-delay, bulk-insert, imports file) — `DomainEventEnvelope.java:15`, `OutboxPollerService.java:78-92`
- [ ] **[Audit n°3 · P12]** Injecter `YowEventKernelProperties` dans le poller (le `batch-size` configuré est actuellement ignoré, constante 50 codée en dur) — `OutboxPollerService.java:50,110`
- [ ] **[Audit n°3 · P13]** Propager `entry.getTenantId()` dans `processEntry` au lieu de `findById(envelopeId, null)` — `OutboxPollerService.java:122`
- [ ] **[Audit n°3 · P14]** Implémenter ou retirer le contrôle de compatibilité Avro référencé par la javadoc (`AvroSchemaCompatibilityChecker` inexistant) — `SchemaRegistryService.java:23,49`
- [ ] **[Audit n°3 · P15]** Retirer le couplage inversé kernel→TiiBnTick (`@Qualifier("tntKafkaTemplate")` dans un module censé être générique) — `KafkaEventPublisher.java:60`, `RedisEventIdempotencyStore.java:33`
- [ ] **[Audit n°3 · P16]** Remplacer `LocalDateTime` par `Instant`/`OffsetDateTime` pour tous les horodatages d'un bus distribué — `DomainEventEnvelope.java:105-107`, `OutboxEntry.java:78-79`
- [ ] **[Audit n°3 · P17]** Remplacer le statut `PROCESSED` silencieux de `handleMissingEnvelope` par un statut d'erreur dédié (perte actuellement maquillée en succès) — `OutboxPollerService.java:209-214`
- [ ] **[Audit n°3 · P18]** Protéger le poller contre l'overlap de cycles (`@Scheduled` + `subscribe()` asynchrone annule la garantie fixed-delay) — `OutboxPollerService.java:85-92,105`
- [ ] **[Audit n°3 · P19]** `publishAll` : aligner l'implémentation sur la javadoc (« single bulk-insert ») ou corriger la javadoc — `EventPublisherService.java:75-100`
- [ ] **[Audit n°3 · P20]** Sortir `application.yaml` du jar bibliothèque (risque de collision de config avec l'app hôte) — `src/main/resources/application.yaml`
- [ ] **[Audit n°3 · P21]** Retirer `@EnableScheduling` global d'une autoconfiguration de bibliothèque (effet de bord sur l'app hôte) — `YowEventKernelAutoConfiguration.java:56`

## Chantier M — Hygiène architecture & documentation résiduelle

- [ ] **[Audit n°1 · A8]** Scinder les god classes : `LogisticTrustEvent.java` (772 lignes), `MissionService.java` (768), et les 2 autres classes >500 lignes
- [ ] **[Audit n°1 · A9]** Déplacer `@RequirePermission` hors de `adapter/in/web` (package incohérent avec sa consommation par la couche application de tous les modules)
- [ ] **[Audit n°1 · A14]** Purger les fuites Spring du domaine : `@ResponseStatus` sur ~12 exceptions, `@Component` sur 2 services de domaine, `MediaType` dans un modèle
- [ ] **[Audit n°1 · A10]** Consolider l'assemblage bootstrap sur un seul mécanisme (auto-configuration), scan restreint, suppression de l'import mort `MarketBackCoreConfig` (`TntCoreConfig.java:7`) et du commentaire faux (`TntCoreConfig.java:43`)
- [ ] **[Audit n°1 · A11]** Fabriques communes `ObjectMapper`/Kafka dans `tnt-common-core` au lieu de 10+ beans `ObjectMapper` et 16 classes de config Kafka quasi identiques par module
- [ ] **[Audit n°1 · A12]** Auto-configuration du `kernelWebClient` dans `tnt-common-core` au lieu du contrat de bean caché actuel (10+ modules référencent un qualifier défini uniquement dans bootstrap)
- [ ] **[Audit n°1 · A15]** Hygiène du dépôt : retirer `BOOT-INF/` (jar décompressé) de la racine, nettoyer le code commenté dans bootstrap, mettre à jour CLAUDE.md (52 modules réels, pas ~34)
- [ ] **[Audit n°2 · reco 7]** Intégrer ou geler `tnt-billing-cost` : brancher `IRouteDataPort`, unifier son `Money` dupliqué sur le type partagé (chantier F Phase 1) ; donner un contenu réel à `tnt-go-freelancer-point-back-core` (0 dépendance entrante ni sortante) ou le retirer du réacteur
- [ ] **[Audit n°2 · P9]** Documenter les 22 modules à fan-in effectif nul comme feuilles légitimes (trust, back-cores, administration) dans CLAUDE.md, à l'exception de la coquille vide `tnt-go-freelancer-point-back-core` déjà traitée ci-dessus
- [ ] **[Audit n°2 · P10]** Corriger les incohérences mineures d'ordre de build (`pom.xml:66-67`) et le commentaire trompeur « L6 Core Backend Market » (`TntCoreConfig.java:43`, déjà couvert par A10 — vérifier non-régression)

---

## Definition of Done — Phase 2

- [ ] Chantier H : cluster Kafka 3 brokers en place ; observabilité Kafka complète (lag, DLQ rate) visible en Grafana ; SASL/TLS actif en prod.
- [ ] Chantier I : au moins une table à forte volumétrie partitionnée et validée sous charge.
- [ ] Chantier J : décision d'extraction documentée (ADR) pour `tnt-billing-wallet`, exécutée seulement si la charge mesurée le justifie.
- [ ] Chantier K : 0 chaîne i18n cassée résiduelle ; référentiels de langue/devise cohérents entre eux.
- [ ] Chantier L : `yow-event-kernel` soit pleinement fiable (Option A), soit entièrement retiré du réacteur (Option B) — aucun état intermédiaire.
- [ ] Chantier M : CLAUDE.md à jour avec les 52 modules réels ; plus de god class > 500 lignes sans justification documentée.
