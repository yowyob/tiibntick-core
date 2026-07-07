# Purpose
Reference for the local development infrastructure defined in `tnt-bootstrap/docker-compose.yml` — what runs, on which port, with which credentials.

# Summary
- One compose file, profile-gated: `docker compose up -d` starts infra only; `docker compose --profile app up -d` also builds/runs the app container.
- 8 infra services + 1 optional app service, all on the `tiibntick-net` bridge network, project name `tiibntick-core-dev`.
- Default dev credentials are intentionally weak/static — **never reuse in staging/prod** (see `security/authentication.md`).

# Details

## Services

| Service | Image | Host Port(s) | Purpose |
|---|---|---|---|
| `postgres` | `postgis/postgis:17-3.5` | 5432 | Primary DB, PostGIS extension for geo data |
| `redis` | `redis:7-alpine` | 6379 | Reactive cache, presence/session/geofence (tnt-realtime-core) |
| `kafka` | `confluentinc/cp-kafka:7.7.0` (KRaft, no ZooKeeper) | 9092 (host), 9094, internal 29092 | Event bus across modules |
| `minio` | `minio/minio:RELEASE.2024-10-13T13-34-11Z` | 9000 (S3 API), 9001 (console) | Object storage (media, incident evidence) |
| `elasticsearch` | `elasticsearch:8.15.0` | 9200 | Pulled in transitively via Kernel; see `infrastructure/elasticsearch.md` for actual usage status |
| `prometheus` | `prom/prometheus:v2.54.0` | 9090 | Scrapes `/actuator/prometheus` |
| `grafana` | `grafana/grafana:11.2.0` | 3100→3000 | Dashboards, provisioned from `./docker/grafana/provisioning` |
| `zipkin` | `openzipkin/zipkin:3.4` | 9411 | Distributed tracing sink (Micrometer Tracing bridge) |
| `tiibntick-core` (profile `app`) | built from local `Dockerfile` | 8080 | The app itself — only starts after all infra healthchecks pass |

## Credentials (dev only)

| Service | User | Password |
|---|---|---|
| Postgres | `tiibntick` | `tiibntick_pass` (db: `tiibntick_core`) |
| MinIO | `minioadmin` | `minioadmin123` |
| Grafana | `admin` | `tiibntick_grafana` |

⚠️ `tnt-bootstrap/src/main/resources/application.yml`'s default-profile fallback values **must** match these exactly — a stale fallback (`minioadmin` without `123`) caused a real MinIO `SignatureDoesNotMatch` bug, fixed 2026-06-30 (see `knowledge/known-issues.md`).

## App container env vars (profile `app`)
Maps 1:1 to `application.yml` placeholders (`${DB_HOST:...}` etc.) — `DB_HOST=postgres`, `REDIS_HOST=redis`, `KAFKA_BOOTSTRAP=kafka:29092` (note: internal Kafka listener, not the host-mapped `9092`), `MINIO_ENDPOINT=http://minio:9000`, `SPRING_PROFILES_ACTIVE=dev`. Full list in the compose file's `tiibntick-core.environment` block.

## Startup order
`tiibntick-core` has `depends_on` with `condition: service_healthy` for postgres/redis/kafka/minio/elasticsearch, and `service_started` for grafana/prometheus/zipkin. App healthcheck polls `GET /actuator/health/liveness` every 30s, 90s start period.

## Running locally without the app container
The normal dev loop is `docker compose up -d` (infra only) + `mvn -pl tnt-bootstrap spring-boot:run` on the host — env vars then come from `application.yml` defaults (`localhost` hosts), which must stay in sync with the compose port mappings above.

# Links
- `infrastructure/database.md` — Postgres schema migration (Liquibase)
- `infrastructure/kafka.md`, `infrastructure/redis.md`, `infrastructure/elasticsearch.md`, `infrastructure/monitoring.md`
- `security/authentication.md` — JWT/Kernel auth, not covered by this compose file
- `knowledge/known-issues.md` — MinIO credential mismatch incident

---
> **Comment maintenir ce document** : à chaque ajout/suppression de service dans `docker-compose.yml`, ou changement de port/credential, mettre à jour le tableau ci-dessus. Ne jamais dupliquer les valeurs ailleurs — ce document est la source de vérité pour l'infra locale.
