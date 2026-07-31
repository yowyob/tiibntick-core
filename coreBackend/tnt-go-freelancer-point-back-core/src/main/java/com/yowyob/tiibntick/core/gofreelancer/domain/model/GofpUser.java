package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Local profile for a base user (the simplest actor in the system).
 *
 * <p>A User is anyone who can create a delivery need without being a
 * registered Client or Freelancer. This entity mirrors and enriches the
 * {@code users} record from the ATANGA backend so the core can operate
 * autonomously without round-trips for basic identity data.
 *
 * <p>Hierarchy: GofpUser → GofpClient  /  GofpUser → GofpFreelancer
 *
 * <p>The {@code coreUserId} is the foreign key to the authoritative
 * {@code users} table in the ATANGA backend and is used as the shared
 * identity key across all modules.
 *
 * @author François-Charles ATANGA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Table("gofp_users")
public class GofpUser implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default @JsonIgnore private boolean isNew = true;

    @Override
    @JsonIgnore
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    /** FK → users.id in ATANGA backend (authoritative identity). */
    @NotNull
    @Column("core_user_id")
    private UUID coreUserId;

    // ── Identity ─────────────────────────────────────────────────────────

    @NotNull
    @Column("first_name")
    private String firstName;

    @NotNull
    @Column("last_name")
    private String lastName;

    @NotNull
    @Column("email")
    private String email;

    @Column("phone")
    private String phone;

    @Column("cni_number")
    private String cniNumber;

    @Column("nui")
    private String nui;

    /** BCrypt-hashed password (replicated for offline/local auth fallback). */
    @Column("password_hash")
    private String passwordHash;

    @Column("profile_photo_url")
    private String profilePhotoUrl;

    // ── Status & lifecycle ────────────────────────────────────────────────

    @NotNull
    @Column("status")
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column("is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column("role")
    private String role;

    // ── Address (single residence address for base users) ─────────────────
    // Freelancers and RelayPoints manage their double address via GofpFreelancer/GofpRelayPoint.

    @Column("address_id")
    private UUID addressId;

    // ── Timestamps ────────────────────────────────────────────────────────

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
