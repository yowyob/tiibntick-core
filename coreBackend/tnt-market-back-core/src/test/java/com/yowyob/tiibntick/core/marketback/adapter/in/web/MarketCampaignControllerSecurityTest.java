package com.yowyob.tiibntick.core.marketback.adapter.in.web;

import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketCampaignUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateCampaignCommand;
import com.yowyob.tiibntick.core.marketback.domain.model.CampaignType;
import com.yowyob.tiibntick.core.marketback.domain.model.DiscountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;

/**
 * Regression test for Audit n°7 · #23 — Core Backend "market" endpoints were reachable
 * with zero authorization ({@code /api/v1/platform/**} resolves to
 * {@code .anyExchange().permitAll()} in {@code TntPlatformGatewaySecurityConfig}, and
 * every market controller method had 0 {@code @PreAuthorize}/{@code @RequirePermission}
 * guard).
 *
 * <p>This test wires a minimal Spring context with {@code @EnableReactiveMethodSecurity}
 * (the same mechanism {@code tnt-bootstrap}'s {@code TntSecurityConfig} enables app-wide)
 * around the real controller bean, with no Authentication populated in the reactive
 * security context — i.e. an anonymous caller reaching the controller directly, exactly
 * what {@code .anyExchange().permitAll()} allows through today. Before the
 * {@code @PreAuthorize("isAuthenticated()")} fix, this call reached the use case with no
 * check at all; after the fix, the method-security interceptor rejects it before the
 * controller body ever runs.
 *
 * @author MANFOUO Braun
 */
@SpringJUnitConfig(classes = MarketCampaignControllerSecurityTest.TestConfig.class)
class MarketCampaignControllerSecurityTest {

    @Configuration
    @EnableReactiveMethodSecurity
    static class TestConfig {
        @Bean
        IManageMarketCampaignUseCase campaignUseCase() {
            return mock(IManageMarketCampaignUseCase.class);
        }

        @Bean
        MarketCampaignController marketCampaignController(IManageMarketCampaignUseCase useCase) {
            return new MarketCampaignController(useCase);
        }
    }

    @Autowired
    private MarketCampaignController controller;

    @Test
    void create_anonymousCaller_isDeniedBeforeReachingUseCase() {
        CreateCampaignCommand command = new CreateCampaignCommand(
                "tenant-1", java.util.UUID.randomUUID(), "PROMO10", "10% off",
                CampaignType.PROMO_CODE, DiscountType.PERCENTAGE,
                10.0, 5000L, 1000L, true,
                null, null, null, null, null, 100, "PROMO10");

        // No Authentication in the reactive context — mirrors a raw request through the
        // platform gateway's .anyExchange().permitAll() with no X-Client-Id/X-Api-Key.
        StepVerifier.create(controller.create(command))
                .expectErrorMatches(e -> e instanceof AccessDeniedException
                        || e instanceof AuthenticationCredentialsNotFoundException)
                .verify();
    }
}
