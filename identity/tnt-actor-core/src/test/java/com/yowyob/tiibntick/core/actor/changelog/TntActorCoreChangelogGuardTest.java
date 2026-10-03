package com.yowyob.tiibntick.core.actor.changelog;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard: no SQL changelog in tnt-actor-core may INSERT directly into {@code tnt_roles}.
 *
 * <p>The sole mechanism for provisioning system roles is
 * {@code TntRoleInitializationService} — which reads from {@code TntRole} (the canonical
 * enum) at startup and reconciles the DB. A Liquibase migration that inserts rows into
 * {@code tnt_roles} bypasses this reconciliation and produces a permanently amputated
 * system role: {@code upsertIfAbsent} (now {@code reconcileOrProvision}) sees
 * {@code exists=true} on the first post-migration boot and skips the canonical seed,
 * leaving the role with whatever sparse permission set the migration chose.
 *
 * <p>This test runs from the module's working directory ({@code identity/tnt-actor-core})
 * and fails fast if any {@code .sql} file under {@code src/main/resources/db/changelog}
 * contains a case-insensitive {@code INSERT INTO tnt_roles} statement.
 *
 * @author KOUAM Kamdem
 */
class TntActorCoreChangelogGuardTest {

    @Test
    void noSqlChangelogInActorCoreModule_shouldContainInsertIntoTntRoles() throws IOException {
        Path changelogRoot = Paths.get("src/main/resources/db/changelog");
        if (!changelogRoot.toFile().exists()) {
            // When invoked from the project root (e.g. multi-module verify)
            changelogRoot = Paths.get("identity/tnt-actor-core/src/main/resources/db/changelog");
        }
        if (!changelogRoot.toFile().exists()) {
            // Changelog directory not present in current working tree — nothing to guard
            return;
        }

        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(changelogRoot)) {
            paths.filter(p -> p.toString().endsWith(".sql"))
                    .forEach(sqlFile -> {
                        try {
                            String content = Files.readString(sqlFile).toLowerCase();
                            if (content.contains("insert into tnt_roles")) {
                                violations.add(sqlFile.toString());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read changelog file: " + sqlFile, e);
                        }
                    });
        }

        assertThat(violations)
                .as("No SQL changelog file in tnt-actor-core may INSERT INTO tnt_roles.\n" +
                        "Use TntRoleInitializationService (the authoritative reconciler) instead.\n" +
                        "Offending files: %s", violations)
                .isEmpty();
    }
}
