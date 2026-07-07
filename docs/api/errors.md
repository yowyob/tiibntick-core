# Purpose
How errors are represented over HTTP — exception types, status codes, response shape. Read this before adding a new exception type or `@ExceptionHandler`.

# Summary
RFC 7807 Problem Detail is the target convention, but **not universally applied yet** — some modules use it fully, others return ad-hoc error bodies. Check the module's existing `*ExceptionHandler` before assuming the format.

# Details

## Problem Detail convention (where applied)
Standard fields: `type` (URI), `title`, `detail`, `status`, `timestamp`, plus module-specific extra properties (e.g. `from`/`to` state on a transition error).

## Module exception handlers

| Module | Handler class | Exception → status |
|---|---|---|
| tnt-delivery-core | `DeliveryExceptionHandler` | `DeliveryNotFoundException`→404, `AnnouncementNotFoundException`→404, `InvalidDeliveryStateTransitionException`→409, `DeliveryDomainException` (catch-all)→400, `WebExchangeBindException`→400, generic→500 |
| tnt-dispute-core | `DisputeExceptionHandler` | `DisputeNotFoundException`→404, `DisputeStateException`→409, `DisputeAccessDeniedException`→403, `IllegalArgumentException`→400, generic→500 |
| tnt-billing-pricing | `BillingPricingExceptionHandler` | (policy/pricing validation errors) |
| tnt-billing-templates | `BillingTemplatesExceptionHandler` | (template validation errors) |
| tnt-billing-dsl | `DslBillingExceptionHandler` | (DSL parse/evaluation errors) |
| tnt-roles-core | `TntRoleException` (thrown, not a handler) | `forbidden(resource, action)` → consumed by global security exception handling → 403 |

⚠️ Type URI format differs by module — `tnt-delivery-core` uses `urn:tiibntick:delivery:not-found`, `tnt-dispute-core` uses `https://tiibntick.yowyob.com/errors/dispute-not-found`. Pick whichever your module's siblings already use; don't introduce a third format.

## Permission-denied flow
`@RequirePermission` failures throw `TntRoleException.forbidden(resource, action)` from `TntPermissionAspect`/`TntPermissionEvaluator` (see `security/authorization.md`) — surfaces as 403 through whatever the module's global handler does with `TntRoleException` (often inherited from a shared base handler in `tnt-common-core`, not re-implemented per module).

## Validation errors
`WebExchangeBindException` (WebFlux's `@Valid` failure) → 400, field-level messages included in `detail`/extra properties depending on module.

# Links
- `api/rest.md` — endpoint list
- `security/authorization.md` — `TntRoleException` origin
- `domain/workflows.md` — state-transition exceptions (409s) map to invalid state-machine moves

---
> **Comment maintenir ce document** : un nouveau module avec un `*ExceptionHandler` = une nouvelle ligne. Si RFC 7807 devient universellement appliqué, retirer l'avertissement "not universally applied" et documenter le format final unique.
