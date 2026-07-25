package com.yowyob.tiibntick.core.agency.billing.adapter.out.persistence.entity;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_commercial", name = "invoice_records")
public class InvoiceRecordEntity implements Persistable<UUID>, TntPersistableEntity {

    @Transient private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }

    @Id @Column("id") private UUID id;
    @Column("tenant_id") private UUID tenantId;
    @Column("agency_id") private UUID agencyId;
    @Column("mission_id") private UUID missionId;
    @Column("reference") private String reference;
    @Column("amount") private BigDecimal amount;
    @Column("currency") private String currency;
    @Column("status") private String status;
    @Column("core_invoice_id") private UUID coreInvoiceId;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
    @Version @Column("version") private Long version;
}
