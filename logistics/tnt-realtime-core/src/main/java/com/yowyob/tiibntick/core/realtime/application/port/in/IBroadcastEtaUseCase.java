package com.yowyob.tiibntick.core.realtime.application.port.in;

import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
import com.yowyob.tiibntick.core.realtime.domain.model.ReroutingAlert;
import reactor.core.publisher.Mono;

/**
 * Use case for broadcasting ETA updates and rerouting alerts.
 * Called by GPS ping pipeline and Kafka consumers.
 *
 * @author MANFOUO Braun
 */
public interface IBroadcastEtaUseCase {

    /**
     * Broadcasts a live ETA update to all subscribers of the relevant topics.
     *
     * @param etaUpdate the ETA update to broadcast
     * @return Mono completing when broadcast is dispatched
     */
    Mono<Void> broadcastEtaUpdate(LiveETAUpdate etaUpdate);

    /**
     * Broadcasts a rerouting alert to the deliverer and agency dispatcher.
     *
     * @param alert the rerouting alert
     * @return Mono completing when broadcast is dispatched
     */
    Mono<Void> broadcastReroutingAlert(ReroutingAlert alert);
}
