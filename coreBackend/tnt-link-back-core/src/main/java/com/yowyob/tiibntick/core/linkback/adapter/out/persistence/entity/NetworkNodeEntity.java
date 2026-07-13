package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity mapped to tnt_link.network_nodes.
 *
 * @author Dilane PAFE
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_link", value = "network_nodes")
public class NetworkNodeEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("ref_type")
    private String refType;

    @Column("ref_id")
    private UUID refId;

    @Column("status")
    private String status;

    @Column("trust_score")
    private double trustScore;

    @Column("gamification_level")
    private int gamificationLevel;

    @Column("community_score")
    private double communityScore;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("heading")
    private Double heading;

    @Column("description")
    private String description;

    @Column("declared_zone_name")
    private String declaredZoneName;

    @Column("declared_city")
    private String declaredCity;

    @Column("declared_capacity_parcels")
    private Integer declaredCapacityParcels;

    /** Comma-joined badge codes — no other module in this codebase persists set-valued columns as arrays. */
    @Column("badges")
    private String badges;

    @Column("last_zone_id")
    private UUID lastZoneId;

    @Column("zone_transition_count")
    private int zoneTransitionCount;

    @Column("pol_verified")
    private boolean polVerified;

    @Column("pol_peer_count")
    private int polPeerCount;

    @Column("pol_verified_at")
    private Instant polVerifiedAt;

    @Column("did_identifier")
    private String didIdentifier;

    @Column("did_issuer")
    private String didIssuer;

    @Column("did_verified_at")
    private Instant didVerifiedAt;

    @Column("beacon_active")
    private boolean beaconActive;

    @Column("beacon_message")
    private String beaconMessage;

    @Column("beacon_expires_at")
    private Instant beaconExpiresAt;

    @Column("beacon_radius_km")
    private Double beaconRadiusKm;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    private long version;
}
