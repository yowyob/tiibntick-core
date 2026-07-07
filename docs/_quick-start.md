# _quick-start — Run This Repo Locally

## 1. Start infrastructure
```bash
cd tnt-bootstrap
docker compose up -d
```
Starts: postgres (PostGIS), redis, kafka (KRaft), minio, elasticsearch, prometheus, grafana, zipkin. See `infrastructure/docker.md` for ports/credentials.

## 2. Build
```bash
mvn clean install
```
First build takes a while (31 modules). See `development/testing.md` for targeted builds (`-pl <module> -am`).

## 3. Run the app
```bash
mvn -pl tnt-bootstrap spring-boot:run
```
Wait for:
```
║          TiiBnTick Core v0.0.1 — Application Ready           ║
║  Status:     ✅ COMPLETED                                    ║
║  Modules:    30 modules active                                ║
```

## 4. Verify
| Check | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| Module inventory | http://localhost:8080/actuator/tnt-modules |
| Kernel status | http://localhost:8080/actuator/tnt-kernel |
| Grafana | http://localhost:3100 (admin/tiibntick_grafana) |
| Zipkin | http://localhost:9411 |
| MinIO console | http://localhost:9001 (minioadmin/minioadmin123) |

## Expected (non-error) log noise
`404 Not Found from POST .../v1/roles` — the Kernel doesn't implement role provisioning yet. Not a bug, see `memory/known-problems.md`.

## If something's broken
1. Check `memory/known-problems.md` and `knowledge/known-issues.md` first.
2. `docker compose ps` — confirm all infra containers are healthy before blaming the app.
3. `mvn clean install` (not just `install`) if you see weird stale-class compile errors — see `knowledge/known-issues.md` for the `maven-jar-plugin` staleness gotcha.

## Full app container (alternative to step 3)
```bash
docker compose --profile app up -d
```
Builds and runs the app inside Docker too — slower iteration, useful for testing the actual deployment artifact.

# Links
- `infrastructure/docker.md` — full service/port/credential reference
- `development/testing.md` — test commands
- `memory/current-state.md` — last-verified working status

---
> **Comment maintenir ce document** : si une étape de démarrage change (nouveau port, nouvelle variable d'env requise), mettre à jour ici ET dans `infrastructure/docker.md`.
