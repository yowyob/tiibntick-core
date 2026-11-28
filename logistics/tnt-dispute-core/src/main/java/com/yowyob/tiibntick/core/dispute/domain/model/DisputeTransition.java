package com.yowyob.tiibntick.core.dispute.domain.model;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus;

import java.time.Instant;
import java.util.Map;

/**
 * One change of a dispute to publish on {@code tnt.dispute.events} (lot L4): a transition of the table §1.4 (LT1–LT12),
 * or the change without transition {@code EVIDENCE_ADDED}.
 *
 * @param from       state before, {@code null} at opening
 * @param to         state after
 * @param changeKind {@code null} for a transition, else {@code EVIDENCE_ADDED}
 * @param trigger    what caused it
 * @param actor      who caused it
 * @param details    event details (spec §3.6)
 * @param at         when
 */
public record DisputeTransition(DisputeStatus from, DisputeStatus to, String changeKind, String trigger,
                                TntEventActor actor, Map<String, Object> details, Instant at) {

    public DisputeTransition {
        details = details == null ? Map.of() : details;
    }

    /** @return {@code EVIDENCE_ADDED} for the change without transition, else the target state name */
    public String eventKind() {
        return changeKind != null ? changeKind : to.name();
    }
}
