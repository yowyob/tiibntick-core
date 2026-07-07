# Purpose
Changelog of significant work done, in plain language — so "didn't we already fix this?" has a fast answer.

# Summary
Three work phases visible in history: (1) Liquibase/build-stabilization cleanup across ~20 modules, (2) `@RequirePermission` hybrid resolver architecture, (3) Spring Boot 4 dependency-compatibility fixes (springdoc/Lombok/swagger) + two infra bugs (sync table, MinIO) + this documentation tree.

# Details

## 2026-07-01 — Single KernelBridge consolidation + 217-endpoint test campaign
- Consolidated all Kernel WebClient beans into a **single KernelBridge** (`KernelBridgeConfig` in `tnt-bootstrap`):
  - Added `X-Client-Id: tibntick-backend` header to all Kernel HTTP calls
  - Added real API key (`g79R7a7A8StQia6If-XmMSWj28T4FT9xHfVRsf5et_w`) as default in `application.yml`
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
