# Purpose
Incident log — real bugs found and fixed, with root cause and symptom, so the same class of bug is recognized instantly next time instead of re-debugged from scratch.

# Summary
8 incidents from the 2026-06 session: Liquibase `includeAll` classpath-scan bug (root cause of ~8 sub-bugs), Lombok/JDK25 incompatibility, springdoc/Spring-Boot-4 version mismatch, Kernel transitive swagger-jar conflict, `tnt_sync_session` unwired Flyway-named file, MinIO wrong-bucket bug, MinIO credential mismatch, two "tests pass but app doesn't start" traps.

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

# Links
- `infrastructure/database.md`, `development/coding-style.md`, `development/testing.md`
- `architecture/decisions.md` — ADR-002, 004, 005, 006
- `knowledge/faq.md`

---
> **Comment maintenir ce document** : ajouter une entrée numérotée à chaque bug non-trivial résolu, avec Symptom/Root cause/Fix. Ne jamais supprimer une entrée — même résolue depuis longtemps, elle reste utile comme pattern reconnaissable.
