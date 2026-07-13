package com.yowyob.tiibntick.core.linkback.domain.model;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.domain.exception.NetworkNodeDomainException;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Link-specific extension over an existing tnt-actor-core/tnt-organization-core
 * actor or organization: trust score, gamification, Proof-of-Location, DID
 * identifier, beacon broadcasting, and community-governance participation.
 *
 * <p>{@code refId} is a pure integration key (no join) — mirrors the convention
 * already used by tnt-delivery-core's {@code Delivery.assignedFreelancerOrgId}.
 * Profile data (name, vehicle, contact, capacity, rating, badges owned by
 * tnt-actor-core...) is never duplicated here; it stays owned by
 * tnt-actor-core / tnt-organization-core and is composed at query time (see
 * {@code GetNetworkNodeProfileUseCase}), not stored redundantly.
 *
 * <p><b>Honesty notes on what's real vs. simplified here</b> (no field is
 * fabricated data — every one is either genuine input or a deterministic
 * derivation of genuine input):
 * <ul>
 *   <li>{@code pol*} (Proof-of-Location) is a presence-count heuristic
 *       (peer count reported by the client at location-update time), not a
 *       cryptographic proof — there is no PoL verification protocol in the
 *       platform today.</li>
 *   <li>{@code did*} is a real, stable identifier string
 *       ({@code did:tnt:link:<nodeId>}) with no blockchain-backed
 *       verifiable-credential issuance behind it — building that is a
 *       separate subsystem, out of scope here.</li>
 *   <li>{@code badges} are Link's own catalog, computed from this node's own
 *       stored metrics (trust, community score, node age, zone transitions)
 *       — distinct from tnt-actor-core's generic {@code Badge} concept,
 *       which is about a different set of platform-wide achievements.</li>
 *   <li>{@code zoneTransitionCount} counts DAO-zone changes as a proxy for
 *       "explorer" behaviour — it is not a full distinct-zones-visited
 *       history (that would need its own table); a real, monotonic, honest
 *       metric, just a simplified one.</li>
 * </ul>
 *
 * @author Dilane PAFE
 */
@Getter
@Builder
public class NetworkNode {

    private static final double TRUSTED_REPORTER_THRESHOLD = 5.0;
    private static final double COMMUNITY_PILLAR_THRESHOLD = 10.0;
    private static final int SUPER_DELIVERER_POINTS_THRESHOLD = 50;
    private static final long VETERAN_AGE_DAYS = 180;
    private static final int EXPLORER_ZONE_TRANSITIONS_THRESHOLD = 10;

    private final UUID id;
    private final UUID tenantId;
    private final NodeRefType refType;
    private final UUID refId;
    private NodeStatus status;
    private double trustScore;
    private int gamificationLevel;
    private double communityScore;
    private GeoPoint lastKnownLocation;
    private Double heading;

    private String description;
    private String declaredZoneName;
    private String declaredCity;
    private Integer declaredCapacityParcels;

    @Builder.Default
    private Set<String> badges = new HashSet<>();

    private UUID lastZoneId;
    private int zoneTransitionCount;

    private boolean polVerified;
    private int polPeerCount;
    private Instant polVerifiedAt;

    private final String didIdentifier;
    private final String didIssuer;
    private final Instant didVerifiedAt;

    private boolean beaconActive;
    private String beaconMessage;
    private Instant beaconExpiresAt;
    private Double beaconRadiusKm;

    private final Instant createdAt;
    private Instant updatedAt;

