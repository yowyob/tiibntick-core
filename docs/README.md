# TiiBnTick Core — Documentation ("Second Brain")

This is the permanent, external memory for this repository — written so a future session (Claude or human) can understand the project in seconds instead of reading thousands of Java files.

## What this project is
TiiBnTick Core is the shared, non-runnable Maven module library for the TiiBnTick logistics/billing platform, built on top of the external **Yowyob Kernel** (RT-comops). 31 modules, hexagonal architecture, fully reactive (Java 21, Spring Boot 4.0.6/Framework 7, WebFlux + R2DBC). `tnt-bootstrap` is the only runnable module — everything else is a library JAR it assembles.

→ Start with `_quick-start.md` to run it, `architecture/overview.md` to understand it.

## Microservices / Modules
31 modules across 6 layers (L0 foundation → L6 bootstrap) — full table in `architecture/modules.md`. Quick summary:
- **Identity** (tnt-actor-core, tnt-organization-core, tnt-tp-core, tnt-administration-core) — who's who, RBAC
- **Logistics** (tnt-geo-core, tnt-route-core, tnt-delivery-core, tnt-dispute-core, tnt-incident-core, tnt-realtime-core, tnt-sync-core, tnt-notify-core, tnt-media-core) — the delivery platform itself
- **Business** (tnt-resource-core, tnt-product-core, tnt-inventory-core, tnt-sales-core, tnt-accounting-core) — assets, catalog, ledger
- **Billing** (tnt-billing-dsl/pricing/cost/invoice/wallet/report/templates) — pricing engine, invoicing, payments

## Technologies
Java 21 · Spring Boot 4.0.6 / Framework 7 · WebFlux (reactive end-to-end) · R2DBC + PostgreSQL/PostGIS · Liquibase (migrations) · Kafka · Redis · MinIO · Elasticsearch (present, unused) · OR-Tools (VRP) · Lombok + MapStruct · Caffeine (permission cache) · springdoc-openapi 3.x.

## Conventions
See `development/conventions.md` and `development/coding-style.md`. Headline rules: Kernel consumed via HTTP/Kafka/data-types only (never its Spring beans), reactive everywhere except Liquibase, `Tnt`-prefixed class names, `I`-prefixed outbound ports.

## Important entry points
| What | Where |
|---|---|
| App entry point | `tnt-bootstrap/.../TiiBnTickApplication.java` |
| Module assembly | `tnt-bootstrap/.../config/TntCoreConfig.java` |
| Root Liquibase changelog | `tnt-bootstrap/.../db/changelog/tnt-core-master.yaml` |
| Local dev infra | `tnt-bootstrap/docker-compose.yml` |
| API docs (live) | `http://localhost:8080/swagger-ui.html` |
| RBAC entry point | `@RequirePermission` annotation, `foundation/tnt-roles-core/` |

## Full index
| Section | Contents |
|---|---|
| [`architecture/`](architecture/overview.md) | Module map, packages, dependencies, ADRs, project tree |
| [`domain/`](domain/bounded-contexts.md) | DDD model: bounded contexts, aggregates, entities, VOs, events, workflows |
| [`api/`](api/rest.md) | REST endpoints, security per endpoint, error handling |
| [`kernel-api/`](kernel-api/README.md) | External Kernel's own HTTP endpoints/schemas (offline mirror of its Swagger) |
| [`infrastructure/`](infrastructure/database.md) | DB/Liquibase, Kafka, Redis, Elasticsearch, Docker, monitoring |
| [`security/`](security/authentication.md) | JWT, RBAC, permissions, roles |
| [`development/`](development/conventions.md) | Conventions, coding style, testing, roadmap |
| [`knowledge/`](knowledge/project-map.md) | Glossary, FAQ, known issues, **project-map (file finder)** |
| [`memory/`](memory/current-state.md) | Living state: current status, todo, problems, completed work, assumptions |

Also see `_quick-start.md`, `_cheat-sheet.md`, `_navigation.md` for fast orientation.

---
> **Comment maintenir ce document** : mettre à jour la liste des modules/technologies si elle change. Ce fichier doit rester un résumé — le détail vit dans les sous-dossiers liés.
