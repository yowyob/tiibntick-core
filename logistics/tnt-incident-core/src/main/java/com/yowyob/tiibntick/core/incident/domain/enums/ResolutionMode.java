package com.yowyob.tiibntick.core.incident.domain.enums;
/**
 * Resolution strategy applied when an incident is resolved.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public enum ResolutionMode {
    /** Resolved entirely by the automated engine with no human intervention. */

    FULLY_AUTOMATIC, MANUAL_AGENCY, INTERAGENCY_COOPERATION,
    /** Automated engine attempted first, then completed by an agency manager. */

    HYBRID_AUTO_THEN_MANUAL, MANUAL_ADMIN_OVERRIDE
}
