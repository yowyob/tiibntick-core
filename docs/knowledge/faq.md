# Purpose
Quick answers to questions that come up repeatedly — read this before re-deriving an answer from scratch.

# Summary
Covers: how to run the app, why some Kernel calls 404, why the build sometimes fails after a dependency bump, how to find a class fast, what's safe to change vs. not.

# Details

**Q: How do I run the app locally?**
A: `cd tnt-bootstrap && docker compose up -d` (infra), then `mvn -pl tnt-bootstrap spring-boot:run`. See `_quick-start.md`.

**Q: Why do I see `404 Not Found from POST https://kernel-core.yowyob.com/v1/roles` in the logs?**
A: Expected — the Kernel doesn't implement role provisioning or permission-resolution endpoints yet. Not a bug. See `development/roadmap.md` and `security/permissions.md`.

**Q: My module won't compile with `TypeTag :: UNKNOWN` / a `NoSuchMethodError` after touching `pom.xml`.**
A: Almost certainly a dependency version mismatch with this repo's Spring Boot 4/Framework 7/JDK 21-25 stack — check `knowledge/known-issues.md` first, several of these exact errors have already been diagnosed (Lombok/JDK25, springdoc/Boot4, Kernel swagger conflict).

**Q: `mvn test` passes but `mvn spring-boot:run` throws `NoSuchBeanDefinitionException` — what's going on?**
A: The `test` profile relaxes security config (`aop-enabled=false`) and test setups can activate stray `@Profile` annotations as a side effect. Always smoke-test with `spring-boot:run` after RBAC/persistence changes. See `development/testing.md`.

**Q: Where do I find the controller/service/repository for X?**
A: `knowledge/project-map.md` — the file-level index. If it's not there yet, `architecture/packages.md` tells you which directory pattern to grep.

**Q: Can I inject a Kernel Spring bean directly to save time?**
A: No — ADR-001 (`architecture/decisions.md`). HTTP/Kafka/data-types/explicit-SPI only.

**Q: I added a new module's Liquibase changelog but the table still isn't created.**
A: Did you add the explicit `include:` line in `tnt-bootstrap/.../db/changelog/tnt-core-master.yaml`? This is the single most common omission — see incident #3 in `knowledge/known-issues.md` (this exact thing happened to `tnt-sync-core`).

**Q: Why are there two different package layouts (`adapter/application/domain` vs `infrastructure/domain`)?**
A: Intentional — `tnt-billing-dsl` and `tnt-accounting-core` use the second shape for domain-service-heavy logic. See `architecture/packages.md`, ADR-007.

**Q: Is Elasticsearch actually used?**
A: No — transitive dependency only, unused by any TiiBnTick module today. See `infrastructure/elasticsearch.md`.

**Q: Where's the permission cache — Redis or in-process?**
A: In-process Caffeine (`PermissionCache`), not Redis. See `security/permissions.md`, `infrastructure/redis.md`.

**Q: What's the difference between `api/security.md` and `security/*.md`?**
A: `api/security.md` is the endpoint-facing summary (which headers/permissions gate which routes). `security/*.md` is the mechanism (JWT validation, AOP interception, resolver architecture).

# Links
- `knowledge/known-issues.md`, `knowledge/project-map.md`
- `_quick-start.md`, `_cheat-sheet.md`

---
> **Comment maintenir ce document** : ajouter une question dès qu'elle revient une deuxième fois dans une conversation. Garder les réponses à 1-3 lignes avec un lien vers le doc détaillé — ce n'est pas l'endroit pour l'explication complète.
