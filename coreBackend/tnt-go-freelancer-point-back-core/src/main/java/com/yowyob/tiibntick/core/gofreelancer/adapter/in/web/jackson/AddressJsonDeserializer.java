package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;

import java.io.IOException;

/**
 * Jackson deserializer for the immutable {@link Address} value object.
 *
 * @author MANFOUO BRAUN
 */
public class AddressJsonDeserializer extends StdDeserializer<Address> {

    public AddressJsonDeserializer() {
        super(Address.class);
    }

    @Override
    public Address deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }

        Address.Builder builder = Address.builder()
                .street(text(node, "street"))
                .landmark(text(node, "landmark"))
                .quarter(text(node, "quarter"))
                .city(text(node, "city"))
                .region(text(node, "region"))
                .country(text(node, "country"))
                .postalCode(text(node, "postalCode"));

        JsonNode coords = node.get("coordinates");
        if (coords != null && !coords.isNull()) {
            Double lat = number(coords, "latitude");
            Double lon = number(coords, "longitude");
            if (lat != null && lon != null) {
                builder.coordinates(GeoCoordinates.of(lat, lon));
            }
        } else {
            Double lat = number(node, "latitude");
            Double lon = number(node, "longitude");
            if (lat != null && lon != null) {
                builder.coordinates(GeoCoordinates.of(lat, lon));
            }
        }

        return builder.build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String value = child.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static Double number(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || !child.isNumber()) {
            return null;
        }
        return child.asDouble();
    }
}
