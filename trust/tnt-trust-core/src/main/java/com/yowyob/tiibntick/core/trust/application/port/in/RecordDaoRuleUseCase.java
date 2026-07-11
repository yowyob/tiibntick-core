package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Mono;

/**
 * Inbound Port — {@code RecordDaoRuleUseCase}.
 *
 * <p>Anchors a DAO zone collective rule on Hyperledger Fabric.
 * Once recorded on-chain, the rule is immutable and auditable by all
 * zone members.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface RecordDaoRuleUseCase {

    /**
     * Anchors a DAO zone rule on the blockchain.
     *
     * @param zoneId   the DAO zone identifier
     * @param rule     the JSON-encoded rule definition
     * @param tenantId the tenant identifier
     * @return a {@link Mono} emitting the Fabric transaction hash
     */
    Mono<String> record(String zoneId, String rule, String tenantId);
}
