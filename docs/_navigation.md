# _navigation — Full Documentation Tree

```
docs/
├── README.md            ← main index, start here
├── _index.md             ← routing by task type (new here / debugging / adding a feature)
├── _quick-start.md       ← run the app locally
├── _cheat-sheet.md       ← commands + key facts, zero prose
├── _navigation.md        ← this file
│
├── architecture/
│   ├── overview.md       ← 30-second mental model, layered build diagram
│   ├── modules.md        ← all 31 modules: layer, owner, purpose
│   ├── packages.md       ← hexagonal package shape (+ 2 exceptions)
│   ├── dependencies.md   ← Mermaid inter-module dependency graph
│   ├── decisions.md      ← ADR log (why things are the way they are)
│   └── project-tree.md   ← physical directory tree
│
├── domain/
│   ├── bounded-contexts.md  ← DDD context map, one line per module
│   ├── aggregates.md        ← every aggregate root
│   ├── entities.md          ← non-root entities per module
│   ├── value-objects.md     ← VOs per module (incl. Money duplication warning)
│   ├── events.md             ← every domain event + cross-module consumers
│   └── workflows.md          ← state machines (Delivery, Incident, Dispute, Sync, Wallet, Invoice)
│
├── api/
│   ├── rest.md            ← every controller, base paths, key endpoints
│   ├── security.md        ← auth/permission requirements per endpoint class
│   └── errors.md           ← exception → HTTP status mapping per module
│
├── kernel-api/                    ← the external Kernel's HTTP surface (not TiiBnTick's own)
│   ├── README.md                  ← how to refresh, how to use
│   ├── openapi.json                ← raw spec, source of truth
│   ├── endpoints.md                 ← generated: every Kernel operation
│   └── schemas.md                    ← generated: every Kernel DTO schema
│
├── infrastructure/
│   ├── database.md         ← Liquibase conventions + gotchas (the big one)
│   ├── kafka.md             ← topic/producer/consumer map
│   ├── redis.md             ← Redis usage map (Caffeine vs Redis distinction)
│   ├── elasticsearch.md     ← status: present but unused
│   ├── docker.md             ← docker-compose service/port/credential reference
│   └── monitoring.md         ← actuator endpoints, health indicators, tracing
│
├── security/
│   ├── authentication.md   ← JWT → TntSecurityContext
│   ├── authorization.md     ← @RequirePermission / TntPermissionAspect mechanics
│   ├── permissions.md       ← ReactivePermissionResolver LOCAL/REMOTE/HYBRID architecture
│   ├── roles.md              ← the 9 TntRole values
│   └── platform-gateway-credentials.md ← issuing/rotating X-Client-Id/X-Api-Key per platform
│
├── development/
│   ├── conventions.md       ← naming, Kernel boundary, multi-tenancy pattern choice
│   ├── coding-style.md       ← Lombok/MapStruct, reactive idioms, enforcer rules
│   ├── testing.md             ← test commands + the "tests pass but app doesn't start" trap
│   └── roadmap.md              ← engineering debt / scaffolds visible in code
│
├── knowledge/
│   ├── glossary.md           ← acronyms and domain terms
│   ├── faq.md                  ← quick answers to recurring questions
│   ├── known-issues.md         ← incident log with root causes (the big reference doc)
│   └── project-map.md          ← ⭐ THE FILE FINDER — where everything is
│
└── memory/                      ← living state, update every session
    ├── current-state.md        ← what's verified working right now
    ├── conventions.md           ← how the user prefers to collaborate
    ├── todo.md                   ← next-session priorities
    ├── known-problems.md         ← currently-active (non-historical) issues
    ├── future-features.md        ← built-but-inactive scaffolds
    ├── completed.md               ← dated changelog of significant work
    └── assumptions.md              ← unconfirmed Kernel-contract assumptions
```

## By "I need to..."
| Need | File |
|---|---|
| Run the app | `_quick-start.md` |
| Find a class/file fast | `knowledge/project-map.md` |
| Understand a module's purpose | `architecture/modules.md` |
| Understand the domain model | `domain/bounded-contexts.md` → relevant `domain/*.md` |
| Add/modify an endpoint | `api/rest.md`, `development/conventions.md` |
| Add a DB table | `infrastructure/database.md` |
| Add a permission check | `security/permissions.md` |
| Debug a weird build error | `knowledge/known-issues.md` |
| Know what's currently broken | `memory/known-problems.md` |
| Know what was recently done | `memory/completed.md` |

# Links
`README.md` for the narrative version of this same map.

---
> **Comment maintenir ce document** : mettre à jour l'arborescence dès qu'un fichier `.md` est ajouté/supprimé/renommé dans `docs/`. C'est la carte du territoire — elle doit toujours correspondre exactement à `find docs/ -name "*.md"`.
