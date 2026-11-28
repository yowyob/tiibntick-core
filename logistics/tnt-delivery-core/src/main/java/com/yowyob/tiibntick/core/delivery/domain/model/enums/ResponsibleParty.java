package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Party held responsible for a failure, a return or a dispute outcome (spec §1.1, decision [B.6]: the cost of a return
 * follows the fault). Reused by incident and dispute under its name ({@code responsibleParty}).
 */
public enum ResponsibleParty {
    SENDER, RECIPIENT, COURIER, PLATFORM, RELAY, AGENCY, NONE;

    /** @return who pays a return when this party is at fault (default the sender [B.6]) */
    public ResponsibleParty returnCostPayer() {
        return switch (this) {
            case COURIER, PLATFORM, RELAY, AGENCY -> this;
            case SENDER, RECIPIENT, NONE -> SENDER;
        };
    }
}
