package com.yowyob.tiibntick.core.incident.domain.enums;

/**
 * Party held responsible by the manager when resolving an incident; it sets the payer of a return [B.6]. Same names
 * as delivery-core's {@code ResponsibleParty} (passed by name through {@code IDeliveryStatusPort.failDelivery}).
 */
public enum IncidentResponsibleParty {
    SENDER, RECIPIENT, COURIER, PLATFORM, RELAY, AGENCY, NONE
}
