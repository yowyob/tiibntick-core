package com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "agency_org", name = "agency_relay_hubs")
public class AgencyRelayHubEntity {

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("agency_id")
    private UUID agencyId;

    @Column("branch_id")
    private UUID branchId;

    private String name;
    private String code;
    private String status;

    @Column("capacity_units")
    private Integer capacityUnits;

    @Column("current_occupancy")
    private Integer currentOccupancy;

    @Column("retention_delay_hours")
    private Integer retentionDelayHours;

    @Column("opening_hours")
    private String openingHours;

    @Column("core_hub_id")
    private UUID coreHubId;

    @Column("addr_street")
    private String addrStreet;

    @Column("addr_landmark")
    private String addrLandmark;

    @Column("addr_quarter")
    private String addrQuarter;

    @Column("addr_city")
    private String addrCity;

    @Column("addr_region")
    private String addrRegion;

    @Column("addr_country")
    private String addrCountry;

    @Column("addr_postal_code")
    private String addrPostalCode;

    private Double latitude;
    private Double longitude;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    private Long version;
}
