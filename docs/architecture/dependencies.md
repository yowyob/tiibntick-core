# Purpose
Visual + tabular map of which TiiBnTick module depends on which — for impact analysis ("if I change X, what could break?") and for understanding build order.

# Summary
- Build order = dependency order (root `pom.xml` `<modules>` list, L0→L7).
- Almost every module depends on `tnt-common-core` (shared types) — omitted from the diagram below for readability.
- Kernel (`yowyob.comops.api:RT-comops-*`) dependencies are read-only/external — see `architecture/overview.md` for the boundary rule.
- `tnt-trust-core` (L6, `trust/`) is the one module that depends on modules across several other layers (L2 actor, L3 delivery/incident, L5 billing-pricing/billing-wallet so far) — always one-directionally, always down into whichever module owns the port it implements. No calling module ever depends back on trust. See `architecture/modules.md` for why this puts trust above L5, not in L3 logistics where it physically used to live.
- `coreBackend/` (L6-Bis) is the widest-reaching layer: each of its 4 modules (one is an aggregator of 14 sub-modules) depends on many L1–L5 modules directly, since they're orchestrators, not narrow single-purpose libraries — see the per-module dependency lists below rather than trying to draw every edge on the main diagram.

# Details

## Mermaid — key inter-module dependencies (TNT → TNT only, `tnt-common-core` omitted)

```mermaid
flowchart LR
    auth[tnt-auth-core] --> common[tnt-common-core]
    roles[tnt-roles-core] --> common

    actor[tnt-actor-core] --> auth
    actor --> roles
    actor --> incident[tnt-incident-core]
    org[tnt-organization-core] --> auth
    org --> roles
    admin[tnt-administration-core] --> auth
    admin --> roles

    route[tnt-route-core] --> geo[tnt-geo-core]
    delivery[tnt-delivery-core] --> actor
    delivery --> org
    delivery --> geo
    delivery --> route
    delivery --> incident
    incident --> auth
    incident --> roles
    dispute[tnt-dispute-core] --> delivery
    dispute --> roles
    realtime[tnt-realtime-core] --> delivery
    notify[tnt-notify-core] --> media[tnt-media-core]

    sales[tnt-sales-core] --> common
    accounting[tnt-accounting-core] -.Kafka.-> billing_invoice[tnt-billing-invoice]

    billing_pricing[tnt-billing-pricing] --> billing_dsl[tnt-billing-dsl]
    billing_cost[tnt-billing-cost] --> geo
    billing_cost --> route
    billing_invoice[tnt-billing-invoice] --> tp[tnt-tp-core]
    billing_report[tnt-billing-report] --> accounting

    trust[tnt-trust-core] --> auth
    trust --> roles
    trust -->|"IBadgeAnchorPort"| actor
    trust -->|"DeliveryProofAnchorPort"| delivery
    trust -->|"IBlockchainAuditPort"| incident
    trust -->|"BillingPolicyAnchorPort"| billing_pricing
    trust -->|"IPaymentAnchorPort"| billing_wallet[tnt-billing-wallet]

    gofp[tnt-go-freelancer-point-back-core] --> actor
    gofp --> delivery
    gofp --> geo
    gofp --> billing_wallet
    gofp --> trust

    agency[tnt-agency-back-core<br/>14 sub-modules] --> actor
    agency --> roles
    agency --> notify
    agency --> realtime[tnt-realtime-core]
    agency --> sync[tnt-sync-core]

    link[tnt-link-back-core] --> actor
    link --> delivery
    link --> geo

    market[tnt-market-back-core] --> actor
    market --> delivery
    market --> billing_pricing
    market --> billing_wallet
    market --> sales
    market --> tp

    bootstrap[tnt-bootstrap] -.assembles.-> actor
    bootstrap -.assembles.-> delivery
    bootstrap -.assembles.-> billing_invoice
    bootstrap -.assembles.-> accounting
    bootstrap -.assembles.-> trust
    bootstrap -.assembles.-> gofp
    bootstrap -.assembles.-> agency
    bootstrap -.assembles.-> link
    bootstrap -.assembles.-> market
```

## `coreBackend/` modules — direct Maven dependencies (from each module's own `pom.xml`)

| Module | Depends on |
|---|---|
| tnt-go-freelancer-point-back-core | tnt-actor-core, tnt-auth-core, tnt-billing-wallet, tnt-delivery-core, tnt-geo-core, tnt-inventory-core, tnt-notify-core, tnt-realtime-core, tnt-roles-core, tnt-trust-core |
| tnt-link-back-core | tnt-actor-core, tnt-auth-core, tnt-delivery-core, tnt-geo-core, tnt-notify-core, tnt-realtime-core, tnt-roles-core |
| tnt-market-back-core | tnt-actor-core, tnt-auth-core, tnt-billing-dsl, tnt-billing-invoice, tnt-billing-pricing, tnt-billing-wallet, tnt-delivery-core, tnt-dispute-core, tnt-geo-core, tnt-media-core, tnt-notify-core, tnt-organization-core, tnt-product-core, tnt-realtime-core, tnt-roles-core, tnt-sales-core, tnt-sync-core, tnt-tp-core |
| tnt-agency-back-core (union across its 14 sub-modules) | tnt-auth-core, tnt-common-core, tnt-notify-core, tnt-realtime-core, tnt-roles-core, tnt-sync-core — plus an internal DAG (`tnt-agency-org-core` is the hub) documented in `architecture/modules.md` |

## Module → Kernel dependency table

