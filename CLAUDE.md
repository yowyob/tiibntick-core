# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

TiiBnTick Core — the shared, non-runnable Maven module library for the TiiBnTick logistics/billing platform built on top of the Yowyob Kernel (RT-comops). Everything here is a library except `tnt-bootstrap`, which is the single `@SpringBootApplication` that assembles and runs the whole stack.

Stack: Java 21, Spring Boot 4.0.6 / Spring Framework 7 (WebFlux, fully reactive), R2DBC + PostgreSQL (PostGIS), Liquibase (JDBC, schema-only), Kafka, Redis (reactive), MinIO, OR-Tools (VRP/CVRP routing), Lombok + MapStruct.

## Build & test commands

This is a single multi-module Maven build (`pom.xml` at root, `tiibntick-core-parent`). Domain folders (`foundation/`, `identity/`, `logistics/`, `business/`, `billing/`) are plain directories for grouping — they are not Maven modules themselves; the root `pom.xml` lists each leaf module (e.g. `billing/tnt-billing-dsl`) directly.

```bash
# Full build (all modules), skip nothing
mvn clean install

# Build a single module and the modules it depends on
mvn -pl logistics/tnt-delivery-core -am install

# Run unit tests only for one module (surefire — *Test.java / *Tests.java)
mvn -pl billing/tnt-billing-dsl test

# Run a single test class / method
mvn -pl logistics/tnt-delivery-core test -Dtest=SomeServiceTest
mvn -pl logistics/tnt-delivery-core test -Dtest=SomeServiceTest#someMethod

# Integration tests (Testcontainers — *IntegrationTest.java / *IT.java), excluded from default `test`
mvn -pl <module> verify -Pintegration-tests

# Run the bootstrap app locally (needs infra below running first)
mvn -pl tnt-bootstrap spring-boot:run
```

Notes:
- `spring-boot-maven-plugin` has `skip=true` globally; only `tnt-bootstrap` is runnable.
- The `maven-enforcer-plugin` bans `commons-logging:commons-logging`, `log4j:log4j`, and a compile-scope `org.postgresql:postgresql` (JDBC must stay `runtime`-only since the app is reactive end-to-end; only Liquibase uses the blocking JDBC driver).
- JaCoCo runs on `verify`; coverage gate (`check`) is present but currently commented out.
- Maven profiles: `gitlab-ci` (auto-activates on `CI_JOB_TOKEN`), `release` (attaches sources/javadoc for GitLab Package Registry publishing), `integration-tests` (enables failsafe).

### Local infrastructure

```bash
cd tnt-bootstrap
docker compose up -d                # postgres (PostGIS), redis, kafka (KRaft), minio, elasticsearch, prometheus, grafana, zipkin
docker compose --profile app up -d  # also build & start the tiibntick-core app container
```

Default ports: app 8080, Postgres 5432, Redis 6379, Kafka 9092, MinIO 9000/9001, Prometheus 9090, Grafana 3100, Zipkin 9411.

## Architecture

### Layered module graph (build order matters — declared in this order in root `pom.xml`)

```
L0  foundation/yow-event-kernel, yow-i18n-kernel      — event bus, i18n (candidates for future migration into the Kernel)
L1  foundation/tnt-common-core, tnt-auth-core, tnt-roles-core, tnt-platform-gateway-core   — shared types, JWT security bridge, RBAC, platform-client gateway
L2  identity/   tnt-actor-core, tnt-organization-core, tnt-tp-core, tnt-administration-core
L3  logistics/  tnt-geo-core, tnt-route-core, tnt-delivery-core, tnt-dispute-core, tnt-incident-core,
                tnt-realtime-core, tnt-sync-core, tnt-notify-core, tnt-media-core
L4  business/   tnt-resource-core, tnt-product-core, tnt-inventory-core, tnt-sales-core, tnt-accounting-core
L5  billing/    tnt-billing-dsl, tnt-billing-pricing, tnt-billing-cost, tnt-billing-invoice,
                tnt-billing-wallet, tnt-billing-report, tnt-billing-templates
L6  tnt-bootstrap   — the only runnable module; wires everything together
```

Each module's `groupId:artifactId` is pinned in the root `<dependencyManagement>` at `${project.version}` — when adding a new module, register it both in `<modules>` and in `<dependencyManagement>`.

### The Yowyob Kernel (RT-comops) boundary

Modules under `groupId yowyob.comops.api` (artifacts like `RT-comops-common-core`, `RT-comops-auth-core`, `RT-comops-kernel-core`, etc.) are an **external, read-only dependency** owned by a different team/repo (managed by TSAFACK Savio). TiiBnTick Core extends and consumes these — never modify or vendor them, and don't expect to find their sources in this repo. They provide base entities, `TenantId`, `Money`, JWT/auth primitives, RBAC persistence, kernel events, and file storage that the `com.yowyob.tiibntick.core.*` modules build on top of.

`tnt-auth-core` and `tnt-roles-core` are described as "thin bridges": they define the TiiBnTick-facing vocabulary (`TntSecurityContext`, `TntRole`, `@RequirePermission`) but delegate actual crypto/persistence/caching to the Kernel.

