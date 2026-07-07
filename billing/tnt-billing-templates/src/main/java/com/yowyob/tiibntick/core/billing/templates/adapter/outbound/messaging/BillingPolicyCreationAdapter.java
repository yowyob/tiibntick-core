package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.messaging;

import com.yowyob.tiibntick.core.billing.templates.port.outbound.IBillingPolicyCreationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Adapter implementing {@link IBillingPolicyCreationPort} by delegating to
 * {@code tnt-billing-pricing} via a direct Spring bean reference (modular monolith mode).
 *
 * <p><b>Architecture note:</b> In the current modular monolith deployment
 * ({@code tnt-bootstrap}), this adapter resolves the {@code IBillingPolicyService}
 * bean from {@code tnt-billing-pricing} directly via Spring DI injection.
 * In a future microservices split, this adapter would be replaced by a Kafka command
 * or gRPC call to the pricing service.
 *
 * <p>This is a stub implementation to be completed when {@code tnt-billing-pricing}
 * exposes its service interface. The method contract is fully defined here.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Component
public class BillingPolicyCreationAdapter implements IBillingPolicyCreationPort {

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@code tnt-billing-pricing}'s {@code IBillingPolicyService#createFromTemplate()}.
     * The BillingPolicy is created in DRAFT state. The actor must explicitly activate it.
     *
     * <p><b>TODO:</b> Inject {@code IBillingPolicyService} from tnt-billing-pricing
     * once the module's public API is stabilized. Replace this stub with the real delegation.
     */
    @Override
    public Mono<UUID> createBillingPolicy(BillingPolicyCreationRequest request) {
        log.info("Creating BillingPolicy in tnt-billing-pricing for actor={} template={}",
                request.ownerActorId(), request.templateCode());

        /*
         * TODO: Replace this stub with actual delegation to tnt-billing-pricing:
         *
         * return billingPolicyService.createFromTemplate(
         *     CreateBillingPolicyCommand.builder()
         *         .tenantId(request.tenantId())
         *         .ownerActorId(request.ownerActorId())
         *         .ownerType(request.ownerType().name())
         *         .name(request.policyName())
         *         .isFromTemplate(request.isFromTemplate())
         *         .templateCode(request.templateCode())
         *         .dslRules(request.generatedDslRules())
         *         .appliedParameters(request.appliedParameters())
         *         .build()
         * );
         */

        // Stub: generates a UUID simulating a created policy for testing / scaffolding
        UUID generatedId = UUID.randomUUID();
        log.warn("BillingPolicyCreationAdapter is a STUB — returning generated UUID {} for template {}. "
                        + "Replace with real tnt-billing-pricing delegation before production.",
                generatedId, request.templateCode());
        return Mono.just(generatedId);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>TODO:</b> Delegate to tnt-billing-pricing's preview calculation endpoint.
     */
    @Override
    public Mono<Map<String, Object>> computePreview(
            String templateCode,
            Map<String, String> customizedParameters,
            Map<String, Object> scenarioContext) {

        log.debug("Preview delegation to tnt-billing-pricing for template={}", templateCode);
        // Stub: returns empty map — local calculator in TemplatePriceCalculatorService is used instead
        return Mono.just(Map.of());
    }
}
