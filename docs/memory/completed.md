# Purpose
Changelog of significant work done, in plain language — so "didn't we already fix this?" has a fast answer.

# Summary
Four work phases visible in history: (1) Liquibase/build-stabilization cleanup across ~20 modules, (2) `@RequirePermission` hybrid resolver architecture, (3) Spring Boot 4 dependency-compatibility fixes (springdoc/Lombok/swagger) + two infra bugs (sync table, MinIO) + this documentation tree, (4) the 2026-07-08 Kernel-facade migration pass across all 10 mandated modules (ADR-008 through ADR-016), whose biggest finding was that every `Kernel*Adapter` in the codebase except `tnt-auth-core`'s was silently unable to read real data back from the Kernel at all (ADR-012).

# Details

## 2026-07-08 — Kernel-facade migration: all 10 mandated modules complete (ADR-008–016)
Ran the mandated "Core as Kernel facade/BFF" migration process (`docs/kernel-api/endpoints.md`+`schemas.md` vs. current module logic) across all 10 modules in the prescribed order: `tnt-media-core` → `tnt-resource-core` → `tnt-product-core` → `tnt-inventory-core` → `tnt-sales-core` → `tnt-accounting-core` → `tnt-actor-core` → `tnt-organization-core` → `tnt-tp-core` → `tnt-administration-core`. Full detail in `architecture/decisions.md` ADR-008 through ADR-016; summary:

