# Purpose
Offline, versioned reference for every HTTP endpoint the Yowyob Kernel (RT-comops, `https://kernel-core.yowyob.com/kernel-api`) exposes — so nobody needs to scrape `/swagger-ui/index.html` by hand to know what's available. TiiBnTick Core talks to the Kernel **only over HTTP** (see root `CLAUDE.md` — no `RT-comops-*-core` Maven dependency, no Kernel class import); this directory is the map of that HTTP surface.

# Summary
- `openapi.json` — raw OpenAPI 3 spec fetched from `GET /v3/api-docs`. Source of truth.
- `endpoints.md` — generated index of all operations grouped by controller tag: method, path, summary, parameters, request body schema name, response codes + schema names.
- `schemas.md` — generated index of all component schemas (DTOs) referenced from `endpoints.md`: field name, type, required.
- All three are **generated** — never hand-edit `endpoints.md`/`schemas.md`, and only refresh `openapi.json` via the script below.

As of the last refresh: 1526 operations / 1160 paths / 155 tags / 1078 schemas (title `IWM Backend API`, version `0.1.0-SNAPSHOT`).

# Details

## Refreshing this reference
```bash
scripts/fetch-kernel-openapi.sh
```
This re-downloads `openapi.json` from the live Kernel and regenerates `endpoints.md` + `schemas.md` via `scripts/gen_kernel_api_docs.py`. The Kernel connection can be slow (multi-minute download observed) — the script uses a 180s curl timeout. Review the diff and commit if the API surface changed. Override the target with `KERNEL_URL=... scripts/fetch-kernel-openapi.sh` if the Kernel moves.

## How to use these files
- Looking for "does the Kernel have an endpoint for X?" → grep `endpoints.md` for the tag/keyword.
- Need the shape of a request/response body named in `endpoints.md` (e.g. `LoginRequest`) → look it up by heading in `schemas.md`.
- Need something `endpoints.md`/`schemas.md` don't carry (full `description` text, examples, security scheme details) → it's in `openapi.json`, same names.

## Auth endpoints (used by every TiiBnTick call to the Kernel)
See `auth-controller` in `endpoints.md` for the full set. The two-step login flow TiiBnTick uses:
1. `POST /api/auth/discover-contexts` (`LoginRequest` → `principal`, `password`) — returns `selectionToken` + `contexts[].contextId` (contextId is per-call, never cache it).
2. `POST /api/auth/select-context` (`selectionToken`, `contextId`) — returns `data.session.accessToken` (Bearer JWT, ~15 min TTL) + `sessionToken`.

Every call to the Kernel (including the two above) requires headers `X-Api-Key` and `X-Client-Id`; see `foundation/tnt-auth-core` for how TiiBnTick threads these through, and the project memory `kernel-auth-flow` for working `curl` examples with current test credentials.

# Links
- `docs/architecture/dependencies.md` — why the Kernel is HTTP-only from TiiBnTick's side
- `foundation/tnt-auth-core` — `TntSecurityContext`, JWT bridge consuming these endpoints
- Live Swagger UI (for spot-checks only, not as the primary reference): `https://kernel-core.yowyob.com/kernel-api/swagger-ui/index.html`

---
> **Comment maintenir ce document** : ne pas éditer `endpoints.md`/`schemas.md` à la main — relancer `scripts/fetch-kernel-openapi.sh` et committer le diff.
