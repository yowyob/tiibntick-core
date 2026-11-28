package com.yowyob.tiibntick.common.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;
import java.util.UUID;

/**
 * Who caused an aggregate change ({@code actor} field of {@link TntAggregateEvent}, spec §3.3): a type and
 * a pseudonymous actor id — never a name or any other personal data [G.3].
 *
 * @param type actor role
 * @param id   actor id (UUID text), {@code null} for {@link Type#SYSTEM}
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TntEventActor(Type type, String id) {

    /** Actor types of {@code envelope.schema.json}. */
    public enum Type { COURIER, SENDER, RECIPIENT, RELAY_OP, AGENCY, SUPPORT, SYSTEM }

    public TntEventActor {
        Objects.requireNonNull(type, "type");
        if (id != null) {
            UUID.fromString(id);
        }
    }

    /** @return the system actor (scheduler, automatic reaction) */
    public static TntEventActor system() {
        return new TntEventActor(Type.SYSTEM, null);
    }

    /** @return an actor of {@code type} identified by {@code id} */
    public static TntEventActor of(final Type type, final UUID id) {
        return new TntEventActor(type, id == null ? null : id.toString());
    }
}
