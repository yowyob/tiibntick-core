package com.yowyob.tiibntick.bootstrap.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the {@code /actuator/prometheus} exposure change made to {@link TntSecurityConfig}
 * for Audit n°7 · #16: the {@code @Order(5)} fully-public security chain must NOT match
 * {@code /actuator/prometheus} when the {@code PROD} profile is active (it falls through
 * to the {@code @Order(20)} authenticated chain there instead), while every other profile
 * keeps the previous, deliberately permissive dev/staging behaviour.
 *
 * <p>Boots the real {@link TntSecurityConfig} bean and asks its own
 * {@code publicPathsSecurityWebFilterChain} bean whether it matches an
 * {@code /actuator/prometheus} request via {@link SecurityWebFilterChain#matches} — the
 * exact mechanism {@code WebFilterChainProxy} uses at runtime to pick a chain — rather
 * than re-implementing the path list separately and risking it drifting from the real one.
 *
 * @author MANFOUO Braun
 */
class TntSecurityConfigPrometheusExposureTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void closeContext() {
        if (context != null && context.isActive()) {
            context.close();
        }
    }

    @Test
    void prometheusIsNotMatchedByThePublicChainInProd() {
        context = bootSecurityConfig("prod");

        boolean matchesPublicChain = publicChainMatchesPrometheus(context);

        assertThat(matchesPublicChain)
                .as("/actuator/prometheus must NOT be part of the public (unauthenticated) chain in PROD")
                .isFalse();
    }

    @Test
    void prometheusIsMatchedByThePublicChainOutsideProd() {
        context = bootSecurityConfig("staging");

        boolean matchesPublicChain = publicChainMatchesPrometheus(context);

        assertThat(matchesPublicChain)
                .as("/actuator/prometheus must stay public outside PROD (unchanged dev/staging convenience)")
                .isTrue();
    }

    private static boolean publicChainMatchesPrometheus(AnnotationConfigApplicationContext ctx) {
        SecurityWebFilterChain publicChain = ctx.getBean("publicPathsSecurityWebFilterChain", SecurityWebFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus").build());
        return Boolean.TRUE.equals(publicChain.matches(exchange).blockOptional().orElse(false));
    }

    private static AnnotationConfigApplicationContext bootSecurityConfig(String profile) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        TestPropertyValues.of("spring.profiles.active=" + profile)
                .applyTo(ctx);
        ctx.register(ApplicationProfileConfig.class, TntSecurityConfig.class);
        ctx.registerBean(ReactiveJwtDecoder.class,
                () -> token -> Mono.error(new UnsupportedOperationException("stub — not exercised by this test")));
        ctx.refresh();
        return ctx;
    }
}
