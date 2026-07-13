package com.yowyob.tiibntick.core.gofp.domain.model.enums;

/**
 * FSM du cycle de vie d'une livraison Market.
 *
 * Transitions valides :
 *   CREATED → ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED
 *                                             ↘ AT_RELAY → IN_TRANSIT (reprise après dépôt relais)
 *   Tout état → FAILED | CANCELLED
 */
public enum DeliveryStatus {

    CREATED,
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    AT_RELAY,
    DELIVERED,
    FAILED,
    CANCELLED;

    public static DeliveryStatus fromValue(String value) {
        for (DeliveryStatus s : values()) {
            if (s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown DeliveryStatus: " + value);
    }

    /** Retourne true si la livraison est dans un état terminal. */
    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED || this == CANCELLED;
    }
}
