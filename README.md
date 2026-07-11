# TiiBnTick Core

**Plateforme logistique & de facturation pour l'Afrique**  
Monolithe modulaire DDD — Spring Boot 4 · WebFlux · R2DBC · Kafka · PostGIS

[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/Licence-Propriétaire-red)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.0.1-blue)](pom.xml)

---

## Vue d'ensemble

`tiibntick-core` est la **bibliothèque Maven multi-modules** de la plateforme TiiBnTick. Elle regroupe l'ensemble de la logique métier — identité, logistique, commerce et facturation — dans un monolithe modulaire suivant les principes DDD et l'architecture hexagonale. Le seul module exécutable est [`tnt-bootstrap`](tnt-bootstrap/README.md).

```
33 modules  ·  7 couches architecturales
```

---

## Stack technique

| Composant | Technologie | Version |
|-----------|-------------|---------|
| Langage | Java | 21 LTS |
| Framework | Spring Boot / WebFlux | 4.0.6 / 7.0.8 |
| Persistence réactive | R2DBC + PostgreSQL (PostGIS) | 18 + PostGIS 3.5 |
| Migrations de schéma | Liquibase (JDBC) | 4.x |
| Messaging | Apache Kafka (KRaft) | 3.x |
| Cache / Sessions | Redis (réactif) | 7 |
| Stockage objets | MinIO (S3-compatible) | RELEASE.2024 |
| Routage VRP | Google OR-Tools (JNI) | 9.8.3296 |
| Sérialisation | Jackson 3 (`tools.jackson`) | 3.x |
| Boilerplate | Lombok + MapStruct | — |
| Docs API | SpringDoc OpenAPI | — |
| Observabilité | Micrometer + Zipkin + Prometheus | — |

> **⚠️ OR-Tools** : les bibliothèques JNI requièrent `glibc` (Debian/Ubuntu). Alpine Linux est incompatible.

---

## Architecture en couches

```
L0  foundation/
│   ├── yow-event-kernel        Bus Kafka + Outbox Pattern
│   ├── yow-i18n-kernel         i18n FR/EN/Pidgin, devises XAF/NGN
│   ├── tnt-common-core         Types partagés, constantes
│   ├── tnt-auth-core           Bridge JWT → TntSecurityContext
│   ├── tnt-roles-core          @RequirePermission, cache RBAC
│   └── tnt-platform-gateway-core  Client-ID/API-Key plateforme, scopes, proxy Kernel auth/SSO
│
L2  identity/
│   ├── tnt-actor-core          Profils Livreur, Freelancer, GPS temps réel
│   ├── tnt-organization-core   Agences, Antennes, Hubs, Zones
│   ├── tnt-tp-core             Tiers, Fidélité client, KYC
│   └── tnt-administration-core RBAC plateforme, RGPD, Gouvernance
│
L3  logistics/
│   ├── tnt-geo-core            PostGIS, Nominatim OSM, POI africains
│   ├── tnt-route-core          A*, VRP OR-Tools, ETA Kalman
│   ├── tnt-delivery-core       Mission, Package, SLA, Annonces TiiBnPick
│   ├── tnt-dispute-core        Litiges, Preuves, Médiation, Remboursements
│   ├── tnt-incident-core       Incidents, Escalades, Alertes SLA
│   ├── tnt-realtime-core       WebSocket STOMP, GPS Stream, Présence
│   ├── tnt-sync-core           Offline-First, DuckDB-Wasm, Delta Sync
│   ├── tnt-notify-core         FCM, MTN SMS, Orange SMS, WhatsApp, Email
│   └── tnt-media-core          QR Code ZXing, PDF JasperReports, MinIO
│
L4  business/
│   ├── tnt-resource-core       Véhicules, Équipements, Maintenance
│   ├── tnt-product-core        Catalogue produits logistiques
│   ├── tnt-inventory-core      Stock Hub, Entrées/sorties
│   ├── tnt-sales-core          Commandes, Dispatch
│   └── tnt-accounting-core     Journal OHADA, Balance comptable
│
L5  billing/
│   ├── tnt-billing-dsl         DSL pricing custom (lexer/parser/AST/évaluateur)
│   ├── tnt-billing-pricing     Calcul prix de vente, templates
│   ├── tnt-billing-cost        Coût opérationnel (carburant, usure)
│   ├── tnt-billing-invoice     Factures, TVA multi-pays, cycle de vie
│   ├── tnt-billing-wallet      MTN MoMo, Orange Money, Stripe
│   ├── tnt-billing-report      Revenus, Commissions, KPIs, Export CSV
│   └── tnt-billing-templates   Templates de politique de prix
│
L6  trust/
│   └── tnt-trust-core          Ancrage blockchain transversal (DeliveryProof, CustodyTransfer,
│                                DID, Badges, BillingPolicy, Payment) — dépend de L2→L5, jamais l'inverse
│
L7  tnt-bootstrap/              ← Unique module exécutable ⭐
    @SpringBootApplication — assemble L0→L6, port 8080
```

