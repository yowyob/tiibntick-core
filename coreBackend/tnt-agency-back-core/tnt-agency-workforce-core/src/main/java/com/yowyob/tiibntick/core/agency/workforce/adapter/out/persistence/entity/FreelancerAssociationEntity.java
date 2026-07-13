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
@Table(schema = "agency_hr", name = "freelancer_associations")
public class FreelancerAssociationEntity {

    @Id @Column("id")                  private UUID id;
    @Column("tenant_id")               private UUID tenantId;
    @Column("agency_id")               private UUID agencyId;
    @Column("freelancer_actor_id")     private UUID freelancerActorId;
    @Column("commission_rate")         private BigDecimal commissionRate;
    @Column("start_date")              private LocalDate startDate;
    @Column("end_date")                private LocalDate endDate;
    @Column("status")                  private String status;
    @Column("associated_at")           private Instant associatedAt;
    @Column("created_at")              private Instant createdAt;
    @Column("updated_at")              private Instant updatedAt;
    @Version @Column("version")        private Long version;
}
