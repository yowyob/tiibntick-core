package com.yowyob.tiibntick.core.delivery.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
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
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementOutcome;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DisputeParcelOutcome;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RelayDeposit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every parcel, delivery and announcement event built by the publisher matches the JSON Schemas of spec §3.6
 * (envelope, payload of its aggregate, and {@code details} when the event type has a details schema), and no
 * personal data of the fixtures reaches the record (masking, §3.4).
 */
class AggregateEventSchemaTest {

    private static final ObjectMapper MAPPER = TntEventJson.mapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final SchemaValidatorsConfig CONFIG = SchemaValidatorsConfig.builder()
            .formatAssertionsEnabled(true).build();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();

    private final KafkaDeliveryEventPublisher publisher = new KafkaDeliveryEventPublisher(null, null,
            new StaticListableBeanFactory().getBeanProvider(TntEventMasking.class));

    @Test
    void everyProducedEventMatchesItsSchemas() {
        List<AggregateEvent> events = new ArrayList<>();
        events.addAll(announcementLifecycle());
        events.addAll(relayStorageExpiry());
        events.addAll(failedAttemptsThenFailure());
        events.addAll(incidentHandoverThenDelivered());
        events.addAll(cancelledBeforePickup());
        events.addAll(parcelLostInDispute());
        events.addAll(returnCancelledBeforePickup());
        events.addAll(alertsRaisedAndCleared());
        List<AggregateEvent> underDelay = deliveryContinuesUnderNonFreezingDispute();
        assertThat(underDelay).filteredOn(e -> "ParcelCustodyChanged".equals(e.eventType()))
                .anySatisfy(e -> assertThat(e.details()).containsKeys("from", "to", "movement"));
        events.addAll(underDelay);

        Set<String> types = new LinkedHashSet<>();
        Set<String> withDetails = new LinkedHashSet<>();
        for (AggregateEvent event : events) {
            DomainEventEnvelope envelope = publisher.toAggregateEnvelope(event);
            String json = envelope.getPayload();
            JsonNode record = read(json);
            assertValid(TntEventSchemas.ENVELOPE, record, event);
            assertValid(TntEventSchemas.payload(event.aggregateType()), record.path("payload"), event);
            String details = TntEventSchemas.details(event.eventType());
            if (getClass().getClassLoader().getResource(details) != null) {
                assertValid(details, record.path("details"), event);
                withDetails.add(event.eventType());
            }
            assertThat(json).as(event.eventType())
                    .doesNotContain("Marché Mokolo", "Carrefour Bastos", "+23769", "Dupont", "3.8712", "11.5163");
            assertThat(event.tenantId()).as("%s has a tenant (D-150)", event.eventType()).isNotNull();
            types.add(event.eventType());
        }

        assertThat(types).contains("DeliveryCreated", "DeliveryAssigned", "DeliveryPickedUp", "DeliveryInTransit",
                "DeliveryAtRelayHub", "DeliveryOutForDelivery", "DeliveryAttemptFailed", "DeliveryDelivered",
                "DeliveryFailed", "DeliveryCancelled", "DeliveryIncidentReported", "DeliveryPausedByIncident",
                "DeliveryIncidentHandover", "DeliveryAlertRaised", "DeliveryAlertCleared", "DeliveryEtaUpdated",
                "ParcelCreated", "ParcelAssigned", "ParcelPickedUp", "ParcelInTransit", "ParcelAtRelayHub",
                "ParcelOutForDelivery", "ParcelDelivered", "ParcelDeliveryFailed", "ParcelReturnRequested",
                "ParcelUnderDispute", "ParcelLost", "ParcelCustodyChanged", "ParcelReturnDeliveryCancelled",
                "AnnouncementDraft", "AnnouncementPublished", "AnnouncementInNegotiation",
                "AnnouncementResponseReceived", "AnnouncementResponseWithdrawn", "AnnouncementAssigned",
                "AnnouncementCompleted");
        assertThat(withDetails).contains("DeliveryAttemptFailed", "DeliveryFailed", "DeliveryCancelled",
                "DeliveryAlertRaised", "DeliveryAlertCleared", "DeliveryEtaUpdated", "ParcelReturnRequested",
                "ParcelCustodyChanged",
                "ParcelLost", "ParcelReturnDeliveryCancelled", "AnnouncementResponseReceived",
                "AnnouncementResponseWithdrawn");
    }