`tnt-platform-gateway-core` is the entry point for platform *backends* (Agency, Go, Link, Market, Point Relais, ...) calling TiiBnTick Core — as opposed to `tnt-auth-core`, which is about human/service-account end users. It owns: the persistent, admin-managed Client-ID/API-Key system (`platform_clients`/`api_keys`/`client_permissions`/`api_key_rotation_history`/`client_audit_logs` tables — this module's own R2DBC + Liquibase, the first L1 module with a schema), the two-level `resource:action` scope model (gateway blocks `AUTH`/`SSO`/`ONBOARDING` today, extensible to curated business-module proxies), and the Kernel auth/OIDC/SSO proxy controllers (`/api/v1/auth/**`, `/api/v1/sso/**`) plus the admin API (`/api/v1/admin/platform-clients/**`, TNT_ADMIN-only). See `docs/auth/platform-client-management-design.md` for the full design. Agency onboarding orchestration (`/api/v1/onboarding/**`) is implemented in `tnt-administration-core`, but its security perimeter is defined in this module's `TntPlatformGatewaySecurityConfig`.

### Hexagonal/clean architecture per module

Nearly every module follows the same internal package shape under `com.yowyob.tiibntick.core.<module>`:

```
adapter/in/{web,kafka,messaging}     — inbound: REST controllers, Kafka consumers
adapter/out/{persistence,messaging}  — outbound: R2DBC repositories/entities/mappers, Kafka publishers
application/port/in                  — use-case interfaces (commands/queries)
application/port/out                 — outbound port interfaces (often prefixed `I...`, e.g. `IDisputeEventPublisher`)
application/service                  — use-case implementations
domain/model/{aggregate,entity,valueobject}
domain/event, domain/exception, domain/policy
config                               — module's `@Configuration` (Kafka beans, etc.), imported by tnt-bootstrap
```

`tnt-billing-dsl` (and a few `business/` modules) use a slightly different but equivalent split: `domain/port/{in,out}` for use-case/port interfaces and `infrastructure/{adapter,config,persistence}` for the outward-facing layer — same dependency direction, different naming.

Outbound port interfaces are commonly prefixed `I` (e.g. `ITntRoleProvisioningPort`, `IDisputeEventPublisher`, `IBlockchainProofPort`).

### How tnt-bootstrap assembles modules

`tnt-bootstrap` does not contain business logic. It only:
- `@Import`s each module's `@Configuration` class into `TntCoreConfig` (see `tnt-bootstrap/.../config/TntCoreConfig.java`).
- Provides cross-cutting shared beans modules can rely on without each redefining them: `tntKafkaTemplate`, `tntObjectMapper` (`@Primary`), plus `@ConditionalOnMissingBean` fallback beans for module-specific qualifiers (e.g. `deliveryKafkaProducer`) so the app still starts if a module JAR predates a refactor.
- Registers RBAC/settings/module metadata via `TntRoleRegistrar`, `TntSettingsRegistrar`, `TntExtensionRegistry`, `TntModuleRegistry`.
- Exposes custom actuator endpoints (`tnt-modules`, `tnt-kernel`) and health indicators per infra dependency (DB pyramid, Kafka, Redis, MinIO, OR-Tools, Kernel, roles).

When changing a module's Kafka/bean configuration, check whether `tnt-bootstrap`'s `TntKafkaConfig`/`TntCoreConfig` defines a fallback or qualifier that needs to stay in sync.

### Multi-tenancy & security

Every request flows through `TntSecurityContext` (from `tnt-auth-core`), populated from a Kernel-issued JWT (`iwm.security.jwt.*` config) and exposing tenant/org/agency/actor IDs reactively. `tnt.roles.*` config controls `@RequirePermission` AOP enforcement (`tnt-roles-core`'s `TntPermissionAspect`), permission cache TTL, and one-time RBAC provisioning of the 9 canonical `TntRole`s into the Kernel DB at startup. In tests, set `tnt.roles.aop-enabled=false` / `tnt.auth.allow-anonymous-context=true` (see the `test` profile in `application.yml`) rather than mocking the Kernel.

A second, parallel principal type exists for platform *backends* (not human users): `PlatformClientAuthenticationToken` (from `tnt-platform-gateway-core`), populated from `X-Client-Id`/`X-Api-Key` headers on `/api/v1/auth/**`/`/api/v1/sso/**`/`/api/v1/onboarding/**`/`/api/v1/platform/**` only (its own `@Order(10)` security chain, separate from the JWT catch-all at `@Order(20)`). Its scopes are checked via the shared `PermissionMatcher` (`tnt-common-core`) — never Spring's native `hasAuthority()`, which doesn't understand the `resource:*`/`*` wildcards this scope format uses. Don't confuse the two: a platform client authenticates the *calling backend*, not the end user making the request through it.

### Billing DSL

`tnt-billing-dsl` implements a hand-written rule language (not ANTLR-generated) for pricing/cost policies: `infrastructure/dsl/{lexer,parser,ast,evaluator,executor}`. Limits (`max-nesting-level-simplified`, `max-rules-per-policy-*`) are configured under `tnt.billing.dsl.*`.

### Conventions

- Java package root is `com.yowyob.tiibntick.core.<module>` for business modules, `com.yowyob.kernel.<module>` for the two foundation kernel modules (`yow-event-kernel`, `yow-i18n-kernel`), and `com.yowyob.tiibntick.bootstrap` for the app.
- Most module-level beans/classes are prefixed `Tnt` (e.g. `TntKafkaConfig`, `TntRoleService`) to disambiguate from Kernel/`comops` types of the same concept.
- Commit messages follow Conventional Commits style: `feat:`, `fix:`, `refactor:`, `chore(deps):`, optionally scoped (`feat(dispute): ...`).
- Authors/ownership by layer (see root `pom.xml` `<developers>`): MANFOUO Braun — L0 event kernel, L3 logistics, L5 billing, L6 bootstrap; PAFE Dilane — L0 i18n, L2 organization, L3 logistics, L4 accounting, L5 billing; FRANCOIS — L2 identity (tp/administration), L4 business.
