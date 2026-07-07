package com.yowyob.tiibntick.core.billing.templates.domain.event;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when an actor saves a customized policy configuration
 * as a personal {@code CustomPolicyTemplate} for future reuse.
 *
 * <p>Published to the Kafka topic: {@code tnt.billing.custom_template.saved}
 * This event is primarily informational and used for audit/analytics purposes.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
public class CustomTemplateSavedEvent {

    /** Unique event identifier for idempotency checks. */
    @Builder.Default
    UUID eventId = UUID.randomUUID();

    /** Kafka topic name for this event. */
    public static final String TOPIC = "tnt.billing.custom_template.saved";

    /** Event type discriminator. */
    @Builder.Default
    String eventType = "CUSTOM_TEMPLATE_SAVED";

    /** The ID of the newly created custom template. */
    UUID customTemplateId;

    /** The user-defined name for this custom template. */
    String customTemplateName;

    /** The actor who saved this custom template. */
    String ownerActorId;

    /** The type of the owning actor. */
    PolicyOwnerType ownerType;

    /** The source catalog template code (may be null if created from scratch). */
    String sourceTemplateCode;

    /** The tenant ID of the owning actor. */
    String tenantId;

    /** Timestamp when the custom template was saved. */
    @Builder.Default
    Instant occurredAt = Instant.now();
}
