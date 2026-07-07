package com.yowyob.tiibntick.core.sales.adapter.in.web.dto.request;

import com.yowyob.tiibntick.core.sales.domain.model.OrderPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * HTTP request body to create a TiiBnTick SalesOrder.
 *
 * <p> — Added optional FreelancerOrg provider context fields.
 *
 * @author MANFOUO Braun
 */
public record CreateSalesOrderRequest(
        @NotNull UUID clientThirdPartyId,
        @NotEmpty @Valid List<CreateOrderLineRequest> lines,
        @NotNull @Valid DeliveryAddressRequest deliveryAddress,
        @Valid DeliveryAddressRequest billingAddress,
        @NotNull OrderPriority priority,
        @NotBlank String currency,

        // : FreelancerOrg provider context
        @Schema(description = "Type of logistics provider: AGENCY | FREELANCER_ORG. Null = agency default.",
                example = "FREELANCER_ORG")
        String providerOrgType,

        @Schema(description = "UUID of the FreelancerOrg or Agency executing this order. "
                            + "References tnt-organization-core UUID — no physical FK.",
                example = "550e8400-e29b-41d4-a716-446655440000")
        String providerOrgId
) {}
