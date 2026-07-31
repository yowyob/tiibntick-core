# Purpose
How the user prefers to collaborate on this repo — distinct from `development/conventions.md` (which is code style); this is working-style/process. Read before starting non-trivial work.

# Summary
User communicates in French for instructions, expects rigorous build/run verification (not just "tests pass"), values root-cause fixes over workarounds, and explicitly wants RBAC to be fully functional rather than stubbed/disabled. Additional confirmed patterns (2026-07): `@RestController` over functional routers, flag-don't-silently-execute-or-skip on large architecture findings, parallel-agent-per-branch for independent fixes, English-only commit messages with no AI co-author trailer.

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

## Controller style: `@RestController`, not WebFlux functional routers
For new controllers, use `@RestController`/`@RequestMapping`/`@GetMapping` etc., not `RouterFunction`/`RouterFunctions.route()`. Confirmed 2026-07-01: the user asked for controllers "aligned with kernel core controllers," which uses annotation-based style, and Springdoc/OpenAPI discovers `@RestController` classes automatically without separate router config. Existing functional routers found in older code are legacy, not a pattern to keep extending — several were later found to be dead duplicate routes still reading `X-Tenant-Id` from a header (a real tenant-IDOR bug, see `knowledge/known-issues.md` and `docs/audits/remediation/phase-0-critical.md` Chantier A) and deleted outright once confirmed to have no internal callers.

## Flag large/risky architecture findings; don't silently execute or silently skip
When a broad mandate (e.g. "migrate every module to delegate to the Kernel," or a Phase-0-style remediation sweep) surfaces a large, genuinely-justified change with real risk (schema/data migration, financial-audit implications, capability regression), surface it via `AskUserQuestion` with a concrete recommendation, then document the finding as a proposed-but-not-executed ADR if the user confirms deferring it — a documented, deliberately-deferred architectural finding satisfies "no TODOs," a silent skip or a silent execution does not. Confirmed multiple times (2026-07-08 Kernel-facade migration, ADR-010/013; 2026-07-08/09 platform-gateway rearchitecture). Once given explicit "implement everything end-to-end, don't skip anything" authorization after a thorough design phase, don't re-ask for confirmation on sub-decisions already covered by that authorization — but still flag any *new* deviation discovered mid-build in the deliverable itself, not just verbally (e.g. the platform-gateway admin API needing `tnt-auth-core`/`tnt-roles-core` after all, contradicting the design doc's "zero dependency" claim).

## Parallel-agent-per-branch-then-sequential-merge works well for independent fixes
Confirmed twice (2026-07-23, once for 4 domain-independent tenant-IDOR/Redis fixes, once for Phase-0-style Kafka-outbox domain migrations): when the user asks for several agents each on an isolated worktree/branch off master, merge sequentially with a full module verification build after each merge (main session does the merging itself, not the agents), this pattern works cleanly for non-overlapping modules. Don't default to a single agent doing everything sequentially when the user has explicitly asked for a parallel-branches split.

## Don't trust a subagent's internal background-bash tracking for long builds
A subagent tasked with running a long `mvn verify` in its own internal background process reported "standing by for the build to complete" as its final message, but no process was actually running when checked (harness confirmed "no active task"). For long build/test runs delegated to a subagent, either run it directly with the main session's own `run_in_background` Bash (reliably notifies), or explicitly instruct the subagent to run the build in the foreground within its own turn.

## Commit messages: English only, no AI co-author trailer, ever
Explicit instruction (2026-07-23, reconfirmed 2026-07-31): all git commit and merge commit messages in this repo must be in English regardless of what language the conversation is conducted in (sessions are often in French), and must never include a `Co-Authored-By: Claude` (or any AI co-author) trailer. This applies even though documentation elsewhere in the repo (e.g. `docs/audits/remediation/*.md`) legitimately stays in French per existing convention — the rule is scoped to commit/merge messages specifically, not to all repo content.

# Links
- `development/testing.md` — the verification trap this guards against
- `architecture/decisions.md` ADR-004, ADR-017 — decisions this section refers to

---
> **Comment maintenir ce document** : ajouter une entrée quand l'utilisateur corrige une approche ou confirme explicitement qu'une approche non-évidente était la bonne. Inclure le "pourquoi" pour pouvoir juger les cas limites futurs.
