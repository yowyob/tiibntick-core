# Purpose
Every aggregate root in the codebase — the entry point for any write operation in DDD terms. If you need to mutate domain state, you go through one of these.

# Summary
Not every module has explicit "aggregate root" classes (several use a flatter `domain/model/` with one obvious root entity per repository) — both patterns are noted below. `tnt-delivery-core` has the richest aggregate structure (4 explicit aggregates).

# Details

## Explicit aggregates (under `domain/model/aggregate/`)

| Module | Aggregate | Path | Purpose |
|---|---|---|---|
| tnt-delivery-core | `Delivery` | `domain/model/aggregate/Delivery.java` | Root: full delivery state machine (CREATED→PICKED_UP→IN_TRANSIT→[RELAY_POINT]→DELIVERED\|FAILED\|CANCELLED) |
| tnt-delivery-core | `Parcel` | `domain/model/aggregate/Parcel.java` | Physical package tracked within a delivery |
| tnt-delivery-core | `DeliveryPerson` | `domain/model/aggregate/DeliveryPerson.java` | Deliverer-in-context-of-a-delivery (capacity, current load) |
| tnt-delivery-core | `DeliveryAnnouncement` | `domain/model/aggregate/DeliveryAnnouncement.java` | Marketplace-style "request for delivery" with responses |

## De-facto roots (no explicit `aggregate/` package, but function as one — typically the class the repository is keyed on)

| Module | Root entity | Notes |
|---|---|---|
| tnt-actor-core | `DelivererProfile` / `FreelancerProfile` / `RelayOperatorProfile` / `ClientProfile` | Sealed hierarchy under `TntActorProfile` |
| tnt-organization-core | `FreelancerOrganization`, `Agency`, `Branch`, `HubRelais` | 4 independent roots, no shared parent |
| tnt-dispute-core | `Dispute` | Holds `DisputeEvidence`, `DisputeComment`, `EscalationRecord` as children |
| tnt-incident-core | `Incident` | Holds `IncidentEscalation`, `IncidentBlockchainRecord`, `IncidentEvidence` as children |
| tnt-sync-core | `SyncSession` | Tracks one push+pull cycle; `OfflineOperation` and `EntityVersionRecord` are independent roots in the same module |
| tnt-resource-core | `Vehicle`, `FreelancerVehicle`, `Equipment`, `FreelancerEquipment` | 4 independent roots |
| tnt-sales-core | `TntSalesOrder` | Holds `TntSalesOrderLine` as children |
| tnt-accounting-core | `JournalEntry` | Double-entry root; `Account`, `AccountingPeriod` are independent roots |
| tnt-billing-invoice | `Invoice` | Holds `InvoiceLine`, `TaxLine` as children; `CreditNote` is a related root |
| tnt-billing-wallet | `Wallet` | Holds `WalletTransaction` as children; `PaymentIntent` is a related root |
| tnt-billing-pricing | `BillingPolicy` | Holds pricing/promotion/surcharge rules as children |
| tnt-billing-dsl | `DslRule` | Self-contained — no children |

## Modules with NO aggregate concept (pure value/read-model modules)
- `tnt-geo-core`, `tnt-route-core`, `tnt-realtime-core` — model graphs/computations, not transactional aggregates
- `tnt-billing-report` — read-only CQRS projections
- `tnt-media-core` — utility layer (file metadata, not a rich domain)

# Links
- `domain/entities.md` — child entities within each aggregate
- `domain/value-objects.md`
- `domain/workflows.md` — state machines for `Delivery`, `Incident`, `Dispute`

---
> **Comment maintenir ce document** : un nouvel aggregate root = une nouvelle ligne dans le tableau approprié. Si un module passe d'un "de-facto root" à un package `aggregate/` explicite, le déplacer entre les deux tableaux.
