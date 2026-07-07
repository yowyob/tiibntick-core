# Purpose
Elasticsearch status — is it actually used, and by what.

# Summary
**Not actively used by any TiiBnTick Core module today.** It runs in `docker-compose.yml` and `spring-data-elasticsearch` is on the classpath, but only as a **transitive dependency of the Kernel** (`RT-comops-kernel-core`). Zero `ElasticsearchTemplate`/`ReactiveElasticsearchOperations`/`@Document` usage found anywhere in `com.yowyob.tiibntick.*` code.

# Details

## Current state
- Service runs (`elasticsearch:8.15.0`, port 9200, single-node, security disabled) — see `infrastructure/docker.md`.
- No index mappings, no repositories, no search use-cases defined in this repo.
- The dependency is present purely because the Kernel jar pulls it in — it is NOT something TiiBnTick code has opted into.

## Likely future use (not yet built)
Candidates mentioned in module purpose but unimplemented: full-text search over incident reports, mission history audit logs, dispute evidence search. If you're asked to add search, this is greenfield — no existing pattern to follow in this codebase, check the Kernel's own Elasticsearch usage (if any) for conventions to mirror instead of inventing new ones.

## If you do add Elasticsearch usage
1. Confirm whether the Kernel already exposes a search SPI before building a parallel one (per the "Kernel is the source of truth for cross-cutting infra" principle in `architecture/decisions.md` ADR-001).
2. Add a dedicated `@Document`-annotated read model in the owning module (don't put search indexing logic in `tnt-common-core`).
3. Update this doc immediately — it's the kind of "surprising absence" that's easy to forget once it's no longer true.

# Links
- `infrastructure/docker.md` — container config
- `architecture/decisions.md` — ADR-001 (Kernel boundary)

---
> **Comment maintenir ce document** : dès qu'un module utilise réellement Elasticsearch, réécrire ce document avec le même format que `infrastructure/redis.md` (tableau module → index → usage). Ne pas laisser ce document dire "non utilisé" si ce n'est plus vrai — c'est l'erreur la plus probable dans ce doc.
