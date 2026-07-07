package com.yowyob.tiibntick.core.sales.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for TntSalesOrder header (sales.orders table).
 *
 * <p>The {@code kernelSalesOrderId} column ({@code kernel_sales_order_id}) stores the
 * optional logical reference to the Yowyob Kernel sales order — there is no physical
 * foreign key cross-database.</p>
 *
 * @author MANFOUO Braun
 */
@Table(schema = "sales", name = "orders")
public record TntSalesOrderEntity(
        @Id @Column("id") UUID id,
        @Column("tenant_id") UUID tenantId,
        @Column("organization_id") UUID organizationId,
        @Column("agency_id") UUID agencyId,
        @Column("client_third_party_id") UUID clientThirdPartyId,
        @Column("order_number") String orderNumber,
        @Column("status") String status,
        @Column("priority") String priority,
        @Column("payment_status") String paymentStatus,
        @Column("currency") String currency,
        @Column("subtotal_amount") BigDecimal subtotalAmount,
        @Column("total_amount") BigDecimal totalAmount,
        @Column("mission_id") UUID missionId,
        @Column("invoice_id") UUID invoiceId,
        /**
         * Optional logical reference to RT-comops-sales-core order UUID.
         * NULL for informal / cash-on-delivery transactions with no Kernel counterpart.
         */
        @Column("kernel_sales_order_id") UUID kernelSalesOrderId,
        @Column("return_reason") String returnReason,
        @Column("return_note") String returnNote,
        @Column("cancel_reason") String cancelReason,
        @Column("delivery_street") String deliveryStreet,
        @Column("delivery_quartier") String deliveryQuartier,
        @Column("delivery_city") String deliveryCity,
        @Column("delivery_country") String deliveryCountry,
        @Column("delivery_landmark") String deliveryLandmark,
        @Column("delivery_latitude") Double deliveryLatitude,
        @Column("delivery_longitude") Double deliveryLongitude,
        @Column("delivery_recipient_name") String deliveryRecipientName,
        @Column("delivery_recipient_phone") String deliveryRecipientPhone,
        @Column("confirmed_at") Instant confirmedAt,
        @Column("delivered_at") Instant deliveredAt,
        @Column("returned_at") Instant returnedAt,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt,

        // : FreelancerOrg provider context
        /**
         * Type of the logistics service provider: AGENCY | FREELANCER_ORG.
         * Null for orders not yet dispatched or for agency-only tenants.
         */
        @Column("provider_org_type") String providerOrgType,

        /**
         * UUID of the providing organization.
         * For FreelancerOrg: references tnt-organization-core UUID.
         * No physical FK — cross-module integration key only.
         */
        @Column("provider_org_id") String providerOrgId
) {}