Chaque module suit l'architecture hexagonale :
```
adapter/in/{web,kafka}       Contrôleurs REST, consommateurs Kafka
adapter/out/{persistence}    R2DBC repositories, entités, mappers
application/port/in          Interfaces use-case (commandes/queries)
application/port/out         Ports sortants (IXxxRepository, IXxxPublisher)
application/service          Implémentations use-case
domain/model/                Agrégats, entités, value objects, events
```

---

## Démarrage rapide

### Prérequis

- Java 21 LTS (Eclipse Temurin recommandé)
- Maven 3.9+ (`./mvnw` disponible)
- Docker + Docker Compose v2

### Infrastructure locale

```bash
cd tnt-bootstrap
docker compose up -d        # postgres, redis, kafka, minio, elasticsearch, zipkin, prometheus, grafana
```

### Compilation

```bash
# Tous les modules depuis la racine
mvn clean install -DskipTests

# Uniquement tnt-bootstrap et ses dépendances
mvn -pl tnt-bootstrap -am clean package -DskipTests
```

### Lancement (développement)

```bash
java -jar tnt-bootstrap/target/tnt-bootstrap-0.0.1.jar \
  --spring.profiles.active=test \
  --spring.r2dbc.url=r2dbc:postgresql://localhost:5432/tiibntick_core \
  --spring.r2dbc.username=tiibntick \
  --spring.r2dbc.password=tiibntick_pass \
  --spring.liquibase.enabled=false
```

### Lancement complet avec Docker Compose

```bash
cd tnt-bootstrap
docker compose --profile app up -d
```

**Services disponibles :**

| Service | URL |
|---------|-----|
| API REST | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| Métriques | http://localhost:8080/actuator/prometheus |
| MinIO Console | http://localhost:9001 |
| Grafana | http://localhost:3100 |
| Zipkin | http://localhost:9411 |

---

## Référence API

> Tous les endpoints sont documentés dans [`ENDPOINT_TEST_REPORT.md`](ENDPOINT_TEST_REPORT.md).  
> Documentation interactive : `GET /swagger-ui.html`

### Couche Identity — `/api/v1/`

| Contrôleur | Base path | Endpoints | Description |
|------------|-----------|-----------|-------------|
| `AgencyController` | `/api/v1/tenants/{t}/agencies` | 8 | CRUD agences + activation |
| `BranchController` | `/api/v1/tenants/{t}/agencies/{a}/branches` | 6 | CRUD antennes |
| `HubRelaisController` | `/api/v1/tenants/{t}/hubs` | 5 | Hubs relais |
| `FreelancerOrgController` | `/api/v1/freelancer-orgs` | 5 | Organisations freelance |
| `DelivererController` | `/api/v1/deliverers` | 11 | Livreurs permanents, GPS, missions |
| `FreelancerController` | `/api/v1/freelancers` | 12 | Profils freelance, KYC, disponibilité |
| `ActorKycController` | `/api/v1/kyc` | 4 | Soumission et validation KYC |
| `TntAdministrationController` | `/api/v1/admin` | 15 | RBAC, gouvernance, RGPD |
| `TntClientProfileController` | `/api/v1/tnt-tp/profiles` | 5 | Profils clients tiers |
| `TpKycController` | `/api/v1/tnt-tp/kyc` | 5 | KYC tiers plateforme |
| `LoyaltyController` | `/api/v1/tnt-tp/loyalty` | 3 | Programme fidélité |
| `RatingController` | `/api/v1/tnt-tp/ratings` | 2 | Évaluations livreurs |

