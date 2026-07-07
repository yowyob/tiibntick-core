package com.yowyob.tiibntick.core.billing.templates.port.outbound;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Outbound port representing the integration with {@code tnt-billing-pricing}.
 *
 * <p>When an actor applies a template, this module delegates to {@code tnt-billing-pricing}
 * to create the actual {@code BillingPolicy} aggregate. This port abstracts the
 * cross-module communication, allowing the domain to remain ignorant of the pricing
 * module's internal implementation (module boundary pattern).
 *
 * <p>The adapter implementation may use a direct Spring bean reference (if deployed
 * in the same JVM via {@code tnt-bootstrap}) or a Kafka command for async cross-service
 * communication in a future microservices split.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface IBillingPolicyCreationPort {

    /**
     * Creates a new {@code BillingPolicy} in {@code tnt-billing-pricing} from a template.
     *
     * @param request the BillingPolicy creation request
     * @return Mono containing the UUID of the newly created BillingPolicy
     */
    Mono<UUID> createBillingPolicy(BillingPolicyCreationRequest request);

    /**
     * Transfers a price preview calculation to the pricing engine.
     *
     * <p>For complex template previews that require the full DSL evaluation engine,
     * the computation is delegated here. For simpler templates, the preview may be
     * computed locally within {@code tnt-billing-templates}.
     *
     * @param templateCode         the catalog template code
     * @param customizedParameters the parameter overrides
     * @param scenarioContext      the pricing scenario context (serialized map)
     * @return Mono containing the preview result as a map of breakdown components
     */
    Mono<Map<String, Object>> computePreview(
            String templateCode,
            Map<String, String> customizedParameters,
            Map<String, Object> scenarioContext);

    /**
     * Request object for BillingPolicy creation in the pricing module.
     * Carries all information derived from the template application.
     */
    record BillingPolicyCreationRequest(
            /** The tenant ID of the policy owner. */
            String tenantId,

            /** The actor UUID (kernel integration key). */
            String ownerActorId,

            /** The actor type. */
            PolicyOwnerType ownerType,

            /** Display name for the generated BillingPolicy. */
            String policyName,

            /** Whether this policy is linked to a catalog template. */
            boolean isFromTemplate,

            /** The source template code (for traceability). */
            String templateCode,

            /** The serialized DSL rules derived from the template + custom params. */
            String generatedDslRules,

            /** The parameter values used, for audit and re-application. */
            Map<String, String> appliedParameters
    ) {}
}
