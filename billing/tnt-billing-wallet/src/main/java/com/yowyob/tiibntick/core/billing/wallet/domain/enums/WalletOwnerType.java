package com.yowyob.tiibntick.core.billing.wallet.domain.enums;

/**
 * Discriminates the type of entity that owns a Wallet in TiiBnTick.
 *
 * <p>Used in {@code Wallet.ownerType} to distinguish between actor-level
 * wallets (individual users), FreelancerOrg wallets, and Agency wallets.</p>
 *
 * <p> — Added FREELANCER_ORG and AGENCY to support the FreelancerOrganization
 * billing model and mission revenue splitting.</p>
 *
 * @author MANFOUO Braun
 */
public enum WalletOwnerType {
    /** Individual actor wallet — linked to a user UUID from tnt-actor-core. */
    ACTOR,
    /** FreelancerOrganization wallet — linked to a FreelancerOrg UUID from tnt-organization-core. */
    FREELANCER_ORG,
    /** Agency wallet — linked to an Agency UUID from tnt-organization-core. */
    AGENCY
}
