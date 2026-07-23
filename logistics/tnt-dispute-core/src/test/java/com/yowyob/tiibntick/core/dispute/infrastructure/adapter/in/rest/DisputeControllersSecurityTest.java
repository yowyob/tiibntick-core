package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest;

import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeQueryUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IEvidenceUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.mock;

/**
 * Regression test for Audit n°7 · #6 — {@code DisputeController}/{@code EvidenceController}/
 * {@code MediationController} had zero {@code @PreAuthorize}/{@code @RequirePermission} guard
 * on any endpoint.
 *
 * <p>Same pattern as {@code VehicleControllerSecurityTest} (tnt-resource-core): wires a
 * minimal Spring context with {@code @EnableReactiveMethodSecurity} around the real
 * controller beans, with no {@code Authentication} in the reactive security context.
 *
 * @author MANFOUO Braun
 */
@SpringJUnitConfig(classes = DisputeControllersSecurityTest.TestConfig.class)
class DisputeControllersSecurityTest {

    @Configuration
    @EnableReactiveMethodSecurity
    static class TestConfig {
        @Bean IDisputeCommandUseCase disputeCommandUseCase() { return mock(IDisputeCommandUseCase.class); }
        @Bean IDisputeQueryUseCase disputeQueryUseCase() { return mock(IDisputeQueryUseCase.class); }
        @Bean IEvidenceUseCase evidenceUseCase() { return mock(IEvidenceUseCase.class); }

        @Bean
        DisputeController disputeController(IDisputeCommandUseCase cmd, IDisputeQueryUseCase qry) {
            return new DisputeController(cmd, qry);
        }

        @Bean
        EvidenceController evidenceController(IEvidenceUseCase evidenceUseCase) {
            return new EvidenceController(evidenceUseCase);
        }

        @Bean
        MediationController mediationController(IDisputeCommandUseCase cmd) {
            return new MediationController(cmd);
        }
    }

    @Autowired private DisputeController disputeController;
    @Autowired private EvidenceController evidenceController;
    @Autowired private MediationController mediationController;

    @Test
    void disputeController_withdraw_anonymousCaller_isDeniedBeforeReachingUseCase() {
        StepVerifier.create(disputeController.withdraw(null, UUID.randomUUID().toString(), null))
                .expectErrorMatches(e -> e instanceof AccessDeniedException
                        || e instanceof AuthenticationCredentialsNotFoundException)
                .verify();
    }

    @Test
    void evidenceController_submitEvidence_anonymousCaller_isDeniedBeforeReachingUseCase() {
        StepVerifier.create(evidenceController.submitEvidence(null, UUID.randomUUID().toString(), null))
                .expectErrorMatches(e -> e instanceof AccessDeniedException
                        || e instanceof AuthenticationCredentialsNotFoundException)
                .verify();
    }

    @Test
    void mediationController_startMediation_anonymousCaller_isDeniedBeforeReachingUseCase() {
        StepVerifier.create(mediationController.startMediation(null, UUID.randomUUID().toString(), null))
                .expectErrorMatches(e -> e instanceof AccessDeniedException
                        || e instanceof AuthenticationCredentialsNotFoundException)
                .verify();
    }
}
