package com.yowyob.tiibntick.core.realtime.config;

import com.yowyob.tiibntick.core.realtime.application.port.out.IActorLocationUpdater;
import com.yowyob.tiibntick.core.realtime.application.port.out.IGeofenceZoneRepository;
import com.yowyob.tiibntick.core.realtime.application.port.out.IKalmanEtaUpdater;
import com.yowyob.tiibntick.core.realtime.application.port.out.IPresenceRepository;
import com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.config.properties.GeofenceProperties;
import com.yowyob.tiibntick.core.realtime.config.properties.PresenceProperties;
import com.yowyob.tiibntick.core.realtime.config.properties.RealtimeProperties;
import com.yowyob.tiibntick.core.realtime.domain.service.EtaBroadcastDomainService;
import com.yowyob.tiibntick.core.realtime.domain.service.GeofenceMonitorService;
import com.yowyob.tiibntick.core.realtime.domain.service.GpsPingProcessor;
import com.yowyob.tiibntick.core.realtime.domain.service.PresenceDomainService;
import com.yowyob.tiibntick.core.realtime.domain.service.SseDomainService;
import com.yowyob.tiibntick.core.realtime.application.port.out.ISseEmitter;
import com.yowyob.tiibntick.core.realtime.domain.service.WebSocketSessionManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Central Spring configuration for tnt-realtime-core.
 * Assembles all domain services and wires them with outbound port implementations.
 *
 * <p>This configuration class is loaded via Spring Boot autoconfiguration
 * (META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).</p>
 *
 * @author MANFOUO Braun
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({RealtimeProperties.class, PresenceProperties.class, GeofenceProperties.class})
public class RealtimeCoreConfig {

    // ─── Domain Service Beans ─────────────────────────────────────────────────

    @Bean
    public WebSocketSessionManager webSocketSessionManager() {
        return new WebSocketSessionManager();
    }

    @Bean
    public PresenceDomainService presenceDomainService(IPresenceRepository presenceRepository,
                                                       IWebSocketBroadcaster broadcaster) {
        return new PresenceDomainService(presenceRepository, broadcaster);
    }

    @Bean
    public GeofenceMonitorService geofenceMonitorService(IGeofenceZoneRepository geofenceZoneRepository,
                                                         IWebSocketBroadcaster broadcaster,
                                                         IRealtimeEventPublisher eventPublisher) {
        return new GeofenceMonitorService(geofenceZoneRepository, broadcaster, eventPublisher);
    }

    @Bean
    public GpsPingProcessor gpsPingProcessor(IActorLocationUpdater locationUpdater,
                                             IKalmanEtaUpdater kalmanEtaUpdater,
                                             IWebSocketBroadcaster broadcaster,
                                             IRealtimeEventPublisher eventPublisher,
                                             GeofenceMonitorService geofenceMonitorService) {
        return new GpsPingProcessor(locationUpdater, kalmanEtaUpdater, broadcaster, eventPublisher, geofenceMonitorService);
    }

    @Bean
    public EtaBroadcastDomainService etaBroadcastDomainService(IWebSocketBroadcaster broadcaster,
                                                               IRealtimeEventPublisher eventPublisher) {
        return new EtaBroadcastDomainService(broadcaster, eventPublisher);
    }

    @Bean
    public SseDomainService sseDomainService(ISseEmitter sseEmitter) {
        return new SseDomainService(sseEmitter);
    }

    // ─── Scheduled Maintenance Tasks ─────────────────────────────────────────
    // See RealtimeMaintenanceScheduler — @Scheduled methods must be no-arg,
    // so the session/presence sweeps live there with constructor-injected deps.

    /**
     * Registers WatchSubDeliverersApplicationService — FreelancerOrg fleet tracking ().
     */
    @org.springframework.context.annotation.Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(
        com.yowyob.tiibntick.core.realtime.application.service.WatchSubDeliverersApplicationService.class)
    public com.yowyob.tiibntick.core.realtime.application.service.WatchSubDeliverersApplicationService watchSubDeliverersApplicationService(
            com.yowyob.tiibntick.core.realtime.application.port.out.IPresenceRepository presenceRepository,
            com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster broadcaster) {
        return new com.yowyob.tiibntick.core.realtime.application.service.WatchSubDeliverersApplicationService(
                presenceRepository, broadcaster);
    }

}