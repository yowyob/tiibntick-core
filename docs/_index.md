# _index — Start Here

This file exists for fast cold-start orientation. For the full narrative overview, see `README.md`.

## 30-second summary
TiiBnTick Core = 31-module reactive Java/Spring Boot logistics+billing platform, hexagonal architecture, built on the external Yowyob Kernel. One runnable module (`tnt-bootstrap`). Java 21, Spring Boot 4.0.6/Framework 7, WebFlux, R2DBC, Kafka, Redis, MinIO.

## Read in this order if you're new to the codebase
1. `README.md` — what this is
2. `_quick-start.md` — how to run it
3. `architecture/overview.md` — how it's built
4. `architecture/modules.md` — the 31 modules, one line each
5. `knowledge/project-map.md` — where to find anything (controllers/services/ports/adapters/configs)
6. `memory/current-state.md` — what's currently working/broken (check this every session)

## Read in this order if you're debugging
1. `memory/known-problems.md` — is this already known?
2. `knowledge/known-issues.md` — has this exact symptom happened before?
3. `knowledge/faq.md` — common questions
4. The relevant `domain/`, `api/`, `infrastructure/`, or `security/` doc for the subsystem involved

## Read in this order if you're adding a feature
1. `development/conventions.md` — the "shape" a feature change should take
2. `architecture/packages.md` — where new files go
3. `domain/bounded-contexts.md` — which module owns this capability
4. `api/rest.md` (if it's an endpoint) or `domain/workflows.md` (if it's a state-machine change)
5. `security/permissions.md` (if it needs a permission check)
6. `infrastructure/database.md` (if it needs a new table)

# Links
See `_navigation.md` for the complete file tree, `_cheat-sheet.md` for commands.

---
> **Comment maintenir ce document** : garder ce fichier court — c'est un routeur, pas un résumé de contenu. Mettre à jour les "ordres de lecture" si la structure de `docs/` change significativement.
