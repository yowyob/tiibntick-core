package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DaoRuleRecord;

/**
 * Response DTO — DAO zone governance rule activation details.
 * Returned by {@code GET /tnt/trust/dao/{zoneId}/rules}.
 *
 * @author MANFOUO Braun
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DaoRuleResponse(
        String ruleId,
        String zoneId,
        String tenantId,
        String ruleJson,
        String activatedAt,
        String blockchainTxHash) {

    /** Converts a {@link DaoRuleRecord} domain object to this DTO. */
    public static DaoRuleResponse from(final DaoRuleRecord rule) {
        return new DaoRuleResponse(
                rule.getRuleId(),
                rule.getZoneId(),
                rule.getTenantId(),
                rule.getRuleJson(),
                rule.getActivatedAt() != null ? rule.getActivatedAt().toString() : null,
                rule.getBlockchainTxHash());
    }
}
