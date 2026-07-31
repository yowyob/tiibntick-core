# Purpose
**The file-finder.** Where is the controller/service/port/adapter/config/DTO/event/repository for X — answered in seconds, without grepping or opening files. This is the single most important doc for minimizing token spend in future sessions.

# Summary
Universal path pattern (applies to most of the repo's 53 modules, see `architecture/packages.md` for the 2 deviating exceptions) + a per-module quick index of the concrete class names that matter most (controllers, config classes). For exhaustive aggregate/entity/event/port lists per module, see `domain/*.md` (already organized that way — not duplicated here).

# Details

## Universal path pattern — "where is X" (standard-shape modules)
| Looking for | Path |
|---|---|
| REST controller | `<module>/src/main/java/.../adapter/in/web/*Controller.java` |
| Kafka consumer | `.../adapter/in/kafka/*Consumer.java` / `*Listener.java` |
| R2DBC repository impl | `.../adapter/out/persistence/*RepositoryAdapter.java` (or `R2dbc*Repository.java`) |
| Kafka publisher | `.../adapter/out/messaging/Kafka*EventPublisher.java` |
| Use-case interface | `.../application/port/in/*UseCase.java` |
| Outbound port interface | `.../application/port/out/I*.java` |
| Use-case impl | `.../application/service/*Service.java` |
| Aggregate/Entity/VO | `.../domain/model/{aggregate,entity,valueobject}/*.java` |
| Domain event | `.../domain/event/*Event.java` |
| `@Configuration` class | `.../config/*Config.java` |
| Liquibase changelog | `.../db/changelog/tnt-<module>-master.yaml` + `changes/*.sql` |

`tnt-billing-dsl` and `tnt-accounting-core` use `infrastructure/{adapter,config,persistence}` + `domain/{port,service}` instead — see `architecture/packages.md` for the mapping.

## Per-module quick index

| Module | Controller(s) | `@Configuration` class | Notes |
|---|---|---|---|
| tnt-roles-core | — (no REST API) | `TntRolesAutoConfiguration`, `TntRolesKafkaConfig` | RBAC core — see `security/permissions.md` |
| tnt-auth-core | — | `TntAuthAutoConfiguration` | JWT bridge — see `security/authentication.md` |
| tnt-platform-gateway-core | `PlatformAuthController`, `PlatformAuthOidcController`, `PlatformSsoController`, `ApiKeyAdminController`, `PlatformClientAdminController`, `ScopeRegistryController` | `TntPlatformGatewayAutoConfiguration`, `TntPlatformGatewayR2dbcConfig`, `TntPlatformGatewaySecurityConfig` | Client-ID/API-Key gateway — see `security/platform-gateway-credentials.md`, `auth/platform-client-management-design.md` |
| tnt-actor-core | `DelivererController`, `FreelancerController`, `ActorKycController` | (module config not separately profiled) | |
| tnt-organization-core | — (managed via admin/actor APIs) | | |
| tnt-tp-core | `TpKycController`, `TntClientProfileController`, `LoyaltyController`, `RatingController` | | |
| tnt-administration-core | `TntAdministrationController` | | RBAC management API |
| tnt-geo-core | — (internal, consumed via ports) | | |
| tnt-route-core | — (internal) | `RouteCoreConfig` | OR-Tools VRP solver: `VrpSolverService` |
| tnt-delivery-core | `DeliveryController`, `DeliveryAnnouncementController`, `DeliveryPersonController` | `DeliveryModuleConfig` | |
| tnt-dispute-core | `DisputeController`, `EvidenceController`, `MediationController` | `TntDisputeCoreConfig` | |
| tnt-incident-core | `IncidentController`, `IncidentAgencyController` | `IncidentCoreConfig`, `IncidentKafkaConfig` | Has many no-op port fallbacks for cross-module ports not yet wired |
| tnt-realtime-core | `SseController` | `RealtimeCoreConfig`, `RedisRealtimeConfig`, `KafkaRealtimeConfig`, `WebSocketConfig` | Redis-heavy — see `infrastructure/redis.md` |
| tnt-sync-core | `SyncController` | (module config not separately profiled) | |
| tnt-notify-core | (REST controller exists, name not separately confirmed) | `NotifyCoreAutoConfiguration` | |
| tnt-media-core | — (consumed via ports) | `MediaCoreConfig` | MinIO client bean here — see `infrastructure/redis.md`-adjacent, actually MinIO not Redis |
| tnt-trust-core | `TrustApiController` | `TntTrustAutoConfiguration`, `KafkaProducerConfig` | L6 (trust/, transversal — not L3 logistics, see `architecture/modules.md`). Standard `application/{port,service}` layout. Implements outbound ports owned by every calling module (delivery/incident/billing-pricing/billing-wallet/actor-core so far) via adapters under `adapter/out/<module>/`, and depends on each of those modules directly (one-directional, no calling module ever depends back) |
| tnt-go-freelancer-point-back-core | `AnnouncementController`, `AuthController`, `ClientController`, `AdminController`, `AdminRelayPointController`, `AdminFreelancerController`, `AdminClientController`, `AddressController` (+more) | `GoFreelancerPointCoreConfig`, `GofpSecurityConfig`, `AdminBootstrapConfig`, `CorsConfig`, `ElasticsearchConfig` | L6-Bis — see `architecture/modules.md`. Only `GoFreelancerPointCoreConfig` is `@Import`ed by `tnt-bootstrap` as of 2026-07-27, check current wiring before assuming other coreBackend beans are live |
| tnt-agency-back-core | (per sub-module, see `architecture/modules.md` sub-table) | — | `packaging=pom` aggregator, no code/config of its own — 14 real sub-modules each under `com.yowyob.tiibntick.core.agency.<name>` |
| tnt-link-back-core | `NetworkNodeController`, `LinkBoardController`, `DaoZoneController`, `LeaderboardController`, `NetworkAlertController`, `LinkNotificationController`, `ActorIdentityController` | `LinkBackR2dbcConfig`, `LinkKafkaConsumerConfig` | L6-Bis — network nodes, bulletin board, DAO zones, gamification |
| tnt-market-back-core | `MarketListingController`, `MarketOrderController`, `MarketSearchController`, `MarketServiceOfferController`, `MarketCampaignController`, `MerchantContractController`, `ProviderReviewController`, `MarketStatsController` | `MarketBackCoreConfig`, `MarketBackR2dbcConfig` | L6-Bis — provider discovery, quotes, market orders |
| tnt-resource-core | — | | Vehicle/equipment domain — richest event set (11 events) |
| tnt-product-core | — | | |
| tnt-inventory-core | — | | |
| tnt-sales-core | `SalesOrderController` | | |
| tnt-accounting-core | `AccountController`, `AccountingReportController`, `JournalEntryController` | | Deviating package layout — see `architecture/packages.md` |
| tnt-hrm-core | `HrmEmployeeController`, `HrmEmployeeSelfServiceController`, `HrmExpenseController`, `HrmKpiController` (+12 more, all extend `AbstractHrmProxyController`) | | Pure Kernel HRM proxy, raw `JsonNode` |
| tnt-legacy-documents-core | `BonAchatController`, `BonCommandeController`, `BonLivraisonController`, `BonReceptionController`, `FactureFournisseurController`, `FactureProformaController`, `NoteCreditController` (all extend `AbstractLegacyDocumentProxyController`) | | Pure Kernel legacy-documents proxy |
| tnt-billing-dsl | `DslRuleController` | | Deviating package layout |
| tnt-billing-pricing | `PricingController`, `BillingPolicyController` | | |
| tnt-billing-cost | `CostController` | `CostModuleConfig` | WebClients to tnt-route-core/tnt-geo-core |
| tnt-billing-invoice | `InvoiceController` | | |
| tnt-billing-wallet | `WalletController`, `PaymentWebhookController` | `WalletModuleConfig` | MTN/Orange/Stripe `WebClient` beans here |
| tnt-billing-report | `ReportingController` | `TntBillingReportAutoConfiguration` | |
| tnt-billing-templates | `PolicyTemplateController` | `BillingTemplatesConfig` | |
| tnt-bootstrap | — (orchestrator) | `TntCoreConfig` (imports everything), `TntKafkaConfig`, `TntActuatorConfig`, `TntSecurityConfig`, `TntOpenApiConfig`, `LiquibaseConfig` | See `architecture/modules.md` for the full `@Import` list |

## By artifact type — where else to look
| Artifact | Where the exhaustive list lives |
|---|---|
| Aggregates | `domain/aggregates.md` |
| Entities | `domain/entities.md` |
| Value Objects | `domain/value-objects.md` |
| Domain Events | `domain/events.md` |
| Inbound/Outbound Ports per module | `domain/bounded-contexts.md` linked agent reports — for exact interface names, grep `application/port/{in,out}/` in the target module (pattern is reliable, see table above) |
| REST endpoints (full detail) | `api/rest.md`, or live at `/swagger-ui.html` |
| Kafka topics/producers/consumers | `infrastructure/kafka.md` |
| Redis usage | `infrastructure/redis.md` |
| MinIO/object storage | `infrastructure/database.md`-adjacent — see `tnt-media-core`'s `MediaCoreConfig`, `MinioStorageClient`, `IncidentMediaStorageAdapter` |
| Health indicators / actuator endpoints | `infrastructure/monitoring.md` |
| `TntPermission` constants | `security/permissions.md` |
| `TntRole` definitions | `security/roles.md` |
| Liquibase changelogs per module | `infrastructure/database.md` |

# Links
- `architecture/packages.md` — the pattern this map is built on
- `domain/*.md` — exhaustive DDD element lists per module
- `architecture/modules.md` — module ownership/purpose

---
> **Comment maintenir ce document** : c'est le document le plus consulté — le maintenir à jour est prioritaire. Ajouter une ligne au "Per-module quick index" dès qu'un nouveau controller/config significatif apparaît. Ne PAS dupliquer les listes exhaustives d'agrégats/événements/ports ici — elles vivent dans `domain/*.md`, ce document ne fait que pointer vers elles.
