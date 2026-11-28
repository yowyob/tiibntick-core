package com.yowyob.tiibntick.core.accounting.domain.model.kernel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A fact to book in the Kernel accounting service (lot L6, cible §4.6, decision H.3): one balanced entry in the book
 * of its owner. Plain data, serialised as is in {@code accounting.kernel_entry_links.fact} so that the administrator
 * can replay it.
 *
 * @param sourceKey     idempotency key of the fact ({@code COMMISSION:<settlementId>}, …), unique
 * @param sourceType    kind of source event ({@code COMMISSION_CALCULATED}, {@code COMPENSATION_PAID},
 *                      {@code INVOICE_PAID})
 * @param sourceEventId {@code eventId} of the event that carried it, for tracing
 * @param ownerType     book: {@code PLATFORM} or {@code ORGANIZATION}; {@code null} when no book can be named
 * @param ownerId       organisation of an ORGANIZATION book; {@code null} for the platform (configured)
 * @param skipReason    stable code when the fact is not booked ({@code NO_ISSUER_ORGANIZATION}…), else {@code null}
 */
public record BookkeepingFact(String sourceKey, String sourceType, String sourceEventId, String ownerType,
                              UUID ownerId, String journalCode, String journalLabel, String reference,
                              Instant date, BigDecimal amount, String currency, List<Line> lines,
                              String skipReason) {

    public static final String PLATFORM = "PLATFORM";
    public static final String ORGANIZATION = "ORGANIZATION";

    /** One side of the entry, on an account of the QO-20 chart. */
    public record Line(String accountCode, String accountLabel, String accountType, BigDecimal debit,
                       BigDecimal credit, String label) {
    }

    public boolean skipped() {
        return skipReason != null;
    }
}
