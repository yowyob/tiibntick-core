package com.yowyob.tiibntick.core.incident.domain.enums;

/**
 * High-level functional category of an incident, used for routing,
 * reporting dashboards and SLA policy selection.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */
public enum IncidentCategory {

    /** Incident caused by the driver or deliverer (withdrawal, accident, fraud, etc.). */
    DRIVER_DELIVERER,

    /** Incident caused by a vehicle failure or incompatibility (breakdown, flat tyre, etc.). */
    VEHICLE,

    /** Incident caused by the client or recipient (absent, refused delivery, wrong address, etc.). */
    CLIENT_RECIPIENT,

    /** Incident involving parcel damage, loss, contamination or illegal content. */
    PARCEL_CARGO,

    /** Incident caused by a platform system or infrastructure failure (server, Kafka, GPS outage). */
    SYSTEM_INFRASTRUCTURE,

    /** Incident caused by geographic or environmental factors (flood, armed conflict, curfew). */
    GEOGRAPHIC,

    /** Incident requiring cooperation between two or more agencies. */
    INTER_AGENCY,

    /** Incident related to payment or financial fraud. */
    FINANCIAL,

    /** Incident related to regulatory non-compliance (documents, police control, customs). */
    REGULATORY,

    /** Incident involving human casualties, injuries or life-threatening situations. */
    HUMAN_CRITICAL,

    /** Incident caused by an automation or AI decision error (bad matching, false positive). */
    AUTOMATION_AI,

    /** Incident at a relay point (closed, full, internal theft, scan error). */
    RELAY_POINT,

    /** Incident consisting of a delivery SLA being breached due to external causes. */
    SLA_TIME
}