    @Test
    void schemasRejectAnUnknownStateAndAnUnmaskedAddress() {
        Delivery delivery = newDelivery(newParcel());
        JsonNode record = read(publisher.toAggregateEnvelope(delivery.getAggregateEvents().get(0)).getPayload());
        ((com.fasterxml.jackson.databind.node.ObjectNode) record.path("payload")).put("status", "TIMED_OUT");
        ((com.fasterxml.jackson.databind.node.ObjectNode) record.path("payload").path("pickupAddress"))
                .put("street", "Rue 1");
        List<String> errors = errors(TntEventSchemas.payload(TntAggregateType.DELIVERY), record.path("payload"));
        assertThat(errors).anyMatch(e -> e.contains("status")).anyMatch(e -> e.contains("pickupAddress"));
    }

    // ── scenarios ─────────────────────────────────────────────────────────────────────────────

    private List<AggregateEvent> announcementLifecycle() {
        DeliveryAnnouncement a = DeliveryAnnouncement.createDraft(TENANT, SENDER, "Colis", "Documents",
                BigDecimal.valueOf(3000), "XAF", newParcel(), pickup(), destination(), recipient(),
                DeliveryUrgency.STANDARD);
        a.publish();
        AnnouncementResponse first = AnnouncementResponse.create(a.getId(), COURIER, Instant.now(), "ok");
        AnnouncementResponse second = AnnouncementResponse.create(a.getId(), UUID.randomUUID(), Instant.now(), "");
        a.addResponse(first);
        a.addResponse(second);
        a.withdrawResponse(second.getId());
        a.selectResponse(first.getId());
        a.linkDelivery(UUID.randomUUID());
        a.complete(AnnouncementOutcome.DELIVERED);
        return aggregate(a.getDomainEvents().stream());
    }

    private List<AggregateEvent> relayStorageExpiry() {
        Parcel parcel = newParcel();
        Delivery d = newDelivery(parcel);
        toInTransit(d);
        d.updateEta(EtaEstimate.of(Instant.now().plusSeconds(600), 2.0, 10));
        Instant now = Instant.now();
        d.depositAtRelayHub(new RelayDeposit(UUID.randomUUID(), UUID.randomUUID(), now, true, 5, 3,
                now.plusSeconds(3 * 86_400), now.plusSeconds(2 * 86_400)));
        d.raiseStorageReminder(now.plusSeconds(2 * 86_400));
        d.expireRelayStorage();
        return all(d, parcel);
    }

    private List<AggregateEvent> failedAttemptsThenFailure() {
        Parcel parcel = newParcel();
        Delivery d = newDelivery(parcel);
        toInTransit(d);
        d.depositAtRelayHub(RelayDeposit.at(UUID.randomUUID()));
        d.startFinalLeg(EtaEstimate.of(Instant.now().plusSeconds(600), 2.0, 10), 2);
        d.recordFailedAttempt(DeliveryFailureReason.CLIENT_ABSENT_AT_DELIVERY, 2);
        d.startFinalLeg(EtaEstimate.of(Instant.now().plusSeconds(600), 2.0, 10), 2);
        d.recordFailedAttempt(DeliveryFailureReason.CLIENT_PHONE_UNREACHABLE, 2);
        return all(d, parcel);
    }

    private List<AggregateEvent> incidentHandoverThenDelivered() {
        Parcel parcel = newParcel();
        Delivery d = newDelivery(parcel);
        toInTransit(d);
        UUID incident = UUID.randomUUID();
        d.markIncidentReported(incident);
        d.pauseForIncident(incident);
        d.startHandover();
        d.resumeAfterIncident("HANDOVER_COMPLETED", UUID.randomUUID(), null);
        d.complete(null);
        return all(d, parcel);
    }

    private List<AggregateEvent> cancelledBeforePickup() {
        Parcel parcel = newParcel();
        Delivery d = newDelivery(parcel);
        d.assignDeliveryPerson(COURIER, null, 3.0, null);
        d.cancel("plus besoin");
        return all(d, parcel);
    }

    private List<AggregateEvent> parcelLostInDispute() {
        Parcel parcel = newParcel();
        Delivery d = newDelivery(parcel);
        toInTransit(d);
        UUID dispute = UUID.randomUUID();
        parcel.openDispute(dispute, true, true, TntEventActor.of(TntEventActor.Type.SENDER, SENDER), null, TENANT);
        parcel.applyDisputeOutcome(DisputeParcelOutcome.LOST, "DISPUTE_RESOLVED", "15000",
                TntEventActor.system(), null, TENANT);
        d.failForParcelLoss(DeliveryFailureReason.PARCEL_LOST, ResponsibleParty.COURIER);
        return all(d, parcel);
    }

