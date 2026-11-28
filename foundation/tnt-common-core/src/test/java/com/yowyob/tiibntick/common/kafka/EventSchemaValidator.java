package com.yowyob.tiibntick.common.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** Test helper: validates JSON documents against the classpath schemas of {@link TntEventSchemas}. */
final class EventSchemaValidator {

    private static final ObjectMapper MAPPER = TntEventJson.mapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG = SchemaValidatorsConfig.builder()
        .formatAssertionsEnabled(true).build();

    private EventSchemaValidator() {
    }

    static JsonNode read(String resource) {
        try (InputStream in = EventSchemaValidator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("schema not on classpath: " + resource);
            }
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonSchema schema(JsonNode schemaNode) {
        return FACTORY.getSchema(schemaNode, CONFIG);
    }

    static JsonSchema envelope() {
        return schema(read(TntEventSchemas.ENVELOPE));
    }

    static JsonSchema maskedAddress() {
        JsonNode envelope = read(TntEventSchemas.ENVELOPE);
        com.fasterxml.jackson.databind.node.ObjectNode def =
            ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.path("$defs").path("maskedAddress")).deepCopy();
        def.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        return schema(def);
    }

    static List<String> errors(JsonSchema schema, String json) {
        try {
            return schema.validate(MAPPER.readTree(json)).stream().map(ValidationMessage::getMessage).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
