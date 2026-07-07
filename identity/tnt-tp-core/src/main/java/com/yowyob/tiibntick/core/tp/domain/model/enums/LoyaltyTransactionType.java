package com.yowyob.tiibntick.core.tp.domain.model.enums;

/**
 * Types of loyalty points transactions.
 *
 * @author MANFOUO Braun
 */
public enum LoyaltyTransactionType {

    /** Points earned after a successful delivery. */
    EARNED_FROM_DELIVERY,

    /** Points earned from a promotional campaign. */
    EARNED_FROM_PROMOTION,

    /** Points earned as a sign-up bonus. */
    EARNED_SIGN_UP_BONUS,

    /** Points redeemed for a discount on delivery. */
    REDEEMED_FOR_DISCOUNT,

    /** Points expired (not used within validity period). */
    EXPIRED,

    /** Manual adjustment by platform administrator. */
    ADMIN_ADJUSTMENT
}
