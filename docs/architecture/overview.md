# Purpose
30-second mental model of TiiBnTick Core: what it is, how it's built, how it's deployed.

# Summary
- **What**: shared library platform for a logistics + billing system (TiiBnTick), built on top of an external "Yowyob Kernel" (RT-comops) which owns auth, base entities, RBAC persistence, file storage.
- **How**: 31 Maven modules, hexagonal architecture per module, fully reactive (WebFlux + R2DBC), event-driven via Kafka, multi-tenant.
- **Runs as**: ONE Spring Boot app (`tnt-bootstrap`) — everything else is a library JAR.

# Details

## The one-sentence pitch
A reactive, multi-tenant, hexagonal-architecture logistics platform (delivery, dispatch, incidents, disputes, real-time tracking) with an integrated billing/invoicing/wallet engine, assembled from 30 independently-versioned library modules into a single deployable Spring Boot application.

## Layered build (L0→L6) — see `architecture/modules.md` for the full table
```mermaid
flowchart TB
    L0["L0 — foundation kernels<br/>(event bus, i18n)"] --> L1["L1 — common, auth, roles"]
    L1 --> L2["L2 — identity<br/>(actor, organization, tp, administration)"]
    L2 --> L3["L3 — logistics<br/>(geo, route, delivery, dispute, incident,<br/>realtime, sync, notify, media)"]
    L3 --> L4["L4 — business<br/>(resource, product, inventory, sales, accounting)"]
    L4 --> L5["L5 — billing<br/>(dsl, pricing, cost, invoice, wallet, report, templates)"]
    L5 --> L6["L6 — tnt-bootstrap<br/>(the only runnable module)"]
```
Each layer only depends on layers above it — `pom.xml` `<modules>` order **is** the build/dependency order.

## The Yowyob Kernel boundary
Everything under groupId `yowyob.comops.api` (`RT-comops-*`) is an **external, read-only** dependency from a different team/repo. TiiBnTick Core consumes it for: base entities, `TenantId`, `Money`, JWT/auth primitives, RBAC persistence, kernel domain events, file storage SPI. **Never modify, vendor, or expect to find Kernel sources in this repo.**

## Request lifecycle (typical)
```mermaid
sequenceDiagram
    participant Client
    participant WebFlux as Controller (adapter/in/web)
    participant Aspect as TntPermissionAspect
    participant Service as Application Service
    participant Port as Outbound Port
    participant DB as R2DBC/Postgres

    Client->>WebFlux: HTTP request + Bearer JWT
    WebFlux->>Aspect: @RequirePermission intercepts
    Aspect->>Aspect: resolve permissions (JWT fast-path or ReactivePermissionResolver)
    Aspect-->>WebFlux: allow / 403
    WebFlux->>Service: use-case call (Mono/Flux)
    Service->>Port: outbound port (repository/publisher)
    Port->>DB: R2DBC query
    DB-->>Port: result
    Port-->>Service: domain object
    Service-->>WebFlux: DTO
    WebFlux-->>Client: JSON response
```

## Why this matters for future work
- Adding a feature almost always means: 1 new use-case method, 1 new port (if new persistence/external need), 1 new adapter, wired in the module's `@Configuration` — see `development/conventions.md`.
- Cross-module calls go through **ports**, never direct class imports across module boundaries (except shared `tnt-common-core` types).
- Everything reactive — `Mono`/`Flux` end-to-end, no blocking calls except Liquibase migrations (JDBC, isolated).

# Links
- `architecture/modules.md` — full module table
- `architecture/dependencies.md` — Mermaid dependency graph
- `architecture/packages.md` — hexagonal package layout
- `security/authorization.md` — `@RequirePermission` flow in detail
- `_quick-start.md` — how to run this locally

---
> **Comment maintenir ce document** : ce document doit rester stable — ne le modifier que si l'architecture globale change (nouvelle couche, changement de paradigme reactive→non-reactive, etc.). Les détails par module vont dans `modules.md`, pas ici.
