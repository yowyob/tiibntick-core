# TiiBnTick Core Bootstrap — `tnt-bootstrap`

> **⭐ Seul module exécutable de TiiBnTick Core**  
> **Auteur :** MANFOUO Braun — ENSP Yaoundé 2026  
> **Architecture :** Monolithe Modulaire DDD (Spring Boot 4 + WebFlux + R2DBC)

---

## Table des matières

1. [Vue d'ensemble](#vue-densemble)
2. [Architecture](#architecture)
3. [Prérequis](#prérequis)
4. [Lancer le projet](#lancer-le-projet)
   - [Avec Docker Compose (recommandé)](#avec-docker-compose-recommandé)
   - [En local (développement)](#en-local-développement)
   - [En production (Docker)](#en-production-docker)
5. [Configuration](#configuration)
   - [Variables d'environnement](#variables-denvironnement)
   - [Profils Spring](#profils-spring)
6. [Modules assemblés](#modules-assemblés)
7. [Endpoints clés](#endpoints-clés)
   - [Swagger UI](#swagger-ui)
   - [Actuator / Health](#actuator--health)
   - [Métriques Prometheus](#métriques-prometheus)
8. [Infrastructure](#infrastructure)
9. [Build et compilation](#build-et-compilation)
10. [Tests](#tests)
11. [CI/CD GitLab](#cicd-gitlab)
12. [Troubleshooting](#troubleshooting)

---

## Vue d'ensemble

`tnt-bootstrap` est le **point d'entrée unique** de TiiBnTick Core. Il n'implémente aucune logique métier — son unique rôle est d'**assembler** tous les modules L0 à L5 dans un seul contexte Spring Boot et d'exposer leurs endpoints REST, WebSocket et Actuator.

```
tiibntick-core/
├── foundation/                  ← L0 (yow-event-kernel, yow-i18n-kernel)
├── identity/                    ← L2 (tnt-actor-core, tnt-organization-core, ...)
├── logistics/                   ← L3 (tnt-delivery-core, tnt-media-core, ...)
├── business/                    ← L4 (tnt-resource-core, tnt-product-core, ...)
├── billing/                     ← L5 (tnt-billing-*, 6 modules)
└── tnt-bootstrap/               ← L6 ← VOUS ÊTES ICI ⭐
    ├── Dockerfile               ← Image Debian (glibc pour OR-Tools JNI)
    ├── docker-compose.yml       ← Stack de développement complète
    ├── pom.xml                  ← spring-boot-maven-plugin skip=false (unique)
    └── src/
        └── main/
            ├── java/.../TiiBnTickApplication.java
            └── resources/application.yml
```

---

## Architecture

```
                    ┌──────────────────────────────────────┐
                    │           tnt-bootstrap               │
                    │  @SpringBootApplication (unique)       │
                    │  Port: 8080                            │
                    │                                        │
                    │  ┌──────────┐  ┌──────────────────┐  │
                    │  │  REST    │  │  WebSocket STOMP  │  │
                    │  │  /api/v1 │  │  /ws              │  │
                    │  └──────────┘  └──────────────────┘  │
                    │                                        │
                    │  ┌────────────────────────────────┐   │
                    │  │   Modules L0 → L5 (31 modules) │   │
                    │  │   auto-configurés via Spring    │   │
                    │  └────────────────────────────────┘   │
                    └──────────────────────────────────────┘
                           │              │            │
                    ┌──────▼──────┐  ┌───▼───┐  ┌────▼───┐
                    │ PostgreSQL  │  │ Redis  │  │ Kafka  │
                    │ 18 + PostGIS│  │   7   │  │  KRaft │
                    └─────────────┘  └───────┘  └────────┘
                           │
                    ┌──────▼──────┐  ┌─────────────┐
                    │    MinIO    │  │Elasticsearch│
                    │ (S3-compat) │  │      8      │
                    └─────────────┘  └─────────────┘
```

---

## Prérequis

| Outil | Version minimale | Notes |
|-------|-----------------|-------|
| Java | 21 LTS | Eclipse Temurin recommandé |
| Maven | 3.9+ | Wrapper `mvnw` disponible |
| Docker | 25+ | Pour docker-compose |
| Docker Compose | v2.x | `docker compose` (v2 intégré) |
| PostgreSQL | 17+ (PostGIS 3.5+) | Via Docker en dev |
| OR-Tools | 9.8.3296 | JNI — Debian Linux **obligatoire** en prod |

> **⚠️ Note OR-Tools :** Les bibliothèques natives Google OR-Tools requièrent `glibc` (Debian/Ubuntu). L'image Docker utilise `eclipse-temurin:21-jre` (Debian Bookworm). **Alpine Linux est incompatible.**

---

## Lancer le projet

### Avec Docker Compose (recommandé)

```bash
# 1. Cloner le projet
git clone https://gitlab.com/tiibntick-org/tiibntick-core.git
cd tiibntick-core/tnt-bootstrap

# 2. Démarrer l'infrastructure (PostgreSQL, Redis, Kafka, MinIO, ...)
docker compose up -d postgres redis kafka minio elasticsearch zipkin prometheus grafana

# 3. Attendre que les services soient healthy
docker compose ps

# 4. Builder l'application (depuis la racine du projet)
cd ..
./mvnw clean package -DskipTests -pl tnt-bootstrap -am

# 5. Démarrer l'application avec le profil app
cd tnt-bootstrap
docker compose --profile app up -d tiibntick-core

# 6. Vérifier les logs
docker compose logs -f tiibntick-core
```

**URLs disponibles après démarrage :**

| Service | URL |
|---------|-----|
| TiiBnTick Core | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health Check | http://localhost:8080/actuator/health |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |
| MinIO Console | http://localhost:9001 (admin: `minioadmin` / `minioadmin123`) |
| Grafana | http://localhost:3100 (admin: `admin` / `tiibntick_grafana`) |
| Prometheus | http://localhost:9090 |
| Zipkin Tracing | http://localhost:9411 |
| Elasticsearch | http://localhost:9200 |

### En local (développement)

```bash
# 1. Démarrer uniquement l'infrastructure
docker compose up -d postgres redis kafka minio

# 2. Compiler tous les modules depuis la racine
cd ..
./mvnw clean install -DskipTests

# 3. Lancer tnt-bootstrap avec le profil dev
cd tnt-bootstrap
../mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# OU avec le JAR directement
java -DSPRING_PROFILES_ACTIVE=dev \
     -DDB_HOST=localhost \
     -DKAFKA_BOOTSTRAP=localhost:9092 \
     -DMINIO_ENDPOINT=http://localhost:9000 \
     -jar target/tnt-bootstrap-0.0.1.jar
```

### En production (Docker)

```bash
# 1. Builder l'image Docker (depuis tnt-bootstrap/)
docker build -t ghcr.io/tiibntick-org/tnt-core:0.0.1 .

# 2. Lancer avec les variables d'environnement de production
docker run -d \
  --name tnt-core \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=prod-postgres.internal \
  -e DB_PASSWORD=<secret> \
  -e KAFKA_BOOTSTRAP=prod-kafka:9092 \
  -e MINIO_ENDPOINT=http://prod-minio:9000 \
  -e MINIO_ACCESS_KEY=<secret> \
  -e MINIO_SECRET_KEY=<secret> \
  -e JWT_ISSUER_URI=https://auth.yowyob.com \
  -e QR_HMAC_SECRET=<strong-random-256bit-key> \
  ghcr.io/tiibntick-org/tnt-core:0.0.1
```

---

## Configuration

### Variables d'environnement

| Variable | Défaut | Description |
|----------|--------|-------------|
| `SPRING_PROFILES_ACTIVE` | `default` | Profil actif (`dev`, `test`, `staging`, `prod`) |
| `SERVER_PORT` | `8080` | Port d'écoute HTTP |
| `DB_HOST` | `localhost` | Hôte PostgreSQL |
| `DB_PORT` | `5432` | Port PostgreSQL |
| `DB_NAME` | `tiibntick_core` | Nom de la base de données |
| `DB_USER` | `tiibntick` | Utilisateur PostgreSQL |
| `DB_PASSWORD` | `tiibntick_pass` | Mot de passe PostgreSQL |
| `REDIS_HOST` | `localhost` | Hôte Redis |
| `REDIS_PORT` | `6379` | Port Redis |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Bootstrap Kafka |
| `KAFKA_PARTITIONS` | `3` | Nombre de partitions par topic |
| `KAFKA_REPLICATION` | `1` | Facteur de réplication Kafka |
| `MINIO_ENDPOINT` | `http://localhost:9000` | Endpoint MinIO S3 |
| `MINIO_ACCESS_KEY` | `minioadmin` | Clé d'accès MinIO |
| `MINIO_SECRET_KEY` | `minioadmin` | Clé secrète MinIO |
| `MINIO_PUBLIC_ENDPOINT` | `http://localhost:9000` | URL publique MinIO |
| `JWT_ISSUER_URI` | `http://localhost:9000` | Issuer URI des tokens JWT |
| `JWT_JWK_SET_URI` | `${JWT_ISSUER_URI}/oauth2/jwks` | URI JWKS pour validation |
| `QR_HMAC_SECRET` | `changeme-...` | **⚠️ Changer en prod** — Clé HMAC QR codes |
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` | Endpoint Zipkin tracing |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,...` | Origines CORS autorisées |
| `TRACING_SAMPLING` | `0.1` | Taux d'échantillonnage des traces (0.0 à 1.0) |

### Profils Spring

| Profil | Usage | Swagger UI | Log Level App |
|--------|-------|-----------|--------------|
| `default` (dev) | Développement local | Activé | DEBUG |
| `test` | Tests Testcontainers | Activé | DEBUG |
| `staging` | Pré-production | Activé | INFO |
| `prod` | Production | **Désactivé** | INFO |

---

## Modules assemblés

`tnt-bootstrap` importe **25 modules** répartis sur 6 couches :

| Couche | Module | Rôle |
|--------|--------|------|
| **L0** | `yow-event-kernel` | Bus événementiel Kafka + Outbox Pattern |
| **L0** | `yow-i18n-kernel` | i18n FR/EN/Pidgin, devises XAF/NGN/KES |
| **L2** | `tnt-actor-core` | Profils Livreur, Freelancer, GPS temps réel |
| **L2** | `tnt-organization-core` | Agences, Antennes, Zones, Hubs |
| **L2** | `tnt-tp-core` | Tiers, Fidélité clients, KYC |
| **L2** | `tnt-administration-core` | RBAC, Gouvernance, RGPD |
| **L3** | `tnt-geo-core` | PostGIS, OSM, Météo, POI locaux africains |
| **L3** | `tnt-route-core` | A\*, VRP OR-Tools 9.8, Kalman ETA |
| **L3** | `tnt-delivery-core` | Mission, Package, SLA, Hub Dépôt, Preuve |
| **L3** | `tnt-dispute-core` | Litiges, Preuves, Remboursements |
| **L3** | `tnt-realtime-core` | WebSocket STOMP, GPS Stream, Présence |
| **L3** | `tnt-sync-core` | Offline-First, DuckDB-Wasm, Delta Sync |
| **L3** | `tnt-notify-core` | FCM, MTN SMS, Orange SMS, WhatsApp, Email |
| **L3** | `tnt-media-core` | QR Code ZXing, PDF JasperReports, MinIO |
| **L4** | `tnt-resource-core` | Véhicules, Équipements, Maintenance |
| **L4** | `tnt-product-core` | Catalogue produits logistiques |
| **L4** | `tnt-inventory-core` | Stock Hub, Tracking entrées/sorties |
| **L4** | `tnt-sales-core` | Commandes, Dispatch |
| **L4** | `tnt-accounting-core` | Journal OHADA, Balance comptable |
| **L5** | `tnt-billing-dsl` | DSL pricing, AST expressions |
| **L5** | `tnt-billing-pricing` | Calcul prix de vente |
| **L5** | `tnt-billing-cost` | Coût opérationnel (carburant, usure) |
| **L5** | `tnt-billing-invoice` | Factures, TVA multi-pays, cycle de vie |
| **L5** | `tnt-billing-wallet` | MTN MoMo, Orange Money, Stripe |
| **L5** | `tnt-billing-report` | Revenus, Commissions, KPIs, Export CSV |

---

## Endpoints clés

### Swagger UI

```
GET /swagger-ui.html
```

L'UI agrège tous les modules en groupes sélectionnables via le dropdown :
- `00-all` — Tous les endpoints
- `02-actor-core` — Acteurs et livreurs
- `02-organization-core` — Agences et branches
- `03-delivery-core` — Missions et colis
- `03-geo-core` — Géolocalisation et POI
- `03-media-core` — QR Code, PDF, Fichiers
- `03-notify-core` — Notifications
- `03-route-core` — VRP et ETA
- `03-dispute-core` — Litiges
- `05-billing-engine` — Facturation

### Actuator / Health

```bash
# Health global
GET /actuator/health

# Probes Kubernetes
GET /actuator/health/liveness   # Liveness probe
GET /actuator/health/readiness  # Readiness probe (vérifie DB, Redis, Kafka)

# Health MinIO (custom)
GET /actuator/health/minio

# Info (module registry)
GET /actuator/info

# Loggers (changer le niveau à chaud)
POST /actuator/loggers/com.yowyob.tiibntick
Content-Type: application/json
{"configuredLevel": "DEBUG"}
```

### Métriques Prometheus

```
GET /actuator/prometheus
```

Métriques exposées : JVM, Spring MVC, R2DBC connection pool, Kafka consumer lag, Redis, WebSocket sessions.

---

## Infrastructure

### Topics Kafka créés au démarrage

| Topic | Module |
|-------|--------|
| `tnt.delivery.mission.created` | tnt-delivery-core |
| `tnt.delivery.mission.status-changed` | tnt-delivery-core |
| `tnt.delivery.package.picked-up` | tnt-delivery-core |
| `tnt.delivery.package.delivered` | tnt-delivery-core |
| `tnt.delivery.hub-deposit.created` | tnt-delivery-core |
| `tnt.geo.gps.position-updated` | tnt-geo-core |
| `tnt.realtime.eta.updated` | tnt-route-core |
| `tnt.notify.notification.requested` | tnt-notify-core |
| `tnt.media.file.uploaded` | tnt-media-core |
| `tnt.billing.invoice.created` | tnt-billing-invoice |
| `tnt.billing.payment.confirmed` | tnt-billing-wallet |
| `tnt.dispute.*` (×7) | tnt-dispute-core |
| `tnt.outbox.events` | yow-event-kernel |
| `tnt.dlq` | yow-event-kernel |

### Schémas PostgreSQL créés par Liquibase

| Schéma | Module |
|--------|--------|
| `tnt_media` | tnt-media-core |
| `tnt_delivery` | tnt-delivery-core |
| `tnt_geo` | tnt-geo-core |
| `tnt_actor` | tnt-actor-core |
| `tnt_organization` | tnt-organization-core |
| `tnt_billing` | tnt-billing-* |
| `tnt_notify` | tnt-notify-core |

---

## Build et compilation

```bash
# Depuis la racine du projet tiibntick-core/

# Compiler tous les modules (ordre respecté par Maven)
./mvnw clean install -DskipTests

# Compiler uniquement tnt-bootstrap et ses dépendances
./mvnw clean package -pl tnt-bootstrap -am -DskipTests

# Produire le fat JAR exécutable
./mvnw clean package -pl tnt-bootstrap -am
ls tnt-bootstrap/target/tnt-bootstrap-0.0.1.jar

# Builder l'image Docker
docker build -t tiibntick/tnt-core:0.0.1 tnt-bootstrap/
```

---

## Tests

```bash
# Tests unitaires uniquement
./mvnw test -pl tnt-bootstrap

# Tests d'intégration (Testcontainers — nécessite Docker)
./mvnw verify -P integration-tests -pl tnt-bootstrap

# Tous les tests avec rapport JaCoCo
./mvnw verify -pl tnt-bootstrap
open tnt-bootstrap/target/site/jacoco/index.html
```

**Couverture minimale requise :** 70% lignes, 60% branches (configuré dans le parent POM).

---

## CI/CD GitLab

Le pipeline GitLab CI/CD (`.gitlab-ci.yml` à la racine du projet) exécute automatiquement :

1. **build** — `mvn clean package -DskipTests`
2. **test** — `mvn test`
3. **integration-test** — `mvn verify -P integration-tests` (avec services Docker)
4. **docker-build** — `docker build` + push vers `registry.gitlab.com`
5. **deploy-staging** — déploiement automatique sur l'environnement staging
6. **deploy-prod** — déploiement manuel sur production (approbation requise)

---

## Troubleshooting

### L'application ne démarre pas : `OR-Tools native libraries could not be loaded`

**Cause :** Les bibliothèques JNI OR-Tools ne sont pas présentes sur le système hôte.  
**Solution (développement) :** C'est non-fatal en dev — les features VRP seront désactivées mais l'app démarre.  
**Solution (production) :** Utiliser obligatoirement l'image Docker Debian fournie.

### Erreur de connexion PostgreSQL au démarrage

```bash
# Vérifier que PostgreSQL est healthy
docker compose ps postgres
docker compose logs postgres | tail -20

# Vérifier la connectivité
docker compose exec postgres pg_isready -U tiibntick -d tiibntick_core
```

### Liquibase : `Table already exists` ou conflits de migration

```bash
# Réinitialiser la base de données de développement (DESTRUCTIF)
docker compose down -v
docker compose up -d postgres
# Relancer l'application — Liquibase recréera tout
```

### Kafka : les topics ne sont pas créés

```bash
# Vérifier que Kafka est healthy
docker compose logs kafka | grep "Kafka Server started"

# Lister les topics existants
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

### Ports déjà utilisés

```bash
# Trouver quel processus utilise le port 8080
lsof -i :8080
# Ou changer le port
export SERVER_PORT=8090
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

*TiiBnTick — Logistique de confiance pour l'Afrique. Propulsé par Yowyob.*  
*Auteur : MANFOUO Braun — ENSP Yaoundé 2026*
