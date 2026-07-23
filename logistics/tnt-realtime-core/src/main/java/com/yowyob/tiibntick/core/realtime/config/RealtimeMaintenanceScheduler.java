package com.yowyob.tiibntick.core.realtime.config;

import com.yowyob.tiibntick.core.realtime.config.properties.PresenceProperties;
import com.yowyob.tiibntick.core.realtime.config.properties.RealtimeProperties;
import com.yowyob.tiibntick.core.realtime.domain.service.PresenceDomainService;
import com.yowyob.tiibntick.core.realtime.domain.service.WebSocketSessionManager;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic maintenance tasks for tnt-realtime-core.
 *
 * <p>{@code @Scheduled} methods must be no-arg — dependencies are therefore
 * injected via the constructor rather than passed as method parameters
 * (as {@link RealtimeCoreConfig} previously attempted).
 *
 * @author MANFOUO Braun
 */
@Component
public class RealtimeMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeMaintenanceScheduler.class);

    private final WebSocketSessionManager sessionManager;
    private final RealtimeProperties realtimeProperties;
    private final PresenceDomainService presenceDomainService;
    private final PresenceProperties presenceProperties;

    public RealtimeMaintenanceScheduler(WebSocketSessionManager sessionManager,
                                         RealtimeProperties realtimeProperties,
                                         PresenceDomainService presenceDomainService,
                                         PresenceProperties presenceProperties) {
        this.sessionManager = sessionManager;
        this.realtimeProperties = realtimeProperties;
        this.presenceDomainService = presenceDomainService;
        this.presenceProperties = presenceProperties;
    }

    /**
     * Periodically sweeps stale WebSocket sessions and expires them.
     * Runs every 30 seconds (configurable via tnt.realtime.session-sweep-interval).
     *
     * <p><b>Deliberately not ShedLock-guarded</b> (Chantier D · Audit n°6 · S2 review):
     * {@link WebSocketSessionManager} is an explicitly per-instance, non-distributed
     * in-memory registry (see its own Javadoc) — each instance only knows about the
     * WebSocket connections terminated on itself, so every instance must run this sweep
     * independently. A distributed lock here would be a correctness bug, not a fix: it
     * would leave every instance except the lock holder never expiring its own stale
     * local sessions.
     */
    @Scheduled(fixedDelayString = "${tnt.realtime.session-sweep-interval:30000}")
    public void sweepStaleSessions() {
        int expired = sessionManager.expireStale(realtimeProperties.getSessionIdleTimeout());
        if (expired > 0) {
            log.info("Session sweep complete — {} stale sessions expired", expired);
        }
    }

    /**
     * Periodically sweeps stale presence records and marks them offline.
     * Runs every 60 seconds (configurable via tnt.realtime.presence.sweep-interval).
     *
     * <p>Unlike {@link #sweepStaleSessions()}, presence is Redis-backed shared state
     * (see {@link PresenceDomainService}) — every instance would otherwise sweep the
     * same shared records redundantly, hence the lock (Chantier D · Audit n°6 · S2).
     */
    @Scheduled(fixedDelayString = "${tnt.realtime.presence.sweep-interval:60000}")
    @SchedulerLock(name = "realtime-sweep-stale-presences", lockAtMostFor = "PT50S", lockAtLeastFor = "PT5S")
    public void sweepStalePresences() {
        LockAssert.assertLocked();
        presenceDomainService.sweepStalePresences(presenceProperties.getStaleDuration())
                .subscribe(
                        count -> { if (count > 0) log.info("Presence sweep — {} records marked OFFLINE", count); },
                        ex -> log.error("Presence sweep failed: {}", ex.getMessage())
                );
    }
}
