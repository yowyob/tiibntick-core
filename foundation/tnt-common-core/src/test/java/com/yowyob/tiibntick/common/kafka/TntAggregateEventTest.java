package com.yowyob.tiibntick.common.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TntAggregateEventTest {

    private static final ObjectMapper MAPPER = TntEventJson.mapper();
    private static final String TENANT = "d3b07384-d9a0-4c9c-b7d1-5f2a1b6c0e91";

    // ── Mechanical event naming (spec §1.0 rules 2/3) ─────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "DELIVERY, DELIVERY_ATTEMPT_FAILED, DeliveryAttemptFailed",
        "DELIVERY, PICKED_UP, DeliveryPickedUp",
        "PARCEL, PICKED_UP, ParcelPickedUp",
        "DELIVERY, CREATED, DeliveryCreated",
        "DELIVERY, DELIVERED, DeliveryDelivered",
        "DELIVERY, INCIDENT_REPORTED, DeliveryIncidentReported",
        "DELIVERY, ETA_UPDATED, DeliveryEtaUpdated",
        "PARCEL, DELIVERY_FAILED, ParcelDeliveryFailed",
        "PARCEL, AT_RELAY_HUB, ParcelAtRelayHub",
        "PARCEL, CUSTODY_CHANGED, ParcelCustodyChanged",
        "INCIDENT, REPORTED, IncidentReported",
        "INCIDENT, ESCALATED_TO_DISPUTE, IncidentEscalatedToDispute",
        "DISPUTE, OPEN, DisputeOpen",
        "DISPUTE, CLOSED_EXPIRED, DisputeClosedExpired",
        "ANNOUNCEMENT, DRAFT, AnnouncementDraft",
        "ANNOUNCEMENT, RESPONSE_RECEIVED, AnnouncementResponseReceived"
    })
    void eventTypeIsMechanical(TntAggregateType type, String state, String expected) {
        assertThat(type.eventTypeFor(state)).isEqualTo(expected);
    }

    @Test
    void eventTypeRejectsNonUpperSnakeCase() {
        assertThatThrownBy(() -> TntAggregateType.DELIVERY.eventTypeFor("pickedUp"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TntAggregateType.DELIVERY.eventTypeFor("PICKED__UP"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(TntAggregateType.class)
    void everyAggregateHasItsOwnTopic(TntAggregateType type) {
        assertThat(type.topic()).isEqualTo("tnt." + type.name().toLowerCase() + ".events");
    }

    @Test
    void aggregateTopicsAreTheTntTopicsConstants() {
        assertThat(TntAggregateType.PARCEL.topic()).isEqualTo(TntTopics.PARCEL_EVENTS);
        assertThat(TntAggregateType.DELIVERY.topic()).isEqualTo(TntTopics.DELIVERY_EVENTS);
        assertThat(TntAggregateType.INCIDENT.topic()).isEqualTo(TntTopics.INCIDENT_EVENTS);
        assertThat(TntAggregateType.DISPUTE.topic()).isEqualTo(TntTopics.DISPUTE_EVENTS);
        assertThat(TntAggregateType.ANNOUNCEMENT.topic()).isEqualTo(TntTopics.ANNOUNCEMENT_EVENTS);
    }

    // ── Serialisation and envelope schema (spec §3.3 / §3.6) ──────────────────────────────────

    @ParameterizedTest
    @EnumSource(TntAggregateType.class)
    void serialisedEventIsValidAgainstEnvelopeSchema(TntAggregateType type) throws Exception {
        String json = TntEventJson.write(sample(type));
        assertThat(EventSchemaValidator.errors(EventSchemaValidator.envelope(), json)).isEmpty();
    }

    @Test
    void nullFieldsArePresentDatesAreIsoUtcAndOrderIsStable() throws Exception {
        TntAggregateEvent<Map<String, Object>> event = TntAggregateEvent.<Map<String, Object>>builder(TntAggregateType.DELIVERY)
            .eventTypeFor("CREATED")
            .occurredAt(Instant.parse("2026-11-26T10:15:30.123Z"))
            .aggregateId(UUID.randomUUID())
            .trigger("CREATE_DELIVERY")
            .actor(TntEventActor.system())
            .payload(new LinkedHashMap<>(Map.of("status", "CREATED")))
            .build();
        String json = TntEventJson.write(event);
        JsonNode node = MAPPER.readTree(json);

        assertThat(node.get("occurredAt").asText()).isEqualTo("2026-11-26T10:15:30.123Z");
        for (String field : new String[] {"tenantId", "correlationId", "causationId", "previousState"}) {
            assertThat(node.has(field)).as(field + " present").isTrue();
            assertThat(node.get(field).isNull()).as(field + " null").isTrue();
        }
        assertThat(node.get("details").isObject()).isTrue();
        assertThat(node.get("details").size()).isZero();
        assertThat(node.get("actor").get("id").isNull()).isTrue();
        assertThat(node.fieldNames()).toIterable().startsWith("eventId", "eventType", "schemaVersion", "occurredAt");
        assertThat(EventSchemaValidator.errors(EventSchemaValidator.envelope(), json)).isEmpty();
    }

    @Test
    void roundTripsThroughJackson() throws Exception {
        TntAggregateEvent<Map<String, Object>> event = base().previousState(Map.of("status", "ASSIGNED"))
            .details(Map.of("attemptNumber", 1)).build();
        TntAggregateEvent<Map<String, Object>> read = MAPPER.readValue(TntEventJson.write(event),
            new TypeReference<TntAggregateEvent<Map<String, Object>>>() { });
        assertThat(read).isEqualTo(event);
    }

    @Test
    void schemaRejectsAnInvalidEnvelope() throws Exception {
        ObjectNode node = (ObjectNode) MAPPER.readTree(TntEventJson.write(sample(TntAggregateType.PARCEL)));
        node.put("aggregateType", "MISSION");
        node.put("eventType", "parcel-picked-up");
        node.put("eventId", "not-a-uuid");
        node.put("occurredAt", "yesterday");
        node.remove("trigger");
        node.put("schemaVersion", 0);
        assertThat(EventSchemaValidator.errors(EventSchemaValidator.envelope(), node.toString()))
            .hasSizeGreaterThanOrEqualTo(6);
    }

    @Test
    void schemaAcceptsAddedFields() throws Exception {
        ObjectNode node = (ObjectNode) MAPPER.readTree(TntEventJson.write(sample(TntAggregateType.PARCEL)));
        node.put("someFutureField", 42);
        assertThat(EventSchemaValidator.errors(EventSchemaValidator.envelope(), node.toString())).isEmpty();
    }

    // ── Validation ────────────────────────────────────────────────────────────────────────────

    @Test
    void rejectsAnEventTypeOfAnotherAggregate() {
        assertThatThrownBy(() -> TntAggregateEvent.<Map<String, Object>>builder(TntAggregateType.PARCEL)
            .eventType("DeliveryPickedUp").aggregateId(UUID.randomUUID()).trigger("X")
            .actor(TntEventActor.system()).payload(Map.of()).build())
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Parcel");
    }

    @Test
    void rejectsNonUuidIdsAndBadTriggerAndVersions() {
        assertThatThrownBy(() -> base().aggregateId("parcel-1").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base().tenantId("system").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base().causationId("cause").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base().trigger("confirm pickup").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base().schemaVersion(0).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base().aggregateVersion(-1).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base().payload(null).build()).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> base().actor(null).build()).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TntEventActor(TntEventActor.Type.COURIER, "bob"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void previousStateIsStatusFirstThenChangedFields() {
        Map<String, Object> changed = new LinkedHashMap<>();
        changed.put("eta", "2026-11-26T10:00:00Z");
        changed.put("status", "IGNORED");
        Map<String, Object> previous = TntAggregateEvent.previousState("IN_TRANSIT", changed);
        assertThat(previous).containsExactly(Map.entry("status", "IN_TRANSIT"), Map.entry("eta", "2026-11-26T10:00:00Z"));
    }

    @Test
    void recognisesAggregateEventsAmongLegacyRecords() throws Exception {
        assertThat(TntAggregateEvent.isAggregateEvent(MAPPER.readTree(TntEventJson.write(sample(TntAggregateType.DELIVERY)))))
            .isTrue();
        assertThat(TntAggregateEvent.isAggregateEvent(MAPPER.readTree(
            "{\"eventType\":\"MissionStatusChanged\",\"aggregateId\":\"x\",\"payload\":{}}"))).isFalse();
        assertThat(TntAggregateEvent.isAggregateEvent(null)).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    private static TntAggregateEvent.Builder<Map<String, Object>> base() {
        return TntAggregateEvent.<Map<String, Object>>builder(TntAggregateType.DELIVERY)
            .eventTypeFor("PICKED_UP").aggregateId(UUID.randomUUID()).tenantId(TENANT)
            .trigger("CONFIRM_PICKUP").actor(TntEventActor.of(TntEventActor.Type.COURIER, UUID.randomUUID()))
            .payload(Map.of("status", "PICKED_UP"));
    }

    static TntAggregateEvent<Map<String, Object>> sample(TntAggregateType type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("status", "SOME_STATE");
        payload.put("address", TntEventMasking.withDefaults().address("Bastos", "Yaoundé", "CM", 3.8666781, 11.5166742));
        return TntAggregateEvent.<Map<String, Object>>builder(type)
            .eventTypeFor("SOME_STATE")
            .aggregateId(UUID.randomUUID())
            .aggregateVersion(7)
            .tenantId(TENANT)
            .correlationId("req-42")
            .causationId(UUID.randomUUID())
            .trigger("SOME_TRIGGER")
            .actor(TntEventActor.of(TntEventActor.Type.COURIER, UUID.randomUUID()))
            .previousState(TntAggregateEvent.previousState("PREVIOUS_STATE", Map.of()))
            .payload(payload)
            .details(Map.of("reason", "TEST"))
            .build();
    }
}
