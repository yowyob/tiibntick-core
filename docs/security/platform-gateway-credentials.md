# Purpose
How platform backends (Agency, Go, Link, Market, Point Relais, App Mobile) obtain and use the `X-Client-Id`/`X-Api-Key` pair to call TiiBnTick Core's platform gateway (`/api/v1/auth/**`, `/api/v1/sso/**`, `/api/v1/onboarding/**`, plus curated `DISPUTE:*`/`SALES:*` internal proxies). Read this before onboarding a new platform or rotating a key.

# Summary
**This mechanism is fully DB-backed, not config/`.env`-based.** A dedicated module, `foundation/tnt-platform-gateway-core`, owns a persistent Client-ID/API-Key registry (`tnt_platform_clients`/`tnt_api_keys`/`tnt_client_permissions`/`tnt_api_key_rotation_history`/`tnt_client_audit_logs`) with BCrypt-hashed keys, show-once plaintext, and a two-level `resource:action` scope model. Everything is managed through an admin REST API (`/api/v1/admin/platform-clients/**`, TNT_ADMIN-only) — there are no environment variables to set per platform anymore. The earlier `.env`-based `TNT_<PLATFORM>_CLIENT_ID`/`_API_KEY` mechanism (previously documented here) was deleted entirely on 2026-07-09; this file used to describe that mechanism and was stale until this rewrite.

# Details

## 1. Where this actually lives
- **Module**: `foundation/tnt-platform-gateway-core` (package `com.yowyob.tiibntick.core.platformgateway`) — its own R2DBC schema + Liquibase, the first foundation-layer module with a DB.
- **Full design record**: [`docs/auth/platform-client-management-design.md`](../auth/platform-client-management-design.md).
- **Day-to-day operations (create/rotate/revoke a client)**: [`docs/auth/platform-client-onboarding-guide.md`](../auth/platform-client-onboarding-guide.md) — use that guide, not this file, for the actual admin API calls.
- Superseded design note: Bloc A/B (auth/SSO proxy) originally lived inline in `tnt-auth-core` (see `docs/auth/platform-client-management-design.md` history) before being extracted into this dedicated module on 2026-07-09.

## 2. What a platform actually gets
- `clientId` — a generated slug, `{platformCode}-{environment}-{6 hex chars}` (e.g. `agency-prod-43bbaa9e`), not secret.
- `apiKey` — format `tnt_<standard-base64(32 random bytes)>`, shown **once** at issuance (BCrypt-hashed at rest — Core itself cannot recover a lost key, only issue a new one).
- A `client_permissions` row per granted scope, `resource:action` format (e.g. `AUTH:*`, `DISPUTE:read`, or the global `*`). **New clients get `*` automatically at creation** — create + issue-key is immediately sufficient to call the whole gateway; `PUT /api/v1/admin/platform-clients/{id}/permissions` narrows it afterward if ever needed.

Platforms authenticate with `X-Client-Id`/`X-Api-Key` headers, checked by `PlatformApiKeyWebFilter` + `PlatformScopeAuthorizationManager` (coarse, route-level) / `@RequirePlatformScope` (fine, per-endpoint) — never Spring's native `hasAuthority()`, which doesn't understand the `resource:*`/`*` wildcard scope format. Scopes are validated via the shared `PermissionMatcher` (`tnt-common-core`), the same engine `TntPermissionEvaluator` uses for human-user RBAC.

## 3. Rotation and revocation
Handled through the admin API, not a file edit or restart:
- `POST /api/v1/admin/platform-clients/{id}/api-keys` — issue a new key; the old one stays `ACTIVE` until explicitly revoked (grace-window overlap, no forced downtime).
- `POST /api/v1/admin/api-keys/{keyId}/revoke` — immediate revocation.
- `POST /api/v1/admin/platform-clients/{id}/decommission` — retire a client entirely.
Cache invalidation on revoke: short TTL (default 45s, `tnt.platform-gateway.client-cache-ttl`) plus immediate same-instance invalidation from the admin mutation itself.

## 4. Production status (as of 2026-07-25)
Five platform-client identities are live on `https://tiibntick-core.yowyob.com`: Agency, Link, Go, Market, and App Mobile (`platformCode=APP_MOBILE`, added after the original 5-platform doc list). All have both a client and an API key issued. **None have had scopes explicitly narrowed yet** — all are running on the default `*` wildcard granted at creation; check `GET /api/v1/admin/scope-registry` for the catalogue (`AUTH`/`SSO`/`ONBOARDING`/`DISPUTE`/`SALES` observed so far) before deciding whether to tighten any of them.

**Known gotcha, now fixed**: between 2026-07-09 and 2026-07-25, `POST /api/v1/admin/platform-clients` (and the sibling API-key-issuance endpoint) returned `201 Created` with a fully-populated response but **silently persisted nothing** — the entities assign their own `@Id` client-side, and Spring Data R2DBC's default `isNew()` check treats a non-null `@Id` as "exists," issuing a no-op `UPDATE` instead of an `INSERT` (R2DBC does not throw on a zero-row update). Fixed in commit `efdecebb` by implementing `Persistable<String>` on the affected entities. See `knowledge/known-issues.md` #18 for the full write-up — the same pattern must be followed by any new R2DBC entity in this codebase that assigns its own client-side id. If you see a platform-client row that was created before 2026-07-25 17:23 UTC and behaves oddly (create succeeded, immediate read/key-issue fails as "not found"), it's likely one of the orphaned dead rows from before this fix — safe to decommission.

Distribute each platform's `clientId`+plaintext key via a secrets manager (Vault, AWS Secrets Manager, k8s Secret) — never email or chat, per the admin API's own one-time-display design (Core does not store or re-display plaintext after issuance).

# Links
- `docs/auth/platform-client-management-design.md` — architecture record, full scope-model detail
- `docs/auth/platform-client-onboarding-guide.md` — operational guide (create/rotate/revoke, curl examples)
- `docs/knowledge/known-issues.md` #18 — the R2DBC Persistable bug that blocked all client creation for ~2.5 weeks
- `security/authentication.md` — end-user JWT flow (distinct from this platform-identity mechanism)

---
> **Comment maintenir ce document** : à chaque nouveau client de plateforme créé en production, mettre à jour la section "Production status" (liste, scopes réellement accordés). Si le mécanisme d'authentification change à nouveau (ex. mTLS, rotation automatique), réviser entièrement ce fichier plutôt que de l'empiler.
