package com.yowyob.tiibntick.core.billing.templates.domain.event;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event emitted when an actor successfully applies a billing policy template,
 * resulting in the creation of a new {@code BillingPolicy} in {@code tnt-billing-pricing}.
 *
 * <p>Published to the Kafka topic: {@code tnt.billing.template.applied}
 * Consumed by: {@code tnt-notify-core} (to notify the actor), {@code tnt-billing-pricing}
 * (implicit, since the policy was already created synchronously via the outbound port).
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
public class TemplateAppliedEvent {

    /** Unique event identifier for idempotency checks. */
    @Builder.Default
    UUID eventId = UUID.randomUUID();

    /** Kafka topic name for this event. */
    public static final String TOPIC = "tnt.billing.template.applied";

    /** Event type discriminator. */
    @Builder.Default
    String eventType = "TEMPLATE_APPLIED";

    /** The code of the applied template (e.g. TPL-FRAGILE). */
    String templateCode;

    /** The name of the applied template for logging / notifications. */
    String templateName;

    /** The actor who applied the template. References kernel actorId. */
    String ownerActorId;

    /** The type of the owning actor. */
    PolicyOwnerType ownerType;

    /** The ID of the BillingPolicy created as a result. */
    UUID createdPolicyId;

    /** The tenant ID of the owning actor (for multi-tenant routing). */
    String tenantId;

    /** Whether the policy was created from a custom saved template. */
    boolean fromCustomTemplate;

    /** The custom template ID if applicable (null for direct catalog application). */
    UUID customTemplateId;

    /** The parameter values used for this application. */
    Map<String, String> appliedParameters;

    /** Timestamp when the template was applied. */
    @Builder.Default
    Instant occurredAt = Instant.now();
}
