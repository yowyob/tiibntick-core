package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.ReconciliationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * ReconciliationRecord — entity that captures the result of a periodic
 * consistency check between wallet transactions and bank/MoMo provider statements.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class ReconciliationRecord {

    private final ReconciliationId id;
    private final UUID tenantId;
    /** Period covered by this reconciliation (year + month). */
    private final YearMonth period;
    /** Sum of all CONFIRMED transactions in the wallet for the period. */
    private final Money walletTotal;
    /** Total reported by the bank or MoMo provider statement. */
    private final Money bankStatementTotal;
    /** Absolute discrepancy: bankStatementTotal - walletTotal. */
    private final Money discrepancy;
    private ReconciliationStatus status;
    private String resolutionNote;
    private LocalDateTime resolvedAt;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Evaluates whether a discrepancy exists and updates the status accordingly.
     */
    public void evaluate() {
        if (discrepancy.isZero()) {
            this.status = ReconciliationStatus.BALANCED;
        } else {
            this.status = ReconciliationStatus.DISCREPANCY_FOUND;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks this reconciliation as resolved with an explanatory note.
     *
     * @param note explanation of how the discrepancy was resolved
     */
    public void resolve(String note) {
        if (this.status != ReconciliationStatus.DISCREPANCY_FOUND) {
            throw new IllegalStateException("Cannot resolve reconciliation in status: " + this.status);
        }
        this.resolutionNote = note;
        this.status = ReconciliationStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasDiscrepancy() {
        return !discrepancy.isZero();
    }
}
