package com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Table(schema = "agency_hr", name = "contracts")
public class ContractEntity {

    @Id @Column("id")                  private UUID id;
    @Column("tenant_id")               private UUID tenantId;
    @Column("agency_id")               private UUID agencyId;
    @Column("deliverer_id")            private UUID delivererId;
    @Column("contract_type")           private String contractType;
    @Column("start_date")              private LocalDate startDate;
    @Column("end_date")                private LocalDate endDate;
    @Column("remuneration_model")      private String remunerationModel;
    @Column("base_salary")             private BigDecimal baseSalary;
    @Column("commission_rate")         private BigDecimal commissionRate;
    @Column("status")                  private String status;
    @Column("signed_at")               private Instant signedAt;
    @Column("created_at")              private Instant createdAt;
    @Column("updated_at")              private Instant updatedAt;
    @Version @Column("version")        private Long version;
}
