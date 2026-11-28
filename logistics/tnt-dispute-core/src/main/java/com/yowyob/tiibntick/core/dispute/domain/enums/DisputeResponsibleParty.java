package com.yowyob.tiibntick.core.dispute.domain.enums;

/**
 * Party found at fault by a ruling (spec §1.4, decision [B.6]): it pays the return or the new delivery ordered by the
 * dispute. Same values as delivery-core's {@code ResponsibleParty}.
 */
public enum DisputeResponsibleParty {
    SENDER, RECIPIENT, COURIER, PLATFORM, RELAY, AGENCY, NONE
}
