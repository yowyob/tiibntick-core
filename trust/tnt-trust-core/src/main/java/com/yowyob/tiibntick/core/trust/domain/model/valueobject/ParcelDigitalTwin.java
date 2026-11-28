package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;

import java.time.LocalDateTime;

/**
 * Digital Twin of a TiiBnTick parcel.
 *
 * <p>Represents the composite real-time view of a parcel, combining:
 * <ul>
 *   <li>Current state of the parcel: {@link ParcelStatus} of delivery-core, the single parcel state machine
 *       (lot evt-l2); inferred from the Chain of Custody only for a parcel delivery-core does not know</li>
 *   <li>Chain of Custody status (from blockchain)</li>
 *   <li>Last known delivery proof (from blockchain, if available)</li>
 *   <li>Blockchain anchoring status</li>
 * </ul>
 *
 * <p>The Digital Twin concept: every physical parcel has an immutable
 * numerical counterpart. Every real-world event (pickup, transfer, delivery)
 * is reflected in the twin through the blockchain anchor.
 *
 * <p>Assembled by {@code LogisticProofResolverService} on demand.
 * NOT persisted — always rebuilt from live sources.
 *
 * @author MANFOUO Braun
 */
public record ParcelDigitalTwin(

        String packageId,
        String trackingCode,
        String tenantId,

        /** Current state of the parcel (delivery-core {@link ParcelStatus}). */
        ParcelStatus currentState,

        /** Complete Chain of Custody from blockchain. */
        ParcelCustodyChain custodyChain,

        /** Most recent delivery proof, if available. */
        DeliveryProofRecord lastDeliveryProof,

        /** Whether the digital twin's blockchain chain is cryptographically intact. */
        boolean blockchainIntact,

        /** Number of blockchain-anchored proofs for this parcel. */
        int blockchainProofCount,

        LocalDateTime lastBlockchainAnchorAt,
        LocalDateTime twinBuiltAt

) {}
