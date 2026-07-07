package com.yowyob.tiibntick.core.billing.templates.application.usecase;

import com.yowyob.tiibntick.core.billing.templates.application.command.ApplyTemplateCommand;
import com.yowyob.tiibntick.core.billing.templates.application.service.DslRuleGeneratorService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateApplicabilityService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateParameterValidationService;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateInactiveException;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotApplicableException;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotFoundException;
import com.yowyob.tiibntick.core.billing.templates.domain.model.*;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ApplyTemplateUseCase}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@ExtendWith(MockitoExtension.class)
class ApplyTemplateUseCaseTest {

    @Mock private IPolicyTemplateRepository templateRepository;
    @Mock private ICustomTemplateRepository customTemplateRepository;
    @Mock private IBillingPolicyCreationPort billingPolicyCreationPort;
    @Mock private ITemplateEventPublisher eventPublisher;

    private TemplateApplicabilityService applicabilityService;
    private TemplateParameterValidationService validationService;
    private DslRuleGeneratorService dslGeneratorService;

    @InjectMocks
    private ApplyTemplateUseCase useCase;

    private PolicyTemplate activeTemplate;
    private PolicyTemplate inactiveTemplate;
    private final UUID generatedPolicyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        applicabilityService = new TemplateApplicabilityService();
        validationService = new TemplateParameterValidationService();
        dslGeneratorService = new DslRuleGeneratorService();

        useCase = new ApplyTemplateUseCase(
                templateRepository, customTemplateRepository,
                applicabilityService, validationService, dslGeneratorService,
                billingPolicyCreationPort, eventPublisher
        );

        activeTemplate = PolicyTemplate.createNew(
                "TPL-BASE-STD", "Standard Base Pricing", "Description",
                TemplateCategory.BASE,
                List.of(PolicyOwnerType.AGENCY, PolicyOwnerType.FREELANCER_ORG),
                List.of(
                        TemplateParameter.builder()
                                .key("basePrice").labelFr("Prix de base").labelEn("Base price")
                                .defaultValue("500").minValue("100").maxValue("50000")
                                .unit("XAF").type(ParameterType.MONEY).helpText("").build()
                ),
                "IF weight >= 0 THEN SET_BASE(500 XAF)"
        );

        inactiveTemplate = activeTemplate.deactivate();
    }

    @Nested
    @DisplayName("Successful application")
    class SuccessPath {

        @Test
        @DisplayName("Should create a BillingPolicy and publish event for valid command")
        void shouldApplyTemplateAndCreatePolicy() {
            when(templateRepository.findByTemplateCode("TPL-BASE-STD"))
                    .thenReturn(Mono.just(activeTemplate));
            when(billingPolicyCreationPort.createBillingPolicy(any()))
                    .thenReturn(Mono.just(generatedPolicyId));
            when(eventPublisher.publishTemplateApplied(any()))
                    .thenReturn(Mono.empty());

            ApplyTemplateCommand command = ApplyTemplateCommand.builder()
                    .templateCode("TPL-BASE-STD")
                    .ownerActorId("actor-123")
                    .ownerType(PolicyOwnerType.AGENCY)
                    .tenantId("AGY-abc")
                    .customizedParameters(Map.of())
                    .build();

            StepVerifier.create(useCase.apply(command))
                    .expectNext(generatedPolicyId)
                    .verifyComplete();

            verify(billingPolicyCreationPort).createBillingPolicy(any());
            verify(eventPublisher).publishTemplateApplied(any());
        }

        @Test
        @DisplayName("Should use custom parameter overrides in the BillingPolicy request")
        void shouldApplyCustomOverrides() {
            when(templateRepository.findByTemplateCode("TPL-BASE-STD"))
                    .thenReturn(Mono.just(activeTemplate));
            when(billingPolicyCreationPort.createBillingPolicy(argThat(req ->
                    req.appliedParameters().containsKey("basePrice")
                    && req.appliedParameters().get("basePrice").equals("700")
            ))).thenReturn(Mono.just(generatedPolicyId));
            when(eventPublisher.publishTemplateApplied(any())).thenReturn(Mono.empty());

            ApplyTemplateCommand command = ApplyTemplateCommand.builder()
                    .templateCode("TPL-BASE-STD")
                    .ownerActorId("actor-123")
                    .ownerType(PolicyOwnerType.AGENCY)
                    .tenantId("AGY-abc")
                    .customizedParameters(Map.of("basePrice", "700"))
                    .build();

            StepVerifier.create(useCase.apply(command))
                    .expectNext(generatedPolicyId)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorPath {

        @Test
        @DisplayName("Should throw TemplateNotFoundException when template code not found")
        void shouldFailWhenTemplateNotFound() {
            when(templateRepository.findByTemplateCode("TPL-UNKNOWN"))
                    .thenReturn(Mono.empty());

            ApplyTemplateCommand command = ApplyTemplateCommand.builder()
                    .templateCode("TPL-UNKNOWN").ownerActorId("a").ownerType(PolicyOwnerType.AGENCY).tenantId("t").build();

            StepVerifier.create(useCase.apply(command))
                    .expectError(TemplateNotFoundException.class)
                    .verify();

            verifyNoInteractions(billingPolicyCreationPort);
        }

        @Test
        @DisplayName("Should throw TemplateInactiveException when template is deactivated")
        void shouldFailWhenTemplateInactive() {
            when(templateRepository.findByTemplateCode("TPL-BASE-STD"))
                    .thenReturn(Mono.just(inactiveTemplate));

            ApplyTemplateCommand command = ApplyTemplateCommand.builder()
                    .templateCode("TPL-BASE-STD").ownerActorId("a").ownerType(PolicyOwnerType.AGENCY).tenantId("t").build();

            StepVerifier.create(useCase.apply(command))
                    .expectError(TemplateInactiveException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should throw TemplateNotApplicableException for wrong owner type")
        void shouldFailWhenNotApplicable() {
            when(templateRepository.findByTemplateCode("TPL-BASE-STD"))
                    .thenReturn(Mono.just(activeTemplate));

            ApplyTemplateCommand command = ApplyTemplateCommand.builder()
                    .templateCode("TPL-BASE-STD").ownerActorId("a").ownerType(PolicyOwnerType.POINT).tenantId("t").build();

            StepVerifier.create(useCase.apply(command))
                    .expectError(TemplateNotApplicableException.class)
                    .verify();
        }
    }
}
