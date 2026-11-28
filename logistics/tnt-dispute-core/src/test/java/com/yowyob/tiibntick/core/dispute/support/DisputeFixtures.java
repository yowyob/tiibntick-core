package com.yowyob.tiibntick.core.dispute.support;

import com.yowyob.tiibntick.core.dispute.application.command.*;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Builders shared by the lot L4 dispute tests. */
public final class DisputeFixtures {

    public static final String TENANT = "6f2b7c3e-1a7d-4c55-9d6f-3c1e7b2a9d10";
    public static final String CLAIMANT = "0b8f8a5e-2c39-4a51-8d36-0e3d7e1b5c21";
    public static final String MEDIATOR = "a3d4c5e6-7f80-4a91-b2c3-d4e5f6a7b8c9";

    private DisputeFixtures() {
    }

    public static OpenDisputeCommand open(DisputeCause cause, String parcelId, String deliveryId) {
        return new OpenDisputeCommand(TENANT, CLAIMANT, ClaimantType.CLIENT, UUID.randomUUID().toString(),
                RespondentType.FREELANCER, cause, DisputeCategory.MISSION_GO, DisputePriority.HIGH, deliveryId,
                parcelId, "TNT-42", "Le client M. Dupont au +237690000000 dit que le colis est cassé", null, null,
                false);
    }

    public static Dispute opened() {
        return Dispute.open(open(DisputeCause.PACKAGE_DAMAGED, UUID.randomUUID().toString(), null),
                DisputeReference.forSequence(7));
    }

    /** OPEN → UNDER_INVESTIGATION → MEDIATION_IN_PROGRESS. */
    public static Dispute inMediation() {
        Dispute d = opened();
        d.assignMediator(MEDIATOR);
        d.startMediation();
        return d;
    }

    public static RuleDisputeCommand rule(Dispute d, ResolutionType type, BigDecimal amount) {
        CompensationDetails c = amount == null ? null
                : CompensationDetails.approved(amount, "XAF", CompensationMethod.WALLET_CREDIT, CLAIMANT);
        return new RuleDisputeCommand(d.getId(), TENANT, MEDIATOR, type, amount != null, c, "décision",
                DisputeResponsibleParty.COURIER);
    }

    public static CloseDisputeCommand close(Dispute d, ClosureType type, String reason) {
        return new CloseDisputeCommand(d.getId(), TENANT, MEDIATOR, type, reason);
    }

    public static DisputeEvidence evidence(Dispute d, String by) {
        return DisputeEvidence.create(d.getId(), by, EvidenceSubmitterType.CLAIMANT, EvidenceType.PHOTO,
                "disputes/x.jpg", "photo du colis", "abc123");
    }

    public static LocalDateTime inTwoDays() {
        return LocalDateTime.now().plusDays(2);
    }
}
