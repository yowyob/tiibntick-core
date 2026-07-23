# Audit n°7 — System Design & Sécurité — TiiBnTick Core

**Date** : 2026-07-17
**Périmètre** : monolithe modulaire réactif, Spring Boot 4 WebFlux, R2DBC/PostgreSQL, Kafka, Redis, MinIO, Kernel Yowyob externe consommé en HTTP. Modules L0–L7 **plus `coreBackend/`** : `tnt-agency-back-core` (agrégateur de 14 sous-modules événementiels, sans REST), `tnt-go-freelancer-point-back-core`, `tnt-link-back-core` (carto/temps réel Link), `tnt-market-back-core`.
**Méthode** : lecture du code source (configurations de sécurité, contrôleurs, services, repositories, configuration applicative, docker-compose), recherches exhaustives par motifs (grep), échantillonnage vérifié. Chaque constat est étayé par un fichier:ligne.

---

## 1. Résumé exécutif

TiiBnTick Core présente une **fondation architecturale nettement au-dessus de la moyenne** pour un projet étudiant/startup : hexagonal réellement appliqué dans ~35 modules, règle de layering stricte documentée et respectée dans les POMs, outbox transactionnel + DLQ, circuit breakers sur le module trust, API keys plateforme hachées BCrypt avec rotation et audit, Kafka idempotent, probes liveness/readiness bien pensées.

En revanche, l'audit révèle **trois failles de sécurité critiques** qui invalident aujourd'hui les garanties métier :

1. **Les webhooks de paiement sont falsifiables** (signature Stripe non vérifiée, signature MTN facultative, aucune vérification Orange) — un attaquant peut créditer un wallet sans payer.
2. **Le tenant est fourni par le client** via l'en-tête `X-Tenant-Id` dans 68 endpoints de 11 modules, jamais recoupé avec le JWT ; combiné à ~139 `findById` sans filtre tenant dans les repositories, l'isolation multi-tenant est **contournable (IDOR cross-tenant)**.
3. **~40 contrôleurs exposant des mutations n'ont aucune annotation d'autorisation** (`@RequirePermission`/`@PreAuthorize`) : tout JWT valide, quel que soit son rôle, peut par exemple marquer une facture payée ou créditer un wallet. Le problème est le plus aigu sur les **Core Backend produits** (`tnt-market-back-core` 0/9, `tnt-go-freelancer-point-back-core` 0/10, `tnt-link-back-core` 2/9), exposés sous `/api/v1/platform/**` où la chaîne plateforme termine en `permitAll()` → accès inter-produits non contrôlé.

Côté system design, le monolithe modulaire est le **bon choix** pour une équipe de 3 ; le découpage en schémas PostgreSQL par module et la communication par événements rendent une extraction future plausible. Les principaux risques d'échelle sont : l'absence totale de rate limiting, le couplage temporel synchrone avec le Kernel (160 endpoints HRM proxifiés sans circuit breaker), l'absence de coordination de saga explicite entre livraison/facturation/wallet, et un pipeline de tracing probablement inopérant (mélange bridge OTel / reporter Brave).

**Verdict** : architecture saine, exécution sécurité incomplète. Les correctifs critiques (§6, P0) représentent ~2–3 semaines de travail et doivent précéder toute mise en production ouverte.

---

## 2. Ce qui est bien conçu (avec preuves)

### 2.1 Hexagonal / ports & adapters — réel, pas cosmétique
- Structure `adapter/in`, `adapter/out`, `application/port/{in,out}`, `domain/` vérifiée sur échantillon (geo, delivery, billing-*, platform-gateway). Ex. : `logistics/tnt-geo-core/.../application/port/out/IServiceZoneRepository.java` implémenté par `adapter/out/persistence/R2dbcServiceZoneRepository.java:28`.
- Les ports sortants sont des interfaces (`IServiceZoneRepository`, `IIdempotencyStore`, `IBlockchainProofPort`), les adaptateurs Kafka/R2DBC/MinIO sont substituables.

### 2.2 Règle de layering stricte, vérifiée dans les POMs
- `logistics/tnt-delivery-core/pom.xml` ne dépend que de L0–L2 + geo/route/incident (même couche) ; `billing/tnt-billing-invoice/pom.xml` ne dépend que de tnt-auth-core, tnt-roles-core, tnt-tp-core, yow-event-kernel. Aucune dépendance montante détectée dans l'échantillon.
- Le pattern trust (le module haut dépend *vers le bas* et implémente le port du module bas) est une inversion de dépendance correcte à l'échelle des modules.

### 2.3 Sécurité plateforme (tnt-platform-gateway-core) — conception soignée
- API keys **hachées BCrypt** avec `keyPrefix` pour lookup rapide : `domain/model/ApiKey.java:12`, vérification `PlatformClientAuthenticationService.java:109` (`hashingService.matches(rawApiKey, key.keyHash())`).
- Historique de rotation (`api_key_rotation_history`), audit de chaque tentative en fire-and-forget (`PlatformApiKeyWebFilter.java:80,90`) — n'ajoute pas de latence au chemin de réponse.
- Trois chaînes de sécurité ordonnées et **documentées avec leurs pièges** : `@Order(5)` public, `@Order(10)` API-key plateforme, `@Order(20)` JWT catch-all (`TntSecurityConfig.java:62-83`, note explicite sur le risque de court-circuit).
- Scopes `resource:action` évalués via `PermissionMatcher` partagé plutôt que `hasAuthority()` natif (wildcards gérés) — choix justifié dans la javadoc de `TntPlatformGatewaySecurityConfig.java:33-35`.
- Le filtre API-key n'est **volontairement pas un `@Bean`** pour éviter l'enregistrement global WebFlux (`TntPlatformGatewaySecurityConfig.java:62-66`) — piège subtil correctement évité.

