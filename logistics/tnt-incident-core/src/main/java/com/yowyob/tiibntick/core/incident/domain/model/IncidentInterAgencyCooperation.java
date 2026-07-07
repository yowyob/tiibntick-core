package com.yowyob.tiibntick.core.incident.domain.model;

import com.yowyob.tiibntick.core.incident.domain.enums.CooperationStatus;
import com.yowyob.tiibntick.core.incident.domain.enums.CooperationType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Inter-agency cooperation request lifecycle: REQUESTED to ACCEPTED to IN_PROGRESS to COMPLETED.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IncidentInterAgencyCooperation {

    private UUID id;
    private UUID incidentId;
    private UUID requestingAgencyId;
    private UUID respondingAgencyId;
    private CooperationType cooperationType;
    private CooperationStatus status;
    private Instant requestedAt;
    private Instant acceptedAt;
    private Instant completedAt;
    private Instant rejectedAt;
    private String rejectionReason;
    private String requestDetails;
    private String responseDetails;
    private String blockchainTxHash;
    private boolean notifiedToRequestingDriver;
    private boolean notifiedToRespondingDriver;

    /**
     * Creates a new inter-agency cooperation request.
     *
     * @param incidentId          the incident requiring cooperation
     * @param requestingAgencyId  the agency initiating the request
     * @param respondingAgencyId  the agency being asked for help
     * @param type                the type of cooperation needed
     * @param details             human-readable request details
     * @return a new cooperation record in REQUESTED status
     */
    public static IncidentInterAgencyCooperation request(UUID incidentId,
                                                          UUID requestingAgencyId,
                                                          UUID respondingAgencyId,
                                                          CooperationType type,
                                                          String details) {
        return IncidentInterAgencyCooperation.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .requestingAgencyId(requestingAgencyId)
                .respondingAgencyId(respondingAgencyId)
                .cooperationType(type)
                .status(CooperationStatus.REQUESTED)
                .requestedAt(Instant.now())
                .requestDetails(details)
                .notifiedToRequestingDriver(false)
                .notifiedToRespondingDriver(false)
                .build();
    }

    /**
     * Accepts the cooperation request.
     *
     * @param responseDetails additional details from the accepting agency
     * @return updated cooperation in ACCEPTED status
     */
    public IncidentInterAgencyCooperation accept(String responseDetails) {
        return toBuilder()
                .status(CooperationStatus.ACCEPTED)
                .acceptedAt(Instant.now())
                .responseDetails(responseDetails)
                .build();
    }

    /**
     * Rejects the cooperation request.
     *
     * @param reason the rejection reason
     * @return updated cooperation in REJECTED status
     */
    public IncidentInterAgencyCooperation reject(String reason) {
        return toBuilder()
                .status(CooperationStatus.REJECTED)
                .rejectedAt(Instant.now())
                .rejectionReason(reason)
                .build();
    }

    public IncidentInterAgencyCooperation startProgress() {
        return toBuilder().status(CooperationStatus.IN_PROGRESS).build();
    }

    /**
     * Records successful completion of the cooperation with a blockchain proof.
     *
     * @param blockchainTxHash the blockchain transaction hash
     * @return updated cooperation in COMPLETED status
     */
    public IncidentInterAgencyCooperation complete(String blockchainTxHash) {
        return toBuilder()
                .status(CooperationStatus.COMPLETED)
                .completedAt(Instant.now())
                .blockchainTxHash(blockchainTxHash)
                .build();
    }

    public IncidentInterAgencyCooperation markDriversNotified() {
        return toBuilder()
                .notifiedToRequestingDriver(true)
                .notifiedToRespondingDriver(true)
                .build();
    }

    /**
     * Returns {@code true} when the cooperation is currently active.
     *
     * @return {@code true} if status is ACCEPTED or IN_PROGRESS
     */
    public boolean isActive() {
        return status == CooperationStatus.ACCEPTED || status == CooperationStatus.IN_PROGRESS;
    }

    public IncidentInterAgencyCooperationBuilder toBuilder() {
        return IncidentInterAgencyCooperation.builder().id(id).incidentId(incidentId)
                .requestingAgencyId(requestingAgencyId).respondingAgencyId(respondingAgencyId)
                .cooperationType(cooperationType).status(status).requestedAt(requestedAt)
                .acceptedAt(acceptedAt).completedAt(completedAt).rejectedAt(rejectedAt)
                .rejectionReason(rejectionReason).requestDetails(requestDetails)
                .responseDetails(responseDetails).blockchainTxHash(blockchainTxHash)
                .notifiedToRequestingDriver(notifiedToRequestingDriver)
                .notifiedToRespondingDriver(notifiedToRespondingDriver);
    }
}