### Couche Logistics — `/api/v1/`

| Contrôleur | Base path | Endpoints | Description |
|------------|-----------|-----------|-------------|
| `GeoController` | `/api/v1/geo` | 18 | Géocodage OSM, Hubs, Geofencing, POI, Zones freelance |
| `RouteController` | `/api/v1/routes` | 8 | Calcul d'itinéraire, VRP OR-Tools, ETA Kalman |
| `DeliveryController` | `/api/v1/tenants/{t}/deliveries` | 10 | Cycle de vie mission : pickup → transit → delivered |
| `DeliveryAnnouncementController` | `/api/v1/tenants/{t}/delivery-announcements` | 7 | TiiBnPick — annonces et enchères livreurs |
| `DeliveryPersonController` | `/api/v1/tenants/{t}/delivery-persons` | 5 | Enregistrement et gestion des livreurs |
| `DisputeController` | `/api/v1/tenants/{t}/disputes` | 8 | Ouverture et résolution de litiges |
| `EvidenceController` | `/api/v1/tenants/{t}/disputes/{d}/evidence` | 4 | Pièces à conviction |
| `MediationController` | `/api/v1/tenants/{t}/disputes/{d}/mediation` | 4 | Médiation et remboursements |
| `IncidentController` | `/api/v1/tenants/{t}/incidents` | 6 | Incidents terrain, escalades SLA |
| `NotificationController` | `/api/v1/notifications` | 10 | FCM, SMS MTN/Orange, WhatsApp, Email, In-App |
| `SyncController` | `/api/v1/sync` | 4 | Offline-First : push/pull/bootstrap/schema DuckDB |

> **Notes GeoController :** params `latitude`/`longitude` (pas `lat`/`lng`) · `radiusKm` · `PATCH /hubs/{id}/occupancy?newOccupancy=`  
> **Notes NotificationController :** canaux valides : `PUSH_FCM`, `SMS_LOCAL`, `WHATSAPP`, `EMAIL`, `IN_APP_WEBSOCKET`  
> **Notes SyncController :** headers requis : `X-User-Id`, `X-Tenant-Id`, `X-Device-Id`

### Couche Business

| Contrôleur | Base path | Endpoints | Description |
|------------|-----------|-----------|-------------|
| `VehicleController` | `/api/resources/vehicles` | 13 | Véhicules, maintenance, affectation missions |
| `FreelancerFleetController` | `/api/resources/freelancer-orgs/{id}/fleet` | 9 | Flotte et équipements freelance |
| `EquipmentController` | `/api/resources/equipment` | 5 | Équipements opérationnels (QR, tablettes, terminaux) |
| `ProductController` | `/api/products` | 7 | Catalogue produits logistiques |
| `ServiceOfferController` | `/api/service-offers` | 6 | Offres de service |
| `SalesOrderController` | `/api/sales/orders` | 13 | Commandes, dispatch, SLA |
| `AccountController` | `/api/accounting/accounts` | 7 | Comptes OHADA |
| `AccountingReportController` | `/api/accounting/reports` | 4 | Balance, Grand Livre, Bilan |
| `JournalEntryController` | `/api/accounting/journal-entries` | 3 | Écritures comptables |

> **Note :** tous les endpoints resource exigent `?tenantId=` en query param.

