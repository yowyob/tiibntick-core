package com.yowyob.tiibntick.core.trust.application.port.in;

import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyVerificationResult;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ParcelCustodyChain;
import reactor.core.publisher.Mono;

/**
 * Use case for retrieving and verifying the Chain of Custody of a parcel.
 *
 * <p>The Chain of Custody answers: "Who had possession of this parcel,
 * when, where, and can we prove the chain was not tampered with?"
 *
 * <p>Implementation queries the blockchain (via {@code TrustProofQueryPort}),
 * filters proofs of type {@code PACKAGE_CUSTODY_TRANSFERRED} for the given
 * package entity, and assembles them into a {@link ParcelCustodyChain}.
 *
 * @author MANFOUO Braun
 */
public interface GetCustodyChainUseCase {

    /**
     * Returns the complete Chain of Custody for a parcel.
     *
     * @param packageId the domain package UUID
     * @param tenantId  the tenant UUID
     * @return the full ordered chain of custody, or empty chain if no records
     */
    Mono<ParcelCustodyChain> getByPackageId(String packageId, String tenantId);

    /**
     * Verifies the cryptographic integrity of a parcel's custody chain.
     *
     * <p>Checks that each link's {@code previousCustodyHash} matches
     * the preceding link's {@code custodyHash}.
     *
     * @param packageId the domain package UUID
     * @param tenantId  the tenant UUID
     * @return verification result with integrity status and broken link details
     */
    Mono<CustodyVerificationResult> verifyCustodyChain(String packageId, String tenantId);

    /**
     * Returns the actor UUID currently holding the parcel.
     *
     * <p>Shortcut: returns the {@code toActorId} of the most recent custody transfer.
     *
     * @param packageId the domain package UUID
     * @param tenantId  the tenant UUID
     * @return the actor UUID of the current custodian, or empty if no transfers
     */
    Mono<String> getCurrentCustodian(String packageId, String tenantId);
}