### 2.4 Conversion JWT défensive
- `TntSecurityConfig.java:285-310` : un `ROLE_OWNER`/`tenant:admin` Kernel n'est promu TNT_ADMIN **que si** le `tid` du JWT est le tenant système TiiBnTick — bloque l'élévation de privilèges par les propriétaires de tenants auto-enregistrés. Commentaire explicite du raisonnement.

### 2.5 Messagerie fiable
- Producteur Kafka idempotent : `acks: all`, `enable.idempotence: true`, `max.in.flight.requests.per.connection: 1` (`tnt-bootstrap/src/main/resources/application.yml:46-50`) ; consommateur `read_committed`, auto-commit désactivé (l.54-58).
- **Outbox transactionnel + dead-letter + registre de schémas d'événements** dans `foundation/yow-event-kernel/.../adapter/persistence/repository/` (`R2dbcOutboxEntryRepository`, `R2dbcDeadLetterRepository`, `R2dbcEventSchemaRepository`).
- Idempotence de consommation côté wallet : `RedisIdempotencyStore` (SET NX/EX) `billing/tnt-billing-wallet/.../adapter/out/redis/RedisIdempotencyStore.java:14-23`.

### 2.6 Résilience et disponibilité (partielles mais réfléchies)
- Circuit breakers + time limiters + retry Resilience4j sur les deux chemins trust (`application.yml:160-186`), avec séparation write (Kafka) / read (REST) argumentée.
- `server.shutdown: graceful` (`application.yml:96`), probes liveness/readiness activées, readiness = `readinessState,r2dbc,redis,kafka` avec **exclusion délibérée et documentée du trust gateway** pour ne pas sortir le pod de rotation quand la blockchain est indisponible (`application.yml:113-121`).
- docker-compose : healthchecks sur tous les services, `depends_on: condition: service_healthy` (`docker-compose.yml:274-284`).

### 2.7 Multi-tenancy — le socle existe
- `TntSecurityContext` dérivé du JWT via autorités synthétiques `TENANT_<uuid>`/`ACTOR_<uuid>` (`TntSecurityContextService.java:97-130`), pattern MDC de logs corrélés `[%X{requestId}] [%X{tenantId}]` (`application.yml:391`).
- Les requêtes DatabaseClient échantillonnées utilisent des **paramètres liés** (aucune concaténation de valeurs utilisateur trouvée) : `R2dbcServiceZoneRepository.java:60-98`.

