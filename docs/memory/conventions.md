# Purpose
How the user prefers to collaborate on this repo — distinct from `development/conventions.md` (which is code style); this is working-style/process. Read before starting non-trivial work.

# Summary
User communicates in French for instructions, expects rigorous build/run verification (not just "tests pass"), values root-cause fixes over workarounds, and explicitly wants RBAC to be fully functional rather than stubbed/disabled.

# Details

## Verification standard
"Tests pass" is explicitly **not** sufficient evidence that something works — the user has caught real bugs that only manifested when actually running `spring-boot:run` (see `development/testing.md`, `knowledge/known-issues.md` #9). **Always smoke-test by actually running the app** after changes to security, persistence, or startup-path code, not just `mvn test`.

## No shortcuts on security
Explicit instruction (verbatim intent, 2026-06-29): don't disable AOP, don't use an always-allow resolver, even temporarily — build the real pluggable architecture (LOCAL/REMOTE/HYBRID) instead. This is a strong signal: **when blocked by an external dependency not being ready (Kernel endpoints), build a forward-compatible abstraction, don't bypass the check.**

## Root-cause over symptom-suppression
When the Liquibase changelog cascade of bugs was found, the fix was to redesign the include strategy project-wide (explicit `include:`, unique filenames) rather than patch the one broken module. When `springdoc` broke Swagger UI, the fix was the correct version bump (with its cascading Lombok/swagger-jar fixes), not disabling Swagger UI.

## Documentation request style
This `docs/` tree was requested with a **very detailed, prescriptive structure** (exact folder names, exact file names, line-count limits, exact section headers) — when the user gives this level of specification, follow it precisely rather than substituting your own structure, but use judgment to merge/reconcile when two parts of their spec overlap (as with the two folder structures given for this very task).

## Language
User writes instructions in French; code, comments, and these docs are in English (matching the existing codebase convention) unless told otherwise.

# Links
- `development/testing.md` — the verification trap this guards against
- `architecture/decisions.md` ADR-004 — the RBAC decision this section refers to

---
> **Comment maintenir ce document** : ajouter une entrée quand l'utilisateur corrige une approche ou confirme explicitement qu'une approche non-évidente était la bonne. Inclure le "pourquoi" pour pouvoir juger les cas limites futurs.
