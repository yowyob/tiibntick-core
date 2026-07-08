# Purpose
Incident log — real bugs found and fixed, with root cause and symptom, so the same class of bug is recognized instantly next time instead of re-debugged from scratch.

# Summary
9 incidents from the 2026-06 session (Liquibase `includeAll` classpath-scan bug — root cause of ~8 sub-bugs, Lombok/JDK25 incompatibility, springdoc/Spring-Boot-4 version mismatch, Kernel transitive swagger-jar conflict, `tnt_sync_session` unwired Flyway-named file, MinIO wrong-bucket bug, MinIO credential mismatch, two "tests pass but app doesn't start" traps), plus five from the 2026-07-08 Kernel-facade migration review — the big one (#12) being that every `Kernel*Adapter` except `tnt-auth-core`'s skipped unwrapping the Kernel's response envelope, so none could ever read real data back even on success.

# Details

## 1. Liquibase `includeAll` scans the whole classpath, not the current module
**Symptom**: migrations silently no-op or apply the wrong module's files.
**Root cause**: `includeAll` does `ClassLoader.getResources()` — JVM-classpath-wide, not JAR-scoped, even with `relativeToChangelogFile: true`. With ~25 module JARs simultaneously on `tnt-bootstrap`'s classpath, this is fundamentally broken at scale.
**Fix**: explicit `include:` per module, globally-unique changelog filenames. See `infrastructure/database.md`, `architecture/decisions.md` ADR-002/005.
**Sub-bugs this uncovered** (previously silently broken, never executed): YAML flow-mapping comma bug in `NUMERIC(15,2)` (needed quoting), comment-directive collision in formatted-SQL files, missing `splitStatements: true` on multi-statement raw SQL, PostGIS `CREATE INDEX ... USING GIST (expr)` needing double parens `((expr))`, wrong table/schema/column names in 3 modules, an orphaned changeset targeting a non-existent table.

## 2. `splitStatements: true` breaks on PL/pgSQL `$$...$$` function bodies
**Symptom**: `Unterminated dollar quote` / `PSQLException` on a Liquibase changeset containing a trigger function.
**Fix**: give the function/trigger its own changeset with `splitStatements: false`. See `logistics/tnt-sync-core/.../changes/002_create_entity_version_trigger.sql` for the canonical split.

## 3. `tnt_sync_session` table missing at runtime
**Symptom**: `relation "tnt_sync_session" does not exist` from `SyncMaintenanceScheduler`.
**Root cause**: the migration file existed (`db/migration/V1__create_sync_tables.sql`, Flyway naming convention) but this repo runs **Liquibase exclusively** — Flyway isn't even on the classpath. The file was dead weight, never executed, since `tnt-sync-core` was never given a master changelog wired into `tnt-core-master.yaml`.
**Fix**: moved into `db/changelog/changes/`, created `tnt-sync-master.yaml`, wired into root master. Fixed 2026-06-30.

## 4. Lombok 1.18.32 incompatible with JDK 25
**Symptom**: `java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` on every compile — even of trivial modules.
**Root cause**: Lombok patches javac internals via reflection; older Lombok versions don't know about newer javac internal API shapes.
**Fix**: bumped to `1.18.46`. If JDK is bumped again, check Lombok compatibility first before assuming a code bug.

## 5. Lombok + MapStruct annotation-processor ordering corruption
**Symptom**: a MapStruct-generated class silently missing its `implements` clause — compiles, but type-checks fail downstream in confusing ways.
**Root cause**: multi-round javac annotation processing between Lombok and MapStruct without the official interop processor.
**Fix**: added `org.projectlombok:lombok-mapstruct-binding:0.2.0` to `annotationProcessorPaths`, positioned between lombok and mapstruct-processor.

## 6. `springdoc-openapi` 2.5.0 only supports Spring Boot 3 / Framework 6
**Symptom**: `/swagger-ui.html` hangs forever — actually throwing `NoSuchMethodError: UriComponentsBuilder.fromHttpRequest(HttpRequest)` in a loop the browser sees as an infinite spinner.
**Fix**: bumped to springdoc `3.0.3` (the Boot-4-targeting line). See ADR-006.

## 7. Kernel's transitive non-jakarta swagger jars conflict with springdoc 3.x
**Symptom**: after fixing #6, `/v3/api-docs/*` throws `NoSuchMethodError: JsonSchema.typesItem(String)`.
**Root cause**: `RT-comops-kernel-core` pulls in `swagger-annotations`/`swagger-models:2.2.22` (non-jakarta) directly; these share the `io.swagger.v3.oas.models` package with the `*-jakarta:2.2.47` artifacts springdoc 3.x needs — the older classes shadow the newer ones on the classpath.
**Fix**: excluded the old jars from `RT-comops-kernel-core` in root `pom.xml` `dependencyManagement`, added an explicit global `swagger-annotations-jakarta` dependency so modules using `@Operation`/`@Tag` directly still compile.

## 8. MinIO `SignatureDoesNotMatch` on `IncidentMediaStorageAdapter` startup
**Symptom**: `The request signature we calculated does not match the signature you provided.`
**Root cause**: `application.yml`'s **default-profile** fallback `secret-key` was `minioadmin`, but docker-compose's real MinIO root password is `minioadmin123` — the test profile had already been corrected in an earlier pass, the default profile was missed.
**Fix**: corrected the default fallback.
**Separate, also-real bug found alongside it**: `IncidentMediaStorageAdapter` copied evidence files *from and to the same bucket* (`tnt-incident-evidences`) instead of sourcing from the tenant's own bucket (`tnt-{tenantId}`) — the code even had a comment admitting it ("In production: tenant ID should be passed through"). Fixed by threading `tenantId` through `IMediaStoragePort.archiveIncidentEvidence(tenantId, incidentId)`.

## 9. Two "tests pass, app doesn't start" traps
**Symptom**: `mvn test`/`mvn install` green, but `mvn spring-boot:run` throws `NoSuchBeanDefinitionException`.
**Root cause both times**: the `test` Spring profile sets `tnt.roles.aop-enabled=false` (RBAC never exercised in tests) and, separately, a stray `@Profile("r2dbc")` on 4 real R2DBC adapters in `tnt-actor-core` that only happened to be active because `@ActiveProfiles({"test","r2dbc"})` activated it as a side effect of test setup — meaning those adapters were silently absent in dev/staging/prod.
**Fix**: removed the bogus `@Profile`, implemented the full LOCAL/REMOTE/HYBRID resolver (ADR-004) so the bean is always present when `aop-enabled=true`.
**Lesson**: see `development/testing.md` — always smoke-test with `spring-boot:run` after security/persistence-adjacent changes.

## 10. `KernelActorAdapter.findActorById` called a non-existent Kernel path
**Symptom**: freelancer vehicle/equipment registration's actor-existence check silently always resolved to "not found" — the fail-open design (404/error → empty) masked this instead of surfacing it.
**Root cause**: `GET /actors/{id}` was missing the `/api` prefix every other Kernel controller uses. Additionally, as of the 2026-07-08 Kernel OpenAPI spec, `actor-controller` doesn't document *any* single-actor-by-id GET endpoint (only `POST /api/actors`, `GET/PUT /api/actors/me`, `PUT /api/actors/{actorId}`) — so even the corrected path may 404 until the Kernel adds one.
**Fix**: corrected the path to `/api/actors/{id}` (2026-07-08, `tnt-resource-core` Kernel-facade review, see ADR-009). The fail-open behavior is intentionally kept — do not make this call hard-blocking until the Kernel confirms the endpoint exists.

## 10b. Missing `/api` prefix is a systemic bug across several `Kernel*Adapter` classes
**Symptom**: same as #10 — fail-open existence checks silently always resolve to "not found", since every Kernel controller other than a handful of foundational ones (`tnt-roles-core`, `tnt-administration-core`'s permission/role adapters, `tnt-organization-core`, `tnt-tp-core`, `tnt-notify-core`) is mounted under `/api/...`.
**Root cause**: these adapters were written against a base `kernelWebClient` and each hand-rolled its own resource path, several without the `/api` prefix the rest of the Kernel's REST surface uses.
**Confirmed-broken and fixed 2026-07-08**: `KernelActorAdapter` (`tnt-resource-core`, `/actors/{id}` → `/api/actors/{id}`, see ADR-009), `KernelProductAdapter` (`tnt-product-core`, `/products` → `/api/products`, see ADR-010).
**Confirmed-broken, not yet fixed** (pending each module's own Kernel-facade review pass): `KernelSalesOrderAdapter` (`tnt-sales-core`, `/sales/orders...` → `/api/sales/orders...`), `KernelAccountingAdapter` (`tnt-accounting-core`, `/accounting/invoices...` → `/api/accounting/invoices...`; its `/accounting/journal-entries/{id}` call has no matching Kernel resource at all — closest is `GET /api/accounting/operations/{operationId}`, needs its own review, not just a prefix fix), `KernelInventoryAdapter` (`tnt-inventory-core`, `/inventory/stock-entries` — Kernel's inventory API has no "stock-entries" resource at all, only `movements`/`sessions`/`transfers`/`transformations`; needs its own review, not just a prefix fix).
**Already correct** (no fix needed): `KernelOrganizationAdapter`, `KernelThirdPartyAdapter`, `KernelNotificationClient`, `KernelPermissionAdapter`, `KernelRoleAdapter`, `KernelRoleProvisioningAdapter` — all already use `/api/...` paths.

## 11. `KernelInventoryPort.findByKernelStockEntryId` was dead code calling a resource the Kernel doesn't have
**Symptom**: none observed at runtime (method had zero callers) — found during the `tnt-inventory-core` Kernel-facade review.
**Root cause**: assumed the Kernel persists an individually-addressable "stock entry" with a stable id; the Kernel actually computes stock as a live balance over a movement ledger (`inventory-movement-controller`), with no such resource.
**Fix**: deleted the method from `KernelInventoryPort` and `KernelInventoryAdapter` (2026-07-08, see ADR-011). The still-used sibling `findByProductAndWarehouse` has the same wrong-resource problem but is deliberately left as-is (still fail-open no-op) pending a real `warehouseId`→Kernel-`agencyId` resolution — see ADR-011 for the follow-up plan.

## 12. Every `Kernel*Adapter` skipped the Kernel's response envelope — none could ever read real data back
**Symptom**: same invisible-failure pattern as #10/#11, but broader — even a `Kernel*Adapter` with a perfectly correct URL and successful 200 response could never have populated its DTO with real values.
**Root cause**: confirmed live against `kernel-core.yowyob.com` — every Kernel response is `{success, data, message, errorCode, timestamp}`. Every adapter except `tnt-auth-core`'s `KernelAuthGatewayAdapter` (which already used the equivalent `KernelApiEnvelope`) deserialized the raw body straight into a flat DTO, silently getting an all-null/default object back instead of an error.
**Fix**: added `KernelEnvelope<T>`/`KernelResponses` (`foundation/tnt-common-core`, `com.yowyob.tiibntick.common.kernel`) as the one shared unwrap+fail-open helper, applied across `tnt-resource-core`, `tnt-product-core`, `tnt-organization-core`, `tnt-tp-core`, `tnt-administration-core` (permission+role adapters), `tnt-roles-core`, `tnt-notify-core`, `tnt-sales-core`, and `tnt-accounting-core`'s `findInvoiceById`. See ADR-012 for the full list and what was deliberately left unfixed (calls to non-existent/wrong-shaped resources, where unwrapping the envelope wouldn't have fixed anything).

