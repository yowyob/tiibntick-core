package com.yowyob.tiibntick.core.billing.wallet.application.port.in.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to transfer a sub-deliverer commission from the FreelancerOrg wallet
 * to the sub-deliverer's personal wallet.
 *
 * <p>Called after a {@code PaymentSplit} is executed, to move the sub-deliverer's
 * commission from the org wallet to the individual sub-deliverer actor wallet.
 *
 * @author MANFOUO Braun
 */
public record TransferSubDelivererCommissionCommand(
        /** UUID of the FreelancerOrg sending the commission. Integration key. */
        @NotBlank String freelancerOrgId,
        /** UUID of the sub-deliverer actor receiving the commission. Integration key. */
        @NotBlank String subDelivererId,
        /** Commission amount to transfer (XAF). */
        @NotNull @Positive BigDecimal amount,
        /** The delivery mission ID — used as transfer reference. */
        @NotBlank String missionId,
        /** Tenant context. */
        @NotNull UUID tenantId
) {}
