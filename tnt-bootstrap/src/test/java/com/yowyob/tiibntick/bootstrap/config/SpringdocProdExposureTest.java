package com.yowyob.tiibntick.bootstrap.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the {@code application.yml} change made for Audit n°7 · #10: the {@code prod}
 * profile document must resolve {@code springdoc.swagger-ui.enabled} and
 * {@code springdoc.api-docs.enabled} to {@code false} — previously both were hard-set to
 * {@code true} there with a "we will need to disable this" comment, meaning the full
 * Swagger UI + {@code /v3/api-docs} attack-surface reconnaissance was reachable in
 * production.
 *
 * <p>Deliberately does not boot the full {@code TiiBnTickApplication} (which needs live
 * Postgres/Redis/Kafka — unavailable in this sandbox, see {@code TiiBnTickApplicationTest}
 * / {@code PublicPathsSecurityTest} failures for the same reason) nor pulls in
 * springdoc's own auto-configuration (a well-tested third-party library — not what this
 * change needs to re-verify). Instead it loads the real
 * {@code tnt-bootstrap/src/main/resources/application.yml} from the classpath with the
 * {@code prod} profile active via a bare {@link SpringApplicationBuilder} (no
 * auto-configuration, {@link WebApplicationType#NONE}) and reads back the resolved
 * {@code Environment} properties — proving the YAML profile-override itself is correct,
 * which is the actual thing this change touches.
 *
 * @author MANFOUO Braun
 */
class SpringdocProdExposureTest {

    @Configuration
    static class EmptyConfig {
    }

    @Test
    void swaggerUiAndApiDocsResolveToDisabledInProd() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EmptyConfig.class)
                .web(WebApplicationType.NONE)
                .profiles("prod")
                .run()) {

            assertThat(context.getEnvironment().getProperty("springdoc.swagger-ui.enabled", Boolean.class))
                    .as("springdoc.swagger-ui.enabled must be false in the prod profile")
                    .isFalse();
            assertThat(context.getEnvironment().getProperty("springdoc.api-docs.enabled", Boolean.class))
                    .as("springdoc.api-docs.enabled must be false in the prod profile")
                    .isFalse();
        }
    }

    @Test
    void swaggerUiAndApiDocsStayEnabledOutsideProd() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EmptyConfig.class)
                .web(WebApplicationType.NONE)
                .profiles("staging")
                .run()) {

            // No override in the staging/default document — springdoc's own default
            // (true) applies, i.e. the property is simply absent here.
            assertThat(context.getEnvironment().getProperty("springdoc.swagger-ui.enabled")).isNull();
            assertThat(context.getEnvironment().getProperty("springdoc.api-docs.enabled")).isNull();
        }
    }
}
