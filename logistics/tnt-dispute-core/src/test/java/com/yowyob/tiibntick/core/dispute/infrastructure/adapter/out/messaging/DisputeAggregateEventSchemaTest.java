package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.common.kafka.TntEventJson;
import com.yowyob.tiibntick.common.kafka.TntEventSchemas;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.dispute.application.command.EscalateDisputeCommand;
import com.yowyob.tiibntick.core.dispute.domain.enums.ClosureType;
import com.yowyob.tiibntick.core.dispute.domain.enums.ResolutionType;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.yowyob.tiibntick.core.dispute.support.DisputeFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4: every dispute event built for {@code tnt.dispute.events} matches the envelope, the dispute payload and its
 * details schema (spec §3.6), is keyed by the dispute, carries consecutive aggregate versions across saves and leaks
 * neither the description nor the ruling summary (§3.4).
 */
class DisputeAggregateEventSchemaTest {

    private static final ObjectMapper MAPPER = TntEventJson.mapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG = SchemaValidatorsConfig.builder()
            .formatAssertionsEnabled(true).build();

    private final DisputeAggregateEventOutboxPublisher publisher = new DisputeAggregateEventOutboxPublisher(null);

    @Test
    @DisplayName("all transitions produce schema-valid, masked, ordered dispute events")
    void eventsMatchSchemas() throws IOException {
        List<Dispute> saves = new ArrayList<>();
        Dispute d = opened();
        saves.add(d);
        Set<String> types = new LinkedHashSet<>();
        Set<String> withDetails = new LinkedHashSet<>();
        long lastVersion = 0;
        // one "save" per step: the events of the step, then cleared as the repository does
        Runnable[] steps = {
                () -> d.assignMediator(MEDIATOR),
                () -> d.requestAdditionalEvidence(CLAIMANT, inTwoDays()),
                () -> d.addEvidence(evidence(d, CLAIMANT)),
                () -> d.addEvidence(evidence(d, CLAIMANT)),
                () -> d.startMediation(),
                () -> d.escalate(new EscalateDisputeCommand(d.getId(), TENANT, MEDIATOR, "désaccord", null)),
                () -> d.rule(rule(d, ResolutionType.TOTAL_LOSS_COMPENSATED, new BigDecimal("15000.50"))),
                () -> d.processCompensation("KRN-PAY-1")};
        lastVersion = check(d, types, withDetails, lastVersion);
        for (Runnable step : steps) {
            step.run();
            lastVersion = check(d, types, withDetails, lastVersion);
        }
        for (Dispute other : List.of(withdrawn(), expired(), administrativelyClosed(), dismissed())) {
            check(other, types, withDetails, 0);
        }

        assertThat(types).containsExactlyInAnyOrder("DisputeOpen", "DisputeUnderInvestigation",
                "DisputeAwaitingEvidence", "DisputeEvidenceAdded", "DisputeMediationInProgress",
                "DisputePendingArbitration", "DisputePendingCompensation", "DisputeCompensated",
                "DisputeClosedWithdrawn", "DisputeClosedExpired", "DisputeClosedResolved");
        assertThat(withDetails).contains("DisputeOpen", "DisputeAwaitingEvidence", "DisputeEvidenceAdded",
                "DisputePendingArbitration", "DisputePendingCompensation", "DisputeCompensated",
                "DisputeClosedResolved");
    }

    private Dispute withdrawn() {
        Dispute d = opened();
        d.withdraw(CLAIMANT);
        return d;
    }

    private Dispute expired() {
        Dispute d = opened();
        d.expire();
        return d;
    }

    private Dispute administrativelyClosed() {
        Dispute d = opened();
        d.close(close(d, ClosureType.ADMINISTRATIVE_CLOSURE, "doublon"));
        return d;
    }

    private Dispute dismissed() {
        Dispute d = inMediation();
        d.rule(rule(d, ResolutionType.COMPLAINT_DISMISSED, null));
        return d;
    }

    private long check(Dispute d, Set<String> types, Set<String> withDetails, long lastVersion) throws IOException {
        for (DomainEventEnvelope envelope : publisher.envelopes(d)) {
            JsonNode record = MAPPER.readTree(envelope.getPayload());
            String type = record.path("eventType").asText();
            assertValid(TntEventSchemas.ENVELOPE, record, type);
            assertValid(TntEventSchemas.payload(TntAggregateType.DISPUTE), record.path("payload"), type);
            String details = TntEventSchemas.details(type);
            if (getClass().getClassLoader().getResource(details) != null) {
                assertValid(details, record.path("details"), type);
                withDetails.add(type);
            }
            assertThat(envelope.getKafkaTopic()).isEqualTo(TntTopics.DISPUTE_EVENTS);
            assertThat(envelope.getAggregateId()).isEqualTo(d.getId().getValue());
            assertThat(record.path("aggregateVersion").asLong()).as(type).isEqualTo(lastVersion + 1);
            lastVersion = record.path("aggregateVersion").asLong();
            assertThat(envelope.getPayload()).doesNotContain("Dupont", "+237690000000", "décision");
            types.add(type);
        }
        d.clearPendingTransitions();
        return lastVersion;
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
