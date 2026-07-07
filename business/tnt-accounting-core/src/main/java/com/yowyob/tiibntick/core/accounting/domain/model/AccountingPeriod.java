package com.yowyob.tiibntick.core.accounting.domain.model;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a fiscal period (month) during which entries can be posted.
 * Must be OPEN to accept new journal entries.
 * Author: MANFOUO Braun
 */
public final class AccountingPeriod {

    private final UUID id;
    private final UUID tenantId;
    private final int year;
    private final int month;
    private final PeriodStatus status;
    private final Instant openedAt;
    private final Instant closedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AccountingPeriod(UUID id, UUID tenantId, int year, int month,
                              PeriodStatus status, Instant openedAt, Instant closedAt,
                              Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.year = year;
        this.month = month;
        this.status = Objects.requireNonNull(status);
        this.openedAt = Objects.requireNonNull(openedAt);
        this.closedAt = closedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static AccountingPeriod open(UUID tenantId, int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        Instant now = Instant.now();
        return new AccountingPeriod(UUID.randomUUID(), tenantId, year, month,
                PeriodStatus.OPEN, now, null, now, now);
    }

    public static AccountingPeriod rehydrate(UUID id, UUID tenantId, int year, int month,
                                              PeriodStatus status, Instant openedAt, Instant closedAt,
                                              Instant createdAt, Instant updatedAt) {
        return new AccountingPeriod(id, tenantId, year, month, status, openedAt, closedAt, createdAt, updatedAt);
    }

    public AccountingPeriod close() {
        if (status != PeriodStatus.OPEN && status != PeriodStatus.CLOSING) {
            throw new IllegalStateException("Period must be OPEN or CLOSING to close it");
        }
        Instant now = Instant.now();
        return new AccountingPeriod(id, tenantId, year, month, PeriodStatus.CLOSED,
                openedAt, now, createdAt, now);
    }

    public AccountingPeriod lock() {
        if (status != PeriodStatus.CLOSED) {
            throw new IllegalStateException("Only CLOSED periods can be locked");
        }
        return new AccountingPeriod(id, tenantId, year, month, PeriodStatus.LOCKED,
                openedAt, closedAt, createdAt, Instant.now());
    }

    public boolean isOpen() {
        return status == PeriodStatus.OPEN;
    }

    public YearMonth asYearMonth() {
        return YearMonth.of(year, month);
    }

    public String periodCode() {
        return String.format("%d-%02d", year, month);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public int getYear() { return year; }
    public int getMonth() { return month; }
    public PeriodStatus getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
