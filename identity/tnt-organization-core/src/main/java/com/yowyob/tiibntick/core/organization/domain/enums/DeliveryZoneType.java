package com.yowyob.tiibntick.core.organization.domain.enums;

/**
 * Classification of a delivery zone by urbanization type.
 *
 * <p>Informs pricing, vehicle selection, and routing strategies.
 *
 * @author MANFOUO Braun
 */
public enum DeliveryZoneType {

    /** Dense urban area with formal roads and clear addressing. */
    URBAN,

    /** Suburban or peri-urban — mixed formal/informal addressing. */
    PERI_URBAN,

    /** Rural area — informal addressing, limited road network. */
    RURAL,

    /** Diplomatic zone — specific access protocols required. */
    DIPLOMATIC,

    /** Port or customs zone — regulatory checks required. */
    PORT_ZONE
}
