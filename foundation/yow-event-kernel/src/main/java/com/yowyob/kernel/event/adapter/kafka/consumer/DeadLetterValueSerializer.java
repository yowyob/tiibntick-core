package com.yowyob.kernel.event.adapter.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serializer;

import java.nio.charset.StandardCharsets;

/**
 * Serializer of the dead-letter producer ({@link TntKafkaErrorHandlingPostProcessor}): the containers of the
 * application deserialise keys and values as {@code String}, {@code byte[]} (Spring Boot's default factory)
 * or JSON objects (factories with a {@code JsonDeserializer}); the record is written back unchanged —
 * bytes as they are, text as UTF-8, any other object as JSON.
 */
public class DeadLetterValueSerializer implements Serializer<Object> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public byte[] serialize(final String topic, final Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof byte[] bytes) {
            return bytes;
        }
        if (data instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return MAPPER.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            return String.valueOf(data).getBytes(StandardCharsets.UTF_8);
        }
    }
}
