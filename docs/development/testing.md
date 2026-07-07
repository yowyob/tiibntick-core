# Purpose
How to run tests, what test types exist, and the test-profile gotchas that have caused real bugs.

# Summary
Surefire = unit tests (`*Test.java`/`*Tests.java`), Failsafe = integration tests (`*IT.java`/`*IntegrationTest.java`, TestContainers-backed, opt-in via `-Pintegration-tests`). The `test` Spring profile relaxes security (`aop-enabled=false`, `allow-anonymous-context=true`) — this has masked real bugs before.

# Details

## Commands
```bash
mvn clean install                                  # full build, all modules
mvn -pl logistics/tnt-delivery-core -am install     # one module + its dependencies
mvn -pl billing/tnt-billing-dsl test                # unit tests only
mvn -pl logistics/tnt-delivery-core test -Dtest=SomeServiceTest
mvn -pl logistics/tnt-delivery-core test -Dtest=SomeServiceTest#someMethod
mvn -pl <module> verify -Pintegration-tests          # integration tests (TestContainers)
mvn -pl tnt-bootstrap spring-boot:run                # run the app locally (needs docker-compose infra up)
```

## ⚠️ The `test` profile hides bugs that only show up in `default`/`dev`/`staging`/`prod`
`application.yml`'s `test` profile sets `tnt.roles.aop-enabled=false` and `tnt.auth.allow-anonymous-context=true` — meaning **`@RequirePermission` is never actually exercised in unit/integration tests**, and a missing `ReactivePermissionResolver` bean would never surface as a test failure. A real incident: the app passed all tests, then crashed on `mvn spring-boot:run` with `NoSuchBeanDefinitionException` because the bean was only required when `aop-enabled=true` (the real-world default). **Lesson: "tests pass" ≠ "the app starts."** Always also do a clean `spring-boot:run` smoke test after RBAC/security-adjacent changes — don't rely on `mvn test` alone.

## Another instance of the same trap
A bogus `@Profile("r2dbc")` annotation on 4 real R2DBC adapters in `tnt-actor-core` made them silently absent in every profile except tests (`@ActiveProfiles({"test","r2dbc"})` in the test setup activated the profile as a side effect, masking that the adapters were dead in dev/staging/prod). Found only by running the full app, not by tests. See `knowledge/known-issues.md`.

## TestContainers
Used for integration tests — Postgres, Kafka, Redis images spun up automatically by Failsafe-run tests. `testcontainers.version=1.19.8`. Only runs with `-Pintegration-tests` — not part of the default `mvn test`/`mvn install` lifecycle.

## JaCoCo
Coverage reporting runs on `verify`. The `check` (coverage gate / fail-build-below-threshold) goal is present in the pom but **currently commented out** — don't assume a coverage percentage is enforced.

## Test infra (local)
`cd tnt-bootstrap && docker compose up -d` before running integration tests or `spring-boot:run` locally — see `infrastructure/docker.md` for full service list/ports/credentials.

# Links
- `infrastructure/docker.md` — local infra setup
- `knowledge/known-issues.md` — the two "tests pass, app doesn't start" incidents in full detail
- `architecture/decisions.md` — ADR-004 (the RBAC architecture that was affected)

---
> **Comment maintenir ce document** : si un nouveau "tests pass mais l'app ne démarre pas" se produit, l'ajouter ici en plus de `knowledge/known-issues.md` — ce pattern d'incident s'est déjà répété deux fois, vaut la peine d'être bien documenté pour la prochaine fois.
