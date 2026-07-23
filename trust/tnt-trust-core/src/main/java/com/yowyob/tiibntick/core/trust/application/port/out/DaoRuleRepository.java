package com.yowyob.tiibntick.core.trust.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DaoRuleRecord;

/**
 * Outbound Port — {@code DaoRuleRepository}.
 *
 * <p>Persistence contract for {@link DaoRuleRecord} value objects.
 * Implemented by the R2DBC adapter targeting the
 * {@code tnt_trust.dao_rule_records} table in the {@code tnt_trust_db} database.
 *
 * <p>This repository is the local PostgreSQL cache for on-chain DAO zone
 * governance rule data. It is updated asynchronously when
 * {@code yow.trust.events.committed} notifications arrive from {@code yow-trust-event}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface DaoRuleRepository {

    /**
     * Saves a new {@link DaoRuleRecord} activation.
     *
     * @param rule the rule record to persist
     * @return a {@link Mono} emitting the saved record
     */
    Mono<DaoRuleRecord> save(DaoRuleRecord rule);

    /**
     * Finds the DAO rule activation history for a zone, most recent first.
     *
     * @param zoneId   the DAO zone identifier
     * @param tenantId the tenant identifier
     * @return a {@link Flux} of rule records ordered by {@code activatedAt} descending
     */
    Flux<DaoRuleRecord> findByZoneId(String zoneId, String tenantId);

    /**
     * Updates the Fabric transaction hash for a rule after on-chain confirmation.
     * Called by {@link com.yowyob.tiibntick.core.trust.adapter.in.kafka.TrustCommittedEventConsumer}.
     *
     * @param ruleId the rule identifier
     * @param txHash the Fabric transaction hash
     * @return a {@link Mono} completing when the update is persisted
     */
    Mono<Void> updateTxHash(String ruleId, String txHash);
}
