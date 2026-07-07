package com.yowyob.tiibntick.core.billing.templates.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a personalized billing policy template saved by an actor for reuse.
 *
 * <p>After applying a {@link PolicyTemplate} with custom parameter values, an actor
 * can persist their customized configuration as a {@code CustomPolicyTemplate}.
 * This "saved template" belongs exclusively to the actor and is not visible in the
 * global catalog. It enables the actor to reuse the same pricing configuration
 * without re-entering all parameters each time.
 *
 * <p><b>Relationship to BillingPolicy:</b>
 * A {@code CustomPolicyTemplate} is a <em>snapshot</em> of a policy configuration.
 * Applying it creates a new {@code BillingPolicy} in {@code tnt-billing-pricing}.
 * Changes to the BillingPolicy after creation do NOT retroactively update the
 * custom template.
 *
 * <p><b>Example:</b>
 * Pauline (FreelancerOrg "Vélo Vert Express") applies {@code TPL-BASE-STD} with
 * basePrice=700 XAF and perKmRate=60 XAF/km. She saves this as "Mon tarif Bastos-Nlongkak".
 * Next month, she can create a new BillingPolicy directly from her saved template
 * without touching the global catalog.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Getter
@Builder
public class CustomPolicyTemplate {

    /**
     * Internal UUID primary key.
     */
    private final UUID id;

    /**
     * The actor (UUID) who owns this custom template.
     * References the {@code actorId} from the kernel layer (no FK to avoid coupling).
     */
    private final String ownerActorId;

    /**
     * The type of the owning actor. Used to enforce DSL access level constraints.
     */
    private final PolicyOwnerType ownerType;

    /**
     * User-defined name for this custom template.
     * Example: "Mon tarif habituel Yaoundé" or "Ernest Pharma Standard Rate".
     */
    @With
    private final String name;

    /**
     * The template code from the global catalog that was used as the source.
     * May be null if the custom template was created from scratch (admin only).
     */
    private final String sourceTemplateCode;

    /**
     * The ID of the {@code BillingPolicy} that was generated from the last
     * application of this custom template. Used for traceability.
     * May be null if the template has not been applied yet (saved but not activated).
     */
    @With
    private final UUID lastGeneratedPolicyId;

    /**
     * The customized parameter values for this template snapshot.
     * Maps parameter key → value string (as entered by the actor).
     */
    private final Map<String, String> customizedParameters;

    /**
     * Timestamp when this custom template was first created.
     */
    private final Instant createdAt;

    /**
     * Timestamp of the last modification (name change, parameter update).
     */
    @With
    private final Instant updatedAt;

    // ─── Factory ───────────────────────────────────────────────────────────

    /**
     * Creates a new custom template from a global catalog template with custom overrides.
     *
     * @param ownerActorId          the actor who is saving this template
     * @param ownerType             the type of the owning actor
     * @param name                  user-defined display name
     * @param sourceTemplateCode    the catalog template used as the base
     * @param customizedParameters  map of parameter key → custom value
     * @return new CustomPolicyTemplate instance
     */
    public static CustomPolicyTemplate createNew(
            String ownerActorId,
            PolicyOwnerType ownerType,
            String name,
            String sourceTemplateCode,
            Map<String, String> customizedParameters) {

        Instant now = Instant.now();
        return CustomPolicyTemplate.builder()
                .id(UUID.randomUUID())
                .ownerActorId(ownerActorId)
                .ownerType(ownerType)
                .name(name)
                .sourceTemplateCode(sourceTemplateCode)
                .lastGeneratedPolicyId(null)
                .customizedParameters(customizedParameters)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Returns a copy of this custom template updated with a new generated policy ID
     * after successfully applying the template and creating a BillingPolicy.
     *
     * @param policyId the ID of the newly created BillingPolicy
     * @return updated instance
     */
    public CustomPolicyTemplate recordPolicyGeneration(UUID policyId) {
        return this.withLastGeneratedPolicyId(policyId).withUpdatedAt(Instant.now());
    }

    /**
     * Returns a copy of this custom template with an updated name.
     *
     * @param newName the new display name
     * @return updated instance
     */
    public CustomPolicyTemplate rename(String newName) {
        return this.withName(newName).withUpdatedAt(Instant.now());
    }
}
