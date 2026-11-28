package com.yowyob.tiibntick.core.dispute.domain.model;

import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus.*;

/**
 * Transition table of a dispute (spec §1.4, rows LT2–LT12; LT1 is the opening). It is the reference: a transition not
 * listed here is refused.
 */
public final class DisputeStateMachine {

    /** One row of the table. */
    public record Row(String id, Set<DisputeStatus> from, DisputeStatus to) {
    }

    public static final List<Row> ROWS = List.of(
            new Row("LT2", EnumSet.of(OPEN), UNDER_INVESTIGATION),
            new Row("LT3", EnumSet.of(UNDER_INVESTIGATION), AWAITING_EVIDENCE),
            new Row("LT4", EnumSet.of(AWAITING_EVIDENCE), UNDER_INVESTIGATION),
            new Row("LT5", EnumSet.of(UNDER_INVESTIGATION), MEDIATION_IN_PROGRESS),
            new Row("LT6", EnumSet.of(UNDER_INVESTIGATION, MEDIATION_IN_PROGRESS), PENDING_ARBITRATION),
            new Row("LT7", EnumSet.of(MEDIATION_IN_PROGRESS, PENDING_ARBITRATION), PENDING_COMPENSATION),
            new Row("LT8", EnumSet.of(MEDIATION_IN_PROGRESS, PENDING_ARBITRATION), CLOSED_RESOLVED),
            new Row("LT9", EnumSet.of(PENDING_COMPENSATION), COMPENSATED),
            new Row("LT10", EnumSet.of(OPEN, UNDER_INVESTIGATION, AWAITING_EVIDENCE, MEDIATION_IN_PROGRESS,
                    PENDING_ARBITRATION), CLOSED_WITHDRAWN),
            new Row("LT11", EnumSet.of(OPEN, UNDER_INVESTIGATION, AWAITING_EVIDENCE), CLOSED_EXPIRED),
            new Row("LT12", EnumSet.of(OPEN, UNDER_INVESTIGATION, AWAITING_EVIDENCE, MEDIATION_IN_PROGRESS,
                    PENDING_ARBITRATION), CLOSED_RESOLVED));

    private static final Map<String, Row> BY_ID;

    static {
        Map<String, Row> m = new java.util.LinkedHashMap<>();
        ROWS.forEach(r -> m.put(r.id(), r));
        BY_ID = Collections.unmodifiableMap(m);
    }

    private DisputeStateMachine() {
    }

    /** @return the row {@code id} ({@code LT2}…{@code LT12}) */
    public static Row row(String id) {
        Row r = BY_ID.get(id);
        if (r == null) {
            throw new IllegalArgumentException("Unknown dispute row " + id);
        }
        return r;
    }

    /** @return whether any row leads from {@code from} to {@code to} */
    public static boolean allows(DisputeStatus from, DisputeStatus to) {
        return ROWS.stream().anyMatch(r -> r.from().contains(from) && r.to() == to);
    }

    /** @return the targets reachable from {@code from} */
    public static Set<DisputeStatus> targets(DisputeStatus from) {
        Set<DisputeStatus> out = EnumSet.noneOf(DisputeStatus.class);
        ROWS.stream().filter(r -> r.from().contains(from)).forEach(r -> out.add(r.to()));
        return out;
    }

    static Map<DisputeStatus, Set<DisputeStatus>> asMap() {
        Map<DisputeStatus, Set<DisputeStatus>> m = new EnumMap<>(DisputeStatus.class);
        for (DisputeStatus s : DisputeStatus.values()) {
            m.put(s, targets(s));
        }
        return m;
    }
}
