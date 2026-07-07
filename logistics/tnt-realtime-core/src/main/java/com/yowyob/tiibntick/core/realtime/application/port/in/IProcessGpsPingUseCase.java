package com.yowyob.tiibntick.core.realtime.application.port.in;

import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import reactor.core.publisher.Mono;

/**
 * Use case for ingesting and processing a GPS ping from a deliverer's device.
 *
 * @author MANFOUO Braun
 */
public interface IProcessGpsPingUseCase {

    /**
     * Processes a GPS ping: validates, filters outliers, updates presence,
     * triggers Kalman ETA recomputation, checks geofences, and broadcasts results.
     *
     * @param entry the GPS stream entry to process
     * @return Mono completing when all downstream operations are done
     */
    Mono<Void> processGpsPing(GPSStreamEntry entry);
}
