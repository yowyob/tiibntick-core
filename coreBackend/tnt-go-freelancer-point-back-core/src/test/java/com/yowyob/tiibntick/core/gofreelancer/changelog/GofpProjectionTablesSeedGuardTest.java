package com.yowyob.tiibntick.core.gofreelancer.changelog;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard (lot C-18): {@code gofp_freelancers} and {@code tnt_delivery_persons} rows for a real
 * freelancer are produced only by {@code GofpFreelancerProjectionService}. A Liquibase
 * changelog or an E2E script that seeds them hides a missing production projection and turns
 * a production bug into a green test — which is exactly how "accept a delivery" stayed broken
 * while the courier E2E passed.
 *
 * <p>Scans, from the repository root (never skipped — the test fails if the root is not found):
 * <ul>
 *   <li>every {@code src/main/resources/db/**} file ({@code .sql/.xml/.yaml/.yml}) of every module;</li>
 *   <li>every script under {@code scripts/}.</li>
 * </ul>
 */
class GofpProjectionTablesSeedGuardTest {

    private static final Pattern FORBIDDEN_INSERT = Pattern.compile(
            "insert\\s+into\\s+(\"?public\"?\\.)?\"?(gofp_freelancers|tnt_delivery_persons)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Pre-existing harnesses seeding <em>synthetic</em> freelancers (fake core ids, not tied to a
     * freelancer profile) for scenarios outside the courier journey: admin approval of PENDING
     * freelancers, GPS presence loop. They do not mask the projection (the courier harness does
     * not use them). Do not add entries: rewrite them on top of the real projection instead.
     */
    private static final Set<String> LEGACY_ALLOWLIST = Set.of(
            "scripts/e2e/e2e-freelancer-flow.sh",
            "scripts/e2e/e2e-presence-loop.sh");

    @Test
    void noChangelogNorScript_insertsIntoProjectionTables() throws IOException {
        Path root = findRepoRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> isChangelog(root.relativize(p)) || isScript(root.relativize(p)))
                    .forEach(p -> {
                        String rel = root.relativize(p).toString().replace('\\', '/');
                        if (LEGACY_ALLOWLIST.contains(rel)) return;
                        try {
                            if (FORBIDDEN_INSERT.matcher(Files.readString(p)).find()) violations.add(rel);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read " + p, e);
                        }
                    });
        }

        assertThat(violations)
                .as("INSERT INTO gofp_freelancers / tnt_delivery_persons is forbidden in changelogs and "
                        + "scripts — GofpFreelancerProjectionService is the only producer. Offending: %s",
                        violations)
                .isEmpty();
    }

    @Test
    void guardPattern_matchesTheSeedsItMustCatch() {
        assertThat(FORBIDDEN_INSERT.matcher("INSERT INTO gofp_freelancers (id) VALUES ('x')").find()).isTrue();
        assertThat(FORBIDDEN_INSERT.matcher("insert   into public.tnt_delivery_persons\n(id)").find()).isTrue();
        assertThat(FORBIDDEN_INSERT.matcher("INSERT INTO tnt_delivery_persons_history (id)").find()).isFalse();
    }

    private static boolean isChangelog(Path rel) {
        String s = rel.toString().replace('\\', '/');
        return !s.contains("/target/") && s.contains("src/main/resources/db/")
                && (s.endsWith(".sql") || s.endsWith(".xml") || s.endsWith(".yaml") || s.endsWith(".yml"));
    }

    private static boolean isScript(Path rel) {
        return rel.toString().replace('\\', '/').startsWith("scripts/");
    }

    private static Path findRepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("tnt-bootstrap")) && Files.isRegularFile(dir.resolve("pom.xml"))
                    && Files.isDirectory(dir.resolve("coreBackend"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Repository root not found from " + Paths.get("").toAbsolutePath()
                + " — the guard must not be silently skipped");
    }
}
