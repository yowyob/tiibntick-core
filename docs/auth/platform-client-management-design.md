# Platform Client Identity Management — Design

> Operating this system day-to-day (creating clients, scoping, rotating, revoking)? See the [Platform Client Onboarding Guide](./platform-client-onboarding-guide.md) instead — this document is the architecture/design record.

Status: **IMPLEMENTED 2026-07-09 (Phase 0 + Phase 1 of §6, in one pass, at the user's explicit request to implement end-to-end).** `tnt-platform-gateway-core` exists, the full reactor (all modules) compiles and existing unit tests pass. There is no Phase 2/3 anymore (see below) — the system is DB-only from day one.

**Post-implementation revisions (2026-07-09, same day, follow-up questions from the user):**
- **API key format changed** to the user's exact spec: `tnt_<standard-base64(32 random bytes)>` — same alphabet/length as `openssl rand -base64 32` (e.g. `tnt_9Iu01WuhBgm3pzlnM0oqd8nDBZscFVa3imab7z9M0YA=`), replacing the earlier `tnt_sk_<env>_<random>` shape. See §2.2.
- **`.env` migration fallback removed entirely** (not just deferred) — the user confirmed no platform was ever consuming this system via the legacy `TNT_<PLATFORM>_CLIENT_ID`/`_API_KEY` env vars, so there was nothing to migrate away from. `TntPlatformGatewayProperties.clients`/`ClientEntry` deleted, `PlatformClientAuthenticationService` is now DB-only, `application.yml`'s static per-platform block removed. **This retires Phase 2/3 of §6 entirely** — see the revised migration section.
- **New clients get the global wildcard scope (`*`) automatically at creation** — the user explicitly asked for "create + issue a key = immediately fully usable," overriding this doc's earlier least-privilege assumption that scopes are always a separate, deliberate admin step. `PUT .../permissions` still exists to narrow a specific client's access afterward, but nothing requires calling it. See §2.4/§4/§5.1.
- **Swagger's Authorize dialog** now shows dedicated `X-Client-Id`/`X-Api-Key` fields (two new `apiKey`-type `SecurityScheme`s, `ClientIdAuth`/`ApiKeyAuth`) applied only to `PlatformAuthController`/`PlatformSsoController`; `PlatformAuthOidcController` (fully public, see §1.1) is marked with an empty `@SecurityRequirements` to show no lock at all. Previously these operations showed the same global Bearer-JWT lock as every other endpoint, which was wrong for this auth mechanism.
Owner: MANFOUO Braun.
Scope: replaces the `.env`-based `TNT_<PLATFORM>_CLIENT_ID` / `TNT_<PLATFORM>_API_KEY` mechanism with a persistent, admin-managed Client-ID / API-Key system (Stripe / AWS IAM / GitHub Apps-inspired), **plus** a two-level authorization model letting each platform client be scoped to specific gateway operations and specific business modules.

**Decisions validated 2026-07-08** (first round) — all implemented as decided:
- Hashing: **BCrypt** (`ApiKeyHashingService`, via `BCryptPasswordEncoder`).
- Cache invalidation on revoke: **short TTL only** (`PlatformClientAuthenticationService`'s Caffeine cache, default 45s, `tnt.platform-gateway.client-cache-ttl`), plus same-instance immediate invalidation from every admin mutation.
- Audit storage: **Postgres table only** (`tnt_client_audit_logs`, written async/fire-and-forget by `PlatformClientAuditRecorder`).

**Decision reversed 2026-07-09, then implemented as reversed**:
- Module placement was validated 2026-07-08 as "stays in `tnt-auth-core`". Working through the scope model (§2) surfaced that the platform-gateway surface is a distinct bounded context from `tnt-auth-core`'s original, narrower purpose (Kernel JWT → `TntSecurityContext` translation). **Built as a new foundation module, `tnt-platform-gateway-core`** (package `com.yowyob.tiibntick.core.platformgateway`) — see §2.0 for what moved and what stayed, now written in the past tense since it's done.

**Implementation deviation from the design doc, discovered while building** (flagged, not silently done): §2.0 said the new module "deliberately does **not** depend on `tnt-auth-core`... nor on `tnt-roles-core`." That statement was correct for the M2M scope-checking mechanism itself, but turned out to be incomplete once the admin API was actually built: the admin controllers are a **human/JWT-authenticated surface** (an administrator, not a platform backend) and legitimately need `tnt-auth-core`'s `@CurrentUser`/`TntSecurityContext` (to record `createdBy`/`grantedBy`/`revokedBy` actor ids) and `tnt-roles-core`'s `@RequirePermission` (to gate every admin endpoint to `TNT_ADMIN`) — exactly like any other admin-facing controller elsewhere in the app. **`tnt-platform-gateway-core` does depend on both, but only for the admin API; the platform-client authentication/scope mechanism itself (`PlatformClientAuthenticationService`, `PlatformApiKeyWebFilter`, `PlatformScopeAuthorizationManager`, `PlatformScopeAspect`) still has zero dependency on either.**

---

## 0. Constraints given

- All new admin endpoints are usable **only** by a TiiBnTick platform administrator (`TNT_ADMIN` role).
- Platforms (`tnt-agency`, `tnt-go`, `tnt-point`, `tnt-link`, `tnt-market`, ...) **consume** credentials — they never create, rotate, or manage their own. Lifecycle is entirely a Core/admin responsibility.
- New constraint (2026-07-09): a platform client must be restrictable to **specific gateway operations** (auth/SSO/onboarding) **and** to **specific business modules** (e.g. "Go can only reach delivery, not billing") — this is the scope model developed in §2.4–§2.6.
- Design only in this phase. No code changes until reviewed.

---

## 1. Analyse de l'existant

### 1.1 Mécanisme actuel

- `TntPlatformGatewayProperties` (`@ConfigurationProperties(prefix = "tnt.auth.platform-gateway")`) loads a list of `{platform-code, client-id, api-key, enabled}` entries from `application.yml`/env at startup (`TNT_AGENCY_CLIENT_ID`, `TNT_AGENCY_API_KEY`, etc. — one pair per platform).
- `PlatformClientRegistry` builds an **immutable in-memory `Map<clientId, PlatformClientApplication>`** once, from those properties. `authenticate(clientId, apiKey)` does a map lookup + `MessageDigest.isEqual` constant-time comparison, filtered on `enabled`.
- `PlatformApiKeyWebFilter` reads `X-Client-Id`/`X-Api-Key` headers, calls the registry, and on success stashes the resolved `PlatformClientApplication` in the exchange attribute `tnt.platform.client`; on failure returns a generic `401 PLATFORM_UNAUTHORIZED`. **This is a real gap for the scope model**: an exchange attribute is invisible to Spring Security's own authorization machinery (`hasAuthority`, `@PreAuthorize`, `ReactiveAuthorizationManager`) — see §2.4.
- This filter is wired into **one dedicated `SecurityWebFilterChain`** (`TntAuthGatewaySecurityConfig`, `@Order(10)`), scoped via `securityMatcher` to exactly `/api/v1/auth/**`, `/api/v1/sso/**`, `/api/v1/onboarding/**`, with `.authorizeExchange(ex -> ex.anyExchange().permitAll())` — i.e. **today there is no per-path authorization at all** beyond "did the API key check pass."
- `PlatformClientApplication` is a record: `(platformCode, clientId, apiKey, enabled)` — the **raw** API key is held in memory for the process lifetime.
- `tnt-auth-core` has **zero R2DBC / Liquibase dependency today** — deliberate (see `TntPlatformGatewayProperties` javadoc anticipating exactly this migration).
- All of Bloc A (`PlatformAuthController`, `PlatformAuthOidcController`, `KernelAuthGatewayAdapter`, `ProxyKernelAuthUseCase`, `KernelAuthGatewayService`, the `KernelApiEnvelope`/`KernelAuthResult`/`KernelRawResponse` wire DTOs) and Bloc B (`PlatformSsoController`, `KernelSsoGatewayService`, `ProxyKernelSsoUseCase`, the `Sso*Request`/`Sso*Response` models) currently live in `tnt-auth-core`, alongside its original, narrower purpose: `TntJwtValidator`, `KernelPublicKeyProvider`, `TntSecurityContextService`, `@CurrentUser`/`TntCurrentUserArgumentResolver`/`ReactiveSecurityContextExtractor` (Kernel-issued JWT → `TntSecurityContext`, used by every JWT-authenticated request across the whole app, completely unrelated to the platform gateway).
- No foundation-layer module (`yow-event-kernel`, `yow-i18n-kernel`, `tnt-common-core`, `tnt-auth-core`, `tnt-roles-core`) owns any database table today — `tnt-bootstrap`'s master Liquibase changelog only `include`s from L2 (identity) upward.
- `tnt-roles-core`'s `TntPermissionEvaluator.matches(...)` (private method) already implements exactly the permission-matching semantics this design wants to reuse for platform scopes: exact (`resource:action`), resource wildcard (`resource:*`), global wildcard (`*`), and a `#SCOPE` suffix for context-scoping (`#AGENCY:<id>`, `#SYSTEM`, `#TENANT`) — verified by reading the source directly. It is private and lives inside a class that also pulls in the Kernel permission resolver, tenant/agency context, etc. — too heavy and too coupled to reuse as-is (see §2.4).
- `docs/api/rest.md` already maintains a "Module → base path(s) → controller" table across all 16 business modules with `@RestController`s — confirmed to exist and to be current; useful as the human-readable index of what "module DELIVERY" etc. means, though (per §2.6) the platform-scope enforcement won't match against these raw paths directly.
- `tnt-administration-core` (owner of Bloc C's `PlatformAgencyOnboardingController`) already depends on `tnt-auth-core`, `tnt-roles-core`, and `tnt-organization-core` (confirmed in its `pom.xml`) — relevant to the migration cost in §8.
- `tnt-auth-core`'s `AutoConfiguration.imports` (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`) currently registers exactly two auto-configurations: `TntAuthAutoConfiguration` and `TntAuthGatewaySecurityConfig` — the second one is entirely platform-gateway-specific and would move wholesale to the new module.

### 1.2 Limites de l'approche actuelle

- **No lifecycle**: creating, rotating, or revoking a credential requires editing `.env`/CI secrets and redeploying/restarting.
- **No fine-grained authorization per platform**: any client that authenticates gets equal, unconditional access to everything behind the platform-gateway chain (all of Bloc A + Bloc B + Bloc C) — there's no way to say "Point Relais can call tracking/read but not billing," or even "Go can call SSO but not onboarding."
- **No expiration or rotation window.**
- **No audit.**
- **Secret handling**: raw key in `.env`/heap, no hash-only storage.
- **Doesn't scale operationally** past a handful of platforms (already 5: Agency, Go, Link, Market, Point Relais).

### 1.3 Points du code impactés

See §8 (fully rewritten this round to reflect the module split).

### 1.4 Risques de migration

- **Breaking currently-working platforms** during rollout — mitigated by the fallback phase (§6).
- **Latency in a pre-auth hot path** — needs a cache in front of the repository.
- **Revocation must be effectively immediate for incident response** — accepted trade-off: short TTL only (decided 2026-07-08).
- **Show-once secrets** — UX/process change from today.
- **Splitting a module that already ships production traffic** (Bloc A/B/C are live) is itself a migration with its own blast radius, on top of the DB migration — see §6 for how the two are sequenced.
- Must not blur the line with Kernel-side credentials (`TNT_KERNEL_*`) — unchanged, out of scope.

---

## 2. Nouvelle architecture cible

```
Client Application (tnt-agency / tnt-go / tnt-point / tnt-link / ...)
        │
        │  X-Client-Id + X-Api-Key
        ▼
tnt-platform-gateway-core  (/api/v1/auth/**, /sso/**, /onboarding/**, /platform/**)
        │
        ▼
PlatformApiKeyWebFilter (@Order(10) chain, unchanged position)
        │
        ▼
PlatformClientLookup (cache) ──miss──▶ PlatformClientRepositoryAdapter (R2DBC)
        │ hit                                    │
        ▼                                        ▼
ApiKeyValidator (prefix index + hash compare, constant-time)
        │
        ▼
PlatformClientAuthenticationToken built, scopes = GrantedAuthority-carrying
Set<String> in resource:action[#..] form, pushed into the reactive SecurityContext
        │
        ├──▶ Coarse check (gateway block / module allowlist): custom
        │    ReactiveAuthorizationManager on path-matchers, backed by the
        │    SAME PermissionMatcher used by tnt-roles-core's user RBAC
        │
        └──▶ Fine check (curated proxy endpoints only): @RequirePlatformScope
             annotation + aspect, same PermissionMatcher
        │
        ▼
Business logic:
  - Bloc A/B/C proxy controllers (unchanged behavior, now scope-gated)
  - NEW curated per-module proxy controllers (§2.6), which call the real
    business use-cases in tnt-delivery-core / tnt-billing-* / etc.
        │
        ▼ (async, non-blocking)
Audit trail (client_audit_logs, Postgres only)
```

### 2.0 Découpage en module : `tnt-platform-gateway-core`

**Why not keep it in `tnt-auth-core`, reversing the 2026-07-08 answer**: `tnt-auth-core`'s own `pom.xml` description states its job precisely — "Contains NO authentication logic. Translates the Kernel security token into a TiiBnTick-enriched `TntSecurityContext`, exposes `@CurrentUser`... defines the `IYowAuthTntAdapter` out-port." That's a JWT-bridge concern used by **every** JWT-authenticated request in the whole app. The platform gateway (client registry, API-key filter, Bloc A/B/C proxy controllers, and now scope management + a persistent CRUD admin API + rotation + audit) answers a completely different question — "which platform backend is calling Core, and what is it allowed to touch" — for a different kind of principal (a platform backend, not a JWT-carrying human/service-account user). These two concerns already have almost no code-level coupling today (the only shared thing is the module they happen to sit in). Growing the platform-gateway side with a DB, an admin CRUD surface, and a whole authorization layer inside `tnt-auth-core` would leave that module owning two unrelated bounded contexts of comparable size — the original sin CLAUDE.md's hexagonal-per-module convention is meant to avoid.

This also matches what `CORE_KERNEL_GATEWAY_SPEC.md` suggested from the start (a dedicated `tnt-platform-kernel-gateway-core`); it wasn't built as a separate module for Bloc A alone because that was too small to justify it — the calculus changes now that the scope of this document (persistent identity + rotation + two-level authorization) is on the table.

**What moves out of `tnt-auth-core` into the new `foundation/tnt-platform-gateway-core`** (package `com.yowyob.tiibntick.core.platformgateway`):
- `TntPlatformGatewayProperties`, `PlatformClientRegistry`, `PlatformClientApplication`, `PlatformApiKeyWebFilter`, `TntAuthGatewaySecurityConfig`.
- Bloc A: `PlatformAuthController`, `PlatformAuthOidcController`, `KernelAuthGatewayAdapter`, `IKernelAuthGatewayPort`, `ProxyKernelAuthUseCase`, `KernelAuthGatewayService`, `KernelApiEnvelope`, `KernelAuthResult`, `KernelRawResponse`.
- Bloc B: `PlatformSsoController`, `KernelSsoGatewayService`, `ProxyKernelSsoUseCase`, `ResolveSsoContextRequest/Response`, `SsoLaunchRequest/Response`, `SsoTokenExchangeRequest/Response`.
- Everything new in this document: `PlatformClient`/`ApiKey` domain + persistence, admin CRUD API, rotation/revocation, audit, the scope model (§2.4–§2.6), `PermissionMatcher`'s *consumer* side (the utility itself moves to `tnt-common-core`, see §2.4).

**What stays in `tnt-auth-core`** (unchanged): `TntJwtValidator`, `KernelPublicKeyProvider`, `TntSecurityContextService`, `TntSecurityContext`/`TntTokenClaims`/`TntTokenPair`/`TntUserIdentity` domain models, `CurrentUser`/`TntCurrentUserArgumentResolver`/`ReactiveSecurityContextExtractor`, `TntAuthExceptionHandler`, `IYowAuthTntAdapter`/`NoOpYowAuthTntAdapter`, `TntAuthAutoConfiguration`, `TntAuthProperties`. This is the module's original, narrower scope — genuinely untouched by this work.

**Bloc C** (`PlatformAgencyOnboardingController`) stays in `tnt-administration-core` as today — only the security-perimeter definition for `/api/v1/onboarding/**` moves (from `TntAuthGatewaySecurityConfig` to the new module's equivalent config class).

**Dependency graph after the split**: `tnt-platform-gateway-core` sits in `foundation/`, alongside (not depending on) `tnt-auth-core`/`tnt-roles-core` — it depends only on `tnt-common-core` (for shared types and the new `PermissionMatcher`, §2.4) and the Kernel-facing `kernelWebClient` bean convention already used by `KernelAuthGatewayAdapter`. It deliberately does **not** depend on `tnt-auth-core` (no shared bean needed — the new module defines its own ObjectMapper/config beans rather than reusing `tnt-auth-core`'s `tntAuthObjectMapper` qualifier) nor on `tnt-roles-core` (that's exactly what extracting `PermissionMatcher` into `tnt-common-core` avoids). `tnt-administration-core` gains a **new** dependency on `tnt-platform-gateway-core` (for the onboarding security-perimeter matcher and, if adopted, `@RequirePlatformScope` on `PlatformAgencyOnboardingController`) **in addition to** its existing dependency on `tnt-auth-core` (still needed there for `@CurrentUser`/`TntSecurityContext` on its JWT-authenticated endpoints, which are a separate concern from the onboarding gateway's platform-key endpoints).

### 2.1 Représentation d'une plateforme cliente

Unchanged from the 2026-07-08 round: one `PlatformClient` row per (platform, environment) pair, `clientId` public, `name`, `platformCode`, `environment` (DEV/STAGING/PROD), `status`, `description`, `contactEmail`, audit columns.

### 2.2 Représentation d'une API Key

`ApiKey` belongs to one `PlatformClient`, multiple ACTIVE keys allowed (rotation), `key_prefix` + `key_hash` (BCrypt) only, show-once plaintext. **Key shape (revised 2026-07-09, at the user's explicit spec):** `tnt_<standard-base64(32 random bytes)>` — same alphabet/length as `openssl rand -base64 32` (44 chars incl. `=` padding), just prefixed with `tnt_` (48 chars total), e.g. `tnt_9Iu01WuhBgm3pzlnM0oqd8nDBZscFVa3imab7z9M0YA=`. Drops the earlier `tnt_sk_<env>_` shape (which encoded the environment in the key itself as a defense-in-depth cross-check) in favor of this simpler, more recognizable format — the environment cross-check is no longer performed at the key-parsing level.

### 2.3 Validation d'une requête entrante

1. Extract `X-Client-Id` + `X-Api-Key`.
2. Prefix-indexed lookup (cache → `PlatformClientRepositoryAdapter`).
3. Constant-time hash compare.
4. Reject generically on any failure (client/key/status/expiry).
5. **Changed from the 2026-07-08 draft**: on success, build a real `PlatformClientAuthenticationToken implements Authentication` — `principal` = the resolved `PlatformClient`, `authorities` = the client's granted scope strings wrapped as `GrantedAuthority` — and push it into the reactive `SecurityContext` for the request (see §2.4 for exactly how, given the chain uses `NoOpServerSecurityContextRepository`). This replaces the current raw exchange-attribute stash (`tnt.platform.client`), which was invisible to any Spring Security authorization primitive.
6. Emit an audit record asynchronously.

### 2.4 Modèle de scopes — conception approfondie

**Two levels, not one system** (this is the key structural decision):

- **Niveau 1 — Scopes de bloc gateway**: coarse gate on the 3 (soon 4, see §2.6) path prefixes the gateway exposes: `/api/v1/auth/**`, `/api/v1/sso/**`, `/api/v1/onboarding/**`, `/api/v1/platform/**` (new, curated business-module proxies).
- **Niveau 2 — Allowlist de modules métier**: within the new `/api/v1/platform/**` prefix, per-business-module scoping (`DELIVERY`, `BILLING`, ...) — detailed in §2.6.

Both levels are expressed in the **same scope format**, reusing `TntPermissionEvaluator`'s existing convention exactly rather than inventing a second one: `resource:action`, with `resource:*` (any action on that resource) and `*` (superuser — internal tooling only) as wildcards. Examples: `AUTH:*`, `SSO:*`, `ONBOARDING:*`, `DELIVERY:read`, `BILLING:*`.

Note on naming: "resource" here means a **gateway block or business module code** (`AUTH`, `SSO`, `ONBOARDING`, `DELIVERY`, `BILLING`, ...), not a fine-grained business resource like the user-RBAC side's `mission`/`invoice`/`wallet`. Keeping the resource vocabulary coarse (module-level) is deliberate — see §2.6 for why finer-than-module granularity is deferred, not designed away.

**Shared matching logic — `PermissionMatcher`**: `TntPermissionEvaluator.matches(...)` in `tnt-roles-core` is private and entangled with the Kernel permission resolver and agency/tenant scope extraction — not reusable as-is, and reimplementing the same wildcard semantics a second time for platform scopes would create exactly the kind of "two systems that quietly drift" risk this design wants to avoid. **Proposed extraction**: a small, pure, dependency-free `PermissionMatcher` (static method, `boolean matches(Set<String> granted, String resource, String action)`, implementing exact/`resource:*`/`*` matching with no Spring/Kernel/agency-scope awareness — that context-scoping suffix (`#AGENCY:<id>` etc.) is specific to the user-RBAC side and stays in `tnt-roles-core`, layered on top of the shared matcher rather than folded into it) moves to `tnt-common-core`. Both `TntPermissionEvaluator` (refactored to delegate its base-matching step to it) and the new platform-gateway scope check call the same function — one definition of what a wildcard means, project-wide.

**A technical correction to the original idea**: Spring's built-in `hasAuthority("DELIVERY:read")` on a `SecurityWebFilterChain` is a plain string-equality check — it does **not** understand `DELIVERY:*` or `*` as satisfying a `DELIVERY:read` requirement. Using the framework's native `hasAuthority()`/`hasRole()` for the coarse, route-level checks would therefore silently fail to honor wildcard scopes, which is exactly the ergonomics this design wants (an admin granting a client "all of DELIVERY" via one scope string). **Resolution**: don't use `hasAuthority()` at all. Define a small custom `ReactiveAuthorizationManager<AuthorizationContext>` — a standard, idiomatic Spring Security WebFlux extension point, wired via `.pathMatchers(...).access(customManager)` instead of `.hasAuthority(...)` — that pulls the current `Authentication`'s authorities and calls the shared `PermissionMatcher.matches(...)`. This keeps exactly one evaluation path (the shared matcher) for both the coarse route-level gate and the fine per-endpoint annotation below, rather than two different semantics (framework string-equality vs. custom wildcard-aware aspect) that could drift apart.

**Fine-grained enforcement** — `@RequirePlatformScope(resource = "delivery", action = "read")`, a new annotation + small AOP aspect **mirroring `tnt-roles-core`'s existing `@RequirePermission`/`TntPermissionAspect` pattern exactly** (same shape: `@Around` on `Mono`/`Flux`-returning methods, reads the current reactive `SecurityContext`, calls `PermissionMatcher.matches(...)` against the `PlatformClientAuthenticationToken`'s authorities instead of a JWT's). Applied on the new curated proxy endpoints (§2.6) where module-level coarse gating isn't precise enough (e.g. read vs. write within `DELIVERY`).

**Making the `Authentication` visible without a `ServerSecurityContextRepository`**: the chain deliberately uses `NoOpServerSecurityContextRepository` (stateless, no session) — so `PlatformApiKeyWebFilter` must inject the built `PlatformClientAuthenticationToken` into the **reactive context** for the rest of the chain to see it (the standard reactive-Spring-Security pattern: wrap the downstream `chain.filter(exchange)` call with `.contextWrite(ReactiveSecurityContextHolder.withAuthentication(token))`), rather than relying on any repository to populate it. Both the custom `ReactiveAuthorizationManager` (framework-invoked, receives `Mono<Authentication>` sourced from that same reactive context) and `TntPermissionAspect`-style aspect (reads `ReactiveSecurityContextHolder.getContext()` directly, exactly like `TntPermissionEvaluator.canFromCurrentContext` already does today) then see it consistently.

### 2.5 Environnements

Unchanged: `environment` lives on `PlatformClient`; key prefix encodes env as a defense-in-depth cross-check.

### 2.6 Accès aux modules métier — le patron du proxy curé

This is the part that answers "give access to specific TiiBnTick Core modules per client ID."

**The choice, and why**: two ways to let a platform client reach a business module (e.g. `tnt-delivery-core`) were considered:

1. **Proxy curé par module (chosen)**: `tnt-platform-gateway-core` exposes its **own** stable endpoints under `/api/v1/platform/{module}/**` (e.g. `/api/v1/platform/delivery/track/{code}`), each annotated `@RequirePlatformScope(resource = "delivery", action = "read")`, which internally calls the real use-case (e.g. `tnt-delivery-core`'s existing application service/port) after the scope check passes.
2. **Double condition on existing business controllers** — e.g. `@PreAuthorize("hasRole('...') or hasAuthority('DELIVERY:read')")` sprinkled onto `DeliveryController` directly.

Chosen: **1**. Reasons:
- **Auditability**: the entire surface a platform can ever reach is one small, explicit set of controllers in one module — not a scattered, ever-growing set of dual-purpose annotations across 16+ business modules that a reviewer has to hunt for.
- **No mixing of authorization models on the same endpoint**: today's business controllers are designed around a JWT-carrying human/service-account principal (`TntSecurityContext`, `@RequirePermission`, tenant/agency scoping via `#AGENCY:<id>`). A platform-key principal is a fundamentally different kind of caller (no tenant context of its own, no user identity) — forcing both onto the same `@PreAuthorize` expression is exactly the kind of two-systems-in-one-annotation smell that tends to rot.
- **Change isolation**: if a business module's internal routes/DTOs change, the curated proxy's own stable contract doesn't have to change in lockstep — the proxy calls the *use-case* (the hexagonal `application/port/in` interface), not the HTTP controller, so it's insulated from the business module's own web-layer churn.
- Matches exactly the pattern already established for Bloc A/B/C (a dedicated proxy layer calling the real thing, not the platform hitting the real thing directly).

**Consequence for the "module allowlist" idea**: because platforms never call raw business paths directly, the earlier idea of matching a client's `allowedModules` against `docs/api/rest.md`'s real base-path table doesn't apply as originally sketched — `docs/api/rest.md` remains the right reference for *which module owns which use-case to call*, but the actual authorization boundary is the curated proxy's own routes and their `@RequirePlatformScope` annotations, not the underlying business paths. Worth adding a short cross-reference note in `docs/api/rest.md` once this ships, pointing at wherever the curated-proxy route table ends up (a new `docs/auth/platform-gateway-scopes.md`, one row per curated endpoint: scope required → proxy route → real use-case it forwards to).

**Build the mechanism generically, but only wire real proxy endpoints on demand.** The annotation, aspect, `ReactiveAuthorizationManager`, and `PlatformClient`/`ApiKey`/scope persistence should all be built once, now. Actual curated proxy controllers per business module (`PlatformDeliveryProxyController`, a future `PlatformBillingProxyController`, ...) should only be added when a real platform has a real need — building proxies for modules nobody is asking to reach yet is speculative surface area (per this repo's own convention against unrequested abstraction). Today, the concrete, already-real need is: Bloc A (`AUTH:*`), Bloc B (`SSO:*`), Bloc C (`ONBOARDING:*`) — no business-module proxy has been requested by name yet, so none should be built in the first implementation pass; §2.6's mechanism just needs to be ready to accept one without further redesign.

---

## 3. Proposition du modèle de données

All tables now owned by `tnt-platform-gateway-core` (moved from the "tnt-auth-core" attribution in the 2026-07-08 draft), new changelog `db/changelog/tnt-platform-gateway-master.yaml`, included from `tnt-core-master.yaml` (first L1 entry — same fact as before, different module name).

```sql
-- platform_clients: one row per (platform, environment)
CREATE TABLE platform_clients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       VARCHAR(64)  NOT NULL UNIQUE,   -- public identifier, e.g. "agency-prod-7f3a1c"
    name            VARCHAR(120) NOT NULL,           -- "TNT Agency"
    platform_code   VARCHAR(40)  NOT NULL,           -- "AGENCY", "GO", "LINK", "MARKET", "POINT_RELAIS", ...
    environment     VARCHAR(16)  NOT NULL,           -- DEV | STAGING | PROD
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | SUSPENDED | DECOMMISSIONED
    description     TEXT,
    contact_email   VARCHAR(160),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(80),
    updated_by      VARCHAR(80)
);

-- api_keys: many-to-one with platform_clients; supports overlapping ACTIVE keys during rotation
CREATE TABLE api_keys (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_client_id  UUID NOT NULL REFERENCES platform_clients(id),
    key_prefix          VARCHAR(24) NOT NULL,
    key_hash            VARCHAR(255) NOT NULL,        -- BCrypt hash
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | ROTATING | REVOKED | EXPIRED
    expires_at          TIMESTAMPTZ,
    last_used_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at          TIMESTAMPTZ,
    revoked_by          VARCHAR(80),
    revoked_reason      VARCHAR(255)
);
CREATE INDEX idx_api_keys_client_prefix ON api_keys(platform_client_id, key_prefix);

-- client_permissions: granted scopes, in the resource:action[#..] format shared with user RBAC
-- (§2.4) — "resource" here is a gateway-block or business-module code (AUTH, SSO, ONBOARDING,
-- DELIVERY, ...), matched by the shared PermissionMatcher (tnt-common-core).
CREATE TABLE client_permissions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_client_id  UUID NOT NULL REFERENCES platform_clients(id),
    scope               VARCHAR(60) NOT NULL,        -- "AUTH:*", "SSO:*", "ONBOARDING:*", "DELIVERY:read", "*", ...
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by          VARCHAR(80),
    UNIQUE (platform_client_id, scope)
);

-- api_key_rotation_history
CREATE TABLE api_key_rotation_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_client_id  UUID NOT NULL REFERENCES platform_clients(id),
    old_api_key_id      UUID REFERENCES api_keys(id),
    new_api_key_id      UUID NOT NULL REFERENCES api_keys(id),
    rotated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    rotated_by          VARCHAR(80),
    reason              VARCHAR(255)
);

-- client_audit_logs: one row per gateway request (success or failure)
CREATE TABLE client_audit_logs (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_client_id    UUID REFERENCES platform_clients(id),
    client_id_attempted   VARCHAR(64),
    endpoint              VARCHAR(200) NOT NULL,
    http_method           VARCHAR(10)  NOT NULL,
    outcome               VARCHAR(24)  NOT NULL, -- SUCCESS | INVALID_KEY | UNKNOWN_CLIENT | SUSPENDED | EXPIRED | FORBIDDEN_SCOPE
    ip_address            VARCHAR(45),
    user_agent            VARCHAR(255),
    occurred_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Decided (2026-07-08): Postgres table only for audit, with a retention/archival job planned before go-live.

---

## 4. APIs d'administration

Base path: `/api/v1/admin/platform-clients` (in `tnt-platform-gateway-core` now). **Every endpoint requires `TNT_ADMIN`.**

| Method & Path | Behavior | Payload | Response |
|---|---|---|---|
| `POST /platform-clients` | Register a new platform client — **grants it the `*` wildcard scope automatically** (decided 2026-07-09: immediately usable, no separate scopes call required) | `{name, platformCode, environment, description?, contactEmail?}` | `201` + client |
| `GET /platform-clients` | List/search, paginated | query: `platformCode?, environment?, status?, page, size` | `200` |
| `GET /platform-clients/{id}` | Detail incl. key metadata, scopes, audit summary | — | `200` |
| `PATCH /platform-clients/{id}` | Update name/description/contact/status | partial fields | `200` |
| `DELETE /platform-clients/{id}` | Soft-decommission, revokes all active keys | — | `204` |
| `POST /platform-clients/{id}/api-keys` | Issue a new API key | `{expiresAt?}` | `201` + plaintext secret **once** |
| `GET /platform-clients/{id}/api-keys` | List keys (prefix, status, lastUsedAt, expiry) | — | `200` |
| `POST /api-keys/{keyId}/rotate` | Issue replacement key, grace-period rotation | `{graceHours?, reason?}` | `201` + new plaintext secret **once** |
| `POST /api-keys/{keyId}/revoke` | Immediate revocation | `{reason}` | `200` |
| `PUT /platform-clients/{id}/permissions` | Replace granted scope set — **now `resource:action` strings** (e.g. `{"scopes": ["AUTH:*", "SSO:*", "DELIVERY:read"]}`), validated against the registry of known gateway-block/module codes | `{scopes: [...]}` | `200` |
| `GET /platform-clients/{id}/audit-logs` | Paginated audit trail | query: `outcome?, from?, to?, page, size` | `200` |
| `GET /scope-registry` *(new)* | Lists every valid scope "resource" code (gateway blocks + wired business-module proxies) and their available actions — lets an admin UI populate a checkbox list instead of hand-typing scope strings | — | `200` |

Design: typed DTOs, `@RestController`.

---

## 5. Flux d'utilisation

### 5.1 Création d'une nouvelle plateforme (as built 2026-07-09)

```
Admin (TNT_ADMIN JWT)
  │
  ▼
POST /platform-clients                → client_id issued (slug), status ACTIVE,
                                          scope "*" granted automatically (decided
                                          2026-07-09 — immediately usable, no
                                          separate scopes call required)
  │
  ▼
POST /platform-clients/{id}/api-keys  → plaintext secret returned ONCE
                                          (tnt_<base64>, see §2.2)
  │
  ▼
(optional) PUT .../permissions        → only if this specific client should be
                                          narrower than full access
  │
  ▼
Secret transmitted to the platform team OUT-OF-BAND (never via this API)
  │
  ▼
Platform starts sending X-Client-Id / X-Api-Key — works immediately, no third call needed
```

### 5.2 Appel normal d'une plateforme

```
tnt-agency → POST /api/v1/sso/context/resolve   (X-Client-Id, X-Api-Key)
  │
  ▼
PlatformApiKeyWebFilter: cache/DB (client's valid keys) → hash compare (constant-time, BCrypt)
  │
  ├─ fail → 401 PLATFORM_UNAUTHORIZED + audit(outcome=INVALID_KEY|UNKNOWN_CLIENT)
  │
  └─ pass → build PlatformClientAuthenticationToken (scopes as authorities),
            contextWrite into the reactive SecurityContext
              │
              ▼
        Custom ReactiveAuthorizationManager (route-level, coarse: "SSO:*"?)
              │
              ├─ denied → 403 + audit(outcome=FORBIDDEN_SCOPE)
              │
              └─ granted → (if a curated proxy endpoint) @RequirePlatformScope
                            fine check via the same PermissionMatcher
                              │
                              ├─ denied → 403 + audit(outcome=FORBIDDEN_SCOPE)
                              │
                              └─ granted → business logic → audit(outcome=SUCCESS), async
```

### 5.3 Rotation d'une clé — unchanged from §5.3 of the 2026-07-08 draft.

### 5.4 Accès à un module métier (nouveau)

```
tnt-go → GET /api/v1/platform/delivery/track/{code}   (X-Client-Id, X-Api-Key)
  │
  ▼
Coarse route check: client scopes contain "DELIVERY:*" or "DELIVERY:read"? (custom AuthorizationManager)
  │
  ├─ no → 403
  │
  └─ yes → @RequirePlatformScope(resource="delivery", action="read") on the curated
           proxy controller method → PermissionMatcher re-checks the exact action →
           calls tnt-delivery-core's existing tracking use-case (application/port/in,
           not its HTTP controller) → response
```

---

## 6. Stratégie de migration (retired — DB-only from day one)

Originally a 4-phase `.env`→DB migration plan. **Revised 2026-07-09**: the user confirmed no platform was ever consuming this system via the legacy `TNT_<PLATFORM>_CLIENT_ID`/`_API_KEY` env vars, so there was nothing to migrate away from — the fallback was removed entirely rather than deprecated over time. Only Phase 0 and a simplified Phase 1 apply; Phase 2/3 (progressive per-platform cutover, fallback removal) no longer exist as concepts.

**Phase 0 — Module split, behavior-preserving. ✅ DONE 2026-07-09.**
Created `tnt-platform-gateway-core`, moved Bloc A/B out of `tnt-auth-core` into it, updated `tnt-bootstrap`'s pom/`application.yml`/`tnt-core-master.yaml`. `TntCoreConfig` itself needed **no change** — both modules use the `AutoConfiguration.imports` mechanism (not manual `@Import`), so Spring Boot picks up the new module's auto-configuration automatically from the classpath, same as it already did for the old Bloc A/B location. `tnt-administration-core` needed **no new dependency** either — Bloc C's `PlatformAgencyOnboardingController` never referenced any of the moved classes directly; only the security-perimeter matcher (which lives entirely inside `TntPlatformGatewaySecurityConfig`) needed to know about `/api/v1/onboarding/**`.

**Phase 1 — DB-backed registry. ✅ DONE 2026-07-09, revised same day to drop the fallback.**
DB schema + entities + admin APIs + scope model built. Initially shipped as a composite registry (DB-first, `.env`-config fallback granting `*`) for backward compatibility — then the fallback was deleted entirely once the user confirmed it had no real consumers (`TntPlatformGatewayProperties.clients`/`ClientEntry` removed, `PlatformClientAuthenticationService` is DB-only). Full reactor compiles; existing unit tests pass.

**Onboarding a real platform today**: `POST /platform-clients` → `POST .../api-keys` → (optional) `PUT .../permissions` → hand the secret to the platform team out-of-band. No `.env`/redeploy step involved at all, for any platform, from the start.

---

## 7. Sécurité

- Hashing: **BCrypt** (decided).
- Prefix-indexed lookup, constant-time hash compare.
- Brute-force protection: per-client-id/IP sliding window in Redis (new capability).
- Expiration: optional `expires_at`.
- Rotation: dual-active-key grace window.
- Revocation: **short TTL cache only** (decided).
- Audit: every attempt, generic failure messages.
- Kernel/platform separation stays absolute.
- No secret logging.
- Show-once secrets.
- **New this round**: the coarse/fine scope-check split (§2.4) is itself a security-relevant design choice — using the framework's naive `hasAuthority()` for wildcard scopes would have been a **silent under-enforcement bug** (an admin granting `DELIVERY:*` would find route-level checks written as `hasAuthority("DELIVERY:read")` never actually match it, since `hasAuthority` doesn't expand wildcards) or, if written the other way round, an **over-grant bug**. The custom `ReactiveAuthorizationManager` + shared `PermissionMatcher` avoids both failure modes by construction — worth calling out in code review once implemented, since it's an easy mistake to reintroduce by reaching for `hasAuthority()` out of habit.

---

## 8. Impact code — as built 2026-07-09

**New module**: `foundation/tnt-platform-gateway-core` (`com.yowyob.tiibntick.core.platformgateway`), hexagonal layout per CLAUDE.md convention:
- `adapter/in/web/{PlatformAuthController, PlatformAuthOidcController, PlatformSsoController}.java` (moved verbatim from `tnt-auth-core`)
- `adapter/in/web/admin/{PlatformClientAdminController, ApiKeyAdminController, ScopeRegistryController}.java` + `admin/dto/*` (new — 12 request/response records)
- `adapter/in/web/{PlatformApiKeyWebFilter, RequirePlatformScope, PlatformScopeAspect, PlatformClientAuthenticationToken, PlatformScopeAuthorizationManager}.java` (moved + new)
- Curated business-module proxy controllers (`PlatformDeliveryProxyController`, ...): **not built**, per §2.6's own decision — no real platform demand yet, only the generic mechanism needed to exist.
- `adapter/out/kernel/KernelAuthGatewayAdapter.java` (moved)
- `adapter/out/persistence/{entity/*, *R2dbcRepository, *RepositoryAdapter, mapper/PlatformClientPersistenceMapper}.java` (new — 5 entities, 5 repositories, 5 repository adapters)
- `application/port/in/{ProxyKernelAuthUseCase, ProxyKernelSsoUseCase}.java` (moved) + `PlatformClientAdminUseCase.java` (new — **one cohesive port**, not five+ single-method interfaces, mirroring this codebase's existing convention of grouping closely-related operations, e.g. `ProxyKernelAuthUseCase`)
- `application/port/out/{IKernelAuthGatewayPort}.java` (moved) + `{IPlatformClientRepository, IApiKeyRepository, IClientPermissionRepository, IApiKeyRotationRepository, IClientAuditLogRepository}.java` (new)
- `application/service/{KernelAuthGatewayService, KernelSsoGatewayService}.java` (moved) + `{PlatformClientAdminService, ApiKeyHashingService, PlatformClientAuthenticationService, PlatformClientAuditRecorder, PlatformScopeRegistry}.java` (new)
- `domain/model/{KernelApiEnvelope, KernelAuthResult, KernelRawResponse, Sso*}.java` (moved) + `{PlatformClient, ApiKey, ClientPermission, ApiKeyRotationRecord, ClientAuditLog, IssuedApiKey, PlatformClientApplication, ApiKeyStatus, ClientStatus, Environment, AuditOutcome, ScopeResourceDefinition}.java` (new — no separate `PlatformScope` enum, per §2.4 scopes are plain validated strings)
- `domain/exception/TntPlatformGatewayException.java` (new, replaces the SSO-related factories moved out of `tnt-auth-core`'s `TntAuthException`)
- `config/{TntPlatformGatewayAutoConfiguration, TntPlatformGatewaySecurityConfig, TntPlatformGatewayProperties, TntPlatformGatewayR2dbcConfig}.java`
- `db/changelog/tnt-platform-gateway-master.yaml` + `changes/001..005_*.sql` (tables prefixed `tnt_`, ids `VARCHAR(36)` storing `UUID.toString()` — matches this repo's existing R2DBC convention, e.g. `tnt-dispute-core`, rather than a native Postgres `uuid` column)
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (2 entries)

**Removed from `tnt-auth-core`**: everything listed as "moves out" in §2.0; its `AutoConfiguration.imports` shrinks to just `TntAuthAutoConfiguration`; its `pom.xml` drops the now-unused `springdoc-openapi` dependency (no controllers left needing `@Operation`/`@Tag`) but is otherwise unchanged — it stays exactly the thin JWT bridge its own description already claims to be.

**Modified elsewhere**:
- `tnt-platform-gateway-core/pom.xml` — depends on `tnt-common-core` (always) **and**, unlike §2.0's original plan, also `tnt-auth-core` + `tnt-roles-core` (admin API only — see the deviation note at the top of this document).
- `tnt-bootstrap` — pom gains a dependency on `tnt-platform-gateway-core`; `application.yml`'s `tnt.auth.platform-gateway.*` block moved to top-level `tnt.platform-gateway.*` (new `ConfigurationProperties` prefix, plus a new `client-cache-ttl` key); `tnt-core-master.yaml` gains an `L1 — Foundation` section (new, since no L1 module had a changelog before) including `tnt-platform-gateway-master.yaml`. **`TntCoreConfig` itself needed no change** (see §6 Phase 0).
- `tnt-roles-core`'s `TntPermissionEvaluator.matches(...)` — refactored to delegate its base exact/`resource:*`/`*` matching to the new `tnt-common-core` `PermissionMatcher`, keeping its own `#SCOPE`-suffix handling on top. Also gained a new exclusive `TntPermission.PLATFORM_CLIENTS_MANAGE` constant.
- `tnt-common-core` — new `com.yowyob.tiibntick.common.security.PermissionMatcher` utility class (pure, no Spring dependency).
- Docs: `CLAUDE.md`, `README.md`, `docs/api/rest.md`, `docs/api/security.md`, `docs/security/permissions.md` all updated to reference the new module (see the repo's actual diffs for wording — not reproduced here to avoid this design doc drifting out of sync with them).

---

## 9. Questions ouvertes — statut final après implémentation (2026-07-09)

**Décidées (2026-07-08, inchangées) :** hashing BCrypt, cache TTL court, audit Postgres seul. Implemented as decided.

**Décidée puis inversée (2026-07-09), puis implémentée telle quelle :** module placement — **ne reste plus dans `tnt-auth-core`, part dans un nouveau module `tnt-platform-gateway-core`** (§2.0). The user explicitly authorized end-to-end implementation after this reversal was discussed; Phase 0 (§6) is done.

**Décidées et implémentées ce tour (2026-07-09) :**
- Scope format: **`resource:action` strings** (`PlatformClient`/`ClientPermission.scope`), reusing `TntPermissionEvaluator`'s exact convention.
- Shared matching engine: **`PermissionMatcher` extracted to `tnt-common-core`**, used by both `TntPermissionEvaluator` and the new platform-scope checks.
- Enforcement: **`PlatformScopeAuthorizationManager`** (coarse, route-level, wired per gateway block in `TntPlatformGatewaySecurityConfig`) + **`@RequirePlatformScope`/`PlatformScopeAspect`** (fine, per-endpoint) — both call `PermissionMatcher`, never `hasAuthority()`.
- Business-module access mechanism: curated-proxy pattern **designed and left ready** (`/api/v1/platform/**` already in the security matcher); **no actual proxy controller built**, per the already-decided "on real demand only" rule — nothing to build yet.
- Build scope: confirmed — mechanism generic, no speculative per-module proxies.

**Resolved during implementation (reasonable calls made, not re-asked, since the user's instruction was to implement end-to-end without stopping):**
1. **Client-ID format**: **slug** — `{platformCode}-{environment}-{6 random hex chars}`, lowercased (e.g. `agency-prod-9f2a1c3d`), generated by `PlatformClientAdminService.generateClientId`.
2. **`client_audit_logs` end-user identity**: **not implemented** — platform-level audit only, exactly the "out of scope for v1" recommendation. `platform_client_id`/`client_id_attempted` only; adding end-user identity later is a non-breaking additive column.
3. **Admin endpoint base path**: **`/api/v1/admin/platform-clients`** (+ `/api/v1/admin/api-keys`, `/api/v1/admin/scope-registry`), as proposed, consistent with the rest of the app's `/api/v1` convention.
4. **Module/package naming**: **`tnt-platform-gateway-core` / `com.yowyob.tiibntick.core.platformgateway`**, as proposed — built.
5. **`TntPermissionEvaluator` → `PermissionMatcher` refactor**: **done in this same pass**, not deferred — it was a small, low-risk, mechanical extraction (see §8).

**Same-day follow-up decisions (2026-07-09, after the user reviewed the running system):**
6. **API key format**: changed to the user's exact spec, `tnt_<standard-base64(32 bytes)>` — see §2.2. Drops the earlier environment-in-key defense-in-depth cross-check; not replaced by anything.
7. **Default scope on client creation**: **`*` (full access) granted automatically** — reverses this doc's earlier least-privilege assumption. `PUT .../permissions` remains available to narrow a specific client afterward.
8. **`.env` fallback**: **removed entirely**, not deferred — confirmed to have zero real consumers. Phase 2/3 of §6 no longer exist as concepts (see §6).
9. **Swagger `Authorize`**: fixed to show only `X-Client-Id`/`X-Api-Key` for the platform-gateway proxy controllers (previously incorrectly showed the same Bearer-JWT lock as every other endpoint).

**No remaining open questions.** Every item from the original design has either been implemented as decided, implemented per an explicit later instruction, or made moot (Phase 2/3).