    public static NetworkNode register(UUID tenantId, NodeRefType refType, UUID refId,
                                        String description, String declaredZoneName,
                                        String declaredCity, Integer declaredCapacityParcels) {
        if (refType == null || refId == null) {
            throw new NetworkNodeDomainException("A network node requires a refType and refId");
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return NetworkNode.builder()
                .id(id)
                .tenantId(tenantId)
                .refType(refType)
                .refId(refId)
                .status(NodeStatus.OFFLINE)
                .trustScore(0.0)
                .gamificationLevel(1)
                .communityScore(0.0)
                .description(description)
                .declaredZoneName(declaredZoneName)
                .declaredCity(declaredCity)
                .declaredCapacityParcels(declaredCapacityParcels)
                .badges(new HashSet<>())
                .zoneTransitionCount(0)
                .polVerified(false)
                .polPeerCount(0)
                .didIdentifier("did:tnt:link:" + id)
                .didIssuer("TiiBnTick Link")
                .didVerifiedAt(now)
                .beaconActive(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void updateStatus(NodeStatus newStatus) {
        if (newStatus == null) {
            throw new NetworkNodeDomainException("Status cannot be null");
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public void updateLocation(GeoPoint location, Double heading) {
        if (location == null) {
            throw new NetworkNodeDomainException("Location cannot be null");
        }
        this.lastKnownLocation = location;
        this.heading = heading;
        this.updatedAt = Instant.now();
    }

    /**
     * Records a presence-count Proof-of-Location signal supplied by the client
     * at location-update time. Not a cryptographic proof — see class javadoc.
     */
    public void recordProofOfLocation(int peerCount) {
        this.polPeerCount = Math.max(0, peerCount);
        this.polVerified = this.polPeerCount >= 1;
        this.polVerifiedAt = Instant.now();
    }

    /** Increments the zone-transition counter when the containing DAO zone changes. */
    public void recordZoneTransition(UUID zoneId) {
        if (zoneId != null && !zoneId.equals(this.lastZoneId)) {
            this.zoneTransitionCount++;
            this.lastZoneId = zoneId;
            this.updatedAt = Instant.now();
        }
    }

    public void activateBeacon(String message, double radiusKm, Duration duration) {
        if (radiusKm <= 0) {
            throw new NetworkNodeDomainException("Beacon radius must be positive");
        }
        this.beaconActive = true;
        this.beaconMessage = message;
        this.beaconRadiusKm = radiusKm;
        this.beaconExpiresAt = Instant.now().plus(duration);
        this.updatedAt = Instant.now();
    }

    public void deactivateBeacon() {
        this.beaconActive = false;
        this.beaconMessage = null;
        this.beaconExpiresAt = null;
        this.beaconRadiusKm = null;
        this.updatedAt = Instant.now();
    }

    /** Whether the beacon is active AND not expired — expiry is checked here, not just at activation. */
    public boolean isBeaconCurrentlyActive() {
        return beaconActive && beaconExpiresAt != null && beaconExpiresAt.isAfter(Instant.now());
    }

    /**
     * Adjusts trust score in response to a community-validated action
     * (e.g. a reported alert getting confirmed by others). Never goes below zero.
     */
    public void earnTrust(double delta) {
        this.trustScore = Math.max(0, this.trustScore + delta);
        this.updatedAt = Instant.now();
    }

    /** Adjusts community-governance participation score (e.g. an authored proposal getting approved). */
    public void earnCommunityScore(double delta) {
        this.communityScore = Math.max(0, this.communityScore + delta);
        this.updatedAt = Instant.now();
    }

    /**
     * Adjusts the gamification score. Currently a raw accumulating point total
     * (never below 1) rather than a discretized tier — deriving badge tiers
     * from thresholds is done at query time (see BFF's
     * {@code NetworkNodeAggregationService.toGamificationLevel}), not stored here.
     */
    public void awardPoints(int delta) {
        this.gamificationLevel = Math.max(1, this.gamificationLevel + delta);
        this.updatedAt = Instant.now();
    }

    /**
     * Recomputes {@link #badges} from this node's current stored metrics.
     * Call after any state change that could affect badge eligibility
     * (trust/points/community-score updates, zone transitions).
     */
    public void refreshBadges() {
        Set<String> earned = new HashSet<>();
        if (trustScore >= TRUSTED_REPORTER_THRESHOLD) {
            earned.add("TRUSTED_REPORTER");
        }
        if (communityScore >= COMMUNITY_PILLAR_THRESHOLD) {
            earned.add("COMMUNITY_PILLAR");
        }
        if (gamificationLevel >= SUPER_DELIVERER_POINTS_THRESHOLD) {
            earned.add("SUPER_DELIVERER");
        }
        if (Duration.between(createdAt, Instant.now()).toDays() >= VETERAN_AGE_DAYS) {
            earned.add("VETERAN");
        }
        if (zoneTransitionCount >= EXPLORER_ZONE_TRANSITIONS_THRESHOLD) {
            earned.add("EXPLORER");
        }
        this.badges = earned;
    }

    public boolean isSuperDeliverer() {
        return badges.contains("SUPER_DELIVERER");
    }

    public boolean isVeteran() {
        return badges.contains("VETERAN");
    }

    public boolean isExplorer() {
        return badges.contains("EXPLORER");
    }
}