| Module | Kernel artifacts depended on |
|---|---|
| tnt-common-core | `RT-comops-common-core`, `RT-comops-kernel-core` |
| tnt-auth-core | `RT-comops-auth-core` |
| tnt-roles-core | `RT-comops-roles-core`, `RT-comops-kernel-core` |
| tnt-actor-core | `RT-comops-actor-core`, `RT-comops-common-core`, `RT-comops-kernel-core` |
| tnt-organization-core | `RT-comops-organization-core` |
| tnt-tp-core | `RT-comops-tp-core` |
| tnt-administration-core | `RT-comops-administration-core` |
| tnt-delivery-core | `RT-comops-common-core`, `RT-comops-kernel-core` |
| tnt-resource-core | `RT-comops-resource-core` |
| tnt-product-core | `RT-comops-product-core` |
| tnt-inventory-core | `RT-comops-inventory-core` |
| tnt-sales-core | `RT-comops-sales-core` |
| tnt-accounting-core | `RT-comops-accounting-core` |
| tnt-billing-pricing | `RT-comops-accounting-core`, `RT-comops-settings-core` |
| tnt-billing-invoice | `RT-comops-common-core`, `RT-comops-settings-core` |

`RT-comops-kernel-core` is the one Kernel artifact that pulled in a conflicting transitive `swagger-annotations`/`swagger-models` (non-jakarta, v2.2.22) — now excluded at the root `pom.xml` `dependencyManagement` level. See `knowledge/known-issues.md`.

## Cross-module port-based integration points

| Caller → Callee | Port | Purpose |
|---|---|---|
| tnt-delivery-core → tnt-route-core | `EtaComputationPort` | ETA forecast |
| tnt-delivery-core → tnt-billing-cost | `DeliveryCostComputationPort` | Cost estimation on assignment |
| tnt-delivery-core ← tnt-incident-core | event: `DeliveryPausedByIncidentEvent` | Pause/resume delivery on incident |
| tnt-route-core → tnt-geo-core | `IRoadNetworkProvider` | Road graph queries |
| tnt-realtime-core → tnt-route-core | `IKalmanEtaUpdater` | Live ETA broadcast |
| tnt-realtime-core → tnt-actor-core | `IActorLocationUpdater` | GPS position persistence |
| tnt-incident-core → tnt-media-core | `IMediaStoragePort` | Evidence archival (MinIO) — see `knowledge/known-issues.md` for the tenant-bucket fix |
| all modules → tnt-notify-core | `IPublishNotificationEventPort` | Event-driven notifications |
| tnt-roles-core → Kernel (HTTP) | `kernelWebClient` | Role provisioning, permission resolution (REMOTE/HYBRID mode) |
| tnt-incident-core → tnt-roles-core, tnt-auth-core | `@RequirePermission`, `@CurrentUser` | Added 2026-07-31 (Go-Freelancer integration hardening) — the module previously had zero access control; see `security/roles.md` |
| tnt-dispute-core → tnt-roles-core | `@RequirePermission` | Added 2026-07-31 — replaces the former class-wide `@PreAuthorize("isAuthenticated()")`, which never checked role or ownership |
| tnt-delivery-core → tnt-trust-core | `DeliveryProofAnchorPort` | Blockchain-anchor delivery proof on completion. Port owned by delivery, implemented in trust — Maven dependency is inverted (`trust → delivery`), never `delivery → trust` |
| tnt-incident-core → tnt-trust-core | `IBlockchainAuditPort` | Incident-chain blockchain anchoring. Port owned by incident, implemented in trust — Maven dependency inverted, same as above |
| tnt-billing-pricing → tnt-trust-core | `BillingPolicyAnchorPort` | Anchor billing policy activation on-chain. Port owned by billing-pricing, implemented in trust — Maven dependency inverted |
| tnt-billing-wallet → tnt-trust-core | `IPaymentAnchorPort` | Anchor committed wallet payments on-chain. Port owned by billing-wallet, implemented in trust — Maven dependency inverted |
| tnt-actor-core → tnt-trust-core | `IBadgeAnchorPort` | Anchor earned badges on-chain, returns tx hash persisted back onto `Badge.blockchainTxHash`. Port owned by actor-core, implemented in trust — Maven dependency inverted |
| tnt-go-freelancer-point-back-core → tnt-geo-core | `IRelayHubPort` | Hub identity/occupancy/provisioning for GOFP relay points — `RelayHub.id` is the shared identity space with `RelayOperatorProfile.hubId` (tnt-actor-core) |
| tnt-go-freelancer-point-back-core → tnt-delivery-core | `DeliveryLifecycleUseCase` | Direct use-case call (not a port owned by GOFP) — relay-point deposit/retrieval drive the authoritative delivery state machine (`AT_RELAY_POINT`/`DELIVERED`) |

## `tnt-bootstrap` assembly
`tnt-bootstrap`'s `TntCoreConfig` `@Import`s every module's `@Configuration` class — see `architecture/modules.md` for the full module list and `infrastructure/monitoring.md` for the custom actuator endpoints that expose this wiring at runtime (`/actuator/tnt-modules`).

# Links
- `architecture/modules.md` — module table
- `architecture/overview.md` — layered build diagram
- `knowledge/known-issues.md` — Kernel dependency exclusion incident

---
> **Comment maintenir ce document** : à chaque nouvelle dépendance inter-module (un module important une interface/port d'un autre), ajouter une ligne au tableau "Cross-module port-based integration points". Régénérer le diagramme Mermaid si la topologie change significativement (nouveau module, dépendance supprimée).
