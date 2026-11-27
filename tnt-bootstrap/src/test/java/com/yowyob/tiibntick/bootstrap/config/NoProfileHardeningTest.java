package com.yowyob.tiibntick.bootstrap.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the hardening that must hold <strong>with no Spring profile active</strong>, because
 * that is how production actually runs.
 *
 * <p>{@code docker-compose.prod.yml} sets no {@code SPRING_PROFILES_ACTIVE}, and the running
 * service confirms it: {@code "No active profile set, falling back to 1 default profile:
 * default"}. The whole {@code prod} document of {@code application.yml} has therefore never
 * applied. That was found on 27 November 2026 the hard way — a first security fix that set
 * {@code springdoc.*.enabled: false} in the {@code prod} document changed nothing at all on
 * the live service, and only checking the served content rather than the green deploy
 * revealed it.
 *
 * <p>The lesson is not "activate the profile". Activating it is a separate decision with its
 * own preconditions (on 27 November 2026 the JWT private key the {@code prod} document
 * expects at {@code /run/secrets/jwt_private_key.pem} was not mounted, while that document
 * also disables key auto-generation). The lesson is that anything which must be true in
 * production must be true in the <em>default</em> document, and must be asserted there.
 *
 * <p>Same technique as {@link SpringdocProdExposureTest}: the real
 * {@code application.yml} is loaded from the classpath through a bare
 * {@link SpringApplicationBuilder} with no auto-configuration and no profile, and the
 * resolved {@code Environment} is read back. Nothing is mocked, and no live infrastructure is
 * needed.
 */
class NoProfileHardeningTest {

    @Configuration
    static class EmptyConfig {
    }

    @Test
    void permissionEnforcementIsOnWithoutAnyProfile() {
        withNoProfile(environment -> {
            assertThat(environment.getProperty("tnt.roles.aop-enabled", Boolean.class))
                    .as("@RequirePermission enforcement must not depend on a profile")
                    .isTrue();
            assertThat(environment.getProperty("tnt.auth.allow-anonymous-context", Boolean.class))
                    .as("anonymous security contexts must stay refused without a profile")
                    .isFalse();
            assertThat(environment.getProperty("tnt.auth.actor-resolution-enabled", Boolean.class))
                    .as("actor resolution must stay on without a profile")
                    .isTrue();
        });
    }

    @Test
    void apiDocumentationIsClosedWithoutAnyProfile() {
        withNoProfile(environment -> {
            assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class))
                    .as("Swagger UI must be closed where production actually reads its config")
                    .isFalse();
            assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class))
                    .as("/v3/api-docs must be closed where production actually reads its config")
                    .isFalse();
        });
    }

    /**
     * Guards the premise of this whole class: production runs with no profile. Should someone
     * set {@code SPRING_PROFILES_ACTIVE=prod} later, the assertions above keep holding anyway
     * — the {@code prod} document only tightens these values — but this test documents which
     * document is being read.
     */
    @Test
    void noProfileMeansTheDefaultDocumentIsWhatApplies() {
        withNoProfile(environment ->
                assertThat(environment.getActiveProfiles())
                        .as("the default document is the one production resolves")
                        .isEmpty());
    }

    private static void withNoProfile(java.util.function.Consumer<Environment> assertions) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EmptyConfig.class)
                .web(WebApplicationType.NONE)
                .run()) {
            assertions.accept(context.getEnvironment());
        }
    }
}
