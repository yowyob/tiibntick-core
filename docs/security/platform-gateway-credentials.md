# Purpose
How to issue, distribute, rotate, and revoke the `X-Client-Id`/`X-Api-Key` pair each platform backend (Agency, Go, Link, Market, Point Relais) uses to call TiiBnTick Core's platform gateway (`/api/v1/auth/**`, `/api/v1/sso/**`, `/api/v1/onboarding/**`). Read this before onboarding a new platform or rotating an existing key.

# Summary
The registry is config-driven (`tnt.auth.platform-gateway.clients` in `application.yml`, bound by `TntPlatformGatewayProperties`, enforced by `PlatformApiKeyWebFilter`) — not a database table. Each platform's pair is distinct from `TNT_KERNEL_*` (Core's own identity toward the Kernel); platforms never see or need Kernel credentials. See `CORE_KERNEL_GATEWAY_SPEC.md` (repo root) for the architecture this implements.

# Details

## 1. Generate the values
- `client-id`: a readable identifier, not secret. Convention: `tnt-<platform>-<env>`, e.g. `tnt-agency-prod`.
- `api-key`: a strong random secret — generate with:
  ```bash
  openssl rand -base64 32
  ```

## 2. Set them on the Core side
Environment variables, one pair per platform: `TNT_AGENCY_CLIENT_ID`/`TNT_AGENCY_API_KEY`, `TNT_GO_CLIENT_ID`/`TNT_GO_API_KEY`, `TNT_LINK_CLIENT_ID`/`TNT_LINK_API_KEY`, `TNT_MARKET_CLIENT_ID`/`TNT_MARKET_API_KEY`, `TNT_POINT_RELAIS_CLIENT_ID`/`TNT_POINT_RELAIS_API_KEY`.

| File | Role |
|---|---|
| `tnt-bootstrap/.env.example` | Local dev — safe placeholder values, copy to `.env` (gitignored) |
| `tnt-bootstrap/.env.prod.example` | Staging/prod — `CHANGE_ME_*` placeholders, real values come from your secret manager |
| `tnt-bootstrap/docker-compose.yml` | Passes the env vars above into the `tiibntick-core` container |

Local dev: `cp tnt-bootstrap/.env.example tnt-bootstrap/.env` and adjust. Never commit a real `.env`. In staging/prod, source these from your secret manager (Vault, AWS Secrets Manager, k8s Secret, ...) — not a file on disk — and inject at deploy time.

`PlatformClientRegistry` skips any pair left blank at startup rather than erroring — this is the supported way to leave a platform disabled.

## 3. Hand off to the platform team
Each platform receives only its own pair plus the Core base URL — never Kernel credentials:
```env
TNT_CORE_BASE_URL=https://tiibntick-core.yowyob.com
TNT_AGENCY_CLIENT_ID=tnt-agency-prod
TNT_AGENCY_API_KEY=<the generated secret>
```
Transmit via your own secret manager/vault — never email or chat in plaintext.

## 4. Rotate a key
No zero-downtime dual-key overlap is implemented (not currently needed). To rotate:
1. Generate a new `api-key`.
2. Update the env var on Core, restart the instance.
3. Update the same value on the platform's side.

There is a short window where the platform's old key is rejected until step 3 completes — coordinate the rotation window with the platform team if that matters for their traffic.

## 5. Disable a platform
Leave both `TNT_<PLATFORM>_CLIENT_ID` and `TNT_<PLATFORM>_API_KEY` blank and restart — no code change needed.

# Links
- `CORE_KERNEL_GATEWAY_SPEC.md` (repo root) — full gateway architecture
- `foundation/tnt-auth-core/.../config/TntPlatformGatewayProperties.java` — property binding
- `foundation/tnt-auth-core/.../adapter/in/web/PlatformApiKeyWebFilter.java` — enforcement
- `foundation/tnt-auth-core/.../config/TntAuthGatewaySecurityConfig.java` — security chain wiring
- `security/authentication.md` — end-user JWT flow (distinct from this platform-identity mechanism)

---
> **Comment maintenir ce document** : à chaque nouvelle plateforme ajoutée au registre (`TntPlatformGatewayProperties`), ajouter sa paire de variables d'env ici et dans `.env.example` / `.env.prod.example` / `docker-compose.yml`.
