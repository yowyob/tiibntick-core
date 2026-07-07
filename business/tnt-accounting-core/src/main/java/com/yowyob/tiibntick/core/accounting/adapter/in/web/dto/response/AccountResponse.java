package com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.accounting.domain.model.Account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API response DTO for Account.
 * Author: MANFOUO Braun
 */
public record AccountResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String type,
        String category,
        String currency,
        BigDecimal balance,
        boolean active,
        UUID parentAccountId,
        int ohadaClass,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getTenantId(), a.getCode(), a.getName(),
                a.getType().name(), a.getCategory().name(), a.getCurrency(), a.getBalance(),
                a.isActive(), a.getParentAccountId(), a.getOhadaClass(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
