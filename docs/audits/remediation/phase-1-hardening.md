# Phase 1 — Durcissement

**Statut global : 🟡 Planifié — non commencé**
**Horizon estimé : 1–3 mois, après la [Phase 0](phase-0-critical.html)**
**Objectif :** rendre le monolithe modulaire résilient, observable et gouvernable avant toute montée en charge significative — et livrer le premier vrai chantier produit : la carte Link en temps réel.

> Cases à cocher : voir le mode d'emploi en tête de la [Phase 0](phase-0-critical.html) — lecture seule dans le navigateur, source de vérité = ce fichier Markdown versionné, coché dans l'IDE puis committé.

## Vue d'ensemble

| Chantier | Effort |
|---|---|
| G — Link : BFF + temps réel par tuiles geohash | 3–4 sem. |
| A — Sécurité approfondie & résilience | 3–4 sem. |
| C — Kafka : fiabilité (DLQ, outbox, idempotence, ordre) | 2–3 sem. |
| D — Scalabilité (pagination, DB, cache, résilience HTTP) | 3–4 sem. |
| E — Architecture & gouvernance | 2 sem. |
| F — i18n & multidevise | 1–2 sem. |

---

## Chantier G — Link : BFF + temps réel par tuiles geohash (priorité n°1 de la Phase 1)

Placé en tête de Phase 1 conformément à la justification de l'[Audit n°7 (§ P1 bis)](../audit-7-system-design.html#p1-bis-architecture-temps-reel-link-carte-live-par-utilisateur-decision-bff) : la carte Link est destinée à des millions d'utilisateurs simultanés, chacun avec sa propre vue — c'est le chantier produit le plus structurant de tout le plan.

