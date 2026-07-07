# Purpose
Observability stack: Actuator endpoints, custom health indicators, metrics, tracing.

# Summary
Standard Spring Boot Actuator + Micrometer/Prometheus/Zipkin, plus 2 custom endpoints and 6 custom health indicators specific to this platform (Kernel connectivity, DB pyramid, MinIO, OR-Tools, auth config, roles config).

# Details

## Custom actuator endpoints

| Endpoint | Class | Returns |
|---|---|---|
| `GET /actuator/tnt-modules` | `TntModuleInventoryEndpoint` | Full module report (all ~31 module descriptors: name, version, layer, dependencies) |
| `GET /actuator/tnt-modules/{moduleId}` | `TntModuleInventoryEndpoint` | Single module descriptor |
| `GET /actuator/tnt-kernel` | `TntKernelStatusEndpoint` | Last-known Kernel connectivity status (cached) — version, YowAuth/event-bus reachability, latency |
| `POST /actuator/tnt-kernel` | `TntKernelStatusEndpoint` | Forces a live Kernel ping, returns refreshed status |

Both registered in `tnt-bootstrap/.../config/`, class names match `TntModuleRegistry`/`TntExtensionRegistry` described in `architecture/modules.md`.

## Custom health indicators (composite group `tnt-infra`)

| Indicator | Path | Checks |
|---|---|---|
| `TntKernelHealthIndicator` | `/actuator/health/tnt-infra/kernel` | Kernel ping — UP/DEGRADED/DOWN with version + latency |
| `TntDatabasePyramidHealthIndicator` | `/actuator/health/tnt-infra/database-pyramid` | Postgres connectivity across all schema levels (incl. `tnt_incident`, `tnt_geo` schemas) |
| `MinioHealthIndicator` | `/actuator/health/tnt-infra/minio` | `listBuckets()` call succeeds |
| `TntOrToolsHealthIndicator` | `/actuator/health/tnt-infra/or-tools` | OR-Tools 9.8.3296 native JNI library loaded |
| `TntAuthHealthIndicator` | `/actuator/health/tnt-infra/auth` | Config-only (no network call): JWT issuer URI set, service code set, cache TTL valid |
| `TntRolesHealthIndicator` | `/actuator/health/tnt-infra/roles` | System tenant ID configured, `TntRoleDefinitionRegistry` loaded with ≥9 roles; `UNKNOWN` if `tnt-roles-core` absent |
| `TntKafkaHealthIndicator` | `/actuator/health/kafka` | `AdminClient.describeCluster()` — added because Spring Boot 4 dropped Kafka's built-in health auto-config (see `knowledge/known-issues.md`) |

## Standard health groups (`application.yml`)
```yaml
management.endpoint.health.group:
  liveness:  { include: livenessState }
  readiness: { include: readinessState, r2dbc, redis, kafka }
```

## Metrics & tracing
- Prometheus scrape endpoint: `GET /actuator/prometheus` (Micrometer auto-config).
- Tags: `application=tiibntick-core`, `profile=<active profile>`.
- Tracing: Micrometer Tracing → Zipkin bridge, `management.tracing.sampling.probability` (0.1 dev, 0.5 staging, 0.05 prod), exports to `${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}`.
- Grafana dashboards provisioned from `tnt-bootstrap/docker/grafana/provisioning/` (not yet documented in detail — check that folder directly if dashboards are missing/broken).

## Swagger / OpenAPI
`/swagger-ui.html`, `/v3/api-docs/{group}` — grouped by domain (`00-all`, `02-actor-core`, `03-incident-core`, etc.), defined in `TntOpenApiConfig`. **springdoc 3.0.3 required for Spring Boot 4/Framework 7** — see `knowledge/known-issues.md` for the version-mismatch incident that broke this.

# Links
- `architecture/modules.md` — `TntModuleRegistry`/`TntExtensionRegistry`
- `knowledge/known-issues.md` — Kafka health indicator gap, springdoc version incident
- `infrastructure/docker.md` — Prometheus/Grafana/Zipkin container config

---
> **Comment maintenir ce document** : un nouveau `HealthIndicator`/`@Endpoint` = une nouvelle ligne dans le tableau correspondant. Si Spring Boot restaure le health-check Kafka natif dans une future version, retirer `TntKafkaHealthIndicator` du tableau (ou noter qu'il est redondant).
