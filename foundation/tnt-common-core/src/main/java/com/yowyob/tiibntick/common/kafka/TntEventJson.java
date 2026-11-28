package com.yowyob.tiibntick.common.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON settings of the aggregate events (spec §3.3): dates as ISO-8601 UTC strings, null fields present.
 * Same settings as the application's {@code tntObjectMapper}; provided here so that every producer and
 * test writes the envelope identically without depending on the bootstrap.
 */
public final class TntEventJson {

    private static final ObjectMapper MAPPER = configure(new ObjectMapper());

    private TntEventJson() {
    }

    /** @return a new mapper configured for aggregate events (callers may customise their copy) */
    public static ObjectMapper mapper() {
        return MAPPER.copy();
    }

    /** Applies the aggregate-event settings to {@code mapper} and returns it. */
    public static ObjectMapper configure(final ObjectMapper mapper) {
        return mapper.registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Serialises an aggregate event.
     *
     * @throws IllegalArgumentException if the payload cannot be serialised
     */
    public static String write(final TntAggregateEvent<?> event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise " + event.eventType() + " " + event.eventId(), e);
        }
    }
}
