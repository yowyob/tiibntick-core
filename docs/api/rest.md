# Purpose
REST endpoint index across all modules — base paths, key endpoints, and where to find each controller. Full live spec is always at `/swagger-ui.html` (springdoc 3.x, see `knowledge/known-issues.md` for the version history) — this doc is the offline, grep-fast version.

# Summary
38 `@RestController` classes, **217 endpoints** total (87 GET, 103 POST, 8 PUT, 8 DELETE, 11 PATCH — verified via `grep -rE "@(Get|Post|Put|Delete|Patch)Mapping" --include=*.java`, excludes class-level `@RequestMapping` base-path declarations). Three multi-tenancy conventions coexist (path variable, header, JWT-derived) — see `# Multi-tenancy patterns` below before adding a new controller.

# Details

## Module → base path(s) → controller

| Module | Base path(s) | Controller(s) |
|---|---|---|
| tnt-administration-core | `/api/v1/admin` | `TntAdministrationController` |
| tnt-actor-core | `/api/v1/deliverers`, `/api/v1/freelancers`, `/api/v1/actors/kyc` | `DelivererController`, `FreelancerController`, `ActorKycController` |
| tnt-tp-core | `/api/v1/tnt-tp/{kyc,clients,loyalty,ratings}` | `TpKycController`, `TntClientProfileController`, `LoyaltyController`, `RatingController` |
| tnt-delivery-core | `/api/v1/tenants/{tenantId}/deliveries`, `/api/v1/tenants/{tenantId}/delivery-announcements`, `/api/v1/deliverers` | `DeliveryController`, `DeliveryAnnouncementController`, `DeliveryPersonController` |
| tnt-incident-core | `/api/v1/incidents` | `IncidentController`, `IncidentAgencyController` |
| tnt-dispute-core | `/api/v1/disputes` (+ `/evidence`, `/mediation` sub-paths) | `DisputeController`, `EvidenceController`, `MediationController` |
| tnt-sync-core | `/api/v1/sync` | `SyncController` |
| tnt-realtime-core | `/api/v1/realtime/sse` (SSE, not classic REST) | `SseController` |
| tnt-sales-core | `/api/sales/orders` | `SalesOrderController` |
| tnt-accounting-core | `/api/v1/accounting/{accounts,reports,journal-entries}` | `AccountController`, `AccountingReportController`, `JournalEntryController` |
| tnt-billing-invoice | `/api/v1/billing/invoices` | `InvoiceController` |
| tnt-billing-wallet | `/billing/wallet`, `/billing/wallet/webhooks` | `WalletController`, `PaymentWebhookController` |
| tnt-billing-pricing | `/api/v1/billing/pricing`, `/api/v1/billing/policies` | `PricingController`, `BillingPolicyController` |
| tnt-billing-cost | `/billing/cost` | `CostController` |
| tnt-billing-report | `/api/v1/billing/reports` | `ReportingController` |
| tnt-billing-dsl | `/api/v1/billing/dsl` | `DslRuleController` |
| tnt-billing-templates | `/api/v1/billing/templates` | `PolicyTemplateController` |

## Notable endpoint groups (full detail: Swagger UI)

**Delivery** (`DeliveryController`) — public tracking endpoint exists: `GET /track/{trackingCode}` (no auth). Lifecycle endpoints (`/pickup`, `/transit/start`, `/relay/{id}/deposit`, `/relay/resume`, `/location`, `/complete`, `/fail`, `/cancel`) map to the state machine in `domain/workflows.md`.

**Incident** (`IncidentController`) — `POST /` (report), `POST /driver-withdrawal` (special case), `/{id}/{triage,auto-resolve,escalate,resolve,close,cancel}`, `/{id}/evidence`, `/{id}/timeline`, `/{id}/blockchain`, `/kpi`.

**Sync** (`SyncController`) — `POST /push`, `GET /pull`, `GET /bootstrap` (first sync), `GET /schema/duckdb` (offline client schema). Headers: `X-User-Id`, `X-Tenant-Id`, `X-Device-Id` (optional).

**Realtime SSE** (`SseController`) — `GET /tracking/{trackingCode}`, `GET /mission/{missionId}`, `GET /fleet/{freelancerOrgId}` — `text/event-stream`, 15s heartbeat comment frames, events named `eta-update`/`mission-update`/`fleet-gps`.

**Wallet** (`WalletController` + `PaymentWebhookController`) — `/{userId}/balance`, `/{userId}/credit`, `/pay` (202 Accepted — async), `/{userId}/transactions`, `/{userId}/{freeze,unfreeze}`; webhooks for MTN MoMo / Orange Money / Stripe under `/billing/wallet/webhooks`.

## Multi-tenancy patterns (pick the right one when adding endpoints)
| Pattern | Used by | Example |
|---|---|---|
| Path variable | tnt-delivery-core | `/api/v1/tenants/{tenantId}/deliveries` |
| `X-Tenant-Id` header | sync, realtime SSE, billing-invoice, wallet, reports, disputes | `GET /api/v1/billing/invoices/{id}` + header |
| `X-Organization-Id`/`X-Agency-Id` headers | sales, accounting | `POST /api/sales/orders` + headers |
| JWT-derived (`@CurrentUser`) | actor-core, administration, KYC | tenant/actor/org IDs read from `TntSecurityContext`, no explicit param |

New endpoints should prefer the **JWT-derived** pattern (no client-supplied tenant ID to validate) unless the endpoint is intentionally public/cross-tenant (e.g. tracking by code).

## Pagination
Zero-indexed `page`/`size` query params (default `page=0&size=20`), e.g. `GET /api/v1/disputes?page=0&size=20&status=OPEN`.

## Response wrapping
Inconsistent across modules — `tnt-actor-core` wraps in `ApiResponse<T>` (`{success, data, message}`); billing/incident/dispute modules return plain DTOs. **Known inconsistency**, not a convention to copy blindly — check the module's existing controllers before adding a new endpoint.

# Links
- `api/security.md` — `@RequirePermission`/`@PreAuthorize` per endpoint
- `api/errors.md` — exception → HTTP status mapping
- `domain/workflows.md` — state machines these endpoints drive
- `knowledge/known-issues.md` — springdoc/swagger-ui version history

---
> **Comment maintenir ce document** : un nouveau controller = une nouvelle ligne dans le tableau "Module → base path". Ne pas dupliquer le détail des DTOs ici (ils changent souvent) — Swagger UI (`/swagger-ui.html`) est la source de vérité pour les schémas exacts.
