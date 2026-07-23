# tnt-agency-back-core — module map

Migrated from `backend/tnt-agency` (Agency monolith ERP).

| Module | Schema (global DB) | Agency origin |
|--------|-------------------|---------------|
| `tnt-agency-org-core` | `agency_org` | Agency, Branch, Hub config, **hub-ops** (`hubops` sub-package) |
| `tnt-agency-staff-core` | `agency_hr` | Staff members |
| `tnt-agency-workforce-core` | `agency_hr` | Deliverers, contracts, freelancers |
| `tnt-agency-assignment-core` | `agency_assignment` | Mission projection + delivery-core orchestration |
| `tnt-agency-commission-core` | `agency_commercial` | Commissions + billing-wallet payout |
| `tnt-agency-billing-core` | `agency_commercial` | Billing policies, estimates, invoices |
| `tnt-agency-fleet-local-core` | `agency_fleet` | Agency fleet view → `tnt-resource-core` (+ FleetMan link) |
| `tnt-agency-onboarding-core` | `agency_onboarding` | Onboarding applications |
| `tnt-agency-intake-core` | `agency_intake` | Client intake queue (approve → assignment + hubops) |
| `tnt-agency-inbox-core` | `agency_inbox` | Agency notification inbox |
| `tnt-agency-sync-core` | `agency_sync` | Offline sync server (extends `tnt-sync-core`) |
| `tnt-agency-compliance-core` | — | Disputes / incidents / claims (proxy to platform cores) |

## Cross-core rule

Platform cores (delivery, inventory, billing, resource, kernel) are called **only** from modules in this tree via `adapter/out/clients` and `AgencyPlatformClientConfig` — never from `tnt-agency` BFF.

## Phasing

1. **F1** — `tnt-agency-org-core`, `tnt-agency-staff-core` ✅
2. **F2** — `tnt-agency-workforce-core`, `tnt-agency-assignment-core` ✅
3. **F3** — onboarding, intake (+ assignment orchestration) ✅
4. **F4** — `tnt-agency-sync-core` + BFF offline contract ✅
5. **F5** — commission (wallet), fleet-local (resource), inbox, hub-ops ✅
6. **F6** — mission lifecycle ERP (create, pickup, deliver, cancel, hub deposit) + BFF proxy ✅
7. **F7** — bootstrap Liquibase masters wired in `tnt-bootstrap` ✅
8. **F8** — `tnt-agency-billing-core` + BFF billing ERP proxy ✅
9. **F9** — hub occupancy + parcel expiry + Kafka projection consumers ✅
10. **F10** — BFF ViewModels + contract tests ✅ (mappers + JSON contract unit tests; auth/sync/search/media kept as integration exceptions)

## F9 — done

| Capability | Module | Notes |
|------------|--------|--------|
| Hub occupancy endpoint + inventory HTTP sync | org-core hubops | `GET .../occupancy` |
| Hub parcel expiry job + endpoint | org-core hubops | Multi-tenant sweep when `tenant-id` unset; manual `POST .../expired/process` |
| Delivery status Kafka → mission projection | assignment-core | `DeliveryStatusConsumer` |
| Wallet payout Kafka → commission | commission-core | `WalletPayoutConsumer` |
| Inventory hub Kafka → occupancy + parcel projection | org-core hubops | `InventoryHubConsumer` |
| Actor KYC Kafka → deliverer | workforce-core | `ActorKycConsumer` |

## Follow-ups (post F9/F10)

| Item | Notes |
|------|--------|
| Document / KYC verification via Kernel proxy | Waiting on Core proxy to Kernel document-governance |
| FleetMan SSO | Optional; password-connect bridge already shipped |
| Mission create-on-Kafka when local projection missing | ✅ Creates projection when `agencyId` present in Kafka payload |
| Billing amount from real distance/weight | ✅ Uses `quotedAmount`, else `distanceKm`/`weightKg` (errors if distance missing) |
| Invoice PDF via Core billing (presigned URL) | ✅ Requires `coreInvoiceId` + tenant header; clear error if unavailable |
| Agency UploadZone proofs persist to media | ✅ UploadZone always uploads; drawers pass category + entityId |
| FleetMan token encryption key in prod | ✅ Fail-fast sans clé si `ALLOW_PLAINTEXT=false` ; doc génération dans `.env.example` |
