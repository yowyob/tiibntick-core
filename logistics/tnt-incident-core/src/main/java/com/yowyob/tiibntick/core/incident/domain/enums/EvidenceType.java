package com.yowyob.tiibntick.core.incident.domain.enums;
/**
 * Type of evidence that can be attached to an incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public enum EvidenceType {
    PHOTO, VIDEO, GPS_TRACE, DIGITAL_SIGNATURE,
    OTP_PROOF, DOCUMENT_SCAN, TELEMETRY_DATA,
    AUDIO_RECORDING, WITNESS_STATEMENT
}
