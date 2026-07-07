# Purpose
Acronyms and domain-specific terms used throughout the codebase and these docs, defined once.

# Summary
Mostly: TiiBnTick-specific terms (FreelancerOrg, HubRelais), Kernel/RT-comops terms, and logistics/accounting domain terms (OHADA, VRP, ETA/Kalman).

# Details

| Term | Meaning |
|---|---|
| **Kernel** / **RT-comops** | The external Yowyob platform (`groupId yowyob.comops.api`) that TiiBnTick Core extends — owns base entities, auth, RBAC persistence, file storage. Read-only dependency, never modified here. |
| **TNT** | Prefix used throughout for TiiBnTick-owned types, to disambiguate from Kernel/`comops` equivalents (`TntRole` vs Kernel's `Role`). |
| **TenantId**, **Money** | Kernel-owned shared value types, imported (not redefined) by TiiBnTick modules. |
| **FreelancerOrg** / **FreelancerOrganization** | An independent delivery contractor entity that can have its own sub-deliverer fleet (owner + sub-deliverers), tracked in `tnt-organization-core`. |
| **HubRelais** / **Relay Hub** | A physical drop-off/pickup point in the delivery network (`tnt-geo-core`/`tnt-organization-core`). |
| **OHADA** | Organisation pour l'Harmonisation en Afrique du Droit des Affaires — the West/Central African accounting standard `tnt-accounting-core`'s chart of accounts implements. |
| **VRP** / **CVRP** | Vehicle Routing Problem / Capacitated VRP — the optimization problem `tnt-route-core`'s OR-Tools solver solves. |
| **ETA (Kalman)** | Estimated Time of Arrival, computed with a Kalman filter for smoothing noisy GPS data (`tnt-route-core`). |
| **WORM** | Write Once Read Many — the MinIO Object Lock retention policy intended for incident evidence (`tnt-incident-core`/`tnt-media-core`), not yet enabled in dev. |
| **Hexagonal architecture** | The ports-and-adapters pattern every module follows — see `architecture/packages.md`. |
| **Aggregate (DDD)** | A cluster of domain objects treated as one unit for data changes, with one root entity — see `domain/aggregates.md`. |
| **Bounded context (DDD)** | A boundary within which a domain model is consistent — roughly 1:1 with a module here, see `domain/bounded-contexts.md`. |
| **Liquibase changelog/changeset** | Liquibase's unit of migration (changelog = file, changeset = one tracked migration step) — see `infrastructure/database.md`. |
| **R2DBC** | Reactive Relational Database Connectivity — the non-blocking Postgres driver used for all runtime DB access (vs. blocking JDBC, used only by Liquibase). |
| **WebFlux** | Spring's reactive web stack — this entire app is WebFlux, not Spring MVC. |
| **`@RequirePermission`** | TiiBnTick's RBAC annotation — see `security/authorization.md`. |
| **`ReactivePermissionResolver`** | The pluggable LOCAL/REMOTE/HYBRID permission-resolution interface — see `security/permissions.md`. |
| **MoMo** | Mobile Money (MTN MoMo / Orange Money) — African mobile payment providers integrated in `tnt-billing-wallet`. |
| **SSE** | Server-Sent Events — used by `tnt-realtime-core` for live tracking instead of (or alongside) WebSocket. |
| **DSL (billing)** | The hand-written rule language in `tnt-billing-dsl` for expressing pricing/cost policies — not ANTLR-generated. |

# Links
- `architecture/overview.md`, `domain/bounded-contexts.md`, `security/permissions.md`

---
> **Comment maintenir ce document** : ajouter un terme dès qu'il apparaît dans le code sans être évident pour quelqu'un de nouveau sur le projet. Ordre alphabétique non requis — grouper par thème si plus lisible.
