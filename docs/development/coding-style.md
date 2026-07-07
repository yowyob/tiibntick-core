# Purpose
Language/framework-level style: Lombok, MapStruct, records, reactive idioms, what the linters/build enforce.

# Summary
Java 21, Lombok 1.18.46 + MapStruct 1.5.5.Final via `lombok-mapstruct-binding`, records preferred for immutable value types, reactive (`Mono`/`Flux`) everywhere. `maven-enforcer-plugin` bans `commons-logging`, `log4j`, compile-scope `org.postgresql:postgresql`.

# Details

## Lombok + MapStruct
Both are annotation processors — order matters in `maven-compiler-plugin`'s `annotationProcessorPaths`: Lombok → `lombok-mapstruct-binding:0.2.0` → mapstruct-processor. Missing the binding jar causes silent multi-round javac corruption (a class compiles but is missing an `implements` clause) — see `knowledge/known-issues.md` if you ever see a MapStruct-generated class behaving like it doesn't implement its own interface.

⚠️ **Lombok version is pinned, not "latest"** — 1.18.32 doesn't support JDK 25's javac internals (`TypeTag :: UNKNOWN` `ExceptionInInitializerError`). Currently `1.18.46`. If the JDK is bumped again, re-verify Lombok compiles cleanly before assuming it's a code bug.

## Records vs. classes
Prefer Java `record` for immutable value objects and DTOs (`KernelCreateRoleRequest` in `KernelRoleProvisioningAdapter` is a good example: an internal record DTO that avoids depending on the Kernel JAR's application layer). Aggregates/entities with mutable lifecycle state stay as classes (often with Lombok `@Getter`/builder).

## Reactive idioms
- `Mono<Void>` for fire-and-forget operations, not `Mono<Boolean>` unless the boolean is meaningful to the caller.
- `switchIfEmpty(Mono.defer(...))` for fallback chains (see `LocalReactivePermissionResolver`/`HybridReactivePermissionResolver` for the canonical pattern).
- `.subscribeOn(Schedulers.boundedElastic())` for any blocking call wrapped in a reactive façade (e.g. MinIO SDK calls, which are synchronous under the hood).
- `onErrorResume` for graceful degradation (e.g. `RemoteReactivePermissionResolver` degrading to empty permissions on Kernel unreachability) vs. `onErrorMap` for wrapping into a domain exception.

## What the build enforces (don't fight these)
| Rule | Enforced by |
|---|---|
| No `commons-logging`/`log4j` on classpath | `maven-enforcer-plugin` ban rule |
| No compile-scope `org.postgresql:postgresql` (must be `runtime`) | `maven-enforcer-plugin` ban rule — keeps the app reactive end-to-end |
| Java 21 release target | `maven.compiler.release=21` |
| `spring-boot-maven-plugin` skipped everywhere except `tnt-bootstrap` | root pom default `skip=true`, overridden per-module |

## SQL/Liquibase style
See `infrastructure/database.md` for the full Liquibase conventions (explicit `include:`, globally-unique filenames, `splitStatements` gotcha with PL/pgSQL).

# Links
- `infrastructure/database.md` — Liquibase-specific style
- `knowledge/known-issues.md` — Lombok/MapStruct corruption incident, Lombok/JDK25 incident
- `development/conventions.md`

---
> **Comment maintenir ce document** : si une nouvelle version de Lombok/MapStruct/Spring Boot casse silencieusement quelque chose, documenter ici avec le symptôme exact (comme pour `TypeTag :: UNKNOWN`) — ça fait gagner des heures la prochaine fois.
