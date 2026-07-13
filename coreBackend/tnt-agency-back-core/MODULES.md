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
| `tnt-agency-fleet-local-core` | `agency_fleet` | Agency fleet view → `tnt-resource-core` |
| `tnt-agency-onboarding-core` | `agency_onboarding` | Onboarding applications |
| `tnt-agency-intake-core` | `agency_intake` | Client intake queue (approve → assignment + hubops) |
| `tnt-agency-inbox-core` | `agency_inbox` | Agency notification inbox |
| `tnt-agency-sync-core` | `agency_sync` | Offline sync server (extends `tnt-sync-core`) |

## Cross-core rule

Platform cores (delivery, inventory, billing, resource, kernel) are called **only** from modules in this tree via `adapter/out/clients` and `AgencyPlatformClientConfig` — never from `tnt-agency` BFF.

## Phasing

1. **F1** — `tnt-agency-org-core`, `tnt-agency-staff-core`
2. **F2** — `tnt-agency-workforce-core`, `tnt-agency-assignment-core`
3. **F3** — onboarding, intake (+ assignment orchestration)
4. **F4** — `tnt-agency-sync-core` + BFF offline contract
5. **F5** — commission (wallet), fleet-local (resource), inbox, hub-ops
6. **F6** — mission lifecycle ERP (create, pickup, deliver, cancel, hub deposit) + BFF proxy
7. **F7** — bootstrap Liquibase masters wired in `tnt-bootstrap` ✅
8. **F8** — `tnt-agency-billing-core` + BFF billing ERP proxy ✅
9. **F9** — hub occupancy + parcel expiry (org-core hubops) + Kafka projection consumers
10. **F10** — BFF ViewModels + contract tests

## Known gaps (F9 target)

| Gap | Module | Status |
|-----|--------|--------|
| Hub occupancy endpoint + inventory sync | org-core hubops | F9 |
| Hub parcel expiry job + endpoint | org-core hubops | F9 |
| Delivery status Kafka → mission projection | assignment-core | F9 |
| Wallet payout Kafka → commission | commission-core | F9 |
| Inventory hub Kafka → occupancy | org-core hubops | F9 |
| Actor KYC Kafka → deliverer | workforce-core | F9 |
| Compliance ERP module | — | BFF exception (documented) |
