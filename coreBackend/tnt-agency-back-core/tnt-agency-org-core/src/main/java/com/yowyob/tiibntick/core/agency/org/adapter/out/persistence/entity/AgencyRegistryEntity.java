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
@Table(schema = "agency_org", name = "agencies")
public class AgencyRegistryEntity {

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    private String name;

    @Column("agency_code")
    private String agencyCode;

    private String type;
    private String status;

    @Column("registration_number")
    private String registrationNumber;

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

    @Column("addr_lat")
    private Double addrLat;

    @Column("addr_lon")
    private Double addrLon;

    @Column("contact_email")
    private String contactEmail;

    @Column("contact_phone")
    private String contactPhone;

    @Column("logo_url")
    private String logoUrl;

    private String website;

    @Column("kernel_organization_id")
    private UUID kernelOrganizationId;

    @Column("kernel_business_actor_id")
    private UUID kernelBusinessActorId;

    @Column("core_agency_id")
    private UUID coreAgencyId;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    private Long version;
}
