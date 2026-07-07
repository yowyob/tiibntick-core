# Purpose
How a request gets from "JWT in the Authorization header" to a usable `TntSecurityContext` in your reactive code.

# Summary
JWT is issued by the Kernel (YowAuth0), validated locally (cached RSA public key, no network call per request), and bridged into `TntSecurityContext` — TiiBnTick's own vocabulary, decoupled from the raw JWT claim names. Module: `foundation/tnt-auth-core`.

# Details

## JWT claims contract
| Claim | Maps to | Type |
|---|---|---|
| `sub` | `userId` | UUID |
| `tenant_id` | `tenantId` | UUID |
| `actor_id` | `actorId` | UUID |
| `organization_id` | `organizationId` | UUID |
| `agency_id` | `agencyId` | UUID |
| `permissions` | `permissions` | `Set<String>` (accepts JSON array or comma-separated string) |
| `roles`/authorities | `roles` | `Set<String>`, split from permissions via `TntRole.isKnownRole()` |
| `iat`/`exp`/`jti` | `issuedAt`/`expiresAt`/`tokenId` | standard JWT claims |

Parsed in `TntJwtValidator` (`foundation/tnt-auth-core/.../application/service/TntJwtValidator.java`).

## `TntSecurityContext` (the thing your code actually reads)
Immutable record, fields: `userId`, `tenantId`, `actorId`, `organizationId`, `agencyId`, `email`, `roles` (`Set<String>`), `permissions` (`Set<String>`), `authenticated` (boolean), `freelancer` (boolean), `clientApplicationId`.
Built by `TntSecurityContextService.buildContext(Authentication)`, reading the Kernel-issued `ApiKeyAuthenticationToken` from `ReactiveSecurityContextHolder` (WebFlux reactor `Context`, not a thread-local — works correctly across async boundaries).

## Public key caching
`KernelPublicKeyProvider` fetches the Kernel's RSA public key **once at startup** (from the JWKS endpoint) and caches it — token validation never makes a network call on the hot path.

## Dev/test JWT generation
`iwm.security.jwt.auto-generate-key-pair=true` (default in dev/test profiles) — an in-memory RSA key pair is generated at startup, no external auth server needed locally. **Never set this `true` in staging/prod** — see `staging`/`prod` profile blocks in `application.yml` (`auto-generate-key-pair: false`, real key loaded from `JWT_PRIVATE_KEY_PATH`/Docker secret).

## Config (`tnt.auth.*`)
| Property | Default | Purpose |
|---|---|---|
| `tnt.auth.service-code` | `TNT_AGENCY` | Client ID registered in YowAuth0 |
| `tnt.auth.token-cache-ttl` | `PT14M` | Must be strictly less than JWT `expires_in` (typically 15min) |
| `tnt.auth.actor-resolution-enabled` | `true` | Enriches `TntSecurityContext` with actor profile (deliverer/freelancer) via `IYowAuthTntAdapter` |
| `tnt.auth.allow-anonymous-context` | `false` (`true` in test profile only) | When true, unauthenticated requests get an anonymous context instead of 401 |

## Anonymous/test bypass
`tnt.auth.allow-anonymous-context=true` + `tnt.roles.aop-enabled=false` is the **test-profile-only** pattern (`application.yml` `test` block) — never replicate this in default/dev/staging/prod. A past bug (`@Profile("r2dbc")` gating real adapters) was caused by code that only worked because tests silently ran with relaxed security — see `knowledge/known-issues.md`.

# Links
- `security/authorization.md` — what happens after the context is built
- `security/roles.md` — `TntRole` enum
- `knowledge/known-issues.md` — anonymous-context test bypass incident

---
> **Comment maintenir ce document** : si un claim JWT change de nom ou de type côté Kernel, mettre à jour le tableau "JWT claims contract" immédiatement — c'est le point d'intégration le plus fragile avec le Kernel.
