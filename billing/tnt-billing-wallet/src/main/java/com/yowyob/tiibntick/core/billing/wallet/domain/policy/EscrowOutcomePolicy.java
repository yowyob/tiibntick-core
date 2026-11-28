package com.yowyob.tiibntick.core.billing.wallet.domain.policy;

/**
 * What happens to the escrow of a delivery (spec §4.2, pure domain), with the decisions QO-10, QO-23, QO-24:
 * <ul>
 *   <li>delivered, or deposited at the relay that is its destination (QO-10) → {@link Action#RELEASE};</li>
 *   <li>cancelled before pickup → {@link Action#REFUND};</li>
 *   <li>cancelled after pickup or failed: fault of the courier or the platform → {@link Action#REFUND}; of the
 *       sender, the recipient, or nobody → {@link Action#RELEASE} (the courier did his work, QO-24); unknown party →
 *       {@link Action#REFUND} would penalise the courier and {@link Action#RELEASE} the payer: left to support,
 *       {@link Action#NONE};</li>
 *   <li>parcel lost or damaged (D21) → {@link Action#REFUND} (the compensation comes from the fund, §4.3);</li>
 *   <li>dispute opened → {@link Action#FREEZE}; dispute closed restoring the parcel → {@link Action#UNFREEZE};
 *       loss/damage compensated → {@link Action#REFUND}; return or redelivery ordered → {@link Action#REFUND} unless
 *       the outbound leg was carried out without the courier's fault (QO-23) → {@link Action#RELEASE}.</li>
 * </ul>
 * An escrow still held or frozen when a dispute closes means the outbound leg did not complete (a completed leg
 * releases it at D7/D11), which is what the dispute consumer passes.
 * <ul>
 * </ul>
 */
public final class EscrowOutcomePolicy {

    public enum Action { RELEASE, REFUND, FREEZE, UNFREEZE, NONE }

    /** Cause of a delivery end, as published on {@code tnt.delivery.events}. */
    public enum DeliveryEnd { DELIVERED, DEPOSITED_AT_DESTINATION_RELAY, CANCELLED_BEFORE_PICKUP, CANCELLED_AFTER_PICKUP,
        FAILED, PARCEL_LOST_OR_DAMAGED }

    private EscrowOutcomePolicy() {
    }

    /** @param responsibleParty {@code SENDER}, {@code RECIPIENT}, {@code COURIER}, {@code PLATFORM}, {@code RELAY},
     *                          {@code AGENCY}, {@code NONE}, or {@code null} when not known */
    public static Action onDeliveryEnd(DeliveryEnd end, String responsibleParty) {
        return switch (end) {
            case DELIVERED, DEPOSITED_AT_DESTINATION_RELAY -> Action.RELEASE;
            case CANCELLED_BEFORE_PICKUP, PARCEL_LOST_OR_DAMAGED -> Action.REFUND;
            case CANCELLED_AFTER_PICKUP, FAILED -> byFault(responsibleParty);
        };
    }

    private static Action byFault(String party) {
        if (party == null) {
            return Action.NONE;
        }
        return switch (party) {
            case "COURIER", "PLATFORM" -> Action.REFUND;
            case "SENDER", "RECIPIENT", "NONE" -> Action.RELEASE;
            default -> Action.NONE; // RELAY, AGENCY: not ruled by QO-24, left to support
        };
    }

    /**
     * @param resolutionType   dispute {@code ResolutionType} name, {@code null} for withdrawal/expiry/administrative
     *                         closure without ruling (the parcel is restored)
     * @param courierAtFault   whether the dispute found the courier responsible (QO-23)
     * @param outboundDone     whether the outbound leg was carried out (delivered or deposited at its destination)
     */
    public static Action onDisputeClosed(String resolutionType, boolean courierAtFault, boolean outboundDone) {
        if (resolutionType == null) {
            return Action.UNFREEZE;
        }
        return switch (resolutionType) {
            case "TOTAL_LOSS_COMPENSATED", "DAMAGE_COMPENSATED" -> Action.REFUND;
            // QO-23: refunded, unless the outbound leg was really carried out and the courier is not at fault
            case "RETURN_TO_SENDER_ORDERED", "REDELIVERY_ORDERED" ->
                    outboundDone && !courierAtFault ? Action.RELEASE : Action.REFUND;
            default -> Action.UNFREEZE;
        };
    }
}
