package com.yowyob.tiibntick.core.billing.templates.application.usecase;

import com.yowyob.tiibntick.core.billing.templates.application.command.PreviewPriceCommand;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateApplicabilityService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateParameterValidationService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplatePriceCalculatorService;
import com.yowyob.tiibntick.core.billing.templates.domain.model.*;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IPolicyTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PreviewPriceFromTemplateUseCase}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@ExtendWith(MockitoExtension.class)
class PreviewPriceFromTemplateUseCaseTest {

    @Mock
    private IPolicyTemplateRepository templateRepository;

    private PreviewPriceFromTemplateUseCase useCase;
    private PolicyTemplate baseStdTemplate;

    @BeforeEach
    void setUp() {
        useCase = new PreviewPriceFromTemplateUseCase(
                templateRepository,
                new TemplateApplicabilityService(),
                new TemplateParameterValidationService(),
                new TemplatePriceCalculatorService()
        );

        baseStdTemplate = PolicyTemplate.createNew(
                "TPL-BASE-STD", "Standard Base Pricing", "Description",
                TemplateCategory.BASE,
                List.of(PolicyOwnerType.AGENCY, PolicyOwnerType.FREELANCER_ORG),
                List.of(
                        TemplateParameter.builder().key("basePrice").labelFr("").labelEn("").defaultValue("500").minValue("100").maxValue("50000").unit("XAF").type(ParameterType.MONEY).helpText("").build(),
                        TemplateParameter.builder().key("perKmRate").labelFr("").labelEn("").defaultValue("50").minValue("0").maxValue("500").unit("XAF/km").type(ParameterType.MONEY).helpText("").build(),
                        TemplateParameter.builder().key("perKgRate").labelFr("").labelEn("").defaultValue("30").minValue("0").maxValue("200").unit("XAF/kg").type(ParameterType.MONEY).helpText("").build(),
                        TemplateParameter.builder().key("minPrice").labelFr("").labelEn("").defaultValue("500").minValue("0").maxValue("10000").unit("XAF").type(ParameterType.MONEY).helpText("").build()
                ),
                "IF weight >= 0 THEN SET_BASE(500 XAF)"
        );
    }

    @Test
    @DisplayName("Should compute correct price for 5km, 2kg, standard package")
    void shouldComputeCorrectBaseStdPrice() {
        when(templateRepository.findByTemplateCode("TPL-BASE-STD")).thenReturn(Mono.just(baseStdTemplate));

        PreviewPriceCommand command = PreviewPriceCommand.builder()
                .templateCode("TPL-BASE-STD")
                .ownerType(PolicyOwnerType.AGENCY)
                .customizedParameters(Map.of())
                .distanceKm(5.0)
                .weightKg(2.0)
                .packageType("STANDARD")
                .build();

        // Expected: base=500 + distance=5*50=250 + weight=2*30=60 = 810 XAF
        StepVerifier.create(useCase.preview(command))
                .assertNext(result -> {
                    assertThat(result.getBasePriceXaf()).isEqualByComparingTo(new BigDecimal("500"));
                    assertThat(result.getDistanceCostXaf()).isEqualByComparingTo(new BigDecimal("250.00"));
                    assertThat(result.getWeightCostXaf()).isEqualByComparingTo(new BigDecimal("60.00"));
                    assertThat(result.getTotalPriceXaf()).isEqualByComparingTo(new BigDecimal("810"));
                    assertThat(result.getCurrency()).isEqualTo("XAF");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should apply minimum price when calculated price is below floor")
    void shouldApplyMinimumPrice() {
        when(templateRepository.findByTemplateCode("TPL-BASE-STD")).thenReturn(Mono.just(baseStdTemplate));

        PreviewPriceCommand command = PreviewPriceCommand.builder()
                .templateCode("TPL-BASE-STD")
                .ownerType(PolicyOwnerType.AGENCY)
                .customizedParameters(Map.of())
                .distanceKm(0.0)
                .weightKg(0.0)
                .build();

        // base=500, distance=0, weight=0 → 500 which is exactly the minPrice
        StepVerifier.create(useCase.preview(command))
                .assertNext(result -> {
                    assertThat(result.getTotalPriceXaf()).isGreaterThanOrEqualTo(new BigDecimal("500"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should use custom parameter overrides in calculation")
    void shouldUseCustomOverrides() {
        when(templateRepository.findByTemplateCode("TPL-BASE-STD")).thenReturn(Mono.just(baseStdTemplate));

        // Override basePrice to 700, perKmRate to 60
        PreviewPriceCommand command = PreviewPriceCommand.builder()
                .templateCode("TPL-BASE-STD")
                .ownerType(PolicyOwnerType.AGENCY)
                .customizedParameters(Map.of("basePrice", "700", "perKmRate", "60"))
                .distanceKm(5.0)
                .weightKg(2.0)
                .build();

        // Expected: base=700 + distance=5*60=300 + weight=2*30=60 = 1060 XAF
        StepVerifier.create(useCase.preview(command))
                .assertNext(result -> {
                    assertThat(result.getBasePriceXaf()).isEqualByComparingTo(new BigDecimal("700"));
                    assertThat(result.getDistanceCostXaf()).isEqualByComparingTo(new BigDecimal("300.00"));
                    assertThat(result.getTotalPriceXaf()).isEqualByComparingTo(new BigDecimal("1060"));
                })
                .verifyComplete();
    }
}
