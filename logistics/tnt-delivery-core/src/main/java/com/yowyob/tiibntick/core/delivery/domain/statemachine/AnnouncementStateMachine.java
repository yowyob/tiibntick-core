package com.yowyob.tiibntick.core.delivery.domain.statemachine;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponseStatus;

import java.util.Set;

import static com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus.*;

/**
 * The transition table of the announcement (spec §1.5, rows A1–A7; QO-9) and of its responses
 * ({@code SENT → ACCEPTED | REJECTED | CANCELLED}).
 */
public final class AnnouncementStateMachine {

    public static final String A1 = "A1";
    public static final String A2 = "A2";
    public static final String A3 = "A3";
    public static final String A4 = "A4";
    public static final String A5 = "A5";
    public static final String A6 = "A6";
    public static final String A7 = "A7";

    public static final TransitionTable<AnnouncementStatus> TABLE =
            TransitionTable.builder(AnnouncementStatus.class, DRAFT)
                    .create(A1, DRAFT, "CREATE_DRAFT")
                    .row(A2, Set.of(DRAFT), PUBLISHED, "PUBLISH", "validateForPublication")
                    .row(A3, Set.of(PUBLISHED), IN_NEGOTIATION, "RESPONSE_RECEIVED", "valid price")
                    .row(A4, Set.of(PUBLISHED, IN_NEGOTIATION), ASSIGNED, "SELECT_RESPONSE", "response SENT")
                    .row(A5, Set.of(DRAFT, PUBLISHED, IN_NEGOTIATION), CANCELLED, "CANCEL_ANNOUNCEMENT", "—")
                    .row(A6, Set.of(ASSIGNED), CANCELLED, "DELIVERY_CANCELLED", "—")
                    .row(A7, Set.of(ASSIGNED), COMPLETED, "DELIVERY_ENDED", "outcome DELIVERED or FAILED (QO-9)")
                    .build();

    /** Responses: a response is sent, then accepted, rejected or withdrawn by its courier. */
    public static final TransitionTable<ResponseStatus> RESPONSE_TABLE =
            TransitionTable.builder(ResponseStatus.class, ResponseStatus.SENT)
                    .create("R1", ResponseStatus.SENT, "RESPOND")
                    .row("R2", Set.of(ResponseStatus.SENT), ResponseStatus.ACCEPTED, "SELECT_RESPONSE", "—")
                    .row("R3", Set.of(ResponseStatus.SENT), ResponseStatus.REJECTED, "OTHER_RESPONSE_SELECTED", "—")
                    .row("R4", Set.of(ResponseStatus.SENT), ResponseStatus.CANCELLED, "RESPONSE_WITHDRAWN", "—")
                    .build();

    private AnnouncementStateMachine() {
    }
}
