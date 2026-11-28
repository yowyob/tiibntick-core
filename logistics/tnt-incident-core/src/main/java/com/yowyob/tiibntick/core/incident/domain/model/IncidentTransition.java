package com.yowyob.tiibntick.core.incident.domain.model;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus;

import java.time.Instant;
import java.util.Map;

/**
 * A change of an incident waiting to be published on {@code tnt.incident.events} (lot L3, spec §3.5): a transition
 * ({@code to} = new state, event type = {@code Incident<State>}) or a change without transition ({@code changeKind}
 * set: {@code ESCALATED_TO_DISPUTE}, {@code HANDOVER_CONFIRMED}, {@code HANDLER_ASSIGNED}).
 *
 * @param from       state before (for a change without transition: the current state)
 * @param to         state after
 * @param changeKind {@code null} for a transition
 * @param trigger    UPPER_SNAKE_CASE cause
 * @param actor      who caused it
 * @param details    event details (may be empty)
 * @param at         when
 */
public record IncidentTransition(IncidentStatus from, IncidentStatus to, String changeKind, String trigger,
                                 TntEventActor actor, Map<String, Object> details, Instant at) {

    public IncidentTransition {
        details = details == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(details));
    }

    /** @return the UPPER_SNAKE_CASE name the event type is derived from */
    public String eventKind() {
        return changeKind != null ? changeKind : to.name();
    }
}
