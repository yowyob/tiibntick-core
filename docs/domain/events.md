# Purpose
Every domain event in the codebase — what gets published, by which module, and (where known) who's listening. Domain events are how modules stay decoupled (see `architecture/dependencies.md` for the event-based integration points).

# Summary
~75 domain events across 16 modules. `tnt-billing-cost`, `tnt-billing-dsl`, `tnt-billing-report`, `tnt-billing-templates`* are event-less (pure computation/read-model modules). Events are published in-process via `application/port/out` publisher interfaces, then bridged to Kafka by module-specific adapters — see `infrastructure/kafka.md` for the topic-level wiring.

# Details

## Identity
| Module | Events |
|---|---|
| tnt-actor-core | `FreelancerAssociatedEvent`, `FreelancerOrgUnlinkedEvent`, `ActorStatusChangedEvent`, `KycValidatedEvent`, `BadgeEarnedEvent`, `FreelancerOrgLinkedEvent`, `ActorLocationUpdatedEvent`, `DelivererMissionAssignedEvent` |
| tnt-organization-core | `FreelancerOrgCreatedEvent`, `FreelancerOrgVerifiedEvent`, `FreelancerOrgSuspendedEvent`, `SubDelivererAssociatedEvent`, `SubDelivererRevokedEvent`, `KycLevelUpgradedEvent` |
| tnt-tp-core | `ClientProfileRegisteredEvent` |
| tnt-administration-core | *(none)* |

## Logistics
| Module | Events |
|---|---|
| tnt-delivery-core | `DeliveryCreatedEvent`, `DeliveryPersonAssignedEvent`, `ParcelPickedUpEvent`, `DeliveryInTransitEvent`, `ParcelAtRelayPointEvent`, `DeliveryCompletedEvent`, `DeliveryFailedEvent`, `DeliveryCancelledEvent`, `AnnouncementPublishedEvent`, `AnnouncementResponseSelectedEvent`, `FreelancerOrgAssignedEvent`, `DeliveryPausedByIncidentEvent`, `DeliveryResumedFromIncidentEvent`, `MissionStatusChangedEvent` |
| tnt-geo-core | `ServiceZoneUpdatedEvent`, `RoadNodeCreatedEvent`, `TrafficConditionChangedEvent` |
| tnt-route-core | `TourOptimizedEvent`, `EtaUpdatedEvent`, `ReroutingTriggeredEvent`, `VrpFallbackActivatedEvent` |
| tnt-realtime-core | `ActorConnectedEvent`, `ActorDisconnectedEvent`, `GpsPositionUpdatedEvent`, `ETAUpdatedEvent`, `GeofenceTriggerEvent` |
| tnt-sync-core | `SyncCompletedEvent`, `SyncConflictDetectedEvent`, `EntityVersionChangedEvent` |
| tnt-dispute-core | `DisputeOpenedEvent`, `DisputeEscalatedEvent`, `MediatorAssignedEvent`, `EvidenceSubmittedEvent`, `DisputeRuledEvent`, `DisputeClosedEvent`, `CompensationProcessedEvent` |
| tnt-incident-core | *(marker class `IncidentDomainEvents` — events not individually named per the survey; check module directly for current event list)* |
| tnt-notify-core | *(consumes, mostly doesn't publish)* |

## Business
| Module | Events |
|---|---|
| tnt-resource-core | `VehicleRegisteredEvent`, `VehicleAssignedEvent`, `VehicleUnassignedEvent`, `VehicleRetiredEvent`, `FreelancerVehicleRegisteredEvent`, `FreelancerVehicleAssignedToMissionEvent`, `FreelancerVehicleReleasedFromMissionEvent`, `EquipmentAssignedEvent`, `VehicleLocationUpdatedEvent`, `MaintenanceAlertTriggeredEvent`, `VehicleSentToMaintenanceEvent` |
| tnt-product-core | `ProductCreatedEvent`, `ServiceOfferPublishedEvent` |
| tnt-inventory-core | `PackagePickedUpEvent`, `PackageDepositedEvent`, `StockLowEvent` |
| tnt-sales-core | `SalesOrderConfirmedEvent`, `SalesOrderDispatchedEvent`, `SalesOrderDeliveredEvent`, `SalesOrderCancelledEvent` |
| tnt-accounting-core | `JournalEntryPostedEvent`, `AccountingPeriodClosedEvent` |

## Billing
| Module | Events |
|---|---|
| tnt-billing-pricing | `BillingPolicyCreatedEvent`, `BillingPolicyActivatedEvent`, `BillingPolicyDeactivatedEvent`, `BillingPolicyAssignedToOrgEvent`, `PriceEvaluatedEvent`, `SpecialSurchargeTriggeredEvent` |
| tnt-billing-invoice | `InvoiceGeneratedEvent`, `InvoicePaidEvent`, `InvoiceCancelledEvent` |
| tnt-billing-wallet | `WalletDebitedEvent`, `WalletCreditedEvent`, `CommissionCalculatedEvent`, `PaymentInitiatedEvent`, `PaymentConfirmedEvent`, `PaymentFailedEvent` |
| tnt-billing-templates | `CustomTemplateSavedEvent`, `TemplateAppliedEvent` |

## Key cross-module event consumers (event-driven coupling)
| Producer event | Consumer module | Effect |
|---|---|---|
| `IncidentResolved`/`IncidentClosed` (incident-core) | tnt-delivery-core, tnt-dispute-core | Resume paused delivery / unblock dispute |
| `MissionStatusChangedEvent` (delivery-core) | tnt-incident-core, tnt-sync-core, tnt-realtime-core | Triage trigger / delta sync / live broadcast |
| `GpsPositionUpdatedEvent`/`GeofenceTriggerEvent` (realtime-core) | tnt-incident-core, tnt-sync-core | Auto-incident detection / delta sync |
| `VehicleAssignedToMission`/`VehicleReleasedFromMission` (resource-core) | tnt-delivery-core | Vehicle availability tracking |
| `InvoiceGeneratedEvent`/`InvoicePaidEvent` (billing-invoice) | tnt-billing-report, tnt-accounting-core | Analytics projection / GL posting |
| `tnt.roles.permission-changed` (not yet produced anywhere) | tnt-roles-core (`PermissionCacheInvalidationListener`) | Cache eviction — scaffold, see `security/permissions.md` |

# Links
- `infrastructure/kafka.md` — topic names and producer/consumer wiring
- `domain/workflows.md` — event sequences within a lifecycle
- `architecture/dependencies.md` — event-based integration points table

---
> **Comment maintenir ce document** : ajouter un événement dès qu'une classe `*Event.java` apparaît sous `domain/event/`. Mettre à jour la table "cross-module event consumers" quand un nouveau listener Kafka consomme un événement d'un autre module — c'est la partie la plus précieuse de ce doc, ne pas la laisser devenir obsolète.
