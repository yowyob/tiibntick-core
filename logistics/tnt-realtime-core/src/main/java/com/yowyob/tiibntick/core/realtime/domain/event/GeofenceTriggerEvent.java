package com.yowyob.tiibntick.core.realtime.domain.event;

import com.yowyob.tiibntick.core.realtime.domain.model.GeofenceTrigger;

/**
 * Emitted when a deliverer crosses a geofence zone boundary.
 * Consumed by tnt-delivery-core for automatic mission state transitions
 * (e.g. ENTER RELAY_HUB → start hub-deposit flow).
 *
 * @author MANFOUO Braun
 */
public class GeofenceTriggerEvent extends RealtimeDomainEvent {

    private static final String TOPIC = "tnt.realtime.geofence.triggered";

    private final GeofenceTrigger trigger;

    public GeofenceTriggerEvent(String tenantId, GeofenceTrigger trigger) {
        super(tenantId);
        this.trigger = trigger;
    }

    public GeofenceTrigger getTrigger() { return trigger; }

    @Override
    public String kafkaTopic() {
        return TOPIC;
    }
}
