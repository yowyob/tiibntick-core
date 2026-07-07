package com.yowyob.tiibntick.core.sync.domain.service;

import com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository;
import com.yowyob.tiibntick.core.sync.domain.model.DeltaRecord;
import com.yowyob.tiibntick.core.sync.domain.model.SyncDelta;
import com.yowyob.tiibntick.core.sync.domain.model.SyncToken;
import com.yowyob.tiibntick.core.sync.domain.exception.SyncTokenExpiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public class DeltaSyncDomainService {

    private static final Logger log = LoggerFactory.getLogger(DeltaSyncDomainService.class);

    private static final Duration MAX_TOKEN_AGE = Duration.ofDays(7);
    private static final int DEFAULT_MAX_RECORDS = 500;

    private final IEntityVersionRepository entityVersionRepository;
    private final int maxRecordsPerDelta;

    public DeltaSyncDomainService(IEntityVersionRepository entityVersionRepository) {
        this(entityVersionRepository, DEFAULT_MAX_RECORDS);
    }

    public DeltaSyncDomainService(IEntityVersionRepository entityVersionRepository, int maxRecordsPerDelta) {
        this.entityVersionRepository = entityVersionRepository;
        this.maxRecordsPerDelta = maxRecordsPerDelta;
    }

    /**
     * Computes all entity changes since the given sync token for the specified tenant and user.
     * Optionally filters by aggregate types (e.g. only "MISSION", "PACKAGE").
     *
     * @param sinceToken       the client's last sync position
     * @param tenantId         the tenant context
     * @param filterAggregates optional set of aggregate types to include (null = all)
     * @return Mono with the computed delta
     */
    public Mono<SyncDelta> computeDelta(SyncToken sinceToken, String tenantId, Set<String> filterAggregates) {
        // Le token initial (bootstrap) est toujours valide, même si sa date est ancienne
        if (!sinceToken.isInitial() && sinceToken.isStale(MAX_TOKEN_AGE)) {
            return Mono.error(new SyncTokenExpiredException(sinceToken.value()));
        }

        LocalDateTime since = sinceToken.lastSyncAt();
        LocalDateTime now = LocalDateTime.now();

        log.debug("Computing delta for tenant={}, user={}, since={}", tenantId, sinceToken.userId(), since);

        Mono<List<DeltaRecord>> recordsMono = entityVersionRepository
                .findChangedSince(tenantId, since, filterAggregates, maxRecordsPerDelta)
                .map(evr -> evr.toDeltaRecord())
                .collectList();

        return recordsMono.map(records -> {
            SyncToken newToken = SyncToken.next(
                    sinceToken.userId(), tenantId, sinceToken.deviceId(), now);

            SyncDelta delta = new SyncDelta(
                    tenantId, sinceToken.userId(),
                    sinceToken, records,
                    java.util.Collections.emptyList(),
                    newToken, now);

            log.debug("Delta computed: {} records for tenant={}", records.size(), tenantId);
            return delta;
        });
    }

    /**
     * Computes a full reset delta (all current entity state) for a device doing its first sync.
     * Used when sinceToken is initial or when the client's local DB is empty.
     *
     * @param tenantId         the tenant context
     * @param userId           the user identifier
     * @param deviceId         the device identifier
     * @param filterAggregates optional set of aggregate types to include
     * @return Mono with the full bootstrap delta
     */
    public Mono<SyncDelta> computeBootstrapDelta(String tenantId, String userId, String deviceId,
                                                  Set<String> filterAggregates) {
        SyncToken initialToken = SyncToken.initial(userId, tenantId, deviceId);
        return computeDelta(initialToken, tenantId, filterAggregates);
    }

    /**
     * Returns the count of entity changes since the token (for monitoring / pre-flight).
     */
    public Mono<Long> countPendingChanges(SyncToken sinceToken, String tenantId) {
        return entityVersionRepository
                .countChangedSince(tenantId, sinceToken.lastSyncAt())
                .doOnNext(count -> log.debug("Pending changes for tenant={}: {}", tenantId, count));
    }
}
