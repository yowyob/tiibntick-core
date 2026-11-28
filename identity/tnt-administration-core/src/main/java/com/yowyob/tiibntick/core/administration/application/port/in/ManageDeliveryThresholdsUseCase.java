package com.yowyob.tiibntick.core.administration.application.port.in;

import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThresholdValues;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.OrganizationDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.PlatformDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageBounds;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Administration of the delivery thresholds ([B.5], QO-8, QO-18, QO-29): platform values set by the TiiBnTick
 * administrator and overrides of a TiiBnTick Agency organization. Every change publishes
 * {@code DeliveryThresholdsChanged} on {@code tnt.administration.events}.
 */
public interface ManageDeliveryThresholdsUseCase {

    Mono<PlatformDeliveryThresholds> getPlatform();

    /**
     * Replaces the platform values.
     *
     * @throws com.yowyob.tiibntick.common.exception.TntConflictException   version is stale (HTTP 409)
     * @throws com.yowyob.tiibntick.common.exception.TntValidationException a value is out of range (HTTP 422)
     */
    Mono<PlatformDeliveryThresholds> updatePlatform(UpdatePlatformCommand command);

    /** Every organization override (platform administration view). */
    Flux<OrganizationDeliveryThresholds> listOrganizationOverrides();

    /** The override of an organization, empty when there is none. */
    Mono<OrganizationDeliveryThresholds> getOrganizationOverride(UUID organizationId);

    /**
     * Creates or replaces the override of a TiiBnTick Agency organization.
     *
     * @throws com.yowyob.tiibntick.common.exception.TntValidationException not an agency, no value, or out of range
     * @throws com.yowyob.tiibntick.common.exception.TntConflictException   version is stale
     */
    Mono<OrganizationDeliveryThresholds> putOrganizationOverride(PutOrganizationCommand command);

    /**
     * Removes the override of an organization (it inherits the platform values again).
     *
     * @return {@code true} when an override was removed, {@code false} when there was none
     */
    Mono<Boolean> deleteOrganizationOverride(UUID organizationId, UUID actorUserId, UUID tenantId);

    /**
     * QO-29: checks the storage duration an independent relay wants to set for itself against the admin's bounds.
     * For the relay settings use case (Go-Freelancer relay points).
     *
     * @throws com.yowyob.tiibntick.common.exception.TntValidationException out of bounds (HTTP 422)
     */
    Mono<Void> checkIndependentRelayStorageDays(int storageDays);

    /**
     * @param values          every threshold (all required)
     * @param relayBounds     QO-29 bounds ({@code null} bound = not set)
     * @param expectedVersion version read by the caller
     * @param actorUserId     the administrator
     * @param tenantId        tenant of the caller (event envelope only — the setting is platform-wide)
     */
    record UpdatePlatformCommand(DeliveryThresholdValues values, RelayStorageBounds relayBounds,
                                 long expectedVersion, UUID actorUserId, UUID tenantId) {
    }

    /**
     * @param organizationId  Kernel organization id of the agency
     * @param values          overridden thresholds only (at least one)
     * @param expectedVersion version read by the caller; {@code null} to create
     * @param actorUserId     the user making the change
     * @param tenantId        tenant of the caller (event envelope)
     */
    record PutOrganizationCommand(UUID organizationId, DeliveryThresholdValues values, Long expectedVersion,
                                  UUID actorUserId, UUID tenantId) {
    }
}
