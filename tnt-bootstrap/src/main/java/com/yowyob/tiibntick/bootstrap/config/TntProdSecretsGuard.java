package com.yowyob.tiibntick.bootstrap.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-fast guard against shipping TiiBnTick Core to production with any of the
 * hard-coded dev/test default secrets still in effect (Audit n°7 · #8 —
 * {@code application.yml:25,266-269,574}, {@code docker-compose.yml:134-135,194-195}).
 *
 * <p>{@code application.yml}'s {@code ${VAR:default}} placeholders resolve to a
 * convenient, well-known literal whenever the corresponding environment variable is
 * unset — perfect for local dev/docker-compose, catastrophic if silently carried into
 * production (guessable DB password, MinIO credentials, QR HMAC signing secret, or an
 * empty Kernel API key that breaks the Kernel bridge silently). This component binds
 * the exact same {@code ${VAR:default}} expressions used by {@link TntDataSourceConfig}
 * and {@code application.yml}'s {@code tnt.media.*} / {@code tnt.kernel.*} blocks, then
 * runs once at context startup — after {@link ApplicationProfileConfig} has resolved
 * {@code spring.profiles.active} into {@link ApplicationProfile} — and refuses to let
 * the context finish refreshing if the {@code PROD} profile is active and any of these
 * values is still the shipped default.
 *
 * <p>Deliberately a plain {@code @PostConstruct} check rather than an async/scheduled
 * validator: throwing here aborts {@code ApplicationContext.refresh()} itself, so
 * {@code SpringApplication.run()} never returns and no request is ever served on
 * insecure defaults.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TntProdSecretsGuard {

    private final ApplicationProfile applicationProfile;

    // ── Mirrors the exact default expressions used elsewhere in the app ────────
    // (TntDataSourceConfig for DB_PASSWORD, application.yml's tnt.media.* / tnt.kernel.*
    // blocks for the rest) so "env var unset" and "env var explicitly set to the known
    // dev value" are both caught the same way the real running app would resolve them.

    @Value("${DB_PASSWORD:tiibntick_pass}")
    private String dbPassword;

    @Value("${MINIO_ACCESS_KEY:minioadmin}")
    private String minioAccessKey;

    @Value("${MINIO_SECRET_KEY:minioadmin123}")
    private String minioSecretKey;

    @Value("${QR_HMAC_SECRET:changeme-use-strong-random-256bit-value-in-production}")
    private String qrHmacSecret;

    @Value("${TNT_KERNEL_API_KEY:}")
    private String kernelApiKey;

    @PostConstruct
    void validate() {
        if (!applicationProfile.isProduction()) {
            log.debug("TntProdSecretsGuard: profile {} is not PROD — skipping default-secret check.",
                    applicationProfile);
            return;
        }

        List<String> violations = new ArrayList<>();

        checkDefault(violations, "DB_PASSWORD", dbPassword, "tiibntick_pass",
                "provision the DBA-issued credential (see tnt-bootstrap/.env.prod.example)");
        checkDefault(violations, "MINIO_ACCESS_KEY", minioAccessKey, "minioadmin",
                "create a dedicated production MinIO access key");
        checkDefault(violations, "MINIO_SECRET_KEY", minioSecretKey, "minioadmin123",
                "create a dedicated production MinIO secret key");
        checkDefault(violations, "QR_HMAC_SECRET", qrHmacSecret,
                "changeme-use-strong-random-256bit-value-in-production",
                "generate a strong random 256-bit value");

        if (kernelApiKey == null || kernelApiKey.isBlank()) {
            violations.add("TNT_KERNEL_API_KEY is empty — the Kernel bridge cannot authenticate without it");
        }

        if (!violations.isEmpty()) {
            String message = "Refusing to start with profile PROD: " + violations.size()
                    + " insecure/default secret(s) detected:\n  - " + String.join("\n  - ", violations);
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("TntProdSecretsGuard: no insecure default secrets detected for profile PROD.");
    }

    private void checkDefault(List<String> violations, String envVar, String actualValue,
                               String insecureDefault, String hint) {
        if (insecureDefault.equals(actualValue)) {
            violations.add(envVar + " is still set to its known dev/test default — " + hint);
        }
    }
}