### 2.8 coreBackend — « Core Backend » par produit, temps réel bien conçu
- Les 4 modules `coreBackend/` sont les backends métier par produit plateforme (Agency, Go, Link, Market). `tnt-agency-back-core` est lui-même un **agrégateur de 14 sous-modules Maven** (`tnt-agency-intake-core`, `-assignment-core`, `-billing-core`, `-eventing-core`, `-workforce-core`…), pilotés par événements Kafka, sans contrôleur REST au sommet — découpage propre par capacité.
- **`tnt-link-back-core` est délibérément un Core Backend « générique », pas un BFF** : ses contrôleurs renvoient des réponses non « screen-shaped » et la javadoc anticipe explicitement un **BFF Link séparé** comme consommateur (`ParcelTrackingController.java:16-18` « the single entry point the Link BFF calls […] BFF talks only to its product's Core Backend » ; `ParcelTrackingResponse.java:8` « that adaptation belongs to the Link BFF, not this Core Backend » ; `ActorIdentityController.java:24,48`, `LinkBoardController.java:35`, `NetworkNodeController.java:50`). L'intention d'architecture Frontend → BFF → Core Backend est donc **déjà inscrite dans le code**.
- **`tnt-realtime-core` (L3) contient un moteur temps réel de bonne facture, réutilisable** : WebSocket réactif WebFlux (`adapter/in/websocket/TntStompWebSocketHandler.java`), SSE (`adapter/in/sse/SseController.java`, `FluxSseEmitter`), ingestion GPS REST (`adapter/in/rest/GpsPingRestController.java`), présence Redis (`RedisPresenceRepository`), publication Kafka des événements (GPS/ETA/geofence/presence, `KafkaRealtimeEventPublisher`). **Point fort décisif** : `adapter/out/websocket/RedisTopicMessageListener.java:17-19` implémente déjà le **fan-out multi-instances via Redis pub/sub** — « quand une instance JVM publie un broadcast sur Redis, toutes les instances ré-émettent vers le topic STOMP correspondant ». C'est exactement le pattern qui permet de scaler horizontalement une passerelle WebSocket stateless.

### 2.9 Prêt pour l'extraction (en partie)
- Schéma PostgreSQL par module (ex. `tnt_geography`, `tnt_trust`), Liquibase par module ; « pyramide » de ConnectionFactory (kernel / core / 6 plateformes) déjà séparée dans `TntDataSourceConfig.java:73-151` — la migration vers des bases physiques distinctes est prévue par construction.
- `maven-enforcer-plugin` interdit le driver JDBC en scope compile (réactivité end-to-end préservée).

---

## 3. Ce qui est mal conçu / risques futurs (System Design)

### 3.1 Couplage temporel avec le Kernel HTTP — non maîtrisé hors trust
- `business/tnt-hrm-core` proxifie **160 endpoints** Kernel en `JsonNode` brut (`HrmEmployeeController.java:40-57` et 15 autres contrôleurs), de manière **synchrone, sans circuit breaker ni time limiter** : Resilience4j n'est configuré que pour `trustEventGateway*` (`application.yml:160-186`). Une panne ou une latence du Kernel se propage directement à tous les appels HRM, auth-gateway, SSO, onboarding — et consomme des connexions/mémoire côté Core.
- Les proxys HRM exposent des chemins `/api/employees/**` (hors `/api/v1`, voir §3.3) et ne portent **aucune autorisation locale** : la protection repose entièrement sur le Kernel aval.

### 3.2 Aucune coordination de saga explicite
- `grep -rn "saga"` sur `*/src/main/java` : **0 résultat**. Le flux livraison → facturation → wallet est chorégraphié par événements Kafka (topics `tnt.delivery.package.delivered`, `tnt.billing.wallet.events`, `application.yml:317-334`) sans orchestrateur, sans étapes de compensation identifiables, sans suivi d'état de bout en bout. En cas d'échec partiel (facture générée, paiement wallet échoué), aucun mécanisme visible ne ramène le système à un état cohérent.
- Atténuant : l'outbox + l'idempotence wallet limitent les doublons ; mais la compensation reste à concevoir.

### 3.3 Versioning d'API incohérent
- Coexistence de `/api/v1/**` (majorité), `/billing/wallet`, `/billing/cost`, `/billing/webhooks`, `/tnt/trust` (relevé exhaustif des `@RequestMapping`) et `/api/employees/**` etc. pour les 160 proxys HRM. Impossible de versionner ou de router proprement derrière une gateway sans réécriture.

### 3.4 Pas d'API gateway ni de rate limiting
- `grep -rn "RateLimit|Bucket4j|RequestRateLimiter"` : aucun code de rate limiting (seul un commentaire sur Nominatim). Aucun frontal (le README/compose expose l'app directement, TLS terminé « au niveau Nginx/LB » — `application.yml:527` — mais aucune conf de limitation n'existe).
- Conséquence : brute-force possible sur `/api/v1/auth/**` et sur `X-Api-Key` ; de plus la vérification BCrypt (coûteuse en CPU) sur chaque tentative rend l'endpoint lui-même un vecteur de DoS.

### 3.5 Idempotence des mutations HTTP non généralisée
- Le seul store d'idempotence est interne au wallet (`RedisIdempotencyStore`). Aucun support d'en-tête `Idempotency-Key` sur les POST métier (`grep -rn "Idempotency"` : uniquement wallet + sync). Un retry client sur `POST /generate` (facture) ou une re-livraison Kafka hors wallet peut dupliquer des écritures.

### 3.6 Observabilité : tracing probablement inopérant
- `tnt-bootstrap/pom.xml:363-367` associe `micrometer-tracing-bridge-otel` avec `zipkin-reporter-brave`. Le reporter Brave ne s'enregistre pas sur le bridge OTel (il faudrait `opentelemetry-exporter-zipkin` ou basculer sur `bridge-brave`). Sauf dépendance transitive non vue, **aucune span n'est exportée vers Zipkin** malgré la config `management.zipkin.tracing.endpoint` (`application.yml:132-134`). Les logs corrélés (`requestId`/`tenantId` MDC) existent, mais la propagation MDC en contexte réactif (WebFlux) n'a pas de configuration `context-propagation` visible — à vérifier en runtime.

### 3.7 Extraction microservices : possible mais trois freins concrets
1. **Base Kernel accédée en direct** en mode monolithe (`KERNEL_DB_HOST` par défaut = la propre DB du Core, `docker-compose.yml:237-238` et note `application.yml:515-517`) — un module extrait devrait reproduire cet accès ou tout basculer en HTTP.
2. **Beans partagés par tnt-bootstrap** (`tntKafkaTemplate`, `tntObjectMapper`, fallbacks `@ConditionalOnMissingBean`) : chaque module extrait devra ré-héberger sa config — prévu, mais non testé.
3. **8 ConnectionFactory sur la même instance PostgreSQL** (`TntDataSourceConfig.java:59` : « 8 factories (kernel + core + 6 platforms) ») : le dimensionnement des pools est couplé au `max_connections` partagé ; en cas d'extraction, la même config sur-allouera des connexions.
- En revanche : pas de transaction R2DBC cross-schéma détectée dans l'échantillon, pas de FK cross-module documentée — le découpage par schéma tient.

### 3.8 coreBackend — autorisation quasi absente et géo non scalable
- **Autorisation quasi inexistante sur les Core Backend produits** : `tnt-market-back-core` = **0** `@RequirePermission`/`@PreAuthorize`/`@RequirePlatformScope` sur 9 contrôleurs ; `tnt-go-freelancer-point-back-core` = 0 sur 10 ; `tnt-link-back-core` = 2 seulement sur 9. Ces contrôleurs sont exposés sous `/api/v1/platform/market/**` et `/api/v1/platform/link/**`, précisément le préfixe où la chaîne plateforme termine en `permitAll()` (§4.1). Combinaison aggravante avec le constat #6/#15.
- **Requêtes géo non scalables** : `tnt-link-back-core/.../NetworkAlertR2dbcRepository.java:17-20` fait un `WHERE latitude BETWEEN … AND longitude BETWEEN …` (bounding box) **sans index spatial** (pas de PostGIS GIST, pas de geohash). `DaoZonePersistenceAdapter` fait du `containingPoint` par balayage. À l'échelle carto Link (millions de points, requêtes viewport à chaque pan/zoom), ces scans séquentiels s'effondrent.
- **Aucun temps réel dans Link lui-même** : `tnt-link-back-core` est 100 % requête/réponse REST (aucun WebSocket/SSE). Or le besoin métier Link (« chaque utilisateur voit SA carte se mettre à jour en temps réel ») exige un canal poussé. Le moteur existe dans `tnt-realtime-core` mais n'est pas branché sur Link → **écart fonctionnel à combler** (voir §6, architecture temps réel).

### 3.9 Disponibilité — points restants
- Panne Kernel = indisponibilité de tous les proxys (auth gateway, SSO, onboarding, HRM) sans dégradation gracieuse (pas de CB, §3.1). La readiness n'inclut pas le Kernel (choix défendable : ne pas sortir le pod), mais aucun health indicator sélectif ne protège les routes proxy.
- Kafka mono-broker en dev (`KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1`, `docker-compose.yml:106-108`) ; la prod prévoit RF=3 (`application.yml:555-556`) — correct si le cluster suit.

---

## 4. Sécurité — constats

### 4.1 AuthN/AuthZ
- **[CRITIQUE] Mutations sans contrôle d'autorisation** : 47 fichiers seulement utilisent `@RequirePermission` (et 18 `@PreAuthorize`) pour 136 `@RestController`. L'analyse « contrôleur avec @Post/@Put/@Delete/@Patch mais aucune annotation d'autorisation » retourne **~40 contrôleurs**, dont : `billing/tnt-billing-wallet/.../WalletController.java`, `billing/tnt-billing-invoice/.../InvoiceController.java` (le service a `@RequirePermission(invoice:read)` sur les lectures, mais `mark-paid`/`cancel` côté contrôleur reposent sur le seul service — à vérifier méthode par méthode), les **16 contrôleurs HRM**, `ProductController`, `VehicleController`, `EquipmentController`, et les 10+ contrôleurs `coreBackend/tnt-go-freelancer-point-back-core`. Derrière le catch-all `@Order(20)`, **tout JWT authentifié** (n'importe quel rôle CLIENT) atteint ces mutations.
- **[ÉLEVÉ] Mode dev = admin total sur un flag** : `tnt.auth.allow-anonymous-context=true` fait injecter un token avec `ROLE_TNT_ADMIN` + tous les rôles et permissions (`TntSecurityConfig.java:150-206`). Une erreur de variable d'environnement (`TNT_AUTH_ALLOW_ANONYMOUS`) en prod ouvre tout. Aucun garde-fou (ex. refus si profil `prod`).
- **[ÉLEVÉ] Webhooks inaccessibles ou non authentifiés** : `/billing/webhooks/**` n'est ni dans `PUBLIC_PATHS` (`TntSecurityConfig.java:62-83`) ni dans la chaîne plateforme → la chaîne JWT exige un token que MTN/Orange/Stripe n'enverront jamais. En l'état, les callbacks échouent en 401 en prod ; le contournement naturel (les rendre publics) est dangereux tant que §4.3 n'est pas corrigé.
- **[MOYEN] `/api/v1/platform/**` sans exigence de scope** : `TntPlatformGatewaySecurityConfig.java:88-92` termine par `.anyExchange().permitAll()` après le filtre API-key. Les contrôleurs Market (`/api/v1/platform/market/**`, 10 mappings) et Link (8 mappings) existent déjà : **tout client plateforme authentifié** (ex. Agency) peut appeler les endpoints Market/Link d'un autre produit. Le commentaire (l.41-44) considérait le préfixe « réservé, aucun proxy n'existe encore » — c'est périmé.
- **[CRITIQUE] Core Backend produits sans autorisation** : `tnt-market-back-core` (0 annotation sur 9 contrôleurs), `tnt-go-freelancer-point-back-core` (0/10), `tnt-link-back-core` (2/9). Exposés sous `/api/v1/platform/{market,link}/**`, ils héritent du `permitAll()` de la chaîne plateforme (§4.1) : tout client plateforme authentifié — ou, si la route n'est pas couverte par le filtre API-key, tout appelant — atteint ces mutations sans contrôle de rôle ni de scope.
- **[MOYEN] Actuator** : `/actuator/prometheus` est public (`TntSecurityConfig.java:66`) — divulgation de métriques internes (noms de topics, URIs, volumétrie). Le profil défaut expose aussi `env,beans,loggers` (`application.yml:106`) derrière JWT simple (sans rôle admin requis) ; le profil prod les retire (l.533-536) — bien.

