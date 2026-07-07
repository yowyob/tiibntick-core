package com.yowyob.tiibntick.core.resource.domain.model;

/**
 * Indicates whether a {@code FreelancerEquipment} is owned or rented by the FreelancerOrg.
 *
 * <p>This distinction impacts the operational cost model in {@code tnt-billing-cost}:
 * rented equipment incurs a periodic fee that must be factored into the delivery price,
 * while owned equipment has only depreciation/wear costs.
 *
 * @author MANFOUO Braun
 */
public enum OwnershipType {

    /** Equipment is owned by the FreelancerOrg (purchased outright). */
    OWNED,

    /** Equipment is rented/leased by the FreelancerOrg. */
    RENTED
}
