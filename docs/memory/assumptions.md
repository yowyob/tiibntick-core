# Purpose
Assumptions baked into the current implementation that haven't been explicitly confirmed by the Kernel team or product owner — revisit these if something unexpected happens at integration time.

# Summary
Mostly Kernel-contract assumptions (claim names, endpoint shapes, error semantics) and a couple of "this is probably temporary" infrastructure choices. Updated 2026-07-31: the system-tenant-id assumption is now split (committed default vs. local override), and a new batch of Kernel wire-format + key-custody assumptions introduced since 2026-07-08 is tracked separately below.

# Details

## Kernel API contract assumptions
| Assumption | Where used | Risk if wrong |
|---|---|---|
| JWT claims are named `tenant_id`, `actor_id`, `organization_id`, `agency_id`, `permissions` (snake_case) | `TntJwtValidator` | Auth context would silently build with null fields if the Kernel renames claims |
| `POST /v1/roles` will accept the exact `KernelCreateRoleRequest` shape (`tenantId`, `code`, `name`, `scopeTypeCode`, `defaultPermissions`) once implemented | `KernelRoleProvisioningAdapter` | Provisioning would fail with a 400 instead of 404 once the endpoint exists — should be re-tested then, not assumed correct |
| `GET /v1/permissions/resolve?userId=` will return a flat permission-string array | `RemoteReactivePermissionResolver` | Resolver parsing would need adjustment if the actual shape differs |
| 409 CONFLICT means "role already exists" (not some other conflict) | `KernelRoleProvisioningAdapter` | Idempotency check would be wrong if 409 means something else |

## Infrastructure assumptions
| Assumption | Where | Notes |
|---|---|---|
| Single `tnt-bootstrap` instance (no horizontal scaling yet) | `PermissionCache` design (Caffeine, not Redis) | Documented explicitly as acceptable *because* of this — re-evaluate if/when the app is scaled to multiple instances (cache invalidation via Kafka should still work correctly per-instance, but cache population would be duplicated work) |
| Dev MinIO/Postgres/Redis credentials in `docker-compose.yml` are dev-only and never reused in staging/prod | `application.yml` profile blocks | Staging/prod profiles already override with env-var-sourced secrets — verify this hasn't drifted if you're setting up a new environment |
| `tnt_system_tenant_id` default (`00000000-0000-0000-0000-000000000001`) is stable across all environments | `tnt.roles.system-tenant-id` | **Superseded locally 2026-07-23**: Braun's own real Kernel account/tenant (`af1f5fb6-0265-481a-ba17-ae55ad53ec41`) is now used as the local dev system tenant, set via `TNT_SYSTEM_TENANT_ID` in the gitignored `tnt-bootstrap/.env` — the committed `.env.example`/`.env.prod.example` templates still correctly keep the generic placeholder default for other environments/teammates. CLAUDE.md's "MUST be identical across all environments" guidance still holds for the *shared/committed* default; this is a personal local override, not a repo-wide change. |

## Kernel wire-format assumptions introduced since the initial batch above (not yet confirmed against a live response)
| Assumption | Where used | Risk if wrong |
|---|---|---|
| Kernel `/oauth2/userinfo` field names (`organizationId`/`id`, `contextId`/`id`) | `KernelSsoGatewayService` (SSO context/organization parsing) | The Kernel's OpenAPI spec types this response as a generic `object` — field-name matching is a best guess from the spec's textual description, not a schema. Verify against a live response if SSO context-resolve doesn't work correctly. |
| Kernel KYC document-verification multipart field is named `"file"` | `tnt-actor-core`'s `POST /api/v1/kyc/verify` proxy | The Kernel's own spec doesn't name a fixed multipart field for this operation (resolves to a bare `object`) — `"file"` is TiiBnTick's own committed contract, chosen for Swagger discoverability, not confirmed against the Kernel's actual expectation. Revisit if verification calls 4xx unexpectedly in production. |

## Key-custody assumption (deliberate simplification, not yet revisited)
- Actor and FreelancerOrg blockchain DID issuance (`tnt-trust-core`) generates a fresh, throwaway ECDSA P-256 keypair per issuance and discards the private key immediately — only the public key is ever used. Neither `TntActorProfile` nor `FreelancerOrganization` captures or stores any public-key material from an actual onboarding flow. This means the org/actor doesn't really hold its own DID private key today. If real org-controlled cryptographic signing is ever needed, this needs a proper key-custody design first — don't extend the current adapters to "fix" this without a dedicated design pass.

## Ownership/process assumptions
- Module ownership comments in root `pom.xml` (`architecture/modules.md`) are assumed current — verify with the team if attributing work, they're maintained inline but could drift.
- `RT-comops-*` artifacts are no longer a dependency anywhere in this repo as of 2026-07-08 (Kernel is HTTP-only) — the earlier assumption about CI package-registry tokens for `RT-comops-*` no longer applies; nothing in the current build needs Kernel-repo credentials.

# Links
- `architecture/decisions.md` — ADR-001 (Kernel boundary), ADR-004 (resolver architecture)
- `security/authentication.md`, `security/permissions.md`

---
> **Comment maintenir ce document** : ajouter une assumption dès qu'une décision est prise sans confirmation externe (API non encore testée contre la vraie implémentation, contrat supposé stable). Retirer une entrée dès qu'elle est confirmée correcte (et la noter dans `memory/completed.md` si la confirmation a nécessité du travail).
