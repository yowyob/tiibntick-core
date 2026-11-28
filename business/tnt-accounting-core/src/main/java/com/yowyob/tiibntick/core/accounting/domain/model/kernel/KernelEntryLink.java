package com.yowyob.tiibntick.core.accounting.domain.model.kernel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Local trace of one fact booked in the Kernel (table {@code accounting.kernel_entry_links}): unique per
 * {@code sourceKey}, so that a replayed event never books twice (lot L6).
 */
public record KernelEntryLink(UUID id, String sourceKey, String sourceType, String sourceEventId, String ownerType,
                              UUID ownerId, String reference, BigDecimal amount, String currency, String fact,
                              UUID kernelEntryId, Status status, String reason, Instant postedAt, Instant createdAt,
                              Instant updatedAt) {

    public enum Status {
        /** Written before the Kernel call: a crash leaves it here, the next attempt looks the entry up first. */
        PENDING,
        /** Entry created in the Kernel, not validated yet. */
        POSTED,
        /** Entry created and validated in the Kernel: done. */
        VALIDATED,
        /** The Kernel call failed or the book is not configured: listed to the administrator, replayable. */
        FAILED,
        /** Nothing to book ({@link BookkeepingFact#skipReason()}). */
        SKIPPED;

        public boolean done() {
            return this == VALIDATED || this == SKIPPED;
        }
    }

    public KernelEntryLink with(Status status, UUID kernelEntryId, String reason, Instant at) {
        return new KernelEntryLink(id, sourceKey, sourceType, sourceEventId, ownerType, ownerId, reference, amount,
                currency, fact, kernelEntryId, status, reason,
                kernelEntryId != null && postedAt == null ? at : postedAt, createdAt, at);
    }

    public KernelEntryLink withOwner(UUID owner, Instant at) {
        return new KernelEntryLink(id, sourceKey, sourceType, sourceEventId, ownerType, owner, reference, amount,
                currency, fact, kernelEntryId, status, reason, postedAt, createdAt, at);
    }
}
