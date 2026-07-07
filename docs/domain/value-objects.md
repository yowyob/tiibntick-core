# Purpose
Immutable value objects (no identity, equality by value) per module — types you pass around instead of primitives.

# Summary
`Money`, `GeoCoordinates`, and `TenantId` (Kernel type) are the most widely-shared VOs across modules. Most modules define their own small set under `domain/model/valueobject/`.

# Details

| Module | Value objects |
|---|---|
| tnt-delivery-core | `DeliveryAddress`, `RecipientInfo`, `GeoCoordinates`, `PackageSpecification`, `TrackingCode`, `DeliveryCost`, `EtaEstimate` |
| tnt-actor-core | `ActorId`, `ActorStatus`, `KycStatus`, `ActorType` |
| tnt-geo-core | `GeoPoint`, `AddressResult`, `CostWeights`, `ZoneAccessDifficulty`, `DeliveryZoneType`, `RoadType`, `PoiType`, `NodeType`, `WeatherCondition`, `HubStatus` |
| tnt-route-core | `VrpRequest`, `VrpSolution`, `RoutePath`, `EtaResult`, `CostParams`, `SolverStatus`, `ReroutingDecision`, `ReroutingChoice`, `DeliveryItem`, `TourStatus`, `WaypointType` |
| tnt-realtime-core | `SessionId`, `BroadcastTopic`, `ETAInterval` |
| tnt-sync-core | `SyncSessionId`, `SyncToken`, `OfflineOpId` |
| tnt-dispute-core | `DisputeId`, `DisputeReference`, `DisputeResolution`, `CompensationDetails`, `EvidenceId`, `DisputeSLAPolicy`, `DisputeStats`, `DisputeEventId` |
| tnt-incident-core | `IncidentSlaImpact`, `IncidentCompensationImpact`, `IncidentRiskScore`, `IncidentGeoSnapshot`, `PricingAdjustment` |
| tnt-media-core | `MediaType`, `ImageProcessingSpec`, `QRFormat` |
| tnt-resource-core | `VehicleType`, `EquipmentType`, `MaintenanceType`, `OwnershipType`, `VehicleStatus`, `EquipmentStatus`, `AllocationStatus`, `FuelType`, `VehicleCapacity`, `ResourceType`, `MaintenanceSchedule` |
| tnt-product-core | `ProductType`, `ProductStatus`, `ProductCategory`, `Dimensions`, `UnitOfMeasure`, `ServiceType` |
| tnt-inventory-core | `WarehouseType`, `MovementType`, `PhysInventoryStatus`, `AlertType` |
| tnt-sales-core | `OrderPriority`, `SalesOrderStatus`, `PaymentStatus`, `ReturnReason` |
| tnt-accounting-core | `AccountType`, `JournalNumber`, `JournalStatus`, `JournalType`, `PeriodStatus`, `StatementType` |
| tnt-billing-pricing | (rule VOs listed in `entities.md` — pricing rules act as both entity and VO depending on context) |
| tnt-billing-cost | `Money` (module-local copy — see note below) |
| tnt-billing-dsl | `PricingContext`, `EvaluationResult`, `DeliveryPriority`, `PackageType`, `PolicyOwnerType`, `RoadType`, `WeatherCondition` |
| tnt-billing-invoice | `InvoiceNumber`, `Money` |
| tnt-billing-wallet | `WalletId`, `TransactionId`, `PaymentIntentId`, `ReconciliationId`, `Money`, `PaymentRequest` |
| tnt-billing-report | `ReportPeriod`, `BillingKPISnapshot` |
| tnt-billing-templates | `ParameterType`, `TemplateCategory`, `PolicyOwnerType` |

⚠️ **`Money` is defined independently in multiple billing modules** (tnt-billing-cost, tnt-billing-invoice, tnt-billing-wallet) rather than shared from `tnt-common-core` — known duplication, not yet consolidated. See `knowledge/known-issues.md` if you touch currency/rounding logic, change all copies consistently.

# Links
- `domain/entities.md`
- `knowledge/known-issues.md` — `Money` duplication note

---
> **Comment maintenir ce document** : ajouter un VO à la ligne de son module dès qu'une nouvelle classe apparaît sous `domain/model/valueobject/`. Si `Money` est enfin consolidé dans `tnt-common-core`, retirer l'avertissement et mettre à jour `knowledge/known-issues.md`.
