# Purpose
Authoritative list of all 33 Maven modules: layer, folder, owner, package root, and whether each follows the standard hexagonal layout. Source of truth: root `pom.xml` `<modules>` (with inline ownership comments) and `<developers>`.

# Summary
- 33 modules: 6 foundation (L0/L1) + 4 identity (L2) + 9 logistics (L3) + 5 business (L4) + 7 billing (L5) + 1 trust (L6) + 1 bootstrap (L7).
- Build order in root `pom.xml` **is** the dependency order — earlier layers never depend on later ones. Strict rule: a module must never declare a Maven dependency on a module in a strictly higher-numbered layer, even if the resulting graph is acyclic (see `tnt-trust-core` below for why it isn't L3).
- Two modules deliberately deviate from the standard `adapter/application/domain` hexagonal package shape: `tnt-billing-dsl` and `tnt-accounting-core` (both use `infrastructure/{adapter,config,persistence}` instead — see `architecture/packages.md`).
- `tnt-trust-core` (L6, `trust/` folder — not `logistics/`) is a cross-cutting exception to the "earlier layers never depend on later ones" rule above: it depends *down* into whichever module owns an outbound port it implements (delivery, incident, billing-pricing, billing-wallet, actor-core so far — spanning L2 through L5), but no calling module ever depends back on it. That consumption pattern is exactly why it can't be L3 — a module legitimately depending on L2–L5 modules must itself sit above L5.

# Details

## Module table

| Module | Layer | Folder | Owner | Package root |
|---|---|---|---|---|
| yow-event-kernel | L0 | foundation/ | MANFOUO Braun | `com.yowyob.kernel.event` |
| yow-i18n-kernel | L0 | foundation/ | PAFE Dilane | `com.yowyob.kernel.i18n` |
| tnt-common-core | L1 | foundation/ | shared | `com.yowyob.tiibntick.common` |
| tnt-auth-core | L1 | foundation/ | shared | `com.yowyob.tiibntick.core.auth` |
| tnt-roles-core | L1 | foundation/ | shared | `com.yowyob.tiibntick.core.roles` |
| tnt-platform-gateway-core | L1 | foundation/ | shared | `com.yowyob.tiibntick.core.platformgateway` |
| tnt-actor-core | L2 | identity/ | MANFOUO Braun | `com.yowyob.tiibntick.core.actor` |
| tnt-organization-core | L2 | identity/ | PAFE Dilane | `com.yowyob.tiibntick.core.organization` |
| tnt-tp-core | L2 | identity/ | FRANCOIS | `com.yowyob.tiibntick.core.tp` |
| tnt-administration-core | L2 | identity/ | FRANCOIS | `com.yowyob.tiibntick.core.administration` |
| tnt-geo-core | L3 | logistics/ | PAFE Dilane | `com.yowyob.tiibntick.core.geo` |
| tnt-route-core | L3 | logistics/ | MANFOUO Braun | `com.yowyob.tiibntick.core.route` |
| tnt-delivery-core | L3 | logistics/ | PAFE Dilane | `com.yowyob.tiibntick.core.delivery` |
| tnt-dispute-core | L3 | logistics/ | MANFOUO Braun | `com.yowyob.tiibntick.core.dispute` |
| tnt-incident-core | L3 | logistics/ | (unattributed) | `com.yowyob.tiibntick.core.incident` |
| tnt-realtime-core | L3 | logistics/ | MANFOUO Braun | `com.yowyob.tiibntick.core.realtime` |
| tnt-sync-core | L3 | logistics/ | MANFOUO Braun | `com.yowyob.tiibntick.core.sync` |
| tnt-notify-core | L3 | logistics/ | PAFE Dilane | `com.yowyob.tiibntick.core.notify` |
| tnt-media-core | L3 | logistics/ | PAFE Dilane | `com.yowyob.tiibntick.core.media` |
| tnt-resource-core | L4 | business/ | FRANCOIS | `com.yowyob.tiibntick.core.resource` |
| tnt-product-core | L4 | business/ | FRANCOIS | `com.yowyob.tiibntick.core.product` |
| tnt-inventory-core | L4 | business/ | FRANCOIS | `com.yowyob.tiibntick.core.inventory` |
| tnt-sales-core | L4 | business/ | FRANCOIS | `com.yowyob.tiibntick.core.sales` |
| tnt-accounting-core | L4 | business/ | PAFE Dilane | `com.yowyob.tiibntick.core.accounting` |
| tnt-billing-dsl | L5 | billing/ | MANFOUO Braun | `com.yowyob.tiibntick.core.billing.dsl` |
| tnt-billing-pricing | L5 | billing/ | MANFOUO Braun | `com.yowyob.tiibntick.core.billing.pricing` |
| tnt-billing-cost | L5 | billing/ | PAFE Dilane | `com.yowyob.tiibntick.core.billing.cost` |
| tnt-billing-invoice | L5 | billing/ | PAFE Dilane | `com.yowyob.tiibntick.core.billing.invoice` |
| tnt-billing-wallet | L5 | billing/ | MANFOUO Braun | `com.yowyob.tiibntick.core.billing.wallet` |
| tnt-billing-report | L5 | billing/ | PAFE Dilane | `com.yowyob.tiibntick.core.billing.report` |
| tnt-billing-templates | L5 | billing/ | (unattributed) | `com.yowyob.tiibntick.core.billing.templates` |
| tnt-trust-core | L6 | trust/ | MANFOUO Braun | `com.yowyob.tiibntick.core.trust` |
| tnt-bootstrap | L7 | root | MANFOUO Braun | `com.yowyob.tiibntick.bootstrap` |

## One-line purpose per module

| Module | Purpose |
|---|---|
| yow-event-kernel | In-process/Kafka event bus abstraction |
| yow-i18n-kernel | African-locale internationalization (fr_CM, en_CM, pidgin_CM) |
| tnt-common-core | Shared types/utilities used by every other module |
| tnt-auth-core | JWT (Kernel-issued) → `TntSecurityContext` bridge |
| tnt-roles-core | RBAC vocabulary: `TntRole`, `TntPermission`, `@RequirePermission` AOP |
| tnt-platform-gateway-core | Client-ID/API-Key auth for platform backends, resource:action scopes, Kernel auth/SSO proxy |
| tnt-actor-core | Deliverer/Freelancer/RelayOperator/ClientProfile identity profiles |
| tnt-organization-core | Agency, Branch, Hub, ServiceZone |
| tnt-tp-core | Third-party extension: KYC, loyalty, ratings, client profiles |
| tnt-administration-core | RBAC management API, tenant provisioning |
| tnt-geo-core | PostGIS road graph, geocoding, points of interest |
| tnt-route-core | A* + OR-Tools VRP/CVRP solver, ETA |
| tnt-delivery-core | Delivery/mission lifecycle state machine |
| tnt-dispute-core | Dispute lifecycle, evidence, mediation |
| tnt-incident-core | Incident reporting/triage/auto-resolution, blockchain evidence chain |
| tnt-realtime-core | GPS tracking, presence, SSE/WebSocket streaming (Redis-backed) |
| tnt-sync-core | Offline-first sync: push/pull, conflict resolution, delta sync |
| tnt-notify-core | Push/SMS/Email/in-app notifications |
| tnt-media-core | File storage (MinIO), QR codes, PDF generation |
| tnt-resource-core | Vehicles, equipment inventory |
| tnt-product-core | Product catalog, SKUs |
| tnt-inventory-core | Stock, warehouse movements |
| tnt-sales-core | Sales order pipeline |
| tnt-accounting-core | OHADA-compliant general ledger, journal entries |
| tnt-billing-dsl | Hand-written rule language for pricing/cost policies |
| tnt-billing-pricing | Price evaluation engine using the DSL |
| tnt-billing-cost | Operational cost computation (fuel, wear, time) |
| tnt-billing-invoice | Invoice generation, VAT per country, credit notes |
| tnt-billing-wallet | Wallet balance, prepaid, MTN/Orange/Stripe payment webhooks |
| tnt-billing-report | Revenue/commission/margin reports, CSV export |
| tnt-billing-templates | Pre-built billing policy templates |
| tnt-trust-core | Cross-cutting blockchain anchoring toward the yow-trust-event Kernel — DeliveryProof/CustodyTransfer/DID/PoL/Badge/BillingPolicy/Payment, incident blockchain chain (`IBlockchainAuditPort`); consumes L2→L5 modules, nothing depends back on it |
| tnt-bootstrap | The only runnable module — assembles and starts everything |

# Links
- `architecture/dependencies.md` — Mermaid inter-module graph
- `architecture/packages.md` — hexagonal layout per module
- `architecture/project-tree.md` — physical directory tree

---
> **Comment maintenir ce document** : à chaque module ajouté/supprimé/renommé dans `pom.xml` `<modules>`, mettre à jour les deux tableaux ci-dessus. Vérifier l'ownership via les commentaires inline du `pom.xml` (pas par déduction).