- [ ] **Décision BFF actée** : construire le Link BFF (frontend web + app mobile → BFF → tiibntick-core), rôle limité à l'agrégation d'écran, la terminaison temps réel, et la gestion d'abonnements viewport — **aucune logique métier dans le BFF** — **[Audit n°7 · P1 bis]**
- [ ] **[Audit n°6 · S24 / Audit n°7 · #24]** Migrer les colonnes géo de Link vers `geometry` + index **GIST** (`ST_DWithin`/`ST_Contains`), indexer `network_alerts` — prérequis absolu avant tout le reste — **Élevé** — `003-create-network-nodes.sql:12-19`, `002-create-network-alerts.sql:10-11`, `NetworkAlertR2dbcRepository.java:17-20`, `DaoZonePersistenceAdapter.java:45`
- [ ] **[Audit n°6 · S23 / Audit n°7 · #25]** Construire le canal de push : abonnement par tuiles geohash (précision 5–6 ≈ 1–5 km), fan-out borné en réutilisant le moteur **`tnt-realtime-core`** existant (STOMP + Redis pub/sub multi-instances) — ne pas réécrire un second moteur — **Critique** — `coreBackend/tnt-link-back-core` (module entier)
- [ ] Abonnement viewport : le client déclare au BFF les tuiles couvrant son viewport à l'ouverture/déplacement de la carte ; la connexion WebSocket n'est abonnée qu'aux canaux de ces tuiles — **[Audit n°7 · P1 bis]**
- [ ] **[Audit n°6 · S25]** Remplacer le plafonnement temporaire de `/nearby` (mitigation Phase 0) par une solution définitive : tuilage complet, plus de scan viewport arbitraire — **Élevé** — `NetworkNodeController.java:119-128`, `NetworkAlertController.java:100-108`
- [ ] **[Audit n°6 · S26]** `updateLocation` : réduire les ≥3 requêtes DB par ping (lecture nœud + zones DAO contenantes + save) et **émettre un événement** à chaque mise à jour pour rendre le fan-out possible — **Moyen** — `NetworkNodeApplicationService.java:61-77`
- [ ] **[Audit n°5 · P-17]** Brancher Link sur Kafka : consommer `tnt.realtime.gps.position.updated` (clé corrigée en Phase 0/chantier C), matérialiser la dernière position en Redis, prévoir un topic compacté `tnt.tracking.position.latest` — remplace la lecture synchrone actuelle de delivery-core à chaque requête — **Moyen** — `TrackParcelApplicationService.java:22-26`
- [ ] **[Audit n°1 · A16]** Durcir le reste de `tnt-link-back-core` : requête bbox devient PostGIS + LIMIT + validation viewport, cache Redis court sur les lectures chaudes — **Élevé** — `NetworkNodeR2dbcRepository.java:17-20`, `NetworkNodeController.java:109-128`
- [ ] **[Audit n°1 · A17]** Nettoyer les 5 dépendances Maven mortes de `tnt-link-back-core` (`tnt-realtime-core`, `tnt-incident-core`, `tnt-sync-core`, `tnt-route-core`, `tnt-organization-core`) — soit les brancher réellement (le cas de `tnt-realtime-core` est traité par ce chantier), soit les retirer du pom — **Faible** — `coreBackend/tnt-link-back-core/pom.xml`
- [ ] **[Audit n°6 · S27]** Corriger le N+1 sur `by-ref/batch` (un `flatMap` par id au lieu d'un `IN (...)`) — **Faible** — `NetworkNodeController.java:109-116`
- [ ] Debounce des positions GPS côté producteur (ex. 1 màj/2–5 s par livreur) + delta-encoding (n'émettre que les entités entrées/sorties/déplacées de la tuile) — **[Audit n°7 · P1 bis]**, budget mémoire/connexion maîtrisé (backpressure Reactor déjà native WebFlux)

**Vérification :** test de charge simulant N clients abonnés à des tuiles différentes + M livreurs en mouvement ; vérifier qu'un client ne reçoit que les événements de ses tuiles ; requête `/nearby` sous 50 ms avec l'index GIST sur un jeu de données représentatif.

## Chantier A — Sécurité approfondie & résilience

- [ ] **[Audit n°7 · P1 item6]** Isolation tenant en profondeur : **PostgreSQL Row-Level Security** par schéma (policy `tenant_id = current_setting(...)`) — couvre structurellement les 139 `findById` sans filtre — meilleur rapport garantie/effort — **Élevé**, effort 2–4 sem.
- [ ] **[Audit n°7 · P1 item7 / #11]** Frontal API gateway léger (nginx/Traefik ou Spring Cloud Gateway) : TLS, rate limiting (par IP + par client-id, compteurs Redis), headers de sécurité (HSTS, X-Content-Type-Options) — **Élevé**, aucun rate limiting actuellement (grep global = 0 résultat)
- [ ] **[Audit n°7 · P1 item8 / #12]** Résilience Kernel généralisée : WebClient partagé avec timeouts, circuit breaker et bulkhead Resilience4j pour tous les proxys (HRM ×160, auth, SSO) — aujourd'hui seul trust en bénéficie — **Élevé** — `business/tnt-hrm-core/.../Hrm*Controller.java`, `application.yml:160-186`
- [ ] **[Audit n°7 · #22]** Redimensionner les 8 pools R2DBC (dimensionnement manuel aujourd'hui) en cohérence avec la résilience Kernel ci-dessus et le chantier D — **Faible**, cross-réf. `TntDataSourceConfig.java:59`
- [ ] **[Audit n°7 · P1 item9 / #14]** Réparer le tracing : aligner `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin` (ou tout-Brave), vérifier la propagation de contexte réactif, tester une trace HTTP→Kafka→consumer — **Moyen** — `tnt-bootstrap/pom.xml:363-367`
- [ ] **[Audit n°7 · P1 item10 / #17]** Migrer le versioning API incohérent (`/billing/*`, `/tnt/trust`, `/api/employees` hors `/api/v1`) sous `/api/v1` avec redirections temporaires — **Moyen**
- [ ] **[Audit n°7 · P1 item10 / #18]** Généraliser un filtre `Idempotency-Key` (Redis SETNX, réponse rejouée) sur les POST financiers, hors wallet où il existe déjà — **Moyen**
- [ ] **[Audit n°7 · P1 item11 / #13]** Formaliser en chorégraphie le flux delivery→invoice→wallet : table `fulfillment_progress` alimentée par les événements + compensations explicites (annulation facture, remboursement wallet) + alerte sur saga bloquée — pas d'orchestrateur lourd nécessaire — **Élevé**, aucune saga/compensation aujourd'hui (grep "saga" = 0)

**Vérification :** test RLS — requête directe SQL avec un `current_setting` différent du tenant propriétaire retourne 0 ligne. Test de coupure réseau Kernel simulée → 503 propre au lieu d'un timeout en cascade.

## Chantier C — Kafka : fiabilité (DLQ, outbox, idempotence, ordre)

- [ ] **[Audit n°5 · P1 item5]** Généraliser le pattern `sync-core` : une auto-configuration Kafka commune (factory producteur + factory listener avec `ErrorHandlingDeserializer`, `DefaultErrorHandler(DeadLetterPublishingRecoverer → tnt.dlq.<module>, ExponentialBackOff)`) au lieu de 15 copies divergentes — couvre **P-04** (une seule DLQ aujourd'hui) — **Élevé**
- [ ] **[Audit n°5 · P1 item6 / Audit n°3 · PR4]** Outbox réel : table outbox R2DBC écrite dans la même transaction que l'agrégat + relayeur vers Kafka ; supprimer `onErrorComplete()`/fire-and-forget des publishers (`KafkaDeliveryEventPublisher.java:66`, `IncidentKafkaEventPublisher.java:137`, `KafkaInvoiceEventPublisher.java:46`, `MarketKafkaEventPublisher.java:41`, `KafkaGofpEventPublisher.java:32-59`) — couvre **P-05** — **Élevé**
- [ ] **[Audit n°5 · P1 item7 / Audit n°3 · PR6]** Idempotence consommateur : table `processed_events(event_id, consumer_group)` ou clé Redis SETNX, alimentée par le header `X-Yow-Envelope-Id` — prioritaire sur wallet/accounting/dispute (17 modules concernés) — couvre **P-06** — **Élevé**
- [ ] **[Audit n°5 · P1 item8]** Clés d'ordre : realtime → `actorId`/`missionId` au lieu d'`eventId` aléatoire (couvre **P-07**, positions GPS hors ordre) ; sync → `aggregateId` ; geo/route → aligner `acks=all` + idempotence (couvre **P-08**) — **Élevé**
- [ ] **[Audit n°3 · PR5]** Unifier l'enveloppe d'événement : une seule (celle du kernel : correlationId, causationId, tenantId, schemaVersion, headers `X-Yow-*`) et un seul contrat (`TntDomainEvent` aligné dessus), migration des 3 formats existants — **Élevé**
- [ ] **[Audit n°5 · P-20b]** Émettre l'événement `tnt.roles.permission-changed` (déclaré, jamais publié) pour invalider le cache RBAC au lieu d'attendre le TTL 300 s — **Moyen**
- [ ] **[Audit n°5 · P-20d]** Remplacer l'appel HTTP synchrone Agency→Core de création de mission par la publication sur `tnt.agency.mission.request` (le topic existe déjà côté producteur, non exploité) — **Moyen**

**Vérification :** test d'intégration DLQ — un message malformé atterrit dans `tnt.dlq.<module>` au lieu d'être silencieusement skippé. Test idempotence — publier deux fois le même `eventId`, vérifier une seule écriture.

## Chantier D — Scalabilité (pagination, DB, cache, résilience HTTP)

- [ ] **[Audit n°6 · S7/S20]** Pagination keyset sur tous les endpoints de listing (deliveries, templates de facturation) — **Élevé/Faible** — `DeliveryController.java:72-97`, `ListTemplatesUseCase.java:69`
- [ ] **[Audit n°6 · S9]** Retry avec backoff sur les échecs de verrou optimiste wallet ; planifier le modèle ledger pour le point de contention wallet d'organisation — **Élevé** — `WalletService.java:80-115,239-291`
- [ ] **[Audit n°6 · S6]** Dimensionner `DB_POOL_MAX_SIZE` (10 connexions aujourd'hui pour ~30 schémas) ; mettre en place 1–2 read replicas et router les lectures — **Élevé** — `TntDataSourceConfig.java:59-64`
- [ ] **[Audit n°6 · S10/S15]** État GPS/geofence en mémoire par instance → Redis (TTL glissant) ou sticky partitioning + éviction locale — **Élevé/Moyen** — `GpsPingProcessor.java:71-77`, `GeofenceMonitorService.java:43`
- [ ] **[Audit n°6 · S11]** Bulkhead sur le solveur VRP (OR-Tools, CPU-bound sans limitation de concurrence) + cache des matrices de distances — **Élevé** — `VrpSolverService.java:39-43,45-66`
- [ ] **[Audit n°6 · S12]** `max.in.flight.requests.per.connection=5` (au lieu de 1, sans bénéfice actuel), plus de partitions sur les topics chauds, cluster Kafka RF 3 — **Élevé** — `application.yml:46-50,314-315`
- [ ] **[Audit n°6 · S17]** Généraliser les timeouts explicites aux 80/83 usages de `WebClient` du dépôt (au-delà du périmètre Kernel déjà couvert en Phase 0/chantier D) — **Moyen**
- [ ] **[Audit n°6 · S18]** Réseau routier complet chargé en mémoire JVM par tenant (empreinte non bornée multi-tenants) — revoir la stratégie de cache — **Moyen** — `RoadNetworkProviderAdapter.java:61`

**Vérification :** test de charge sur un endpoint de listing avec pagination — temps de réponse stable indépendamment du volume total. Test de coupure Kernel avec les nouveaux timeouts — dégradation propre, pas de blocage de thread.

## Chantier E — Architecture & gouvernance

- [ ] **[Audit n°1 · A4]** Charte `coreBackend/` : position dans les couches (L6-bis), règle « BFF orchestre, ne réimplémente pas », activation par profil dans bootstrap — **Élevé**
- [ ] **[Audit n°1 · A5]** Refactorer les 9 contrôleurs `tnt-go-freelancer-point-back-core` qui accèdent directement à la persistance, en passant par de vrais use-cases — **Élevé** — `coreBackend/tnt-go-freelancer-point-back-core/.../adapter/in/web/*`
- [ ] **[Audit n°1 · A13]** Clarifier les responsabilités potentiellement dupliquées entre la couche billing/sync cœur et les sous-modules agency (`tnt-agency-sync-core` vs `tnt-sync-core`, `tnt-agency-billing-core` vs `billing/*`) — **Moyen**
- [ ] **[Audit n°1 · A7]** Tests sur `tnt-platform-gateway-core` (82 classes de sécurité, 0 test aujourd'hui) puis réactivation de la gate JaCoCo avec ratchet progressif — **Élevé**
- [ ] **[Audit n°2 · reco 2]** Purger les 112 dépendances Maven mortes (liste exhaustive §2.2 de l'audit, directement actionnable pom par pom) — **Élevé**, effort moyen
- [ ] **[Audit n°2 · reco 5]** Mettre l'assemblage en cohérence : documenter le `@ComponentScan` global dans CLAUDE.md et nettoyer `TntCoreConfig` (import mort ligne 7, commentaire faux ligne 43) — **Moyen**
- [ ] **[Audit n°2 · reco 6]** Documenter la couche `coreBackend/` (L6-bis, feuilles consommatrices) et `tnt-hrm-core` dans CLAUDE.md, avec sa règle : jamais de dépendance entrante depuis le cœur — **Moyen**, pairs avec A4

**Vérification :** `mvn -pl tnt-platform-gateway-core test` passe avec une couverture non nulle. `mvn dependency:analyze` ne remonte plus les 112 dépendances mortes après purge.

## Chantier F — i18n & multidevise

- [ ] **[Audit n°4 · P1]** Unifier `Money` sur `tnt-common-core` (supprimer les 6 doublons, y compris market-back et sa sémantique `long` ambiguë), migrer `'FCFA'→'XAF'` en base, contrainte ISO 4217 — couvre **P-6, P-7, P-19, P-22, P-23** — **Élevé**, 4–6 j, à traiter en ADR (touche billing + market)
- [ ] **[Audit n°4 · P1]** Implémenter la résolution de locale : filtre `Accept-Language` → contexte Reactor ; consultation de `preferredLanguage` dans `NotificationService` au lieu des producteurs Kafka — couvre **P-4** — **Élevé**
- [ ] **[Audit n°4 · P1]** Implémenter le fallback de locale dans `JsonLocalePackAdapter` (fr_CM→fr_FR→défaut) et corriger `LocaleConfig.toLocaleTag()` (`replace("_","_")` est un no-op) — couvre **P-5, P-12** — **Moyen**

**Vérification :** test unitaire sur `Money` — une opération XAF+XAF ne produit jamais de décimales fantômes. Test d'intégration — une notification envoyée sans `Accept-Language` explicite utilise `preferredLanguage` du destinataire.

---

## Definition of Done — Phase 1

- [ ] Chantier G : test de charge tuiles/BFF vert ; `/nearby` sous 50 ms avec index GIST.
- [ ] Chantier A : RLS actif sur au moins les tables sensibles (factures, wallet, litiges) ; gateway de rate limiting en place ; circuit breakers Kernel généralisés.
- [ ] Chantier C : DLQ générique fonctionnelle sur tous les modules ; idempotence consommateur vérifiée sur wallet/accounting/dispute.
- [ ] Chantier D : pagination systématique ; read replica opérationnel ; VRP sous bulkhead.
- [ ] Chantier E : dépendances mortes purgées ; `coreBackend/` documenté dans CLAUDE.md.
- [ ] Chantier F : `Money` unique ; résolution de locale opérationnelle en test.
