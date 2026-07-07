package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity mapped to the accounting.accounts table.
 *
 * <p> — Added {@code owner_org_id} and {@code owner_org_type} for FreelancerOrg
 * per-org account segregation (accounts 411-FRL-{uuid}, 421-FRL-{uuid}, 706-FRL-{uuid}).
 *
 * @author MANFOUO Braun
 */
@Table(schema = "accounting", name = "accounts")
public record AccountEntity(
        @Id @Column("id") UUID id,
        @Column("tenant_id") UUID tenantId,
        @Column("code") String code,
        @Column("name") String name,
        @Column("type") String type,
        @Column("category") String category,
        @Column("currency") String currency,
        @Column("balance") BigDecimal balance,
        @Column("active") boolean active,
        @Column("parent_account_id") UUID parentAccountId,
        @Column("ohada_class") int ohadaClass,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt,

        // : FreelancerOrg ownership context
        /**
         * UUID of the FreelancerOrg owning this account.
         * Null for standard platform/agency accounts.
         * References tnt-organization-core UUID — no physical FK.
         */
        @Column("owner_org_id") String ownerOrgId,

        /**
         * Type of the owning entity: FREELANCER_ORG or AGENCY.
         * Null for shared platform accounts.
         */
        @Column("owner_org_type") String ownerOrgType
) {}
