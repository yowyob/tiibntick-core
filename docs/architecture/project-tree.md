# Purpose
Physical directory tree of the repo (excludes `target/`, `.idea/`, `.git/`, `node_modules/`) — for quick visual orientation.

# Summary
53 modules (39 declared in root `pom.xml` + 14 nested under `coreBackend/tnt-agency-back-core`'s own aggregator POM) under 7 plain grouping folders + 1 runnable module at root. Each module's `src/main/java` follows the shape documented in `architecture/packages.md`.

# Details

```
tiibntick-core/
├── pom.xml                              ← root parent, defines 39 modules + dependencyManagement
├── CLAUDE.md                            ← authoritative project instructions
├── docs/                                ← this documentation tree
│
├── foundation/
│   ├── yow-event-kernel/                [L0] event bus — com.yowyob.kernel.event
│   ├── yow-i18n-kernel/                 [L0] i18n (fr_CM/en_CM/pidgin_CM) — com.yowyob.kernel.i18n
│   ├── tnt-common-core/                 [L1] shared types/utilities
│   ├── tnt-auth-core/                   [L1] JWT → TntSecurityContext bridge
│   ├── tnt-roles-core/                  [L1] RBAC: TntRole, @RequirePermission, ReactivePermissionResolver
│   │   └── src/main/java/.../roles/{adapter,application,domain,config}/
│   └── tnt-platform-gateway-core/       [L1] Client-ID/API-Key platform auth, resource:action scopes, Kernel auth/SSO proxy
│
├── identity/
│   ├── tnt-actor-core/                  [L2] Deliverer/Freelancer/RelayOperator/ClientProfile
│   ├── tnt-organization-core/           [L2] Agency, Branch, Hub, FreelancerOrganization
│   ├── tnt-tp-core/                     [L2] KYC, loyalty, ratings, client profiles
│   └── tnt-administration-core/         [L2] RBAC management API, platform options
│
├── logistics/
│   ├── tnt-geo-core/                    [L3] PostGIS road graph, geocoding, POI
│   ├── tnt-route-core/                  [L3] A* + OR-Tools VRP/CVRP, Kalman ETA
│   ├── tnt-delivery-core/               [L3] Delivery/mission lifecycle state machine
│   ├── tnt-dispute-core/                [L3] Dispute lifecycle, evidence, mediation
│   ├── tnt-incident-core/               [L3] Incident triage/escalation, blockchain evidence
│   ├── tnt-realtime-core/               [L3] GPS tracking, presence, SSE/WebSocket (Redis)
│   ├── tnt-sync-core/                   [L3] Offline-first push/pull sync, conflict resolution
│   ├── tnt-notify-core/                 [L3] Push/SMS/Email/in-app notifications
│   └── tnt-media-core/                  [L3] MinIO storage, QR codes, PDF generation
│
├── business/
│   ├── tnt-resource-core/               [L4] Vehicles, equipment, maintenance
│   ├── tnt-product-core/                [L4] Product catalog, service offers
│   ├── tnt-inventory-core/              [L4] Stock, hub occupancy
│   ├── tnt-sales-core/                  [L4] Sales order pipeline
│   ├── tnt-accounting-core/             [L4] OHADA general ledger
│   │   └── src/main/java/.../accounting/{infrastructure,domain,application}/  ← deviating layout
│   ├── tnt-hrm-core/                    [L4] Kernel HRM/human-resources proxy (16 controllers, 160 ops)
│   └── tnt-legacy-documents-core/       [L4] Kernel billing-legacy-documents-controller proxy
│
├── billing/
│   ├── tnt-billing-dsl/                 [L5] Pricing/cost rule DSL (lexer/parser/AST/evaluator)
│   │   └── src/main/java/.../dsl/{infrastructure,domain,application}/  ← deviating layout
│   ├── tnt-billing-pricing/             [L5] Price evaluation engine
│   ├── tnt-billing-cost/                [L5] Operational cost computation
│   ├── tnt-billing-invoice/             [L5] Invoice generation, VAT, credit notes
│   ├── tnt-billing-wallet/               [L5] Wallet, MoMo/Stripe payment webhooks
│   ├── tnt-billing-report/              [L5] Revenue/commission/margin reports
│   └── tnt-billing-templates/           [L5] Reusable billing policy templates
│
├── trust/
│   └── tnt-trust-core/                  [L6] Cross-cutting blockchain anchoring — DeliveryProof/CustodyTransfer/
│                                              DID/Badge/BillingPolicy/Payment, incident blockchain chain.
│                                              Depends down into L2→L5 modules that own its outbound ports;
│                                              nothing depends back on it.
│
├── coreBackend/                         [L6-Bis] Per-product business backends. Orchestrate L0-L5,
│   │                                             expose generic business APIs (no frontend logic).
│   │                                             Consumed only by tnt-bootstrap (L7).
│   ├── tnt-go-freelancer-point-back-core/  Market's Go Freelancer Point — announcements, deliveries,
│   │                                        relay points, TOPSIS/AHP matching — com.yowyob.tiibntick.core.gofreelancer
│   ├── tnt-agency-back-core/               packaging=pom aggregator, NO code of its own — 14 real sub-modules:
│   │   ├── tnt-agency-eventing-core/           shared domain events + Kafka publisher (base module)
│   │   ├── tnt-agency-org-core/                Agency, Branch, Hub config, hub-ops (internal dependency hub)
│   │   ├── tnt-agency-staff-core/              Staff members, hub operators, credentials provisioning
│   │   ├── tnt-agency-workforce-core/          Deliverers, contracts, freelancer associations
│   │   ├── tnt-agency-assignment-core/         Mission projection + tnt-delivery-core orchestration
│   │   ├── tnt-agency-commission-core/         Commissions + tnt-billing-wallet payout
│   │   ├── tnt-agency-billing-core/            Billing policies, estimates, invoices
│   │   ├── tnt-agency-onboarding-core/         Agency onboarding applications (admin + applicant)
│   │   ├── tnt-agency-intake-core/             Client intake queue → assignment + hub-ops
│   │   ├── tnt-agency-inbox-core/              Agency notification inbox
│   │   ├── tnt-agency-fleet-local-core/        Agency fleet view → tnt-resource-core (+ FleetMan link)
│   │   ├── tnt-agency-compliance-core/         Disputes / incidents / claims proxy
│   │   ├── tnt-agency-analytics-core/          Cross-sub-module reporting/analytics
│   │   └── tnt-agency-sync-core/               Offline sync server, extends tnt-sync-core
│   ├── tnt-link-back-core/                 Link — network nodes, bulletin board, DAO zones, gamification —
│   │                                        com.yowyob.tiibntick.core.linkback
│   └── tnt-market-back-core/               Market — provider discovery, quotes, market orders —
│                                            com.yowyob.tiibntick.core.marketback
│
└── tnt-bootstrap/                       [L7] THE ONLY RUNNABLE MODULE
    ├── docker-compose.yml               ← local dev infra (postgres/redis/kafka/minio/es/prometheus/grafana/zipkin)
    ├── Dockerfile
    └── src/main/
        ├── java/.../bootstrap/
        │   ├── config/                  ← TntCoreConfig (@Import orchestrator), TntKafkaConfig, security, OpenAPI...
        │   ├── health/                  ← custom HealthIndicators (Kafka, Kernel, DB pyramid, OR-Tools, MinIO...)
        │   ├── actuator/                ← custom endpoints: tnt-modules, tnt-kernel
        │   └── TiiBnTickApplication.java ← @SpringBootApplication entry point
        └── resources/
            ├── application.yml          ← default/test/staging/prod profiles
            └── db/changelog/
                └── tnt-core-master.yaml ← root Liquibase changelog, includes every module's own
```

# Links
- `architecture/modules.md` — module table with owners/purposes
- `architecture/packages.md` — what's inside each module's `src/main/java`
- `infrastructure/docker.md` — `docker-compose.yml` details

---
> **Comment maintenir ce document** : régénérer cette arborescence si un module est ajouté/déplacé/renommé. Garder la profondeur à 3-4 niveaux max — pour le détail fichier-par-fichier, voir `knowledge/project-map.md`.
