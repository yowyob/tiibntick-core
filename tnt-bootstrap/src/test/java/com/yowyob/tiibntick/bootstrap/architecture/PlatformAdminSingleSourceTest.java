package com.yowyob.tiibntick.bootstrap.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decision D-160: the TNT_ADMIN authority is given at ONE place, the JWT converter of {@code TntSecurityConfig}
 * (owner rule and Core registry), and the {@code roles}/{@code permissions} claims of the Kernel token are read
 * only there and in the token validator, both filtering what could make an administrator (D-162). A new code path
 * that minted {@code ROLE_TNT_ADMIN} or read those claims itself would bypass the registry: this scan fails.
 */
class PlatformAdminSingleSourceTest {

    private static final Set<String> MINTING_ALLOWED = Set.of(
            "tnt-bootstrap/src/main/java/com/yowyob/tiibntick/bootstrap/config/TntSecurityConfig.java");
    private static final Set<String> CLAIM_READING_ALLOWED = Set.of(
            "tnt-bootstrap/src/main/java/com/yowyob/tiibntick/bootstrap/config/TntSecurityConfig.java",
            "foundation/tnt-auth-core/src/main/java/com/yowyob/tiibntick/core/auth/application/service/TntJwtValidator.java");

    private static final Pattern MINT = Pattern.compile(
            "new\\s+SimpleGrantedAuthority\\s*\\(\\s*\"ROLE_TNT_ADMIN\"");
    private static final Pattern CLAIM = Pattern.compile(
            "(getClaim|getClaimAsStringList|getClaimAsString|claims\\.get|getClaims\\(\\)\\.get)\\s*\\(\\s*\"(roles|permissions)\"");

    @Test
    void onlyTheConverterMintsTntAdminAndReadsTheRoleClaims() throws IOException {
        Path root = repoRoot();
        List<String> minting = new ArrayList<>();
        List<String> reading = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> f.toString().contains("/src/main/java/"))
                    .filter(f -> !f.toString().contains("/target/") && !f.toString().contains("/tiibntick-platforms/"))
                    .toList()) {
                String rel = root.relativize(p).toString();
                if (rel.startsWith(".")) continue; // .claude worktrees, .git: not modules of this build
                String src = Files.readString(p);
                if (MINT.matcher(src).find() && !MINTING_ALLOWED.contains(rel)) minting.add(rel);
                if (CLAIM.matcher(src).find() && !CLAIM_READING_ALLOWED.contains(rel)) reading.add(rel);
            }
        }
        assertThat(minting).as("ROLE_TNT_ADMIN minted outside the JWT converter").isEmpty();
        assertThat(reading).as("roles/permissions claims read outside the converter and the validator").isEmpty();
    }

    private static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("tnt-bootstrap")) ) {
            p = p.getParent();
        }
        assertThat(p).as("repository root").isNotNull();
        return p;
    }
}