## 12b. Several `KernelXxxDto`s also don't match the Kernel's real field names/shape — not exhaustively fixed
**Symptom**: even with #12 fixed, a `KernelXxxDto` field can still silently stay null/default if its name doesn't match the Kernel's real JSON key.
**Root cause**: these DTOs were written without checking the real `docs/kernel-api/schemas.md` entry — common mismatches found: `id` (Kernel) vs a `kernelXxxId`-style name (TNT), `customerThirdPartyId` (Kernel) vs `clientThirdPartyId` (TNT), and an `isActive`/`active` boolean TNT expects that the Kernel doesn't model at all (it uses a `status` string instead).
**Fixed** (2026-07-08, schema was already in hand): `KernelSalesOrderDto`, `KernelInvoiceDto` — via `@JsonProperty` aliases, see ADR-012.
**Not yet verified**: `KernelActorDto`, `KernelOrganizationDto`, `KernelThirdPartyDto`, `KernelProductDto`, `KernelPermissionDto`, `KernelRoleDto`, `KernelStockEntryDto` (partially moot, see #11). Before trusting any `existsAndActive`/`findBy...Id` check beyond "did the Kernel respond," cross-check the DTO's fields against its real schema entry.

## 13. `KernelAccountingAdapter`: one dead-but-fixable method, one dead-and-unfixable, one with an identifier gap
**Symptom**: `findJournalEntryById` (unused) and `findInvoiceByReferenceId` (used, always resolves empty) never worked.
**Root cause**: `findJournalEntryById` targets a Kernel concept (double-entry journal entry, separate debit/credit) that doesn't exist — the closest resource, `AccountingOperationView`, models a single-amount operation instead. `findInvoiceByReferenceId` calls a real resource (`GET /api/accounting/invoices`) but with unsupported query params (`tenantId`/`referenceId` instead of the Kernel's only supported filter, `organizationId`) — same "no tenantId→organizationId resolution" gap as #11.
**Fix**: fixed `findInvoiceById`'s path+envelope (real resource, unused today, kept for future use) and `KernelInvoiceDto`'s field aliases. Left `findJournalEntryById` and `findInvoiceByReferenceId`'s query broken and documented in ADR-013 rather than guessed.

