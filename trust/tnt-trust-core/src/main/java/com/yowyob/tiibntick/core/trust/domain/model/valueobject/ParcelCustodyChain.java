package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate Value Object representing the complete Chain of Custody for a parcel.
 *
 * <p>Answers: "Who has had possession of this parcel, in what order,
 * and is the cryptographic chain intact?"
 *
 * <p>Constructed by {@link com.yowyob.tiibntick.core.trust.application.service.CustodyChainService}
 * from blockchain proof queries. Each element in {@code links} is an ordered
 * {@link CustodyTransferRecord}, from the initial pickup to the most recent
 * custody transfer.
 *
 * <p>The chain is considered intact when every link's {@code previousCustodyHash}
 * matches the preceding link's {@code custodyHash}. A broken chain indicates
 * potential tampering or data loss.
 *
 * @author MANFOUO Braun
 */
public record ParcelCustodyChain(

        String packageId,
        String trackingCode,
        String tenantId,

        /**
         * Ordered list of custody transfer records, oldest first.
         * Corresponds to the Event Stream for custody.
         */
        List<CustodyTransferRecord> links,

        /**
         * True if the cryptographic chain (PoI) is unbroken across all links.
         * A false value indicates tampering, data loss, or a recording gap.
         */
        boolean chainIntact,

        /**
         * The custodyHash of the first broken link, if chainIntact is false.
         * Null if the chain is intact.
         */
        String brokenAtCustodyHash,

        /** Actor UUID currently holding the parcel. Last link's toActorId. */
        String currentCustodian,

        /** CustodyTransferType of the most recent link. */
        CustodyTransferType currentCustodyType,

        LocalDateTime firstTransferAt,
        LocalDateTime lastTransferAt,
        LocalDateTime chainVerifiedAt

) {

    /** Returns the number of custody links (transfers) in this chain. */
    public int transferCount() {
        return links != null ? links.size() : 0;
    }

    /** Returns true if the parcel has been delivered (final custody transfer). */
    public boolean isDelivered() {
        return currentCustodyType == CustodyTransferType.TRANSFER_TO_RECIPIENT;
    }

    /** Returns an empty chain for a parcel with no custody records. */
    public static ParcelCustodyChain empty(final String packageId, final String tenantId) {
        return new ParcelCustodyChain(
                packageId, null, tenantId,
                Collections.emptyList(),
                true, null, null, null,
                null, null, LocalDateTime.now()
        );
    }
}
