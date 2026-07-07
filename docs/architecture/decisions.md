# Purpose
Lightweight ADR (Architecture Decision Record) log — why things are the way they are, so nobody "fixes" an intentional design choice by accident.

# Summary
Most consequential decisions: (1) Kernel is HTTP/Kafka-only, never a Spring bean dependency; (2) Liquibase with explicit `include:`, never `includeAll`; (3) reactive end-to-end, JDBC isolated to Liquibase; (4) `@RequirePermission` resolver is pluggable LOCAL/REMOTE/HYBRID so RBAC works before the Kernel ships a permission endpoint.

# Details

## ADR-001 — Kernel consumed only via HTTP/WebSocket + data-type imports
**Status**: Accepted (foundational, CLAUDE.md).
**Decision**: TiiBnTick Core never injects Kernel-internal Spring beans/interfaces directly (except explicitly-published SPI interfaces like `ReactivePermissionResolver`, `RoleRepository`). All Kernel interaction is HTTP (`kernelWebClient`) or Kafka, plus importing Kernel **data types** (`TenantId`, `Money`, etc.).
**Why**: Kernel is owned by a different team/repo (TSAFACK Savio) and evolves independently — coupling to its internal beans would break on every Kernel release.
**Consequence**: Kernel role-provisioning currently gets `404` (endpoint not implemented yet) — this is expected, not a bug. See `knowledge/known-issues.md`.

## ADR-002 — Explicit Liquibase `include:`, never `includeAll`
**Status**: Accepted, enforced 2026-06 after a multi-day debugging session.
**Decision**: Root master changelog (`tnt-core-master.yaml`) explicitly `include:`s one uniquely-named master changelog per module. No module changelog uses `includeAll`.
**Why**: `includeAll` scans the entire JVM classpath (`ClassLoader.getResources()`), not the current module's JAR — with ~25 module JARs simultaneously on `tnt-bootstrap`'s classpath, it silently picks up the wrong files or no-ops on name collisions. This caused ~8 previously-undetected migration bugs to ship silently.
**Consequence**: Adding a new module's migrations requires a manual `include:` line in `tnt-core-master.yaml` — easy to forget (see ADR-004 below, the `tnt-sync-core` incident).

## ADR-003 — Reactive end-to-end; Liquibase is the one blocking exception
**Status**: Accepted (foundational).
**Decision**: All runtime DB access is R2DBC (non-blocking). Liquibase uses a separate, blocking JDBC driver, scoped to schema migration only — enforced by `maven-enforcer-plugin` banning `org.postgresql:postgresql` at compile scope.
**Why**: Liquibase has no mature reactive driver; isolating it to `runtime` scope + migration-only usage keeps the app's hot path 100% non-blocking.

## ADR-004 — Pluggable permission resolution (LOCAL/REMOTE/HYBRID)
**Status**: Accepted 2026-06-29, implemented in `tnt-roles-core`.
**Decision**: `@RequirePermission`'s public API never changes. Behind it, `ReactivePermissionResolver` has three interchangeable implementations selected by `tnt.roles.permission.mode`: `LocalReactivePermissionResolver` (resolves from local `UserRoleAssignmentRepository`/`RoleRepository`/`TntRoleDefinitionRegistry`), `RemoteReactivePermissionResolver` (Kernel HTTP, forward-compatible), `HybridReactivePermissionResolver` (local-first, Kernel fallback). All wrapped by a Caffeine-backed `CachingReactivePermissionResolverDecorator`, with Kafka-driven cache invalidation (`tnt.roles.permission-changed` topic, currently unused by any producer — scaffold for future role-mutation flows).
**Why**: The user required RBAC to be **fully functional today**, without disabling AOP or using an always-allow resolver, even though the Kernel doesn't expose a permission-resolution REST endpoint yet.
**Consequence**: Default mode is `LOCAL`. Switching to `HYBRID`/`REMOTE` requires zero code changes — just `tnt.roles.permission.mode=HYBRID`.

## ADR-005 — Module master changelog filenames must be globally unique
**Status**: Accepted (consequence of ADR-002).
**Decision**: No module changelog is named `db.changelog-master.yaml`; each is `tnt-<module>-master.yaml`.
**Why**: With every module JAR on one classpath, a generic name is ambiguous to Liquibase's classpath resolver.

## ADR-006 — Lombok and springdoc-openapi pinned to Spring Boot 4 / Framework 7 / JDK 25-compatible versions
**Status**: Accepted 2026-06-30.
**Decision**: `lombok.version=1.18.46` (was 1.18.32 — broke javac on JDK 25 with `TypeTag :: UNKNOWN`), `springdoc.version=3.0.3` (was 2.5.0 — Boot-3-only, broke `/swagger-ui.html`).
**Why**: This repo runs ahead of most libraries' stable support matrix (Spring Boot 4.0.6 / Framework 7 / JDK 21-25). Pin versions explicitly rather than relying on transitive resolution; re-verify after every Spring Boot bump.
**Consequence**: `RT-comops-kernel-core` transitively pulls non-jakarta `swagger-annotations`/`swagger-models:2.2.22`, which conflicts with springdoc 3.x's jakarta variants — excluded explicitly in root `pom.xml` `dependencyManagement`, plus an explicit `swagger-annotations-jakarta` dependency added globally so modules using `@Operation`/`@Tag` compile correctly. See `knowledge/known-issues.md`.

## ADR-007 — `tnt-billing-dsl` and `tnt-accounting-core` use `infrastructure/` + `domain/port` instead of standard hexagonal
**Status**: Accepted (foundational, CLAUDE.md).
**Decision**: These two modules use `domain/port/{in,out}` for use-case/port interfaces and `infrastructure/{adapter,config,persistence}` for the outward-facing layer, instead of `application/port` + `adapter/`.
**Why**: Same dependency direction, just renamed for domains where the "domain service" (rule evaluator, double-entry bookkeeping) needs first-class status alongside the aggregate.

# Links
- `architecture/overview.md`, `architecture/packages.md`, `architecture/dependencies.md`
- `security/permissions.md` — ADR-004 in full detail
- `infrastructure/database.md` — ADR-002/003/005 in full detail
- `knowledge/known-issues.md` — incident write-ups behind ADR-001, 006

---
> **Comment maintenir ce document** : ajouter une nouvelle entrée ADR-NNN (numérotation séquentielle) à chaque décision architecturale significative — surtout celles qui pourraient sembler "bizarres" ou "à corriger" sans le contexte. Ne jamais supprimer une ADR, marquer `Status: Superseded by ADR-XXX` si remplacée.