### 4.2 Multi-tenancy / IDOR
- **[CRITIQUE] Tenant fourni par le client** : 68 occurrences de `@RequestHeader("X-Tenant-Id")` dans 11 modules (billing-invoice 5, billing-report 7, accounting 14, product 6, resource 5, sales 13, notify 13, etc.). Aucun code dans `tnt-auth-core` ne recoupe ce header avec l'autorité `TENANT_<uuid>` du JWT (`grep "X-Tenant-Id" foundation/tnt-auth-core` : 0 résultat). Un utilisateur authentifié du tenant A lit/écrit les données du tenant B en changeant un header.
- **[CRITIQUE] `findById` sans tenant** : 139 signatures/appels `findById` sans variante tenant dans les repositories. Cas prouvé de bout en bout : `GET /{invoiceId}` → `InvoiceController.java:49-52` (aucun tenant) → `InvoiceService.getById` → `invoiceRepository.findById(invoiceId)` (`InvoiceService.java:112`) ; idem `getPdfUrl` (`InvoiceController.java:95-98`), `getByNumber`, et le même motif dans `BillingPolicyService` (11 appels), `DslRuleService` (5), `PricingEngineService`, `LoyaltyDiscountService`, `ReconciliationService`. Les UUID v4 ne sont pas une défense (fuite par logs, factures PDF, énumération via d'autres endpoints).
- Contre-exemple sain : `R2dbcServiceZoneRepository.findById(id, tenantId)` filtre le tenant… **sauf si `tenantId` est null**, auquel cas le filtre disparaît silencieusement (`R2dbcServiceZoneRepository.java:89-99`) — le fallback permissif est le mauvais défaut.

### 4.3 Webhooks de paiement (tnt-billing-wallet)
- **[CRITIQUE] Stripe : signature lue mais jamais vérifiée** — `PaymentWebhookController.java:101-116` : « *Stripe signature verification would use stripe-java SDK in production. Simplified* » puis parsing direct du body. Forger un `payment_intent.succeeded` suffit à confirmer un paiement.
- **[CRITIQUE] MTN : l'absence de signature vaut acceptation** — `verifyMtnSignature` retourne `true` si le header `X-MTN-Signature` est absent (`PaymentWebhookController.java:121-126`, « MTN sandbox may not send signatures — allow in non-production » — mais aucun test de profil n'est fait).
- **[CRITIQUE] Orange : aucune vérification** — `handleOrangeCallback` (`PaymentWebhookController.java:74-95`) construit `ConfirmPaymentCommand` directement depuis le body.

