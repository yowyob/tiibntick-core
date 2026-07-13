package com.yowyob.tiibntick.core.agency.billing.adapter.out.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_commercial", name = "billing_policies")
public class BillingPolicyEntity {

    @Id @Column("id") private UUID id;
    @Column("tenant_id") private UUID tenantId;
    @Column("agency_id") private UUID agencyId;
    @Column("name") private String name;
    @Column("description") private String description;
    @Column("status") private String status;
    @Column("currency") private String currency;
    @Column("base_price") private BigDecimal basePrice;
    @Column("price_per_km") private BigDecimal pricePerKm;
    @Column("price_per_kg") private BigDecimal pricePerKg;
    @Column("min_price") private BigDecimal minPrice;
    @Column("core_policy_id") private UUID corePolicyId;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
    @Version @Column("version") private Long version;
}
