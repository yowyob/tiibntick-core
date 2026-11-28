package com.yowyob.tiibntick.core.incident.adapter.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.common.kafka.TntEventJson;
import com.yowyob.tiibntick.common.kafka.TntEventMasking;
import com.yowyob.tiibntick.common.kafka.TntEventSchemas;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.valueobject.IncidentGeoSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L3: every incident event built for {@code tnt.incident.events} matches the envelope, the incident payload and
 * its details schema (spec §3.6), is keyed by the incident, carries strictly increasing aggregate versions, chains
 * its causation, and leaks neither the free-text description nor precise coordinates (§3.4).
 */
class IncidentAggregateEventSchemaTest {

    private static final ObjectMapper MAPPER = TntEventJson.mapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG = SchemaValidatorsConfig.builder()
            .formatAssertionsEnabled(true).build();
    private static final String SECRET_DESCRIPTION = "Le client M. Dupont au +237690000000 refuse";

    private final IncidentAggregateEventOutboxPublisher publisher = new IncidentAggregateEventOutboxPublisher(null,
            new StaticListableBeanFactory().getBeanProvider(TntEventMasking.class));

    @Test
    @DisplayName("a full lifecycle produces schema-valid, masked, ordered incident events")
    void lifecycleEventsMatchSchemas() throws IOException {
        UUID courier = UUID.randomUUID();
        Incident reported = Incident.create(null, UUID.randomUUID(), PlatformType.AGENCY, UUID.randomUUID(),
                IncidentCategory.CLIENT_RECIPIENT, IncidentType.CLIENT_REFUSED_RECEPTION, SECRET_DESCRIPTION, courier,
                ActorRole.AGENCY_DRIVER, List.of(UUID.randomUUID()));
        IncidentGeoSnapshot geo = IncidentGeoSnapshot.builder().incidentId(reported.getId())
                .latitude(3.871234).longitude(11.516321).capturedAt(Instant.now()).zoneRiskIndex(0.3).build();

        List<Incident> steps = new ArrayList<>();
        steps.add(reported);
        Incident i = clear(reported).acknowledge().triage(IncidentSeverity.MEDIUM, geo, null, true);
        steps.add(i);
        i = clear(i).pendingAgencyAssignment().startAgencyHandling()
                .recordHandlerAssigned(UUID.randomUUID(), TntEventActor.of(TntEventActor.Type.AGENCY, UUID.randomUUID()));
        steps.add(i);
        i = clear(i).proposeResolution(IncidentDeliveryOutcome.FAILED, IncidentResponsibleParty.RECIPIENT, "absent",
                TntEventActor.of(TntEventActor.Type.AGENCY, UUID.randomUUID()));
        steps.add(i);
        i = clear(i).contestResolution("still there", TntEventActor.of(TntEventActor.Type.COURIER, courier))
                .escalate(1, ActorRole.ADMIN_TIIBNTICK).recordEscalatedToDispute("transféré en litige")
                .resolve(ResolutionMode.MANUAL_ADMIN_OVERRIDE, IncidentDeliveryOutcome.DISPUTE, null,
                        "transféré en litige", "TRANSFERRED_TO_DISPUTE", TntEventActor.system());
        steps.add(i);
        i = clear(i).close();
        steps.add(i);
        Incident cancelled = clear(Incident.create(UUID.randomUUID(), null, PlatformType.GO, UUID.randomUUID(),
                IncidentCategory.GEOGRAPHIC, IncidentType.GEO_ROAD_FLOODED, "x", courier, ActorRole.GO_BIKER,
                List.of())).cancel("erreur", TntEventActor.of(TntEventActor.Type.COURIER, courier));

        Set<String> types = new LinkedHashSet<>();
        Set<String> withDetails = new LinkedHashSet<>();
        long lastVersion = 0;
        String lastEventId = null;
        for (Incident step : steps) {
            for (DomainEventEnvelope envelope : publisher.envelopes(step, step.getPendingTransitions())) {
                JsonNode record = MAPPER.readTree(envelope.getPayload());
                String type = record.path("eventType").asText();
                assertValid(TntEventSchemas.ENVELOPE, record, type);
                assertValid(TntEventSchemas.payload(TntAggregateType.INCIDENT), record.path("payload"), type);
                String details = TntEventSchemas.details(type);
                if (getClass().getClassLoader().getResource(details) != null) {
                    assertValid(details, record.path("details"), type);
                    withDetails.add(type);
                }
                assertThat(envelope.getKafkaTopic()).isEqualTo(TntTopics.INCIDENT_EVENTS);
                assertThat(record.path("aggregateId").asText()).isEqualTo(reported.getId().toString());
                assertThat(record.path("aggregateVersion").asLong()).as(type).isEqualTo(lastVersion + 1);
                lastVersion = record.path("aggregateVersion").asLong();
                if (lastEventId != null && !type.equals("IncidentReported")) {
                    // within one save the events are chained; across saves the causation restarts
                    assertThat(record.has("causationId")).isTrue();
                }
                lastEventId = record.path("eventId").asText();
                assertThat(record.path("tenantId").asText()).as("system tenant when none (D-150)").isNotBlank();
                assertThat(envelope.getPayload()).doesNotContain("Dupont", "+237690000000", "3.871234", "11.516321");
                types.add(type);
            }
        }
        for (DomainEventEnvelope envelope : publisher.envelopes(cancelled, cancelled.getPendingTransitions())) {
            JsonNode record = MAPPER.readTree(envelope.getPayload());
            assertValid(TntEventSchemas.payload(TntAggregateType.INCIDENT), record.path("payload"), "cancel");
            types.add(record.path("eventType").asText());
            if (record.path("eventType").asText().equals("IncidentCancelled")) {
                assertValid(TntEventSchemas.details("IncidentCancelled"), record.path("details"), "IncidentCancelled");
                withDetails.add("IncidentCancelled");
            }
        }

        assertThat(types).contains("IncidentReported", "IncidentAcknowledged", "IncidentTriaged",
                "IncidentPendingAgencyAssignment", "IncidentAgencyHandling", "IncidentHandlerAssigned",
                "IncidentPendingConfirmation", "IncidentEscalated", "IncidentEscalatedToDispute", "IncidentResolved",
                "IncidentClosed", "IncidentCancelled");
        assertThat(withDetails).contains("IncidentReported", "IncidentTriaged", "IncidentHandlerAssigned",
                "IncidentPendingConfirmation", "IncidentEscalated", "IncidentEscalatedToDispute", "IncidentResolved",
                "IncidentCancelled");
    }

    /** What the repository returns after a save: same state, no pending change. */
    private static Incident clear(Incident i) {
        return i.toBuilder().pendingTransitions(new ArrayList<>()).build();
    }

    private void assertValid(String schemaResource, JsonNode node, String type) {
        List<String> errors;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(schemaResource)) {
            assertThat(in).as("schema %s on the classpath", schemaResource).isNotNull();
            errors = FACTORY.getSchema(MAPPER.readTree(in), CONFIG).validate(node).stream()
                    .map(ValidationMessage::getMessage).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(errors).as("%s against %s", type, schemaResource).isEmpty();
    }
}