### 4.4 Secrets & configuration
- **[ÉLEVÉ] Défauts faibles embarqués comme fallback** (silencieux si l'env var manque en prod) : DB `tiibntick_pass` (`application.yml:25,37`), MinIO `minioadmin/minioadmin123` (`application.yml:266-267`, `docker-compose.yml:134-135,243-244`), HMAC QR `changeme-use-strong-random-256bit-value-in-production` (`application.yml:269`), Grafana `admin/tiibntick_grafana` (`docker-compose.yml:194-195`), `TNT_KERNEL_API_KEY` défaut vide (`application.yml:574`). Aucun fail-fast au démarrage.
- **[ÉLEVÉ] Swagger UI + api-docs activés en prod** — `application.yml:541-545` avec le commentaire « *We will need to disable Swagger UI in production* » : cartographie complète de l'API offerte à l'attaquant.
- **[BIEN]** JWT : validation signature via JWKS distant (`jwk-set-uri`, `application.yml:79-83`), expiration gérée par Spring ; clé privée prod montée en secret Docker (`application.yml:584-586`). Point d'attention : `iss` non validé (choix documenté l.76-78, le Kernel émet un `iss` non-URL) — acceptable si le JWKS est le bon, à re-vérifier si plusieurs émetteurs partagent la clé.

### 4.5 Injections
- **[BIEN]** Aucune concaténation de valeurs utilisateur dans le SQL trouvée : les usages `DatabaseClient` échantillonnés (geo, sync, billing-report, sequences) utilisent des binds nommés ; la seule concaténation trouvée est un fragment constant `" AND tenant_id = :tenantId"` (`R2dbcServiceZoneRepository.java:90`). Pas de SpEL dynamique construit depuis des entrées utilisateur détecté.
- **[FAIBLE]** MinIO : les `objectKey` sont construits côté serveur (`MinioStorageClient.java:68-155`) ; pas de path traversal évident, mais aucune validation centralisée des noms de fichiers uploadés n'a été observée — à couvrir par un test.

### 4.6 Exposition de données / headers
- **[MOYEN]** `TntGlobalExceptionHandler` renvoie `ex.getMessage()` dans les `ProblemDetail` (l.29-40) et `InvoiceExceptionHandler` ajoute des propriétés internes — verbosité modérée, acceptable si les messages domaine restent neutres, mais aucune revue systématique des messages n'existe.
- **[MOYEN]** Aucun header de sécurité HTTP configuré (pas de `headers()` dans les chaînes : HSTS, CSP, X-Content-Type-Options absents ; `grep headers( tnt-bootstrap` : 0). À traiter idéalement au frontal.
- **[FAIBLE]** CORS : `allowCredentials=true` avec origins configurables et défauts localhost (`TntSecurityConfig.java:217-232`) — correct tant que `CORS_ALLOWED_ORIGINS` prod ne contient pas de wildcard.
- **[FAIBLE]** `X-Forwarded-For` pris tel quel pour l'audit IP (`PlatformApiKeyWebFilter.java:106-113`) — spoofable, fausse les logs d'audit.

---

## 5. Problèmes détectés — tableau récapitulatif

| # | Problème | Localisation (preuve) | Criticité | Impact |
|---|----------|----------------------|-----------|--------|
| 1 | Signature Stripe non vérifiée | `PaymentWebhookController.java:101-116` | **Critique** | Confirmation de paiement forgeable → crédit wallet gratuit |
| 2 | Signature MTN facultative (absente ⇒ acceptée) | `PaymentWebhookController.java:121-126` | **Critique** | Idem #1, il suffit d'omettre le header |
| 3 | Callback Orange sans aucune vérification | `PaymentWebhookController.java:74-95` | **Critique** | Idem #1 |
| 4 | Tenant pris du header client `X-Tenant-Id` (68 endpoints, 11 modules), jamais recoupé au JWT | ex. `InvoiceController.java:43,70,78,88,105` ; grep global | **Critique** | Usurpation de tenant : lecture/écriture cross-tenant |
| 5 | `findById` sans filtre tenant (139 occurrences) — IDOR prouvé sur factures/PDF/policies/règles DSL | `InvoiceService.java:112,163,184,205,243` ; `BillingPolicyService.java:70-262` ; `DslRuleService.java:60-110` | **Critique** | Fuite de données inter-tenant par UUID |
| 6 | ~40 contrôleurs de mutation sans `@RequirePermission`/`@PreAuthorize` (wallet, invoice, HRM×16, product, resource, gofp×10…) | liste §4.1 ; ex. `WalletController.java`, `HrmEmployeeController.java:50-57` | **Critique** | Tout JWT (rôle CLIENT inclus) peut muter des données métier |
| 7 | Webhooks `/billing/webhooks/**` derrière la chaîne JWT → 401 pour les providers | `TntSecurityConfig.java:62-83` (absents de PUBLIC_PATHS) | **Élevé** | Paiements cassés en prod, ou ouverture sauvage pour « faire marcher » |
| 8 | Secrets par défaut en fallback silencieux (DB, MinIO, HMAC QR, Grafana, kernel api-key vide) | `application.yml:25,266-269,574` ; `docker-compose.yml:134-135,194-195` | **Élevé** | Déploiement prod avec credentials connus si env var oubliée |
| 9 | Mode dev `allow-anonymous-context` = injection ROLE_TNT_ADMIN, sans garde-fou de profil | `TntSecurityConfig.java:150-206` | **Élevé** | Un flag mal positionné ouvre l'admin total |
| 10 | Swagger UI + api-docs activés en prod | `application.yml:541-545` | **Élevé** | Reconnaissance complète de la surface d'attaque |
| 11 | Aucun rate limiting (auth, API-key BCrypt, onboarding) | grep global : 0 résultat | **Élevé** | Brute-force + DoS CPU (BCrypt par tentative) |
| 12 | 160 proxys Kernel synchrones sans circuit breaker/timeout dédié | `business/tnt-hrm-core/.../Hrm*Controller.java` ; resilience4j = trust uniquement (`application.yml:160-186`) | **Élevé** | Panne/latence Kernel propagée, épuisement ressources |
| 13 | Aucune saga/compensation livraison→facturation→wallet | grep "saga" : 0 ; topics `application.yml:317-334` | **Élevé** | Incohérences financières en échec partiel, réconciliation manuelle |
| 14 | Tracing Zipkin probablement muet : `bridge-otel` + `zipkin-reporter-brave` | `tnt-bootstrap/pom.xml:363-367` | **Moyen** | Pas de traces distribuées malgré la config ; debug prod difficile |
| 15 | `/api/v1/platform/**` sans scope requis (Market/Link déjà déployés) | `TntPlatformGatewaySecurityConfig.java:88-92` vs mappings market/link | **Moyen** | Accès croisé entre produits plateforme |
| 16 | `/actuator/prometheus` public ; `env,beans,loggers` exposés (profil défaut) | `TntSecurityConfig.java:66` ; `application.yml:106` | **Moyen** | Fuite d'informations opérationnelles |
| 17 | Versioning API incohérent (`/billing/*`, `/tnt/trust`, `/api/employees` hors `/api/v1`) | relevé complet des `@RequestMapping` §3.3 | **Moyen** | Routage/gateway/versionnement futurs compliqués |
| 18 | Pas d'idempotence HTTP générale (Idempotency-Key) hors wallet | grep §3.5 | **Moyen** | Doubles écritures sur retries |
| 19 | Fallback permissif : `findById(id, null)` géo saute le filtre tenant | `R2dbcServiceZoneRepository.java:89-99` | **Moyen** | Bypass tenant si un appelant passe null |
| 20 | Messages d'erreur `ex.getMessage()` dans ProblemDetail, pas de headers sécurité HTTP | `TntGlobalExceptionHandler.java:29-40` ; chaînes sans `headers()` | **Faible** | Fuite d'infos modérée ; durcissement à faire au frontal |
| 21 | `X-Forwarded-For` non validé pour l'audit | `PlatformApiKeyWebFilter.java:106-113` | **Faible** | Logs d'audit falsifiables |
| 22 | 8 pools R2DBC sur une instance PG partagée, dimensionnement manuel | `TntDataSourceConfig.java:59` | **Faible** | Épuisement `max_connections` sous charge |
| 23 | Core Backend produits quasi sans autorisation : market 0/9, gofp 0/10, link 2/9 contrôleurs annotés | `coreBackend/tnt-market-back-core`, `-go-freelancer-point-back-core`, `-link-back-core` | **Critique** | Sous `/api/v1/platform/**` (permitAll §15), accès inter-produits non contrôlé |
| 24 | Requêtes carto par bounding box sans index spatial | `NetworkAlertR2dbcRepository.java:17-20` ; `DaoZonePersistenceAdapter.java:45` | **Élevé** | Scans séquentiels ; carte Link inutilisable à l'échelle |
| 25 | Aucun canal temps réel dans Link (moteur existant dans tnt-realtime-core non branché) | `tnt-link-back-core` (0 WS/SSE) vs `tnt-realtime-core/adapter/in/{websocket,sse}` | **Moyen** | Besoin métier « carte live par utilisateur » non couvert |

---

## 6. Architecture cible & feuille de route priorisée

Principe directeur : **rester monolithe modulaire** (équipe de 3, un seul déployable, opérabilité maîtrisée) et investir dans ce qui rend le monolithe (a) sûr, (b) horizontalement scalable, (c) extractible plus tard. Un monolithe WebFlux stateless derrière un LB, avec PostgreSQL répliqué et Kafka partitionné, porte réalistement des millions d'utilisateurs actifs ; « centaines de millions » ne se joue pas maintenant, il se prépare en gardant les coutures propres.

### P0 — Quick wins sécurité (1–3 semaines, avant toute prod ouverte)
1. **Webhooks** : vérification stricte des signatures Stripe (SDK officiel), MTN (rejet si header absent hors profil dev), Orange (secret partagé/allowlist IP) ; sortir `/billing/webhooks/**` dans une chaîne dédiée publique-mais-signée. *Impact : ferme la faille financière n°1 ; effort ~3 j.*
2. **Tenant depuis le JWT, pas du header** : résolveur central (étendre `@CurrentUser`/`TntSecurityContext`) et suppression des 68 `@RequestHeader("X-Tenant-Id")` (ou validation systématique header == claim, en une classe filtre). *Impact : élimine l'usurpation de tenant ; effort ~1 sem.*
3. **Autorisation des mutations** : passe systématique `@RequirePermission` sur les ~40 contrôleurs listés ; règle d'archi testée (ArchUnit) « toute méthode @Post/@Put/@Delete/@Patch doit porter une annotation d'autorisation ». *Impact : ferme l'escalade horizontale ; effort ~1 sem., test pérenne.*
4. **Config durcie** : fail-fast au démarrage prod si secret = valeur par défaut ; `springdoc.enabled=false` en prod ; retirer `/actuator/prometheus` du public (auth réseau ou token scrape) ; refuser `allow-anonymous-context=true` quand profil prod. *Effort ~2 j.*
5. **Scopes `/api/v1/platform/**`** : exiger `MARKET:*`/`LINK:*` par bloc comme pour AUTH/SSO/ONBOARDING. *Effort ~1 j.*

### P1 — Moyen terme (1–3 mois)
6. **Isolation tenant en profondeur** : soit critère tenant obligatoire dans une couche repository commune, soit **PostgreSQL Row-Level Security** par schéma (une policy `tenant_id = current_setting(...)`) — RLS est le meilleur rapport garantie/effort ici et couvre les 139 `findById`. Supprimer les fallbacks `tenantId == null`. *Impact : IDOR structurellement impossible ; effort 2–4 sem.*
7. **Frontal API gateway léger** (nginx/Traefik ou Spring Cloud Gateway) : TLS, rate limiting (par IP + par client-id, compteurs Redis), headers de sécurité (HSTS, X-Content-Type-Options), normalisation des logs d'accès. *Impact : anti-brute-force/DoS + point de contrôle unique ; effort ~2 sem.*
8. **Résilience Kernel** : WebClient partagé avec timeouts, circuit breaker et bulkhead Resilience4j pour tous les proxys (HRM, auth, SSO) ; réponse 503 propre + métrique dédiée. *Impact : une panne Kernel dégrade au lieu d'abattre ; effort ~1 sem.*
9. **Tracing réparé** : aligner `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin` (ou tout-Brave), vérifier la propagation contexte réactif ; tester une trace HTTP→Kafka→consumer. *Effort ~3 j.*
10. **Idempotence & versioning** : généraliser un filtre `Idempotency-Key` (Redis SET NX, réponse rejouée) sur les POST financiers ; migrer `/billing/*`, `/tnt/trust`, `/api/employees` sous `/api/v1` avec redirections temporaires. *Effort ~2 sem.*
11. **Saga documentée** : formaliser le flux delivery→invoice→wallet en chorégraphie avec états persistés (table `fulfillment_progress` alimentée par les événements) + compensations explicites (annulation facture, remboursement wallet) + alerte sur saga bloquée. Pas besoin d'un orchestrateur lourd. *Effort 2–3 sem.*

### P1 bis — Architecture temps réel Link (carte live par utilisateur) & décision BFF

**Problème posé** : chaque utilisateur Link visualise une carte qui doit se rafraîchir en temps réel (positions de livreurs, alertes réseau, zones), pour potentiellement des millions d'utilisateurs simultanés, chacun sur SA vue. Deux questions : (a) faut-il un BFF ? (b) comment pousser le temps réel sans effondrer le système ?

**(a) Décision BFF — OUI pour Link, et le code l'anticipe déjà.**
- `tnt-link-back-core` est conçu comme Core Backend *générique* (réponses non « screen-shaped », voir §2.8) ; la javadoc désigne explicitement un « Link BFF » comme unique consommateur. Introduire ce BFF n'est donc pas un ajout gratuit mais la concrétisation d'une frontière déjà dessinée.
- **Rôle du BFF Link** : (1) agrégation/adaptation aux écrans (composer node + alerte + zone en une réponse), (2) **terminaison du canal temps réel** (WebSocket/SSE côté client), (3) gestion des abonnements viewport (voir plus bas), (4) throttling/debounce par utilisateur. Le BFF ne contient **aucune logique métier** : il parle au Core Backend (REST) et au bus temps réel.
- **Anti-pattern à éviter** : ré-implémenter un moteur WebSocket dans le BFF. Il faut **réutiliser/extraire `tnt-realtime-core`** (STOMP + fan-out Redis pub/sub déjà en place, §2.8) comme socle du canal poussé — le BFF est un client de ce moteur, pas un second moteur.
- **Alternative écartée** : exposer le Core Backend directement au frontend (pas de BFF). Rejetée car cela remonterait la logique de composition d'écran et de gestion d'abonnement viewport dans le client mobile/web (couplage fort, N appels/écran, versionnement impossible) — exactement ce que les commentaires du code cherchent à éviter.

**(b) Modèle temps réel scalable — abonnement par tuiles géographiques (geohash) + fan-out borné.**
- **Découpage en tuiles** : indexer chaque entité mobile/alerte par un préfixe **geohash** (précision 5–6 ≈ 1–5 km). Les producteurs (`GpsPingRestController`, alertes) publient l'événement sur un canal `tile:{geohash}` (Redis pub/sub sharded, ou topic Kafka partitionné par tuile).
- **Abonnement viewport** : à l'ouverture/déplacement de la carte, le client déclare au BFF les tuiles couvrant son viewport ; le BFF (via le moteur realtime) n'abonne la connexion WebSocket **qu'aux canaux de ces tuiles**. Résultat : un utilisateur ne reçoit que les événements de sa zone, pas du monde entier → fan-out borné et prévisible. Un livreur qui bouge publie sur 1 tuile ; seuls les utilisateurs regardant cette tuile sont notifiés.
- **Chargement initial** : la requête « points dans le viewport » doit passer par un **index spatial** — remplacer les `latitude/longitude BETWEEN` (constat #24) par PostGIS `geometry` + index **GIST** (`ST_DWithin`/`ST_Contains`) ou, a minima, un index sur préfixe geohash. Sans cela, chaque pan/zoom déclenche un scan séquentiel.
- **Passerelle WebSocket stateless** : le fan-out Redis multi-instances de `RedisTopicMessageListener` (§2.8) permet déjà de faire tourner N instances de passerelle derrière un LB sticky (ou sessions WS non-sticky avec Redis). Pour l'échelle « millions de connexions », prévoir un **tier passerelle dédié** (scale indépendant du reste), Redis en cluster avec pub/sub sharded, et un budget mémoire par connexion maîtrisé (backpressure Reactor, déjà natif WebFlux).
- **Débit / coût** : debounce des positions GPS (ex. 1 màj/2–5 s par livreur), agrégation par tuile côté producteur, et delta-encoding (n'émettre que les entités entrées/sorties/déplacées de la tuile) pour éviter de renvoyer tout le viewport à chaque tick.
- **Trajectoire d'extraction** : à terme, la passerelle temps réel (moteur `tnt-realtime-core` + BFF) est un excellent premier candidat microservice (profil de charge — connexions longues, CPU I/O — radicalement différent du CRUD transactionnel), sans toucher aux Core Backend.

*Impact : couvre le besoin métier « carte live par utilisateur » à coût de fan-out borné ; réutilise l'existant (realtime-core) au lieu de le réécrire ; effort ~3–4 sem. pour le MVP tuiles+BFF Link, + l'index spatial (#24) en prérequis.*

### P2 — Long terme (6–18 mois, sous condition de traction)
12. **Scalabilité lecture** : réplicas PostgreSQL en lecture + routage R2DBC lecture/écriture ; cache Redis sur les lectures chaudes (policies pricing, zones géo). *Impact : ×5–10 sur le débit lecture.*
13. **Extraction sélective** : premiers candidats = **tnt-billing-wallet** (frontière financière, schéma dédié, idempotence déjà en place, communication événementielle) puis **tnt-realtime-core** (GPS/SSE, profil de charge très différent). Les coreBackend (Agency/Go/Link/Market) sont déjà quasi autonomes (ConnectionFactory dédiées). Prérequis : #6, #9, contrats d'événements versionnés (le registre de schémas de yow-event-kernel est l'atout à exploiter).
14. **Partitionnement des données** : partition par `tenant_id` (ou hash) sur les tables volumineuses (missions, positions GPS, événements) ; archivage froid vers MinIO/parquet.
15. **Kafka en cluster** (3 brokers min, RF=3 partout), consumer groups dimensionnés par partition ; envisager le compactage pour les topics d'état.
16. **Multi-AZ / DR** : le monolithe stateless se réplique trivialement ; l'effort porte sur PostgreSQL (failover managé) et MinIO (erasure coding/site replication).

---

## 7. Conclusion

Le système est **architecturalement mûr mais opérationnellement vulnérable**. La discipline hexagonale, le layering vérifié dans les POMs, l'outbox, l'idempotence wallet et la conception du gateway plateforme témoignent d'un vrai savoir-faire — rare à ce stade. Mais les trois failles critiques (webhooks falsifiables, tenant contrôlé par le client + IDOR massif, mutations sans autorisation) signifient qu'**en l'état, ni l'argent ni l'isolation des clients ne sont protégés** : le P0 (2–3 semaines) est un prérequis absolu à toute exposition publique.

Pour l'ambition d'échelle : ne pas éclater en microservices maintenant. Le chemin rationnel pour 3 développeurs est monolithe durci (P0) → gateway + RLS + résilience Kernel + tracing (P1) → réplicas/partitionnement et extraction ciblée wallet/realtime (P2). Les coutures nécessaires (schémas par module, événements, ports) existent déjà ; c'est l'exécution sécurité et l'outillage d'exploitation qui doivent rattraper la conception.

---
*Audit réalisé par analyse statique du code au commit courant (branche `master`, HEAD c1c1732). Les constats « absence de X » s'appuient sur des recherches exhaustives par motifs ; toute pièce de code hors dépôt (Kernel RT-comops) est hors périmètre.*
