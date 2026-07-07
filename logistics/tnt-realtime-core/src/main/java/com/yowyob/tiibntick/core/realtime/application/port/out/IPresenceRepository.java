package com.yowyob.tiibntick.core.realtime.application.port.out;

import com.yowyob.tiibntick.core.realtime.domain.model.PresenceRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Outbound port for persisting and querying presence records.
 * Implementations back this with Redis (reactive) using TTL-based expiry.
 *
 * @author MANFOUO Braun
 */
public interface IPresenceRepository {

    /**
     * Persists or updates a presence record, refreshing its TTL.
     *
     * @param record the presence record to save
     * @return Mono completing after persistence
     */
    Mono<Void> save(PresenceRecord record);

    /**
     * Finds the presence record for a specific user within a tenant.
     *
     * @param userId   the user identifier
     * @param tenantId the tenant context
     * @return Mono with the record, or empty if not found / expired
     */
    Mono<PresenceRecord> findByUserAndTenant(String userId, String tenantId);

    /**
     * Returns all active presence records for a tenant.
     *
     * @param tenantId the tenant context
     * @return Flux of all presence records in this tenant
     */
    Flux<PresenceRecord> findAllByTenant(String tenantId);

    /**
     * Returns all presence records that have not been updated within the stale duration.
     *
     * @param staleDuration the maximum allowed silence duration
     * @return Flux of stale presence records
     */
    Flux<PresenceRecord> findAllStale(Duration staleDuration);

    /**
     * Deletes the presence record for a user.
     *
     * @param userId   the user identifier
     * @param tenantId the tenant context
     * @return Mono completing after deletion
     */
    Mono<Void> deleteByUserAndTenant(String userId, String tenantId);
    /**
     * Finds all currently online actors tagged with the given FreelancerOrg ().
     * Used by WatchSubDeliverersApplicationService to get initial fleet snapshot.
     *
     * @param freelancerOrgId the FreelancerOrg UUID
     * @param tenantId        tenant scope
     * @return Flux of presence records for online sub-deliverers in this org
     */
    default reactor.core.publisher.Flux<PresenceRecord> findOnlineActorsByOrg(String freelancerOrgId, String tenantId) {
        // Default: return empty — concrete Redis adapter overrides with org-filtered scan
        return reactor.core.publisher.Flux.empty();
    }

}