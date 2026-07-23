package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Flux;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DaoRuleRecord;

/**
 * Inbound Port — {@code GetDaoRulesUseCase}.
 *
 * <p>Retrieves the DAO zone governance rule activation history for a zone.
 * Results sourced from the local PostgreSQL cache (tnt_trust schema).
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface GetDaoRulesUseCase {

    /**
     * Retrieves the DAO rule activation history for a zone.
     *
     * @param zoneId   the DAO zone identifier
     * @param tenantId the tenant identifier
     * @return a {@link Flux} of rule records, most recent first
     */
    Flux<DaoRuleRecord> getByZoneId(String zoneId, String tenantId);
}
