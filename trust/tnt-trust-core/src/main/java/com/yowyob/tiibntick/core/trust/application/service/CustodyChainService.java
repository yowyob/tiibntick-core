package com.yowyob.tiibntick.core.trust.application.service;

import com.yowyob.tiibntick.core.trust.domain.model.valueobject.BlockchainProof;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyVerificationResult;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ParcelCustodyChain;
import com.yowyob.tiibntick.core.trust.application.port.in.GetCustodyChainUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service implementing Chain of Custody for TiiBnTick parcels.
 *
 * <p>Queries the blockchain for all {@code PACKAGE_CUSTODY_TRANSFERRED} proofs
 * for a given package entity and assembles them into an ordered
 * {@link ParcelCustodyChain}. Verifies cryptographic integrity (PoI chain).
 *
 * <p>The Chain of Custody implements the "Fil d'Ariane de possession" concept:
 * every physical handover of a parcel is anchored on Hyperledger Fabric with
 * a Proof of Content (PoC) and chained via previousCustodyHash.
 *
 * @author MANFOUO Braun
 */
@Service
public class CustodyChainService implements GetCustodyChainUseCase {

    private static final String GENESIS_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String CUSTODY_EVENT_TYPE = "PACKAGE_CUSTODY_TRANSFERRED";
    private static final String CUSTODY_ENTITY_TYPE = "CUSTODY_TRANSFER";

    private final TrustProofQueryPort trustProofQueryPort;

    public CustodyChainService(final TrustProofQueryPort trustProofQueryPort) {
        this.trustProofQueryPort = trustProofQueryPort;
    }

    @Override
    public Mono<ParcelCustodyChain> getByPackageId(
            final String packageId, final String tenantId) {
        return trustProofQueryPort
                .getAuditHistoryWithDetails(packageId, CUSTODY_ENTITY_TYPE, tenantId)
                .filter(proof -> CUSTODY_EVENT_TYPE.equals(proof.eventType()))
                .sort((a, b) -> {
                    if (a.timestamp() == null) return -1;
                    if (b.timestamp() == null) return 1;
                    return a.timestamp().compareTo(b.timestamp());
                })
                .collectList()
                .map(proofs -> buildChain(packageId, tenantId, proofs));
    }

    @Override
    public Mono<CustodyVerificationResult> verifyCustodyChain(
            final String packageId, final String tenantId) {
        return getByPackageId(packageId, tenantId)
                .map(chain -> new CustodyVerificationResult(
                        packageId,
                        chain.chainIntact(),
                        chain.transferCount(),
                        chain.brokenAtCustodyHash(),
                        chain.chainIntact()
                                ? "Custody chain is cryptographically intact"
                                : "Chain broken at custody hash: " + chain.brokenAtCustodyHash(),
                        LocalDateTime.now()
                ));
    }

    @Override
    public Mono<String> getCurrentCustodian(
            final String packageId, final String tenantId) {
        return getByPackageId(packageId, tenantId)
                .map(chain -> chain.currentCustodian() != null
                        ? chain.currentCustodian()
                        : "");
    }

    private ParcelCustodyChain buildChain(
            final String packageId,
            final String tenantId,
            final List<BlockchainProof> proofs) {

        if (proofs.isEmpty()) {
            return ParcelCustodyChain.empty(packageId, tenantId);
        }

        final List<CustodyTransferRecord> links = new ArrayList<>();
        boolean chainIntact = true;
        String brokenAt = null;

        for (int i = 0; i < proofs.size(); i++) {
            final CustodyTransferRecord link = proofToRecord(proofs.get(i), tenantId);
            links.add(link);

            if (i > 0) {
                final String expectedPrevHash = links.get(i - 1).getCustodyHash();
                final String actualPrevHash = link.getPreviousCustodyHash();
                if (expectedPrevHash != null && !expectedPrevHash.equals(actualPrevHash)) {
                    chainIntact = false;
                    brokenAt = link.getCustodyHash();
                }
            } else {
                final String prevHash = link.getPreviousCustodyHash();
                if (prevHash != null && !GENESIS_HASH.equals(prevHash)) {
                    chainIntact = false;
                    brokenAt = link.getCustodyHash();
                }
            }
        }

        final CustodyTransferRecord last = links.get(links.size() - 1);
        return new ParcelCustodyChain(
                packageId,
                last.getTrackingCode(),
                tenantId,
                links,
                chainIntact,
                brokenAt,
                last.getToActorId(),
                last.getTransferType(),
                links.get(0).getTransferredAt(),
                last.getTransferredAt(),
                LocalDateTime.now()
        );
    }

    private CustodyTransferRecord proofToRecord(
            final BlockchainProof proof, final String tenantId) {
        final String transferTypeRaw = proof.getPayloadField("transferType");
        CustodyTransferType transferType = null;
        if (transferTypeRaw != null && !transferTypeRaw.isBlank()) {
            try {
                transferType = CustodyTransferType.valueOf(transferTypeRaw);
            } catch (IllegalArgumentException ignored) {
                transferType = CustodyTransferType.PICKUP_FROM_SENDER;
            }
        }

        final CustodyTransferRecord record = new CustodyTransferRecord(
                proof.getPayloadField("transferId"),
                proof.entityId(),
                proof.getPayloadField("trackingCode"),
                tenantId,
                proof.getPayloadField("fromActorId"),
                proof.getPayloadField("toActorId"),
                transferType,
                proof.getPayloadField("hubId"),
                parseDouble(proof.getPayloadField("gpsLat")),
                parseDouble(proof.getPayloadField("gpsLng")),
                proof.timestamp()
        );
        record.setPocHash(proof.getPayloadField("pocHash"));
        record.setPreviousCustodyHash(proof.previousProofHash());
        record.setCustodyHash(proof.proofHash());
        if (proof.txHash() != null) {
            record.confirmOnChain(proof.txHash());
        }
        return record;
    }

    private Double parseDouble(final String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value);
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
