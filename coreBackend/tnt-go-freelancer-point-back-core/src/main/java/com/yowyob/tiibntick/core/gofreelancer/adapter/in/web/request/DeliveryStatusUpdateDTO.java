package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * DTO for updating delivery status (state transitions).
 *
 * <p>When {@code status == DELIVERED} and {@code relayPointId} is provided,
 * a {@link com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit}
 * is automatically created to track the parcel's custody at the relay point.</p>
 *
 * @author François-Charles ATANGA
 */
@Data
public class DeliveryStatusUpdateDTO {

    @NotNull
    private DeliveryStatus status;

    /**
     * 6-digit confirmation code required for status transitions that need physical proof:
     * <ul>
     *   <li>{@code PICKED_UP} — code given by the shipper to confirm parcel handoff</li>
     *   <li>{@code DELIVERED} (direct, no relay point) — code given by the recipient to confirm receipt</li>
     * </ul>
     * Not required for {@code IN_TRANSIT}, {@code FAILED}, {@code CANCELLED}, or relay-point delivery.
     */
    private String confirmationCode;

    // ── Point-relais deposit fields (only required when status == DELIVERED via relay) ──

    /**
     * UUID of the relay point where the parcel is deposited.
     * When non-null and status == DELIVERED, triggers automatic RelayDeposit creation.
     */
    private UUID relayPointId;

    /**
     * UUID of the client who will pick up the parcel at the relay point.
     * Required when relayPointId is provided.
     */
    private UUID clientId;

    /**
     * Storage fee charged by the relay point per day (or per period).
     * Defaults to 0.0 if not provided.
     */
    private Double storageFee = 0.0;
}