### Couche Billing

| Contrôleur | Base path | Endpoints | Description |
|------------|-----------|-----------|-------------|
| `BillingPolicyController` | `/api/v1/billing/policies` | 12 | Politiques tarifaires CRUD, activation, affectation org |
| `PolicyTemplateController` | `/api/v1/billing/templates` | 12 | Templates de politique, application, aperçu |
| `DslRuleController` | `/api/v1/billing/dsl` | 11 | Règles DSL : création, évaluation, validation syntaxique |
| `PricingController` | `/api/v1/billing/pricing` | 9 | Calcul prix vente, surcharges, commission split |
| `InvoiceController` | `/api/v1/billing/invoices` | 9 | Génération, PDF, annulation, crédit-note |
| `ReportingController` | `/api/v1/billing/reports` | 10 | Revenus, commissions, marges, KPIs, export CSV |
| `WalletController` | `/billing/wallet` | 9 | Solde, crédits, transactions, gel (`?tenantId=` requis) |
| `CostController` | `/billing/cost` | 6 | Coût opérationnel, paramètres flotte |
| `PaymentWebhookController` | `/billing/webhooks` | 3 | MTN MoMo, Orange Money, Stripe |

#### DSL Billing — syntaxe des expressions

```
# Conditions
weight <= 5 AND distance <= 10
packageType IN [FRAGILE, ELECTRONICS] AND distance BETWEEN 5 AND 20
true   ← toujours vrai

# Actions
SET_BASE 1000 XAF
SET_BASE 2500 XAF

# Validation : POST /api/v1/billing/dsl/validate {"expression":"..."}
```

---

## Configuration

### Variables d'environnement

| Variable | Défaut | Description |
|----------|--------|-------------|
| `SPRING_PROFILES_ACTIVE` | `default` | Profil actif : `test`, `staging`, `prod` |
| `SERVER_PORT` | `8080` | Port HTTP |
| `DB_HOST` | `localhost` | Hôte PostgreSQL |
| `DB_PORT` | `5432` | Port PostgreSQL |
| `DB_NAME` | `tiibntick_core` | Base de données |
| `DB_USER` | `tiibntick` | Utilisateur |
| `DB_PASSWORD` | `tiibntick_pass` | Mot de passe |
| `REDIS_HOST` | `localhost` | Hôte Redis |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Bootstrap Kafka |
| `MINIO_ENDPOINT` | `http://localhost:9000` | Endpoint MinIO |
| `MINIO_ACCESS_KEY` | `minioadmin` | Clé MinIO |
| `MINIO_SECRET_KEY` | `minioadmin` | Secret MinIO |
| `JWT_ISSUER_URI` | — | Issuer URI JWT (Yowyob Kernel) |
| `QR_HMAC_SECRET` | `changeme-...` | **⚠️ Changer en prod** — HMAC QR codes |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Origines CORS |

### Profils Spring

| Profil | Usage | Auth | Swagger |
|--------|-------|------|---------|
| `test` | Tests & développement local | Anonyme autorisé | ✅ |
| `default` | Dev standard | JWT requis | ✅ |
| `staging` | Pré-production | JWT requis | ✅ |
| `prod` | Production | JWT requis | ❌ |

> En profil `test` : `tnt.auth.allow-anonymous-context=true` · `tnt.roles.aop-enabled=false`

---

## Build & tests

```bash
# Build complet (tous modules)
mvn clean install

# Un module + ses dépendances
mvn -pl billing/tnt-billing-dsl -am install

# Tests unitaires d'un module
mvn -pl logistics/tnt-delivery-core test

# Tests d'intégration (Testcontainers)
mvn -pl tnt-bootstrap verify -P integration-tests

# Rapport JaCoCo
mvn verify -pl <module>
open <module>/target/site/jacoco/index.html
```

