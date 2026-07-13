package com.yowyob.tiibntick.core.sync.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationApplier;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationRepository;
import com.yowyob.tiibntick.core.sync.application.port.out.ISyncEventPublisher;
import com.yowyob.tiibntick.core.sync.application.port.out.ISyncSessionRepository;
import com.yowyob.tiibntick.core.sync.config.properties.ConflictResolutionProperties;
import com.yowyob.tiibntick.core.sync.config.properties.SyncProperties;
import com.yowyob.tiibntick.core.sync.domain.service.ConflictResolverService;
import com.yowyob.tiibntick.core.sync.domain.service.DeltaSyncDomainService;
import com.yowyob.tiibntick.core.sync.domain.service.OfflineQueueDomainService;
import com.yowyob.tiibntick.core.sync.domain.service.SyncSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({SyncProperties.class, ConflictResolutionProperties.class})
public class SyncCoreConfig {

    private static final Logger log = LoggerFactory.getLogger(SyncCoreConfig.class);

    @Bean
    public ConflictResolverService conflictResolverService(ConflictResolutionProperties props) {
        ConflictResolverService.Strategy strategy;
        try {
            strategy = ConflictResolverService.Strategy.valueOf(props.getDefaultStrategy().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown conflict resolution strategy '{}', defaulting to LWW", props.getDefaultStrategy());
            strategy = ConflictResolverService.Strategy.LWW;
        }
        return new ConflictResolverService(strategy);
    }

    @Bean
    public DeltaSyncDomainService deltaSyncDomainService(IEntityVersionRepository entityVersionRepository,
                                                          SyncProperties props) {
        return new DeltaSyncDomainService(entityVersionRepository, props.getMaxRecordsPerDelta());
    }

    @Bean
    public OfflineQueueDomainService offlineQueueDomainService(IOfflineOperationRepository operationRepository,
                                                                IEntityVersionRepository entityVersionRepository,
                                                                ConflictResolverService conflictResolverService,
                                                                ISyncEventPublisher eventPublisher,
                                                                List<IOfflineOperationApplier> offlineOperationAppliers,
                                                                @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        return new OfflineQueueDomainService(operationRepository, entityVersionRepository, conflictResolverService,
                eventPublisher, offlineOperationAppliers, objectMapper);
    }

    @Bean
    public SyncSessionManager syncSessionManager(ISyncSessionRepository sessionRepository) {
        return new SyncSessionManager(sessionRepository);
    }

    // See SyncMaintenanceScheduler — @Scheduled methods must be no-arg,
    // so the session cleanup lives there with constructor-injected deps.
}
