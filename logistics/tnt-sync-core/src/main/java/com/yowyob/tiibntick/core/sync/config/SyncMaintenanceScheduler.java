package com.yowyob.tiibntick.core.sync.config;

import com.yowyob.tiibntick.core.sync.config.properties.SyncProperties;
import com.yowyob.tiibntick.core.sync.domain.service.SyncSessionManager;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic maintenance tasks for tnt-sync-core.
 *
 * <p>{@code @Scheduled} methods must be no-arg — dependencies are therefore
 * injected via the constructor rather than passed as method parameters
 * (as {@link SyncCoreConfig} previously attempted).
 *
 * @author MANFOUO Braun
 */
@Component
public class SyncMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncMaintenanceScheduler.class);

    private final SyncSessionManager sessionManager;
    private final SyncProperties properties;

    public SyncMaintenanceScheduler(SyncSessionManager sessionManager, SyncProperties properties) {
        this.sessionManager = sessionManager;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${tnt.sync.session-cleanup-interval:21600000}")
    @SchedulerLock(name = "sync-cleanup-expired-sessions", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void cleanupExpiredSessions() {
        LockAssert.assertLocked();
        sessionManager.cleanupExpired(properties.getSessionRetention())
                .subscribe(null, ex -> log.error("Session cleanup failed: {}", ex.getMessage()));
    }
}
