package com.yowyob.tiibntick.core.realtime.domain.model;

import java.util.Objects;

/**
 * Value object representing a WebSocket broadcast topic path.
 * Encapsulates the topic naming convention used across all TiiBnTick platforms.
 *
 * <p>Topic paths follow the STOMP convention: {@code /topic/{category}/{identifier}}</p>
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code /topic/delivery/MISSION-123} — ETA updates for a specific mission</li>
 *   <li>{@code /topic/tracking/TNT-DEL-000042} — package tracking events</li>
 *   <li>{@code /topic/presence/tenant-xyz} — presence board for an agency</li>
 *   <li>{@code /topic/geofence/tenant-xyz} — geofence triggers for a tenant</li>
 *   <li>{@code /topic/reroute/MISSION-123} — rerouting alerts for a mission</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public record BroadcastTopic(String path) {

    public BroadcastTopic {
        Objects.requireNonNull(path, "Topic path must not be null");
        if (!path.startsWith("/topic/")) {
            throw new IllegalArgumentException("Topic path must start with /topic/");
        }
    }

    // ─── Factory methods ───────────────────────────────────────────────────────

    /**
     * Topic for live ETA updates during a delivery mission.
     * Subscribers: customer, agency dispatcher, relay operator.
     */
    public static BroadcastTopic forDelivery(String missionId) {
        Objects.requireNonNull(missionId, "missionId must not be null");
        return new BroadcastTopic("/topic/delivery/" + missionId);
    }

    /**
     * Topic for package tracking events (status changes, location updates).
     * Subscribers: any authenticated user with a tracking code.
     */
    public static BroadcastTopic forTracking(String trackingCode) {
        Objects.requireNonNull(trackingCode, "trackingCode must not be null");
        return new BroadcastTopic("/topic/tracking/" + trackingCode);
    }

    /**
     * Topic for tenant-wide presence board updates.
     * Subscribers: agency dispatchers monitoring their deliverer fleet.
     */
    public static BroadcastTopic forPresence(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return new BroadcastTopic("/topic/presence/" + tenantId);
    }

    /**
     * Topic for geofence trigger events within a tenant.
     * Subscribers: agency dispatchers, relay operators.
     */
    public static BroadcastTopic forGeofence(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return new BroadcastTopic("/topic/geofence/" + tenantId);
    }

    /**
     * Topic for rerouting alerts for a specific mission.
     * Subscribers: the assigned deliverer.
     */
    public static BroadcastTopic forReroute(String missionId) {
        Objects.requireNonNull(missionId, "missionId must not be null");
        return new BroadcastTopic("/topic/reroute/" + missionId);
    }

    /**
     * Topic for personal notifications for a specific user.
     * Subscribers: the user themselves.
     */
    public static BroadcastTopic forUser(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return new BroadcastTopic("/topic/user/" + userId);
    }

    /**
     * Topic for agency-wide operational alerts.
     * Subscribers: all agency agents for a given agency.
     */
    public static BroadcastTopic forAgency(String agencyId) {
        Objects.requireNonNull(agencyId, "agencyId must not be null");
        return new BroadcastTopic("/topic/agency/" + agencyId);
    }

    /**
     * Converts the topic path to a Redis pub-sub channel name.
     * Replaces '/' with ':' to comply with Redis key naming conventions.
     *
     * @return Redis channel name
     */
    public String toRedisChannel() {
        return "tnt:rt" + path.replace("/", ":");
    }

    @Override
    public String toString() {
        return path;
    }
    // ── : FreelancerOrg fleet tracking topics ─────────────────────────────

    /**
     * Topic for real-time tracking of all sub-deliverers within a FreelancerOrg fleet.
     * Subscribers: FreelancerOrg OWNER monitoring their sub-deliverers' positions.
     *
     * <p>Receives GPS ping events from all sub-deliverers whose
     * {@code GpsPingMessage.freelancerOrgId} matches this org.
     *
     * <p>Topic format: {@code /topic/fleet/{freelancerOrgId}}
     *
     * @param freelancerOrgId the FreelancerOrg UUID (from tnt-organization-core)
     */
    public static BroadcastTopic forFreelancerOrgFleet(String freelancerOrgId) {
        Objects.requireNonNull(freelancerOrgId, "freelancerOrgId must not be null");
        return new BroadcastTopic("/topic/fleet/" + freelancerOrgId);
    }

    /**
     * Topic for tracking a specific sub-deliverer within a FreelancerOrg.
     * Subscribers: FreelancerOrg OWNER, client waiting for their specific delivery.
     *
     * <p>Topic format: {@code /topic/fleet/{freelancerOrgId}/sub/{subDelivererId}}
     *
     * @param freelancerOrgId  the FreelancerOrg UUID
     * @param subDelivererId   the sub-deliverer actor UUID
     */
    public static BroadcastTopic forSubDeliverer(String freelancerOrgId, String subDelivererId) {
        Objects.requireNonNull(freelancerOrgId, "freelancerOrgId must not be null");
        Objects.requireNonNull(subDelivererId, "subDelivererId must not be null");
        return new BroadcastTopic("/topic/fleet/" + freelancerOrgId + "/sub/" + subDelivererId);
    }

}