> `spring-boot-maven-plugin` est désactivé (`skip=true`) pour tous les modules sauf `tnt-bootstrap`.  
> L'`enforcer` interdit : `commons-logging`, `log4j`, et `org.postgresql` en scope `compile`.

---

## Sécurité & Multi-tenancy

Chaque requête transite par `TntSecurityContext` (module `tnt-auth-core`), populé depuis un JWT Yowyob Kernel. Il expose de façon réactive : `tenantId`, `orgId`, `agencyId`, `actorId`.

- **RBAC** : annotation `@RequirePermission` via AOP (`TntPermissionAspect` dans `tnt-roles-core`)
- **9 rôles canoniques** provisionnés au démarrage : `TNT_ADMIN`, `AGENCY_MANAGER`, `BRANCH_MANAGER`, `PERMANENT_DELIVERER`, `FREELANCER`, `FREELANCER_OWNER`, `RELAY_OPERATOR`, `CLIENT`, `SUPPORT_AGENT`
- **Cache permissions** : Redis, TTL configurable via `tnt.roles.cache-ttl`

Les plateformes clientes (Agency, Go, Link, Market, Point Relais) s'authentifient séparément via `X-Client-Id`/`X-Api-Key` (module `tnt-platform-gateway-core`) — Client-ID/API-Key persistants (hash BCrypt, rotation, révocation), scopes `resource:action` à deux niveaux (blocs gateway + futurs proxies métier curés). Administration exclusivement via `/api/v1/admin/platform-clients/**` (rôle `TNT_ADMIN`). Conception : `docs/auth/platform-client-management-design.md` — guide opérationnel : `docs/auth/platform-client-onboarding-guide.md`.

---

## Observabilité

```bash
# Health global
GET /actuator/health

# Probes Kubernetes
GET /actuator/health/liveness
GET /actuator/health/readiness

# Modules enregistrés
GET /actuator/tnt-modules

# Métriques Prometheus
GET /actuator/prometheus

# Changer le niveau de log à chaud
POST /actuator/loggers/com.yowyob.tiibntick
Content-Type: application/json
{"configuredLevel": "DEBUG"}
```

---

## CI/CD GitLab

Pipeline défini dans `.gitlab-ci.yml` (profil `gitlab-ci` activé sur `CI_JOB_TOKEN`) :

1. **build** — `mvn clean package -DskipTests`
2. **test** — `mvn test`
3. **integration-test** — `mvn verify -P integration-tests`
4. **docker-build** — push vers `registry.gitlab.com`
5. **deploy-staging** — automatique
6. **deploy-prod** — manuel (approbation requise)

Publication des artefacts Maven (sources + javadoc) via le profil `release` vers le GitLab Package Registry.

---

## Troubleshooting rapide

**OR-Tools ne charge pas :** non-fatal en développement (VRP désactivé). En production, utiliser l'image Docker Debian fournie dans `tnt-bootstrap/`.

**Erreur PostgreSQL au démarrage :**
```bash
docker compose exec postgres pg_isready -U tiibntick -d tiibntick_core
```

**Conflits Liquibase :**
```bash
docker compose down -v && docker compose up -d postgres
```

**Topics Kafka manquants :**
```bash
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

**Port 8080 occupé :**
```bash
lsof -ti:8080 | xargs kill -9
```

---

## Auteurs & Ownership

| Couches | Auteur | Contact |
|---------|--------|---------|
| L0 event kernel · L3 logistics · L5 billing · L6 trust · L7 bootstrap | **MANFOUO Braun** | mkbraun256@gmail.com |
| L0 i18n · L2 organization · L3 logistics · L4 accounting · L5 billing | **MANFOUO Braun** | — |
| L2 identity (tp/administration) · L4 business | **MANFOUO Braun** | — |

> Bibliothèque Yowyob Kernel (`yowyob.comops.api`) — dépendance externe, propriété de TSAFACK Savio. Ne pas modifier ni vendoriser.

---

*TiiBnTick — Logistique de confiance pour l'Afrique. Propulsé par Yowyob.*
