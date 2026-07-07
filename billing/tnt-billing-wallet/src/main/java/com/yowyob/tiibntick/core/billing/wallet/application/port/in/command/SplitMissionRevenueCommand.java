package com.yowyob.tiibntick.core.billing.wallet.application.port.in.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to split mission revenue between the platform, FreelancerOrg, and sub-deliverer.
 *
 * <p>Revenue model:
 * <pre>
 *   totalAmount = platformCommission + orgRevenue + subDelivererCommission
 * </pre>
 *
 * @author MANFOUO Braun
 */
public record SplitMissionRevenueCommand(
        /** The delivery mission ID. */
        @NotBlank String missionId,
        /** Total amount paid by the client (XAF). */
        @NotNull @Positive BigDecimal totalAmount,
        /** UUID of the FreelancerOrg receiving the org revenue. Integration key. */
        @NotBlank String freelancerOrgId,
        /** Tenant context. */
        @NotNull UUID tenantId,
        /**
         * Sub-deliverer actor UUID (null if the OWNER executes directly).
         * References tnt-actor-core UUID — pure integration key.
         */
        String subDelivererId,
        /** Platform commission rate [0.0, 1.0]. Default: 0.05 (5%). */
        double platformCommissionRate,
        /** Sub-deliverer commission rate [0.0, 1.0]. Ignored when subDelivererId is null. */
        double subDelivererCommissionRate
) {
    public SplitMissionRevenueCommand {
        if (platformCommissionRate < 0 || platformCommissionRate > 1)
            throw new IllegalArgumentException("platformCommissionRate must be in [0, 1]");
        if (subDelivererCommissionRate < 0 || subDelivererCommissionRate > 1)
            throw new IllegalArgumentException("subDelivererCommissionRate must be in [0, 1]");
    }
}