- **The headline bug (ADR-012)**: verified live against `kernel-core.yowyob.com` that every Kernel response is wrapped `{success, data, message, errorCode, timestamp}`. Every `Kernel*Adapter` except `tnt-auth-core`'s deserialized the raw body directly into a flat DTO, skipping the envelope — combined with universal fail-open error handling, this meant these "existence check" adapters have *never* been able to read real data back, even on a successful 200 response (Jackson silently produced an all-null/default object). Fixed with a new shared `KernelEnvelope`/`KernelResponses` helper (`foundation/tnt-common-core`, `com.yowyob.tiibntick.common.kernel`) applied to 9 adapters across `tnt-resource-core`, `tnt-product-core`, `tnt-organization-core`, `tnt-tp-core`, `tnt-administration-core`, `tnt-roles-core`, `tnt-notify-core`, `tnt-sales-core`, `tnt-accounting-core`.
- **Also found and fixed**: missing `/api` prefix on 3 adapters (`KernelActorAdapter`, `KernelProductAdapter`, `KernelOrganizationAdapter` — the last had a stray `/v1/`); a dead method calling a nonexistent Kernel resource in `tnt-inventory-core` (deleted); DTO field-name mismatches (`id` vs `kernelXxxId`, an `isActive` boolean the Kernel doesn't model) fixed in `KernelSalesOrderDto`/`KernelInvoiceDto` where the real schema was already in hand.
- **`tnt-actor-core`** (zero Kernel integration going in) got a brand-new `IKernelActorPort`/`KernelActorAdapter`/`KernelActorDto`, wired fail-open into all 4 profile-creation services (Client/Freelancer/Deliverer/RelayOperator) — the mission's one genuine "build from scratch" deliverable. 3 new tests, all 42 module tests pass.
- **Real duplication found but deliberately NOT executed** (flagged as follow-ups, not silently dropped): `tnt-product-core`'s catalog fields plausibly duplicate the Kernel's `product-structure-controller` (ADR-010); `tnt-accounting-core`'s `Account`/`JournalEntry` persistence plausibly duplicates the Kernel's much more complete accounting/reporting engine (ADR-013). Both need a dedicated data-migration-aware session — not something to guess through in one pass, given production-data and (for accounting) financial-audit stakes.
- **Confirmed correctly local, no Kernel equivalent worth adopting**: `tnt-media-core` (MinIO storage, WORM legal retention, QR/PDF/signature rendering — ADR-008), `tnt-resource-core` (fleet/vehicle management vs. Kernel's generic IoT-shaped resource registry — ADR-009), `tnt-organization-core`/`tnt-tp-core` (their hard-blocking existence-check design was already correct — ADR-015), `tnt-administration-core`'s `TntRoleTemplateRegistry` (it's the source *pushed to* the Kernel, not sourced from it — ADR-016).
- Found one pre-existing, unrelated test/implementation drift in `tnt-tp-core` (`TntClientProfileServiceTest` expects `IllegalArgumentException`, service throws `ResponseStatusException`) — flagged in known-issues.md #14, not fixed (out of scope).

## 2026-07-01 — Single KernelBridge consolidation + 217-endpoint test campaign
- Consolidated all Kernel WebClient beans into a **single KernelBridge** (`KernelBridgeConfig` in `tnt-bootstrap`):
  - Added `X-Client-Id: tibntick-backend` header to all Kernel HTTP calls
  - Added real API key as default in `application.yml` (removed 2026-07-09 — Kernel devops now manages this credential out-of-band)
  - Added `kernelOrganizationWebClient` and `kernelTpWebClient` beans to `KernelBridgeConfig` (previously missing auth headers)
  - Added `tnt.kernel.client-id` property to `application.yml` and `YowyobKernelBridge`
  - Removed dead `@Bean kernelWebClient` definitions from `KernelWebClientConfig`, `TntAdministrationCoreConfiguration`, `ProductCoreAutoConfiguration`
- Fixed `KernelPublicKeyProvider.init()` and `TntJwtValidator.init()` to degrade gracefully when Kernel JWKS is unreachable (was throwing `IllegalStateException` at startup, crashing the app in local dev without Kernel network access)
- Fixed Postgres port conflict (local system postgres on 5432 → docker-compose now binds `5433:5432`; `application.yml` defaults updated)
- **Tested all 217 REST endpoints**: 217/217 PASS — no 404, no 500, no connection failures. All routes registered and auth correctly enforced (401 on secured endpoints, 200/302 on public ones, 503 on health with partial infra).

## 2026-06-30 — Documentation
- Created the full `docs/` "second brain" tree (this file included) — architecture, domain, api, infrastructure, security, development, knowledge, memory sections, plus top-level navigation docs.

## 2026-06-30 — Infra bug fixes (user-flagged: "tnt_sync_session missing", "MinIO signature mismatch")
- Wired `tnt-sync-core`'s migrations into the Liquibase changelog chain (was Flyway-named, never executed) — split the PL/pgSQL trigger into its own changeset to work around a `splitStatements` limitation.
- Fixed `application.yml` default-profile MinIO secret-key fallback (`minioadmin` → `minioadmin123`, matching docker-compose).
- Fixed `IncidentMediaStorageAdapter`'s wrong-bucket bug (archived from/to the same bucket instead of the tenant's staging bucket) — threaded `tenantId` through `IMediaStoragePort.archiveIncidentEvidence`.

## 2026-06-30 — Spring Boot 4 / Framework 7 compatibility chain (user-flagged: "swagger-ui.html spins forever")
- `springdoc-openapi` 2.5.0 → 3.0.3 (Boot 3-only → Boot 4-targeting).
- This broke compilation due to a Lombok/JDK25 incompatibility, surfaced independently — Lombok 1.18.32 → 1.18.46.
- This then surfaced a Kernel-transitive swagger-jar conflict (`swagger-annotations`/`swagger-models:2.2.22` non-jakarta vs. springdoc's jakarta 2.2.47) — excluded the old jars, added an explicit global `swagger-annotations-jakarta` dependency.
- Verified end-to-end: clean build, clean app start, `/swagger-ui.html` 200, `/v3/api-docs/00-all` generates a full 200KB spec with no errors.

## 2026-06-29 — `@RequirePermission` hybrid resolver architecture (user-specified in detail)
- Implemented `ReactivePermissionResolver` with `LocalReactivePermissionResolver`/`RemoteReactivePermissionResolver`/`HybridReactivePermissionResolver`, selected via `tnt.roles.permission.mode` (LOCAL/REMOTE/HYBRID).
- Added `PermissionCache` (Caffeine), `CachingReactivePermissionResolverDecorator`, `PermissionCacheInvalidationListener` (Kafka scaffold).
- Fixed a bogus `@Profile("r2dbc")` on 4 `tnt-actor-core` adapters that made them silently absent outside the test profile.
- Fixed a `ClassCastException` in `KernelRoleProvisioningAdapter` (`Set` cast to `List`).
- Verified: app reaches `✅ COMPLETED`, 30 modules active, in the default profile (not just test profile).

## (Earlier session) — Build stabilization
- Diagnosed and fixed the root cause of ~8 silently-broken Liquibase migrations: `includeAll`'s JVM-classpath-wide scan behavior, across ~20 modules — converted to explicit `include:` with globally-unique changelog filenames.
- Fixed a Lombok+MapStruct multi-round annotation-processing corruption bug (`lombok-mapstruct-binding` interop processor).
- Resolved a `maven-jar-plugin` stale-jar issue.
- Got `tnt-bootstrap`'s tests passing (`Tests run: 1, Failures: 0, Errors: 0`).

# Links
- `knowledge/known-issues.md` — root-cause detail for each fix above
- `memory/current-state.md` — what's true right now
- `architecture/decisions.md` — the ADRs these fixes produced

---
> **Comment maintenir ce document** : ajouter une nouvelle section datée à la fin de chaque session de travail significative, dans le même format (titre = date + résumé court, puis liste à puces). Ne jamais réécrire l'historique — seulement ajouter.
