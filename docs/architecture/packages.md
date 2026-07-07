# Purpose
The package shape every module follows (or deliberately deviates from) — so you know where to look for a class without grepping.

# Summary
- Standard shape: `adapter/{in,out}`, `application/{port,service}`, `domain/{model,event,exception,policy}`, `config`.
- Two modules use a different (also valid, intentional) shape: `tnt-billing-dsl` and `tnt-accounting-core` — `infrastructure/{adapter,config,persistence}` + `domain/{model,port,service}`.
- Package root is always `com.yowyob.tiibntick.core.<module>` except the two L0 kernel modules (`com.yowyob.kernel.<module>`) and bootstrap (`com.yowyob.tiibntick.bootstrap`).

# Details

## Standard hexagonal shape (most modules — e.g. tnt-actor-core, tnt-geo-core, tnt-delivery-core, tnt-roles-core)
```
com.yowyob.tiibntick.core.<module>/
├── adapter/
│   ├── in/
│   │   ├── web/        ← REST controllers
│   │   └── kafka/       ← Kafka consumers (if any)
│   └── out/
│       ├── persistence/ ← R2DBC repositories, entities, mappers
│       ├── messaging/   ← Kafka publishers
│       └── kernel/       ← Kernel HTTP bridge adapters (if any)
├── application/
│   ├── port/
│   │   ├── in/          ← use-case interfaces (commands/queries)
│   │   └── out/          ← outbound port interfaces, often prefixed `I...`
│   └── service/          ← use-case implementations
├── domain/
│   ├── model/
│   │   ├── aggregate/
│   │   ├── entity/
│   │   └── valueobject/
│   ├── event/
│   ├── exception/
│   └── policy/
└── config/                ← module's @Configuration, imported by tnt-bootstrap
```

## Deviating shape (tnt-billing-dsl, tnt-accounting-core)
```
com.yowyob.tiibntick.core.<module>/
├── infrastructure/
│   ├── adapter/    ← REST controllers + Kafka listeners
│   ├── config/      ← @Configuration
│   └── persistence/ ← R2DBC repositories
├── domain/
│   ├── model/
│   ├── port/{in,out}/  ← use-case + outbound port interfaces (vs application/port/ elsewhere)
│   ├── service/         ← domain services (stateful logic, e.g. double-entry bookkeeping invariants)
│   └── exception/
```
Same dependency direction as the standard shape — just `domain/port` instead of `application/port`, and `infrastructure/` instead of `adapter/+config/`. Intentional, per CLAUDE.md, for DSL/ledger complexity where domain services need first-class status.

## Quick lookup — "where is X?"
| Looking for... | Standard-shape path | Deviating-shape path |
|---|---|---|
| REST controller | `adapter/in/web/` | `infrastructure/adapter/` |
| Kafka consumer | `adapter/in/kafka/` | `infrastructure/adapter/` |
| R2DBC repository impl | `adapter/out/persistence/` | `infrastructure/persistence/` |
| Kafka publisher | `adapter/out/messaging/` | `infrastructure/adapter/` |
| Use-case interface | `application/port/in/` | `domain/port/in/` |
| Outbound port interface | `application/port/out/` | `domain/port/out/` |
| Use-case implementation | `application/service/` | `domain/service/` (or `infrastructure/`) |
| Aggregate/Entity/VO | `domain/model/{aggregate,entity,valueobject}/` | `domain/model/` (flatter) |
| Domain event | `domain/event/` | `domain/model/` or `domain/event/` |
| `@Configuration` class | `config/` | `infrastructure/config/` |

See `knowledge/project-map.md` for the per-module concrete file listing (controllers, services, ports, adapters, configs, DTOs, events, repositories) — this doc is the *pattern*, that one is the *index*.

# Links
- `architecture/modules.md` — which modules are standard vs deviating
- `knowledge/project-map.md` — concrete file-by-file index
- `development/conventions.md` — naming conventions (e.g. `I`-prefix for outbound ports)

---
> **Comment maintenir ce document** : si un nouveau module introduit une troisième variante de structure, documenter ici avec le même format tableau. Ne pas dupliquer la liste de fichiers concrets — ça va dans `knowledge/project-map.md`.