## 14. `TntClientProfileServiceTest` (tnt-tp-core) expects the wrong exception types — pre-existing, unrelated to the Kernel-facade work
**Symptom**: `register_shouldFail_whenKernelTpNotFound`, `register_shouldFail_whenKernelPortReturnsEmptyMono`, `register_shouldFail_whenProfileAlreadyExists` all fail.
**Root cause**: the test expects `IllegalArgumentException`/`IllegalStateException`; `TntClientProfileService.register()` actually throws `ResponseStatusException(NOT_FOUND/CONFLICT)` — an application-service layer throwing a web-layer exception type, and test/implementation have drifted apart. Confirmed unrelated to ADR-012/015: the test mocks `KernelThirdPartyPort` directly, never exercising `KernelThirdPartyAdapter`.
**Fix**: not applied — out of scope for the Kernel-facade migration; flagged here so it isn't mistaken for a regression from this work.

# Links
- `infrastructure/database.md`, `development/coding-style.md`, `development/testing.md`
- `architecture/decisions.md` — ADR-002, 004, 005, 006, 009
- `knowledge/faq.md`

---
> **Comment maintenir ce document** : ajouter une entrée numérotée à chaque bug non-trivial résolu, avec Symptom/Root cause/Fix. Ne jamais supprimer une entrée — même résolue depuis longtemps, elle reste utile comme pattern reconnaissable.