    private List<AggregateEvent> deliveryContinuesUnderNonFreezingDispute() {
        Parcel parcel = newParcel();
        Delivery d = newDelivery(parcel);
        toInTransit(d);
        parcel.openDispute(UUID.randomUUID(), false, true, TntEventActor.of(TntEventActor.Type.SENDER, SENDER),
                null, TENANT);
        d.depositAtRelayHub(RelayDeposit.at(UUID.randomUUID()));
        return all(d, parcel);
    }

    private List<AggregateEvent> returnCancelledBeforePickup() {
        Parcel parcel = Parcel.create(new PackageSpecification(1, 10, 10, 10, false, false, "Box"));
        Delivery outbound = newDelivery(parcel);
        outbound.assignDeliveryPerson(COURIER, null, 3.0, null);
        outbound.confirmPickup();
        outbound.cancel("annulée");
        Delivery ret = Delivery.create(TENANT, null, SENDER, parcel, destination(), pickup(), recipient(),
                DeliveryUrgency.STANDARD, null, null, com.yowyob.tiibntick.core.delivery.domain.model.enums
                        .DeliveryKind.RETURN, outbound.getId(), 3);
        ret.assignDeliveryPerson(COURIER, null, 3.0, null);
        ret.cancel("plus de livreur");
        List<AggregateEvent> out = new ArrayList<>(outbound.getAggregateEvents());
        out.addAll(all(ret, parcel));
        return out;
    }

    /** Lot L3: watch alerts (D.2) — raised with observed value and threshold, then cleared. */
    private List<AggregateEvent> alertsRaisedAndCleared() {
        Parcel parcel = newParcel();
        Delivery d = newDelivery(parcel);
        toInTransit(d);
        Instant now = Instant.now();
        d.raiseAlert(com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryAlertType.GPS_SIGNAL_LOST,
                now.minusSeconds(900), java.time.Duration.ofMinutes(10), now);
        d.raiseAlert(com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryAlertType.DELIVERY_LATE,
                now, now.minusSeconds(60), now);
        d.clearAlert(com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryAlertType.GPS_SIGNAL_LOST, now);
        return all(d, parcel);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    private static Parcel newParcel() {
        return Parcel.create(new PackageSpecification(2.5, 30, 20, 15, false, false, "Documents"), TENANT, SENDER,
                TntEventActor.of(TntEventActor.Type.SENDER, SENDER));
    }

    private static Delivery newDelivery(Parcel parcel) {
        return Delivery.create(TENANT, null, SENDER, parcel, pickup(), destination(), recipient(),
                DeliveryUrgency.STANDARD, null, null);
    }

    private static void toInTransit(Delivery d) {
        d.assignDeliveryPerson(COURIER, null, 3.0, null);
        d.confirmPickup();
        d.startTransit(EtaEstimate.of(Instant.now().plusSeconds(900), 3.0, 15));
    }

    private static List<AggregateEvent> all(Delivery d, Parcel parcel) {
        List<AggregateEvent> out = new ArrayList<>(d.getAggregateEvents());
        out.addAll(aggregate(parcel.getDomainEvents().stream()));
        return out;
    }

    private static List<AggregateEvent> aggregate(Stream<?> events) {
        return events.filter(AggregateEvent.class::isInstance).map(AggregateEvent.class::cast).toList();
    }

    private static DeliveryAddress pickup() {
        return new DeliveryAddress(null, "Marché Mokolo", "Mokolo", "Yaoundé", "CM",
                new GeoCoordinates(3.87123, 11.51634));
    }

    private static DeliveryAddress destination() {
        return new DeliveryAddress(null, "Carrefour Bastos", "Bastos", "Yaoundé", "CM",
                new GeoCoordinates(3.88001, 11.52502));
    }

    private static RecipientInfo recipient() {
        return new RecipientInfo("Jean Dupont", "+237690000001", null);
    }

    private void assertValid(String schema, JsonNode node, AggregateEvent event) {
        assertThat(errors(schema, node)).as("%s %s against %s", event.eventType(), event.row(), schema).isEmpty();
    }

    private static List<String> errors(String schemaResource, JsonNode node) {
        JsonSchema schema = FACTORY.getSchema(readResource(schemaResource), CONFIG);
        return schema.validate(node).stream().map(ValidationMessage::getMessage).toList();
    }

    private static JsonNode readResource(String resource) {
        try (InputStream in = AggregateEventSchemaTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("schema not on classpath: " + resource);
            }
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
