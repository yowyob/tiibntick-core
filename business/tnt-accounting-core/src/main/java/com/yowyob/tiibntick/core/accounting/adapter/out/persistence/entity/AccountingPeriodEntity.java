package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity for accounting periods.
 * Author: MANFOUO Braun
 */
@Table(schema = "accounting", name = "accounting_periods")
public record AccountingPeriodEntity(
        @Id @Column("id") UUID id,
        @Column("tenant_id") UUID tenantId,
        @Column("year") int year,
        @Column("month") int month,
        @Column("status") String status,
        @Column("opened_at") Instant openedAt,
        @Column("closed_at") Instant closedAt,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt
) {}
