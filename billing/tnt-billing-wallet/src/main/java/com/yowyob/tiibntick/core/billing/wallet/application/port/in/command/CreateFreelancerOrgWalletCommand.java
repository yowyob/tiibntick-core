package com.yowyob.tiibntick.core.billing.wallet.application.port.in.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Command to create a dedicated Wallet for a FreelancerOrganization.
 *
 * <p>Each FreelancerOrg gets its own wallet, separate from the OWNER actor's personal wallet.
 * Revenue from missions is credited to this org wallet, not the individual actor wallet.
 *
 * @author MANFOUO Braun
 */
public record CreateFreelancerOrgWalletCommand(
        /** UUID of the FreelancerOrganization owning this wallet. Integration key — no join. */
        @NotBlank String freelancerOrgId,
        /** Tenant context. */
        @NotNull UUID tenantId,
        /** Currency code (default XAF). */
        @NotBlank String currency
) {
    public CreateFreelancerOrgWalletCommand {
        if (currency == null || currency.isBlank()) currency = "XAF";
    }
}
