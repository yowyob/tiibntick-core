package com.yowyob.tiibntick.core.resource.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Entity representing a piece of operational equipment (QR scanner, tablet, payment terminal, etc.)
 * managed within TiiBnTick's resource pool.
 *
 * @author MANFOUO Braun.
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public final class Equipment {

    private final UUID id;
    private final UUID tenantId;
    private final UUID organizationId;
    private final UUID branchId;
    private final EquipmentType type;
    private final String serialNumber;
    private final String description;
    private final EquipmentStatus status;
    private final UUID assignedUserId;
    private final LocalDate purchasedAt;
    private final LocalDate warrantyExpiresAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private static final Set<EquipmentStatus> ASSIGNABLE_FROM = Set.of(EquipmentStatus.AVAILABLE);
    private static final Set<EquipmentStatus> RETIRE_FROM = Set.of(
            EquipmentStatus.AVAILABLE, EquipmentStatus.MAINTENANCE);

    private Equipment(UUID id, UUID tenantId, UUID organizationId, UUID branchId,
            EquipmentType type, String serialNumber, String description,
            EquipmentStatus status, UUID assignedUserId, LocalDate purchasedAt,
            LocalDate warrantyExpiresAt, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId is required");
        this.branchId = Objects.requireNonNull(branchId, "branchId is required");
        this.type = Objects.requireNonNull(type, "type is required");
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("serialNumber must not be blank");
        }
        this.serialNumber = serialNumber.toUpperCase().trim();
        this.description = description != null ? description.trim() : null;
        this.status = Objects.requireNonNull(status, "status is required");
        this.assignedUserId = assignedUserId;
        this.purchasedAt = purchasedAt;
        this.warrantyExpiresAt = warrantyExpiresAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static Equipment register(UUID tenantId, UUID organizationId, UUID branchId,
            EquipmentType type, String serialNumber, String description,
            LocalDate purchasedAt, LocalDate warrantyExpiresAt) {
        Instant now = Instant.now();
        return new Equipment(UUID.randomUUID(), tenantId, organizationId, branchId,
                type, serialNumber, description, EquipmentStatus.AVAILABLE, null,
                purchasedAt, warrantyExpiresAt, now, now);
    }

    public static Equipment rehydrate(UUID id, UUID tenantId, UUID organizationId, UUID branchId,
            EquipmentType type, String serialNumber, String description, EquipmentStatus status,
            UUID assignedUserId, LocalDate purchasedAt, LocalDate warrantyExpiresAt,
            Instant createdAt, Instant updatedAt) {
        return new Equipment(id, tenantId, organizationId, branchId, type, serialNumber,
                description, status, assignedUserId, purchasedAt, warrantyExpiresAt, createdAt, updatedAt);
    }

    public Equipment assign(UUID userId) {
        Objects.requireNonNull(userId, "userId is required");
        if (!ASSIGNABLE_FROM.contains(status)) {
            throw new IllegalStateException("Cannot assign equipment in status: " + status);
        }
        return new Equipment(id, tenantId, organizationId, branchId, type, serialNumber,
                description, EquipmentStatus.IN_USE, userId, purchasedAt, warrantyExpiresAt, createdAt, Instant.now());
    }

    public Equipment unassign() {
        if (status != EquipmentStatus.IN_USE) {
            throw new IllegalStateException("Cannot unassign equipment in status: " + status);
        }
        return new Equipment(id, tenantId, organizationId, branchId, type, serialNumber,
                description, EquipmentStatus.AVAILABLE, null, purchasedAt, warrantyExpiresAt, createdAt, Instant.now());
    }

    public Equipment sendToMaintenance() {
        return new Equipment(id, tenantId, organizationId, branchId, type, serialNumber,
                description, EquipmentStatus.MAINTENANCE, null, purchasedAt, warrantyExpiresAt, createdAt, Instant.now());
    }

    public Equipment completeMaintenance() {
        if (status != EquipmentStatus.MAINTENANCE) {
            throw new IllegalStateException("Cannot complete maintenance for equipment in status: " + status);
        }
        return new Equipment(id, tenantId, organizationId, branchId, type, serialNumber,
                description, EquipmentStatus.AVAILABLE, null, purchasedAt, warrantyExpiresAt, createdAt, Instant.now());
    }

    public Equipment retire() {
        if (!RETIRE_FROM.contains(status)) {
            throw new IllegalStateException("Cannot retire equipment in status: " + status);
        }
        return new Equipment(id, tenantId, organizationId, branchId, type, serialNumber,
                description, EquipmentStatus.RETIRED, null, purchasedAt, warrantyExpiresAt, createdAt, Instant.now());
    }

    public boolean isAvailable() { return status == EquipmentStatus.AVAILABLE; }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID organizationId() { return organizationId; }
    public UUID branchId() { return branchId; }
    public EquipmentType type() { return type; }
    public String serialNumber() { return serialNumber; }
    public String description() { return description; }
    public EquipmentStatus status() { return status; }
    public UUID assignedUserId() { return assignedUserId; }
    public LocalDate purchasedAt() { return purchasedAt; }
    public LocalDate warrantyExpiresAt() { return warrantyExpiresAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
