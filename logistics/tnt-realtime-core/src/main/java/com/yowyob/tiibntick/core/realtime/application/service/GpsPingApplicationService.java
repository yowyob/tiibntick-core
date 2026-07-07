package com.yowyob.tiibntick.core.realtime.application.service;

import com.yowyob.tiibntick.core.realtime.adapter.out.websocket.RedisBackedWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.application.port.in.IProcessGpsPingUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.realtime.domain.service.GpsPingProcessor;
import com.yowyob.tiibntick.core.realtime.domain.service.PresenceDomainService;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Application service implementing the GPS ping processing use case.
 * Orchestrates the domain service pipeline and records metrics.
 *
 * @author MANFOUO Braun
 */
@Service
public class GpsPingApplicationService implements IProcessGpsPingUseCase {

    private static final Logger log = LoggerFactory.getLogger(GpsPingApplicationService.class);

    private final GpsPingProcessor gpsPingProcessor;
    private final IWebSocketBroadcaster broadcaster;
    private final PresenceDomainService presenceDomainService;
    private final Counter pingsReceivedCounter;
    private final Counter pingsProcessedCounter;
    private final Timer pingProcessingTimer;

    public GpsPingApplicationService(GpsPingProcessor gpsPingProcessor,
                                     RedisBackedWebSocketBroadcaster broadcaster,
                                     PresenceDomainService presenceDomainService,
                                     MeterRegistry meterRegistry) {
        this.gpsPingProcessor = gpsPingProcessor;
        this.broadcaster = broadcaster;
        this.presenceDomainService = presenceDomainService;

        this.pingsReceivedCounter = Counter.builder("tnt.realtime.gps.pings.received")
                .description("Total GPS pings received")
                .register(meterRegistry);
        this.pingsProcessedCounter = Counter.builder("tnt.realtime.gps.pings.processed")
                .description("Total GPS pings successfully processed")
                .register(meterRegistry);
        this.pingProcessingTimer = Timer.builder("tnt.realtime.gps.pings.processing.duration")
                .description("GPS ping processing duration")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> processGpsPing(GPSStreamEntry entry) {
        pingsReceivedCounter.increment();

        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start();
            Mono<Void> coreProcessing = presenceDomainService
                    .updateCoordinates(entry.delivererId(), entry.tenantId(), entry.coordinates())
                    .then(gpsPingProcessor.process(entry));

            // : If this ping is from a FreelancerOrg sub-deliverer, broadcast to fleet topic
            Mono<Void> fleetBroadcast = entry.hasFreelancerOrg()
                    ? broadcastToFreelancerOrgFleet(entry)
                    : Mono.empty();

            return coreProcessing
                    .then(fleetBroadcast)
                    .doOnSuccess(v -> {
                        sample.stop(pingProcessingTimer);
                        pingsProcessedCounter.increment();
                        log.trace("GPS ping processed for deliverer {} (tenant={} org={})",
                                entry.delivererId(), entry.tenantId(), entry.freelancerOrgId());
                    })
                    .doOnError(ex -> {
                        sample.stop(pingProcessingTimer);
                        log.error("Failed to process GPS ping for deliverer {}: {}",
                                entry.delivererId(), ex.getMessage());
                    });
        });
    }

    /**
     * Broadcasts a sub-deliverer GPS ping to the FreelancerOrg fleet topic.
     * Called when entry.hasFreelancerOrg() == true.
     * Topic: {@code /topic/fleet/{freelancerOrgId}}
     *
     * @param entry the GPS ping with a non-null freelancerOrgId
     * @return Mono completing when the broadcast is enqueued
     */
    private Mono<Void> broadcastToFreelancerOrgFleet(GPSStreamEntry entry) {
        String fleetTopic = com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic
                .forFreelancerOrgFleet(entry.freelancerOrgId()).path();
        log.trace("Broadcasting sub-deliverer {} ping to fleet topic {}", entry.delivererId(), fleetTopic);
        return broadcaster.broadcastToTopic(fleetTopic, entry)
                .onErrorResume(e -> {
                    log.warn("Failed to broadcast to fleet topic {}: {}", fleetTopic, e.getMessage());
                    return Mono.empty();
                });
    }
}
