package com.yowyob.tiibntick.core.billing.templates.application.usecase;

import com.yowyob.tiibntick.core.billing.templates.application.command.PreviewPriceCommand;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateApplicabilityService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplateParameterValidationService;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplatePriceCalculatorService;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotFoundException;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplatePreviewResult;
import com.yowyob.tiibntick.core.billing.templates.port.inbound.IPreviewPriceUseCase;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IPolicyTemplateRepository;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DeliveryPriority;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.WeatherCondition;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.BillingPolicy;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceEvaluation;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceLineItem;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IPricingUseCase;
import com.yowyob.tiibntick.core.billing.templates.application.service.TemplatePolicyMaterializer;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Implementation of the price preview use case.
 *
 * <p>Computes a price estimate by:
 * <ol>
 *   <li>Looking up the template from the catalog</li>
 *   <li>Checking applicability (ownerType)</li>
 *   <li>Validating any custom parameter overrides</li>
 *   <li>Merging overrides with template defaults</li>
 *   <li>Running the local price calculator against the sample scenario</li>
 * </ol>
 *
 * <p>No BillingPolicy is created. No Kafka event is published. This is purely
 * a read-side calculation for UI preview purposes.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewPriceFromTemplateUseCase implements IPreviewPriceUseCase {

    private final IPolicyTemplateRepository templateRepository;
    private final TemplateApplicabilityService applicabilityService;
    private final TemplateParameterValidationService validationService;
    private final TemplatePriceCalculatorService calculatorService;
    private final TemplatePolicyMaterializer materializer;
    private final IPricingUseCase pricingEngine;

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<TemplatePreviewResult> preview(PreviewPriceCommand command) {
        log.debug("Computing price preview for template={} ownerType={} distanceKm={} weightKg={}",
                command.getTemplateCode(), command.getOwnerType(),
                command.getDistanceKm(), command.getWeightKg());

        return templateRepository.findByTemplateCode(command.getTemplateCode())
                .switchIfEmpty(Mono.error(new TemplateNotFoundException(command.getTemplateCode())))
                // Check applicability and active status
                .flatMap(template -> applicabilityService.validate(template, command.getOwnerType()))
                // Validate custom parameter overrides
                .flatMap(template -> validationService.validate(template, command.getCustomizedParameters())
                        .thenReturn(template))
                // Compute preview
                .flatMap(template -> {
                    Map<String, String> effectiveParams = validationService.mergeWithDefaults(
                            template, command.getCustomizedParameters());
                    if (materializer.supports(template.getTemplateCode())) {
                        // Same rules, same engine as the real billing: the preview is the price.
                        return previewWithEngine(template.getTemplateCode(), effectiveParams, command);
                    }
                    TemplatePreviewResult result = calculatorService.compute(
                            template.getTemplateCode(), effectiveParams, command);
                    log.debug("Preview result for template {}: totalPrice={} XAF",
                            template.getTemplateCode(), result.getTotalPriceXaf());
                    return Mono.just(result);
                });
    }

    private Mono<TemplatePreviewResult> previewWithEngine(String templateCode, Map<String, String> params,
                                                          PreviewPriceCommand c) {
        TemplatePolicyMaterializer.Materialized m = materializer.materialize(templateCode, params);
        BillingPolicy policy = BillingPolicy.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).name("preview " + templateCode)
                .pricingRules(m.pricingRules()).surchargeRules(m.surchargeRules())
                .promotions(List.of()).loyaltyRules(m.loyaltyRules()).commissionRules(m.commissionRules())
                .specialSurcharges(List.of()).hubStorageRules(List.of()).networkTransitRules(List.of())
                .status(PolicyStatus.ACTIVE).validFrom(LocalDate.now()).build();
        return pricingEngine.evaluateWith(policy, toContext(c))
                .map(ev -> toResult(templateCode, policy, ev, c));
    }

    private static PricingContext toContext(PreviewPriceCommand c) {
        return PricingContext.builder()
                .distanceKm(c.getDistanceKm()).weightKg(c.getWeightKg())
                .packageTypes(List.of(enumOr(PackageType.class, c.getPackageType(), PackageType.STANDARD)))
                .priority(priorityOf(c.getPriority()))
                .clientTxCount(c.getClientTransactionCount())
                .timeOfDay(parseTime(c.getTimeOfDay()))
                .weatherCondition(enumOr(WeatherCondition.class, c.getWeatherCondition(), WeatherCondition.CLEAR))
                .deliveryZoneType(c.getDeliveryZoneType()).zoneAccessDifficulty(c.getZoneAccessDifficulty())
                .paymentMethod(c.getPaymentMethod())
                .dayOfWeek(enumOr(DayOfWeek.class, c.getDayOfWeek(), DayOfWeek.TUESDAY))
                .isPublicHoliday(c.isPublicHoliday())
                .packageCount(c.getPackageCount())
                .declaredValue(BigDecimal.valueOf(c.getDeclaredValueXaf()))
                .requiresRefrigeration(c.isRequiresRefrigeration()).requiresAssembly(c.isRequiresAssembly())
                .requiresIDCheck(c.isRequiresIDCheck()).deliveryAttemptNumber(c.getDeliveryAttemptNumber())
                .build();
    }

    /** The catalog speaks of EXPRESS; the engine's scale is LOW, NORMAL, HIGH, URGENT, CRITICAL. */
    private static DeliveryPriority priorityOf(String raw) {
        if (raw == null) return DeliveryPriority.NORMAL;
        return switch (raw.toUpperCase()) {
            case "STANDARD" -> DeliveryPriority.NORMAL;
            case "EXPRESS", "PRIORITY" -> DeliveryPriority.HIGH;
            default -> enumOr(DeliveryPriority.class, raw, DeliveryPriority.NORMAL);
        };
    }

    private static LocalTime parseTime(String raw) {
        try {
            return raw == null ? LocalTime.NOON : LocalTime.parse(raw);
        } catch (Exception e) {
            return LocalTime.NOON;
        }
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String raw, E fallback) {
        if (raw == null) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static TemplatePreviewResult toResult(String templateCode, BillingPolicy policy,
                                                  PriceEvaluation ev, PreviewPriceCommand c) {
        BigDecimal base = BigDecimal.ZERO, km = BigDecimal.ZERO, kg = BigDecimal.ZERO, surchargesTotal = BigDecimal.ZERO;
        List<TemplatePreviewResult.SurchargeBreakdownItem> surcharges = new ArrayList<>();
        for (PriceLineItem l : ev.getPriceBreakdown()) {
            BigDecimal amount = l.getAmount().getAmount();
            switch (l.getType()) {
                case BASE -> base = base.add(amount);
                case PER_KM -> km = km.add(amount);
                case PER_KG -> kg = kg.add(amount);
                case SURCHARGE -> {
                    surchargesTotal = surchargesTotal.add(amount);
                    surcharges.add(TemplatePreviewResult.SurchargeBreakdownItem.builder()
                            .code(l.getLabel()).labelFr(l.getLabel()).labelEn(l.getLabel())
                            .amountXaf(amount).unit("XAF").build());
                }
                default -> { }
            }
        }
        BigDecimal minimum = policy.getPricingRules().stream()
                .filter(r -> r.getMinimumPrice() != null)
                .map(r -> r.getMinimumPrice().getAmount()).findFirst().orElse(BigDecimal.ZERO);
        BigDecimal total = ev.getSellingPrice().getAmount();
        return TemplatePreviewResult.builder()
                .templateCode(templateCode).totalPriceXaf(total).basePriceXaf(base)
                .distanceCostXaf(km).weightCostXaf(kg).appliedSurcharges(surcharges)
                .totalSurchargesXaf(surchargesTotal).currency("XAF")
                .aboveMinimumPrice(total.compareTo(minimum) >= 0).minimumPriceXaf(minimum)
                .scenario(TemplatePreviewResult.PreviewScenario.builder()
                        .distanceKm(c.getDistanceKm()).weightKg(c.getWeightKg()).packageType(c.getPackageType())
                        .priority(c.getPriority()).clientTransactionCount(c.getClientTransactionCount())
                        .deliveryZoneType(c.getDeliveryZoneType()).weatherCondition(c.getWeatherCondition())
                        .paymentMethod(c.getPaymentMethod()).requiresRefrigeration(c.isRequiresRefrigeration())
                        .requiresAssembly(c.isRequiresAssembly()).requiresIDCheck(c.isRequiresIDCheck())
                        .timeOfDay(c.getTimeOfDay()).dayOfWeek(c.getDayOfWeek()).build())
                .build();
    }
}
