package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.OrderPriority;
import com.yowyob.tiibntick.core.sales.domain.model.TntAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Command to create a new TiiBnTick sales order.
 *
 * <p> — Added FreelancerOrg provider context:
 * <ul>
 *   <li>{@link #providerOrgType} — AGENCY | FREELANCER_ORG</li>
 *   <li>{@link #providerOrgId} — UUID of the executing org</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public record CreateTntSalesOrderCommand(
        @NotNull UUID tenantId,
        @NotNull UUID organizationId,
        @NotNull UUID agencyId,
        @NotNull UUID clientThirdPartyId,
        @NotEmpty @Valid List<CreateTntOrderLineCommand> lines,
        @NotNull TntAddress deliveryAddress,
        TntAddress billingAddress,
        @NotNull OrderPriority priority,
        @NotBlank String currency,
        String requestedByUserId,

        // ── : FreelancerOrg provider context ─────────────────────────────────

        /**
         * Type of the logistics provider executing this order.
         * Values: {@code "AGENCY"} (default) | {@code "FREELANCER_ORG"}.
         * Null = agency default (backward compatible).
         */
        String providerOrgType,

        /**
         * UUID of the FreelancerOrg or Agency executing this order.
         * References tnt-organization-core UUID — pure integration key (no join).
         * Null for standard agency orders.
         */
        String providerOrgId
) {
    /**
     * Backward-compatible factory without provider context.
     */
    public static CreateTntSalesOrderCommand simple(
            UUID tenantId, UUID organizationId, UUID agencyId, UUID clientThirdPartyId,
            List<CreateTntOrderLineCommand> lines, TntAddress deliveryAddress,
            TntAddress billingAddress, OrderPriority priority, String currency,
            String requestedByUserId) {
        return new CreateTntSalesOrderCommand(tenantId, organizationId, agencyId, clientThirdPartyId,
                lines, deliveryAddress, billingAddress, priority, currency, requestedByUserId,
                null, null);
    }
}
