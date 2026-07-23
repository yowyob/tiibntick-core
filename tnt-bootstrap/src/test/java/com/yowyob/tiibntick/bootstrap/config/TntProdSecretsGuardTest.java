package com.yowyob.tiibntick.bootstrap.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the fail-fast behaviour of {@link TntProdSecretsGuard} (Audit n°7 · #8) by
 * actually starting a minimal Spring context — not just instantiating the class —
 * so the assertion is "does {@code ApplicationContext.refresh()} abort", the same
 * signal {@code SpringApplication.run()} would surface in a real prod boot.
 *
 * <p>Only {@link ApplicationProfileConfig} + {@link TntProdSecretsGuard} are registered
 * (not the full assembled application) — the guard's dependency is exactly
 * {@link ApplicationProfile}, so this stays a fast, infra-free unit-of-behaviour test
 * instead of requiring the full DB/Kafka/Redis stack that {@code @SpringBootTest}
 * would need.
 *
 * @author MANFOUO Braun
 */
class TntProdSecretsGuardTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void closeContext() {
        if (context != null && context.isActive()) {
            context.close();
        }
    }

    @Test
    void refusesToStartInProdWithDefaultSecretsUntouched() {
        context = new AnnotationConfigApplicationContext();
        // No DB_PASSWORD / MINIO_* / QR_HMAC_SECRET / TNT_KERNEL_API_KEY overrides at all —
        // exactly the "operator forgot to set env vars" scenario the guard exists for.
        TestPropertyValues.of("spring.profiles.active=prod")
                .applyTo(context);
        context.register(ApplicationProfileConfig.class, TntProdSecretsGuard.class);

        assertThatThrownBy(context::refresh)
                .isInstanceOf(BeanCreationException.class)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start with profile PROD")
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("MINIO_ACCESS_KEY")
                .hasMessageContaining("MINIO_SECRET_KEY")
                .hasMessageContaining("QR_HMAC_SECRET")
                .hasMessageContaining("TNT_KERNEL_API_KEY");
    }

    @Test
    void refusesToStartInProdWhenDefaultExplicitlyReSet() {
        context = new AnnotationConfigApplicationContext();
        // Simulates an operator copy-pasting the dev .env file into prod: values are
        // "set", but to the exact same insecure literals — must still be rejected.
        TestPropertyValues.of(
                        "spring.profiles.active=prod",
                        "DB_PASSWORD=tiibntick_pass",
                        "MINIO_ACCESS_KEY=minioadmin",
                        "MINIO_SECRET_KEY=minioadmin123",
                        "QR_HMAC_SECRET=changeme-use-strong-random-256bit-value-in-production",
                        "TNT_KERNEL_API_KEY=some-real-key")
                .applyTo(context);
        context.register(ApplicationProfileConfig.class, TntProdSecretsGuard.class);

        assertThatThrownBy(context::refresh)
                .isInstanceOf(BeanCreationException.class)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("MINIO_ACCESS_KEY")
                .hasMessageContaining("MINIO_SECRET_KEY")
                .hasMessageContaining("QR_HMAC_SECRET")
                .hasMessageNotContaining("TNT_KERNEL_API_KEY is empty");
    }

    @Test
    void startsCleanlyInProdWhenAllSecretsAreOverridden() {
        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(
                        "spring.profiles.active=prod",
                        "DB_PASSWORD=a-real-vault-issued-password",
                        "MINIO_ACCESS_KEY=prod-minio-access-key",
                        "MINIO_SECRET_KEY=prod-minio-secret-key",
                        "QR_HMAC_SECRET=b6f1e2c3a4d5e6f7089a1b2c3d4e5f60718293a4b5c6d7e8f9001122334455",
                        "TNT_KERNEL_API_KEY=kernel-issued-api-key")
                .applyTo(context);
        context.register(ApplicationProfileConfig.class, TntProdSecretsGuard.class);

        context.refresh();

        assertThat(context.getBean(ApplicationProfile.class).isProduction()).isTrue();
    }

    @Test
    void doesNotBlockNonProdProfilesEvenWithDefaultSecrets() {
        context = new AnnotationConfigApplicationContext();
        // "dev" (and equally "staging"/"test") must never be blocked by this guard —
        // it is scoped to PROD only, per the audit item.
        TestPropertyValues.of("spring.profiles.active=dev")
                .applyTo(context);
        context.register(ApplicationProfileConfig.class, TntProdSecretsGuard.class);

        context.refresh();

        assertThat(context.getBean(ApplicationProfile.class).isProduction()).isFalse();
    }
}
