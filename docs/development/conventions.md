# Purpose
Project-wide conventions that aren't enforced by the compiler — things you should follow even though nothing breaks if you don't.

# Summary
Naming (`Tnt` prefix, `I` prefix for outbound ports), package shape (`architecture/packages.md`), Conventional Commits, multi-tenancy header patterns (`api/rest.md`), Kernel boundary rule (`architecture/decisions.md` ADR-001).

# Details

## Naming
- Module-level beans/classes prefixed `Tnt` (`TntKafkaConfig`, `TntRoleService`) to disambiguate from Kernel/`comops` types of the same concept.
- Outbound port interfaces prefixed `I` (`ITntRoleProvisioningPort`, `IDisputeEventPublisher`, `IBlockchainProofPort`) — inbound (use-case) ports typically aren't prefixed.
- Java package root: `com.yowyob.tiibntick.core.<module>` (business modules), `com.yowyob.kernel.<module>` (the 2 L0 foundation modules), `com.yowyob.tiibntick.bootstrap` (the app).

## Commit messages
Conventional Commits, optionally scoped: `feat:`, `fix:`, `refactor:`, `chore(deps):`, `feat(dispute): ...`.

## The Kernel boundary (re-stated — see `architecture/decisions.md` ADR-001 for the full rationale)
Never inject a Kernel-internal Spring bean directly. Consume the Kernel via: (1) HTTP through `kernelWebClient`, (2) Kafka, (3) importing Kernel **data types** (`TenantId`, `Money`, etc.), (4) explicitly-published Kernel SPI interfaces (`ReactivePermissionResolver`, `RoleRepository`) that are designed to be implemented by either side.

## Multi-tenancy — pick a pattern deliberately
Three coexist (`api/rest.md` has the full table): path variable, `X-Tenant-Id` header, JWT-derived (`@CurrentUser`). **Prefer JWT-derived for new endpoints** — no client-supplied tenant ID to validate, and it's what `tnt-actor-core`/`tnt-administration-core` (the most recently-touched modules) use.

## Reactive discipline
Everything is `Mono`/`Flux` end-to-end. The one sanctioned exception is Liquibase (blocking JDBC, migration-only, isolated by the enforcer plugin — `infrastructure/database.md`). If you find yourself calling `.block()` outside a test, that's a code smell worth flagging, not a pattern to copy.

## Adding a feature — the usual shape
1. New use-case method on an existing (or new) inbound port (`application/port/in/` or `domain/port/in/`).
2. If it needs new persistence/external access: new outbound port (`application/port/out/`, `I`-prefixed) + adapter implementing it.
3. Wire the adapter as a `@Bean` in the module's `@Configuration` class (often `@ConditionalOnMissingBean` so other modules/tests can override).
4. If it's a new DB table: Liquibase changelog under `db/changelog/changes/`, wired into the module's own `tnt-<module>-master.yaml`, which must already be `include:`d in `tnt-core-master.yaml` (`infrastructure/database.md` — easy step to forget, see `knowledge/known-issues.md`).
5. If it mutates state another module syncs on: also write an `EntityVersionRecord` (`domain/workflows.md` — offline sync convention, not compiler-enforced).
6. If it needs a permission check: `@RequirePermission(resource, action)` using an existing `TntPermission` constant, or add a new one (`security/permissions.md`).

## Dependency versions — verify against the actual support matrix
This repo runs ahead of most libraries' stable Spring Boot 4/Framework 7/JDK 21-25 support (ADR-006). Before bumping a dependency, check whether the new major version actually targets this stack — don't assume the latest release is compatible.

# Links
- `architecture/packages.md` — package shape
- `architecture/decisions.md` — ADR log (the "why" behind these conventions)
- `development/coding-style.md`
- `development/testing.md`

---
> **Comment maintenir ce document** : ajouter une convention ici seulement si elle n'est pas déjà couverte ailleurs (sinon, lier plutôt que dupliquer). Si une convention listée ici devient obsolète (ex: tous les modules migrent vers un seul pattern multi-tenant), la mettre à jour partout où elle apparaît (`api/rest.md` inclus).
