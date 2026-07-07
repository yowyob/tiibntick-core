# Purpose
How schema migrations work in this repo: PostgreSQL + PostGIS, R2DBC for runtime access, **Liquibase** (JDBC, blocking, schema-only) for migrations. This is the most failure-prone part of the build — read this before touching any `db/changelog/` file.

# Summary
- Runtime DB access is 100% reactive (R2DBC). Liquibase uses a **separate blocking JDBC driver**, scoped to migrations only — the enforcer plugin bans `org.postgresql:postgresql` at compile scope to guarantee this split.
- One root master changelog (`tnt-bootstrap/src/main/resources/db/changelog/tnt-core-master.yaml`) explicitly `include:`s one `<module>-master.yaml` per module — **never `includeAll`** (see gotcha below).
- Every module-level changelog filename must be **globally unique** — generic names like `db.changelog-master.yaml` collide because all module JARs share one classpath at runtime.

# Details

## Why explicit `include:`, never `includeAll`
`includeAll` performs a JVM-classpath-wide `ClassLoader.getResources()` scan — it is **not** scoped to the current module's JAR even with `relativeToChangelogFile: true`. With ~25 module JARs on `tnt-bootstrap`'s classpath simultaneously, `includeAll` silently picks up files from unrelated modules or no-ops if a name collides. The fix applied across this repo: rename every module's master changelog to a globally-unique name (`tnt-actor-master.yaml`, `tnt-billing-dsl-master.yaml`, etc.) and `include:` each one explicitly by name in the root master — a missing/renamed file then fails loudly instead of silently no-oping.

## Layout convention
```
<module>/src/main/resources/db/changelog/
  tnt-<module>-master.yaml      ← included explicitly from root master
  changes/
    001_create_x_table.sql      ← either "--liquibase formatted sql" + include:, OR plain SQL + sqlFile:
    002_create_y_table.sql
```
Two valid wiring styles coexist in this repo (both fine, pick one per file):
1. **`include:` of a formatted-SQL file** — file must start with `--liquibase formatted sql` then `--changeset <author>:<id>` per changeset. Required when the same file is meant to be included directly as a mini-changelog.
2. **`sqlFile:` change type inside a `changeSet:` block** — for raw, unannotated SQL. Needs `relativeToChangelogFile: true` and `splitStatements: true` for multi-statement files.

## ⚠️ `splitStatements: true` breaks on PL/pgSQL `$$...$$` bodies
Liquibase's naive `;`-based statement splitter does not understand dollar-quoted function bodies. A function/trigger definition containing internal `;` **must be its own changeset** with `splitStatements: false` — see `logistics/tnt-sync-core/src/main/resources/db/changelog/changes/002_create_entity_version_trigger.sql` for the canonical example (split out from the table-creation file specifically to fix this).

## ⚠️ PostGIS `CREATE INDEX ... USING GIST`
PostgreSQL requires **double parentheses** around expression indexes: `USING GIST ((expr))` not `USING GIST (expr)`. Caught and fixed in `tnt-geo-core`'s road/POI changelogs.

## Module → changelog status

| Module | Master changelog | Format |
|---|---|---|
| tnt-actor-core | `tnt-actor-master.yaml` | formatted SQL include |
| tnt-organization-core | `tnt-organization-master.yaml` | formatted SQL include |
| tnt-tp-core | `tnt-tp-master.yaml` | formatted SQL include |
| tnt-administration-core | `tnt-administration-master.yaml` | formatted SQL include |
| tnt-geo-core | `tnt-geo-master.yaml` | `sqlFile` + `splitStatements: true`, v1.0/ subfolder |
| tnt-route-core | `tnt-route-master.yaml` | mixed |
| tnt-delivery-core | `tnt-delivery-master.yaml` | formatted SQL include |
| tnt-dispute-core | `tnt-dispute-master.yaml` | formatted SQL include, `changes/` subfolder |
| tnt-incident-core | `tnt-incident-master.xml` | **XML**, not YAML (legacy, still valid) |
| tnt-notify-core | `tnt-notify-master.yaml` | formatted SQL include |
| tnt-media-core | `tnt-media-master.yaml` | formatted SQL include |
| tnt-sync-core | `tnt-sync-master.yaml` | `sqlFile` (2 changesets — see PL/pgSQL gotcha above) — **fixed 2026-06-30**, was previously unwired (Flyway-named file, never executed) |
| tnt-resource-core, tnt-product-core, tnt-inventory-core, tnt-sales-core, tnt-accounting-core | `tnt-<name>-master.yaml` | formatted SQL include |
| All 7 billing modules | `tnt-billing-<name>-master.yaml` | mixed, `sqlFile` for raw files |
| tnt-realtime-core | *(none)* | Redis-only module, no schema |

## R2DBC connection
`spring.r2dbc.url=r2dbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:tiibntick_core}`, pool 5–30 connections, `validation-query: SELECT 1`.

## Liquibase connection (separate, JDBC)
`spring.liquibase.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:tiibntick_core}`, `change-log: classpath:db/changelog/tnt-core-master.yaml`. Runs once at startup via `tnt-bootstrap`'s `LiquibaseConfig`.

# Links
- `architecture/modules.md` — full module list
- `knowledge/known-issues.md` — full incident history of Liquibase fixes (Phase 2 cleanup)
- `infrastructure/docker.md` — Postgres container config

---
> **Comment maintenir ce document** : à chaque nouveau module avec migrations, ajouter une ligne dans le tableau "Module → changelog status". Si un nouveau piège Liquibase est découvert (comme le bug `splitStatements`/`$$`), l'ajouter dans `# Details` avec le fichier exemple qui l'illustre.
