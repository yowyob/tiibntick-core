package com.yowyob.tiibntick.core.realtime.domain.model;

import com.yowyob.tiibntick.core.realtime.domain.model.enums.PresenceStatus;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain entity representing the real-time presence of an actor (deliverer,
 * relay operator, agency agent) within a specific tenant context.
 *
 * <p>Presence records are stored in Redis with a configurable TTL.
 * If a deliverer sends no heartbeat within the TTL, their record expires
 * and they are considered offline.</p>
 *
 * @author MANFOUO Braun
 */
@Getter
public class PresenceRecord {

    private final String userId;
    private final String tenantId;
    private final DeviceInfo deviceInfo;
    private final LocalDateTime firstSeenAt;

    private PresenceStatus status;
    private GeoCoordinates currentCoordinates;
    private String activeMissionId;
    private LocalDateTime lastSeenAt;

    public PresenceRecord(String userId, String tenantId, DeviceInfo deviceInfo) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.deviceInfo = deviceInfo;
        this.firstSeenAt = LocalDateTime.now();
        this.lastSeenAt = firstSeenAt;
        this.status = PresenceStatus.ONLINE_AVAILABLE;
    }

    /**
     * Updates the actor's known GPS coordinates.
     * Also refreshes the lastSeenAt timestamp.
     *
     * @param coords the new coordinates from a GPS ping
     */
    public void updateLocation(GeoCoordinates coords) {
        this.currentCoordinates = Objects.requireNonNull(coords, "Coordinates must not be null");
        this.lastSeenAt = LocalDateTime.now();
    }

    /**
     * Changes the presence status of this actor.
     *
     * @param status the new presence status
     */
    public void setStatus(PresenceStatus status) {
        this.status = Objects.requireNonNull(status, "Status must not be null");
        this.lastSeenAt = LocalDateTime.now();
    }

    /**
     * Associates the actor with an active mission.
     * Automatically transitions status to ONLINE_ON_MISSION.
     *
     * @param missionId the mission identifier
     */
    public void assignMission(String missionId) {
        this.activeMissionId = missionId;
        this.status = PresenceStatus.ONLINE_ON_MISSION;
        this.lastSeenAt = LocalDateTime.now();
    }

    /**
     * Clears the active mission association.
     * Transitions status back to ONLINE_AVAILABLE.
     */
    public void clearMission() {
        this.activeMissionId = null;
        this.status = PresenceStatus.ONLINE_AVAILABLE;
        this.lastSeenAt = LocalDateTime.now();
    }

    /**
     * Checks whether this presence record is stale (actor has not sent a
     * heartbeat within the allowed silence duration).
     *
     * @param staleDuration maximum allowed duration without a heartbeat
     * @return true if the record should be considered stale
     */
    public boolean isStale(Duration staleDuration) {
        Objects.requireNonNull(staleDuration, "staleDuration must not be null");
        return LocalDateTime.now().isAfter(lastSeenAt.plus(staleDuration));
    }

    /**
     * Convenience method to check if the actor is currently online.
     *
     * @return true if the actor is in any online state
     */
    public boolean isOnline() {
        return status != PresenceStatus.OFFLINE && status != PresenceStatus.SUSPENDED;
    }

    /**
     * Marks this actor as offline (e.g. session disconnect).
     */
    public void markOffline() {
        this.status = PresenceStatus.OFFLINE;
        this.lastSeenAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PresenceRecord p)) return false;
        return Objects.equals(userId, p.userId) && Objects.equals(tenantId, p.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, tenantId);
    }

    @Override
    public String toString() {
        return "PresenceRecord{userId=" + userId + ", tenant=" + tenantId + ", status=" + status + "}";
    }

    public GeoCoordinates getCurrentCoordinates() {
        return currentCoordinates;
    }

    public String getActiveMissionId() {
        return activeMissionId;
    }

    public String getUserId() {
        return userId;
    }
}
