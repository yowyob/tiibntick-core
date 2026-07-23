package com.yowyob.tiibntick.bootstrap.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the fail-fast guard added to {@link TntSecurityConfig} (Audit n°7 · #9):
 * {@code tnt.auth.allow-anonymous-context=true} must never be combined with an active
 * {@code PROD} profile, since it injects a synthetic, fully-authenticated
 * {@code ROLE_TNT_ADMIN} principal into every unauthenticated request
 * ({@link TntSecurityConfig#devAuthFilter()}).
 *
 * <p>Boots a real (minimal) Spring context — {@link ApplicationProfileConfig} +
 * {@link TntSecurityConfig} — so the assertion is "does
 * {@code ApplicationContext.refresh()} abort", matching what
 * {@code SpringApplication.run()} would surface on a real prod boot with this
 * dangerous combination set.
 *
 * @author MANFOUO Braun
 */
class TntAnonymousContextGuardTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void closeContext() {
        if (context != null && context.isActive()) {
            context.close();
        }
    }

    @Test
    void refusesToStartInProdWithAnonymousContextAllowed() {
        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(
                        "spring.profiles.active=prod",
                        "tnt.auth.allow-anonymous-context=true")
                .applyTo(context);
        context.register(ApplicationProfileConfig.class, TntSecurityConfig.class);

        assertThatThrownBy(context::refresh)
                .isInstanceOf(BeanCreationException.class)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start with profile PROD")
                .hasMessageContaining("allow-anonymous-context");
    }

    @Test
    void startsCleanlyInProdWhenAnonymousContextIsDisabled() {
        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(
                        "spring.profiles.active=prod",
                        "tnt.auth.allow-anonymous-context=false")
                .applyTo(context);
        context.register(ApplicationProfileConfig.class, TntSecurityConfig.class);
        // TntSecurityConfig's non-anonymous branch wires an OAuth2 resource server, which
        // needs a ReactiveJwtDecoder bean — normally auto-configured from
        // spring.security.oauth2.resourceserver.jwt.jwk-set-uri. Not relevant to what this
        // test proves (the guard doesn't block a legitimately-disabled anonymous context),
        // so a stub is enough to let the rest of the bean graph resolve.
        context.registerBean(ReactiveJwtDecoder.class,
                () -> token -> Mono.error(new UnsupportedOperationException("stub — not exercised by this test")));

        context.refresh();

        assertThat(context.getBean(ApplicationProfile.class).isProduction()).isTrue();
    }

    @Test
    void allowsAnonymousContextOutsideProd() {
        context = new AnnotationConfigApplicationContext();
        // Dev/test profiles legitimately rely on tnt.auth.allow-anonymous-context=true
        // (see the "test" profile block in application.yml) — must not be blocked.
        TestPropertyValues.of(
                        "spring.profiles.active=dev",
                        "tnt.auth.allow-anonymous-context=true")
                .applyTo(context);
        context.register(ApplicationProfileConfig.class, TntSecurityConfig.class);

        context.refresh();

        assertThat(context.getBean(ApplicationProfile.class).isProduction()).isFalse();
    }
}
