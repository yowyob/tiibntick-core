package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;

import java.io.IOException;
import java.util.Locale;

/**
 * Reads a delivery urgency sent by a client. The platform urgency is the one of {@code tnt-delivery-core}
 * (STANDARD, EXPRESS, SAME_DAY, SCHEDULED); the legacy gofp value {@code NORMAL} is still accepted on
 * input and means {@code STANDARD}. Matching is case-insensitive; a blank value is read as absent.
 */
public class DeliveryUrgencyInputDeserializer extends JsonDeserializer<DeliveryUrgency> {

    @Override
    public DeliveryUrgency deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String raw = parser.getValueAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if ("NORMAL".equals(value)) {
            return DeliveryUrgency.STANDARD;
        }
        try {
            return DeliveryUrgency.valueOf(value);
        } catch (IllegalArgumentException e) {
            return (DeliveryUrgency) context.handleWeirdStringValue(DeliveryUrgency.class, raw,
                    "not one of STANDARD, EXPRESS, SAME_DAY, SCHEDULED (NORMAL is accepted as STANDARD)");
        }
    }
}
