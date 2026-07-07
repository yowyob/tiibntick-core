package com.yowyob.tiibntick.core.dispute.application.port.outbound;

import reactor.core.publisher.Mono;

/**
 * Secondary port for updating the package status in tnt-delivery-core
 * when a dispute is opened (DISPUTED) or closed (released to original state).
 *
 * @author MANFOUO Braun
 */
public interface IDeliveryStatusPort {

    /**
     * Sets the package status to DISPUTED in tnt-delivery-core,
     * blocking further delivery operations until the dispute is resolved.
     *
     * @param packageId the package ID
     * @param disputeId the associated dispute ID (for reference)
     * @param tenantId  the tenant scope
     * @return a Mono that completes when the status is updated
     */
    Mono<Void> markPackageAsDisputed(String packageId, String disputeId, String tenantId);

    /**
     * Releases the package from DISPUTED status following dispute closure.
     * The target status (DELIVERED, RETURNED_TO_SENDER, etc.) is determined
     * by the dispute resolution.
     *
     * @param packageId          the package ID
     * @param disputeId          the associated dispute ID
     * @param resolutionOutcome  the target post-dispute package status (e.g. "REFUNDED", "RESOLVED")
     * @param tenantId           the tenant scope
     * @return a Mono that completes when the status is updated
     */
    Mono<Void> releasePackageFromDispute(String packageId, String disputeId, String resolutionOutcome, String tenantId);
}
