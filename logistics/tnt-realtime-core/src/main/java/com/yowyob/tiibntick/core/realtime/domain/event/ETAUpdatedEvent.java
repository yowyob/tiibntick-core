package com.yowyob.tiibntick.core.realtime.domain.event;

import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;

/**
 * Emitted after a new ETA has been computed by the Kalman filter and
 * broadcast via WebSocket. Consumed by analytics and SLA monitoring.
 *
 * @author MANFOUO Braun
 */
public class ETAUpdatedEvent extends RealtimeDomainEvent {

    private static final String TOPIC = "tnt.realtime.eta.updated";

    private final LiveETAUpdate etaUpdate;

    public ETAUpdatedEvent(String tenantId, LiveETAUpdate etaUpdate) {
        super(tenantId);
        this.etaUpdate = etaUpdate;
    }

    public LiveETAUpdate getEtaUpdate() { return etaUpdate; }

    @Override
    public String kafkaTopic() {
        return TOPIC;
    }
}
