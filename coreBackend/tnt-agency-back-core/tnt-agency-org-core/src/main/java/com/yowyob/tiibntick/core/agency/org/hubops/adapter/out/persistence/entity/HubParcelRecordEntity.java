package com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_org", name = "hub_parcel_records")
public class HubParcelRecordEntity {

    @Id @Column("id") private UUID id;
    @Column("tenant_id") private UUID tenantId;
    @Column("hub_id") private UUID hubId;
    @Column("package_id") private UUID packageId;
    @Column("mission_id") private UUID missionId;
    @Column("tracking_code") private String trackingCode;
    @Column("deposited_at") private Instant depositedAt;
    @Column("withdrawal_deadline") private Instant withdrawalDeadline;
    @Column("status") private String status;
    @Column("identity_verified") private Boolean identityVerified;
    @Column("withdrawn_by") private String withdrawnBy;
    @Column("core_hub_package_entry_id") private UUID coreHubPackageEntryId;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
    @Version @Column("version") private Long version;
}
