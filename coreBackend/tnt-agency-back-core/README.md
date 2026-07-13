# tnt-agency-back-core

Agency ERP layer (Layer 6) inside TiiBnTick Core — migrated from the `tnt-agency` monolith local DB domains.

- **Source of truth:** PostgreSQL global DB (`agency_*` schemas via Liquibase masters in `tnt-bootstrap`)
- **HTTP surface:** `/api/v1/tenants/{tenantId}/agency-registry/**`
- **Consumers:** `backend/tnt-agency` BFF (HTTP proxy + offline sync), not frontends directly
- **Platform orchestration:** delivery, inventory, billing, wallet, resource clients live in back-core adapters only

See `MODULES.md` for the module map, phasing F1–F8, and closed gaps (hub occupancy/expiry, Kafka projection consumers).

## Build

From the `core/` repository root (parent POM includes this aggregator):

```bash
mvn -pl coreBackend/tnt-agency-back-core -am compile
```

Full bootstrap (ERP modules + platform cores):

```bash
mvn -pl tnt-bootstrap -am package -DskipTests
```

## Bootstrap wiring

All 11 agency ERP modules are registered in:

- `tnt-bootstrap/pom.xml` (dependencies)
- `tnt-bootstrap/src/main/resources/db/changelog/tnt-core-master.yaml` (Liquibase)

Kafka consumers and hub expiry job are configured under `tnt.agency.*` in `tnt-bootstrap/src/main/resources/application.yml`.
