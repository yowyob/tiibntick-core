# Purpose
Non-root entities (and key supporting model classes) per module — the things that live *inside* an aggregate or are read/written independently but aren't "the" root.

# Summary
This is intentionally a high-density index, not a description doc — go to the file path for actual field definitions. Grouped by module, identity-context first.

# Details

## Identity
| Module | Key entities/models |
|---|---|
| tnt-actor-core | `ActorRating`, `ActorLocation`, `PerformanceScore`, `AvailabilitySlot`, `Badge` |
| tnt-organization-core | `KernelOrganizationDto` (sync DTO, not domain) |
| tnt-administration-core | `TntPermissionCatalog`, `TntPermissionEntry`, `TntPlatformOptions` |
| tnt-tp-core | `KycRecord`, `LoyaltyAccount`, `LoyaltyTransaction`, `ThirdPartyRating` |

## Logistics
| Module | Key entities/models |
|---|---|
| tnt-delivery-core | `AnnouncementResponse` (child of `DeliveryAnnouncement`) |
| tnt-geo-core | `RoadNode`, `RoadArc`, `PointOfInterest`, `RelayHub` |
| tnt-route-core | `Tour`, `TourStop`, `RouteWaypoint`, `RouteSegment`, `GPSMeasurement`, `KalmanState` |
| tnt-realtime-core | `GeofenceTrigger`, `LiveETAUpdate`, `GPSStreamEntry`, `ReroutingAlert`, `DeviceInfo` |
| tnt-sync-core | `DeltaRecord`, `ConflictRecord`, `VectorClock` |
| tnt-dispute-core | `DisputeEvidence`, `DisputeComment`, `EscalationRecord`, `DisputeEvent` |
| tnt-incident-core | `IncidentParticipant`, `IncidentAssignment`, `IncidentEscalation`, `IncidentDriverReplacement`, `DriverCandidate`, `VehicleInfo`, `IncidentEvidence`, `IncidentInterAgencyCooperation`, `ParcelIncidentLink`, `IncidentEventLog`, `IncidentAutomationDecision`, `IncidentVehicleSubstitution` |
| tnt-media-core | `MediaFile`, `SignatureCapture` |
| tnt-notify-core | `Notification`, `NotificationPreference`, `FreelancerOrgNotificationTemplates` |

## Business
| Module | Key entities/models |
|---|---|
| tnt-resource-core | `VehicleMaintenanceRecord`, `ResourceAllocation` |
| tnt-product-core | `ProductVariant`, `LogisticsProfile`, `ServiceOffer`, `OfferComparison` |
| tnt-inventory-core | `HubPackageEntry`, `InventoryMovement`, `InventoryAlert` |
| tnt-sales-core | `TntSalesOrderLine`, `TntAddress` |
| tnt-accounting-core | `JournalEntryLine`, `AccountCategory`, `FinancialLine`, `TrialBalanceLine` |

## Billing
| Module | Key entities/models |
|---|---|
| tnt-billing-pricing | `Promotion`, `BonusRule`, `LoyaltyRule`, `PlatformFeeRule`, `CommissionRule`, `SpecialSurchargeRule`, `SurchargeRule`, `NetworkTransitRule`, `HubStorageRule`, `PriceLineItem` |
| tnt-billing-cost | `FuelConsumptionModel`, `WearModel`, `EquipmentCostResult` |
| tnt-billing-dsl | `DslAction`, `AppliedRuleRecord`, `ValidationError` |
| tnt-billing-invoice | `InvoiceLine`, `TaxLine`, `InvoiceDiscount`, `SurchargeLineItem`, `CreditNote` |
| tnt-billing-wallet | `WalletTransaction`, `PaymentSplit`, `PaymentSplitResult`, `ReconciliationRecord`, `MoMoPayload` |
| tnt-billing-report | `RevenueReport`, `MarginReport`, `CommissionSummary`, `SurchargeAnalyticsReport`, `TemplateUsageReport`, `InvoiceReportEntry`, `FreelancerOrgReport` |
| tnt-billing-templates | `CustomPolicyTemplate`, `TemplateParameter`, `TemplatePreviewResult` |

# Links
- `domain/aggregates.md` — the roots these belong to
- `domain/value-objects.md`
- `knowledge/project-map.md` — exact file paths

---
> **Comment maintenir ce document** : ajouter une entité à la ligne de son module quand une nouvelle classe apparaît sous `domain/model/entity/` (ou équivalent). Ne pas lister les Value Objects ici — voir `value-objects.md`.
