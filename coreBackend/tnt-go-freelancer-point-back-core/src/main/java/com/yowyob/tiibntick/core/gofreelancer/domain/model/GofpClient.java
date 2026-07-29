package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.client.ClientStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user.LoyaltyStatus;
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
 * Local enrichment of a Client within the Go-Freelancer context.
 *
 * <p><strong>Composition design:</strong> rather than embedding all Person/User
 * fields here, {@code GofpClient} composes with {@link GofpUser} via
 * {@code coreUserId}. The full identity (name, email, phone, etc.) is
 * available through the associated {@code GofpUser} record.
 *
 * <p>The {@code coreClientId} is the FK to the authoritative {@code clients}
 * table in the ATANGA backend.
 *
 * <p>Address: a client has a single <em>delivery address</em> (default
 * drop-off location). Additional per-announcement addresses are stored on
 * the announcement / delivery need itself.
 *
 * @author François-Charles ATANGA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofp_clients")
public class GofpClient implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    /** FK → clients.id in ATANGA backend. */
    @NotNull
    @Column("core_client_id")
    private UUID coreClientId;

    /**
     * FK → gofp_users.core_user_id.
     * Composition link: all identity data (name, email, phone…) lives in GofpUser.
     */
    @NotNull
    @Column("core_user_id")
    private UUID coreUserId;

    // ── Client-specific attributes ────────────────────────────────────────

    @NotNull
    @Column("status")
    @Builder.Default
    private ClientStatus status = ClientStatus.ACTIVE;

    @NotNull
    @Column("loyalty_status")
    @Builder.Default
    private LoyaltyStatus loyaltyStatus = LoyaltyStatus.BRONZE;

    /** Total number of completed orders — used for loyalty tier calculation. */
    @Column("total_orders")
    @Builder.Default
    private Integer totalOrders = 0;

    /** Average rating given by delivery persons. */
    @Column("rating")
    private Double rating;

    // ── Address ───────────────────────────────────────────────────────────

    /**
     * Default delivery address (FK → addresses.id).
     * Used as a suggestion when creating a new delivery need.
     */
    @Column("default_delivery_address_id")
    private UUID defaultDeliveryAddressId;

    // ── Transient composition (loaded by service, not R2DBC) ─────────────

    /** Full user identity — populated by service layer, not persisted here. */
    @Transient
    private GofpUser user;

    // ── Timestamps ────────────────────────────────────────────────────────

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
