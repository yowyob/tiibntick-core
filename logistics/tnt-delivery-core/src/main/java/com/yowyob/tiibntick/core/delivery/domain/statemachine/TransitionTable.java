package com.yowyob.tiibntick.core.delivery.domain.statemachine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The single transition table of an aggregate (spec {@code cible-evenements-colis-livraison.md} §1.0 rule 1,
 * decision [C bis]): an ordered list of numbered rows ({@code D1…}, {@code P1…}, {@code A1…}), each naming its source
 * states, its target state(s) and whether its target is the state memorised before a pause (rule 4).
 *
 * <p>Everything else is <b>derived</b> from the rows and never written twice: the allowed pairs, the terminal states
 * (no exit at all), the reachable states, and the enum helpers ({@code isTerminal()}, {@code isActive()}…) of the
 * aggregates. A pair absent from the rows is refused.
 *
 * <p>Domain class, no Spring.
 *
 * @param <S> status enum of the aggregate
 */
public final class TransitionTable<S extends Enum<S>> {

    /**
     * One row of the table.
     *
     * @param id                row number as written in the spec ({@code D7}, {@code P11}, {@code A4})
     * @param from              source states; empty for the creation row
     * @param to                target states (a single state, or the set of possible memorised states)
     * @param restoresMemorised whether the target must equal the state memorised before the pause (guard)
     * @param trigger           use case or cause (spec "Déclencheur" column, UPPER_SNAKE_CASE)
     * @param guard             human-readable guard, {@code "—"} when none
     */
    public record Row<S extends Enum<S>>(String id, Set<S> from, Set<S> to, boolean restoresMemorised,
                                         String trigger, String guard) {
        public Row {
            Objects.requireNonNull(id, "id");
            from = Set.copyOf(from);
            to = Set.copyOf(to);
            if (to.isEmpty()) {
                throw new IllegalArgumentException("Row " + id + " has no target");
            }
        }

        /** @return whether this row creates the aggregate (no source state) */
        public boolean isCreation() {
            return from.isEmpty();
        }

        /** @return whether this row goes from {@code source} to {@code target} */
        public boolean covers(S source, S target) {
            return from.contains(source) && to.contains(target);
        }
    }

    private final Class<S> type;
    private final S initial;
    private final List<Row<S>> rows;
    private final Map<S, Set<S>> exits;

    private TransitionTable(Class<S> type, S initial, List<Row<S>> rows) {
        this.type = type;
        this.initial = initial;
        this.rows = List.copyOf(rows);
        Map<S, Set<S>> computed = new EnumMap<>(type);
        for (S state : type.getEnumConstants()) {
            computed.put(state, EnumSet.noneOf(type));
        }
        for (Row<S> row : this.rows) {
            for (S source : row.from()) {
                computed.get(source).addAll(row.to());
            }
        }
        computed.replaceAll((state, targets) -> Collections.unmodifiableSet(targets));
        this.exits = Collections.unmodifiableMap(computed);
    }

    /** @return a builder for the table of {@code type} whose creation lands in {@code initial} */
    public static <S extends Enum<S>> Builder<S> builder(Class<S> type, S initial) {
        return new Builder<>(type, initial);
    }

    public Class<S> type() {
        return type;
    }

    public S initial() {
        return initial;
    }

    /** @return the rows, in spec order */
    public List<Row<S>> rows() {
        return rows;
    }

    /** @return whether {@code from → to} is a pair of the table */
    public boolean isAllowed(S from, S to) {
        return exits.get(from).contains(to);
    }

    /** @return every state reachable in one step from {@code from} (empty for a terminal state) */
    public Set<S> targetsFrom(S from) {
        return exits.get(from);
    }

    /** @return the rows covering {@code from → to} (several when two causes lead to the same pair) */
    public List<Row<S>> rowsFor(S from, S to) {
        List<Row<S>> matching = new ArrayList<>();
        for (Row<S> row : rows) {
            if (row.covers(from, to)) {
                matching.add(row);
            }
        }
        return matching;
    }

    /** @return the row numbered {@code id} */
    public Row<S> row(String id) {
        return findRow(id).orElseThrow(() -> new IllegalArgumentException("No row " + id + " in " + type.getSimpleName()));
    }

    public Optional<Row<S>> findRow(String id) {
        return rows.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /** @return states without any exit */
    public Set<S> terminalStates() {
        EnumSet<S> terminal = EnumSet.noneOf(type);
        exits.forEach((state, targets) -> {
            if (targets.isEmpty()) {
                terminal.add(state);
            }
        });
        return Collections.unmodifiableSet(terminal);
    }

    /** @return states reachable from the initial state (the initial state included) */
    public Set<S> reachableFromInitial() {
        EnumSet<S> seen = EnumSet.of(initial);
        Deque<S> todo = new ArrayDeque<>(List.of(initial));
        while (!todo.isEmpty()) {
            for (S next : exits.get(todo.pop())) {
                if (seen.add(next)) {
                    todo.push(next);
                }
            }
        }
        return Collections.unmodifiableSet(seen);
    }

    /** @return the states that are the source of a row restoring the memorised state (pause-like states) */
    public Set<S> restoringStates() {
        EnumSet<S> states = EnumSet.noneOf(type);
        rows.stream().filter(Row::restoresMemorised).forEach(r -> states.addAll(r.from()));
        return Collections.unmodifiableSet(states);
    }

    /**
     * Checks that row {@code rowId} allows {@code from → to}.
     *
     * @return the row
     * @throws IllegalStateException when the row does not cover the pair (the caller maps it to its 409 exception)
     */
    public Row<S> require(String rowId, S from, S to) {
        Row<S> row = row(rowId);
        if (!row.covers(from, to)) {
            throw new IllegalStateException(type.getSimpleName() + " " + from + " -> " + to
                    + " is not allowed by row " + rowId);
        }
        return row;
    }

    /** Fluent builder keeping the spec order of the rows. */
    public static final class Builder<S extends Enum<S>> {

        private final Class<S> type;
        private final S initial;
        private final List<Row<S>> rows = new ArrayList<>();

        private Builder(Class<S> type, S initial) {
            this.type = type;
            this.initial = initial;
        }

        /** Creation row: no source, lands in {@code to}. */
        public Builder<S> create(String id, S to, String trigger) {
            rows.add(new Row<>(id, Set.of(), Set.of(to), false, trigger, "—"));
            return this;
        }

        public Builder<S> row(String id, Set<S> from, S to, String trigger, String guard) {
            rows.add(new Row<>(id, from, Set.of(to), false, trigger, guard));
            return this;
        }

        /** Row whose target is the state memorised before the pause (guard: target = memorised). */
        public Builder<S> restore(String id, Set<S> from, Set<S> memorisable, String trigger) {
            rows.add(new Row<>(id, from, memorisable, true, trigger, "target = memorised state"));
            return this;
        }

        public TransitionTable<S> build() {
            return new TransitionTable<>(type, initial, rows);
        }
    }
}
