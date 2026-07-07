# _cheat-sheet — Commands & Quick Facts

## Build
```bash
mvn clean install                                   # full build
mvn -pl <module> -am install                         # one module + deps
mvn -pl <module> test -Dtest=ClassName#method         # one test
mvn -pl <module> verify -Pintegration-tests           # integration tests
```

## Run
```bash
docker compose up -d                                  # infra (from tnt-bootstrap/)
mvn -pl tnt-bootstrap spring-boot:run                  # app (default/dev profile)
```

## Key facts
| Fact | Value |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 / Framework 7 |
| Modules | 31 (1 runnable: `tnt-bootstrap`) |
| Lombok | 1.18.46 (pinned — see `knowledge/known-issues.md`) |
| springdoc | 3.0.3 (pinned — Boot 4 only) |
| App port | 8080 |
| Postgres | 5432, db `tiibntick_core`, user `tiibntick`/`tiibntick_pass` |
| Redis | 6379 |
| Kafka | 9092 (host) / `kafka:29092` (internal) |
| MinIO | 9000 (API) / 9001 (console), `minioadmin`/`minioadmin123` |
| System tenant ID | `00000000-0000-0000-0000-000000000001` |

## Permission system modes
```yaml
tnt.roles.permission.mode: LOCAL   # default — no Kernel dependency
                          # REMOTE  — Kernel HTTP only (endpoint not live yet)
                          # HYBRID  — local first, Kernel fallback
```

## 9 canonical roles (full table: `security/roles.md`)
`AGENCY_MANAGER · BRANCH_MANAGER · PERMANENT_DELIVERER · FREELANCER · RELAY_OPERATOR · CLIENT · SUPPORT_AGENT · ORG_ADMIN · TNT_ADMIN`

## Package shape (standard modules — exceptions: tnt-billing-dsl, tnt-accounting-core)
```
adapter/{in/web, in/kafka, out/persistence, out/messaging}
application/{port/in, port/out, service}
domain/{model/{aggregate,entity,valueobject}, event, exception, policy}
config/
```

## Liquibase — adding a migration
1. SQL in `<module>/src/main/resources/db/changelog/changes/NNN_description.sql`
2. Changeset in `<module>/.../db/changelog/tnt-<module>-master.yaml` (`sqlFile` + `splitStatements: true`, or formatted-SQL `include:`)
3. **Don't forget**: `include:` line in `tnt-bootstrap/.../db/changelog/tnt-core-master.yaml` — most common miss.
4. PL/pgSQL functions/triggers → own changeset, `splitStatements: false`.

## Where is X? → `knowledge/project-map.md`

# Links
Full detail behind every line here: `_quick-start.md`, `infrastructure/docker.md`, `security/roles.md`, `infrastructure/database.md`.

---
> **Comment maintenir ce document** : garder strictement au format "fait/commande", aucune explication. Si une ligne nécessite plus d'une phrase d'explication, elle appartient ailleurs avec un lien depuis ici.
