package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.messaging;

import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.common.kafka.TntTopics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.command.ProcessCompensationCommand;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeSystemUseCase;
import com.yowyob.tiibntick.core.dispute.domain.enums.ClaimantType;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeCategory;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeCause;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputePriority;
import com.yowyob.tiibntick.core.dispute.domain.enums.RespondentType;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Kafka consumer for inbound events triggering dispute lifecycle operations.
 *
 * <p>Handles the following topics:</p>
 * <ul>
 *   <li>{@code tnt.billing.compensation.paid} — emitted by tnt-billing-wallet after
 *       a successful Mobile Money / wallet payout. Triggers {@code processCompensation}
 *       on the dispute aggregate to transition it to COMPENSATED state.</li>
 *   <li>{@code IncidentEscalatedToDispute} of {@code tnt.incident.events} (lot L8; formerly the dedicated topic
 *       {@code tnt.incident.escalated.to.dispute}) — emitted by tnt-incident-core when
 *       an incident (fraud, package loss, confirmed damage) is escalated to a formal
 *       dispute. <b>Automatically creates a new Dispute aggregate</b> with the relevant
 *       cause, category, and linked incident context. This implements the
 *       incident → dispute escalation bridge.</li>
 * </ul>
 *
 * <h3>IncidentEscalatedToDispute — Processing logic:</h3>
 * <p>When an incident is escalated to a dispute, the consumer:</p>
 * <ol>
 *   <li>Extracts the incident payload: {@code incidentId}, {@code missionId},
 *       {@code parcelIds}, {@code fraudReason}, {@code tenantId}, {@code agencyId}.</li>
 *   <li>Resolves the {@link DisputeCause} from the incident's fraud/cause reason.</li>
 *   <li>Creates a new {@link OpenDisputeCommand} with claimant type SYSTEM
 *       (auto-opened by the platform) and respondent type PLATFORM.</li>
 *   <li>Calls {@code IDisputeCommandUseCase.openDispute()} to persist the new dispute.</li>
 *   <li>The dispute reference is stored alongside the incident for cross-module traceability.</li>
 * </ol>
 *
 * @author MANFOUO Braun
 */
@Component
public class DisputeEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DisputeEventConsumer.class);

    /**
     * Chantier D · Audit n°6 · S14 / Audit n°1 · A3 — {@code .block()} below used to have
     * no timeout: a stuck downstream call (DB, Kafka producer for domain events, etc.)
     * would hang this Kafka consumer thread forever, stalling the whole partition. Bounded
     * instead, matching the same pattern {@code EntityChangedEventConsumer} (tnt-sync-core)
     * already uses for its own Kafka-listener-thread {@code .block()}.
     */
    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(30);

    /** Description prefix for auto-opened disputes from incident escalation. */
    private static final String AUTO_DISPUTE_DESCRIPTION_PREFIX =
            "[AUTO] Dispute automatically opened from incident escalation. ";

    private final IDisputeSystemUseCase commandUseCase;
    private final ObjectMapper objectMapper;

    public DisputeEventConsumer(
            IDisputeSystemUseCase commandUseCase,
            ObjectMapper objectMapper) {
        this.commandUseCase = commandUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * Kafka listener for inbound dispute-related events.
     * Acknowledgment is manual because the consumer factory disables auto-commit.
     */
    @KafkaListener(
            topics = {
                    TntTopics.BILLING_COMPENSATION_PAID,
                    // lot L8: IncidentEscalatedToDispute of the incident aggregate topic (was
                    // tnt.incident.escalated.to.dispute, removed)
                    TntTopics.INCIDENT_EVENTS
            },
            containerFactory = "disputeKafkaListenerContainerFactory"
    )
    public void listen(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.debug("Received Kafka message: topic={} key={} partition={} offset={}",
                record.topic(), record.key(), record.partition(), record.offset());

        // Lot L4 (C15): an error is no longer swallowed — it goes back to the container, whose error handler retries the
        // record and finally sends it to the DLQ (decision [G.8]); the offset is acknowledged only after success.
        JsonNode json;
        try {
            json = objectMapper.readTree(record.value());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unreadable record on " + record.topic() + ": " + e.getMessage(), e);
        }
        Mono<Void> processing = switch (record.topic()) {
            case TntTopics.BILLING_COMPENSATION_PAID -> handleCompensationPaid(json);
            case TntTopics.INCIDENT_EVENTS -> TntAggregateEvent.isAggregateEvent(json)
                    && ESCALATED_TO_DISPUTE.equals(json.path("eventType").asText())
                    ? handleIncidentEscalatedToDispute(fromIncidentEvent(json)) : Mono.<Void>empty();
            default -> {
                log.debug("Ignoring unhandled topic: {}", record.topic());
                yield Mono.<Void>empty();
            }
        };
        ReactiveListenerSupport.awaitBounded(processing.doOnError(e -> log.error(
                "Failed to process record from topic={}: {}", record.topic(), e.getMessage())), PROCESSING_TIMEOUT);
        acknowledgment.acknowledge();
    }

    static final String ESCALATED_TO_DISPUTE = "IncidentEscalatedToDispute";

    /**
     * Lot L8: the fields of the former {@code tnt.incident.escalated.to.dispute} record, read from
     * {@code IncidentEscalatedToDispute} (payload = incident snapshot, {@code details.fraudReason}).
     */
    static JsonNode fromIncidentEvent(JsonNode event) {
        JsonNode p = event.path("payload");
        com.fasterxml.jackson.databind.node.ObjectNode legacy =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        legacy.put("incidentId", event.path("aggregateId").asText(null));
        legacy.put("missionId", p.path("deliveryId").asText(null));
        legacy.put("tenantId", event.path("tenantId").asText(null));
        legacy.put("agencyId", p.path("agencyId").asText(null));
        legacy.set("parcelIds", p.path("affectedParcelIds").isArray() ? p.path("affectedParcelIds")
                : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode());
        JsonNode details = event.path("details");
        String reason = details.hasNonNull("fraudReason") ? details.path("fraudReason").asText()
                : details.path("reason").asText(null);
        if (reason != null) {
            legacy.put("fraudReason", reason);
        }
        legacy.put("incidentType", p.path("type").asText(""));
        legacy.put("trackingCode", p.path("trackingCode").asText(null));
        return legacy;
    }

    // ── Handler: tnt.billing.compensation.paid ────────────────────────────────

    /**
     * Handles the {@code tnt.billing.compensation.paid} event from tnt-billing-wallet.
     * Marks the dispute compensation as paid and transitions it to COMPENSATED state.
     *
     * @param payload the parsed event JSON
     * @return Mono completing when the dispute is updated
     */
    private Mono<Void> handleCompensationPaid(JsonNode payload) {
        String reason = payload.path("reason").asText();
        if ("RETURN_COST".equals(reason) || "COURIER_REPLACEMENT".equals(reason)) {
            // lot L6: a return paid by the compensation fund (QO-22); QO-11 / D-153: a replacement courier paid by
            // the fund — neither is a dispute's indemnity
            return Mono.empty();
        }
        String disputeId       = payload.path("disputeId").asText();
        String tenantId        = payload.path("tenantId").asText();
        String paymentReference = payload.path("paymentReference").asText();

        if (disputeId.isBlank() || tenantId.isBlank() || paymentReference.isBlank()) {
            log.warn("Incomplete compensation.paid event: disputeId={} paymentRef={}",
                    disputeId, paymentReference);
            return Mono.empty();
        }

        log.info("Processing compensation payment confirmation: disputeId={} paymentRef={}",
                disputeId, paymentReference);

        return commandUseCase.confirmCompensationPaid(
                new ProcessCompensationCommand(
                        DisputeId.of(disputeId), tenantId, paymentReference, "BILLING_SYSTEM"))
                .then();
    }

    // ── Handler: tnt.incident.escalated.to.dispute ────────────────────────────

    /**
     * Handles the {@code tnt.incident.escalated.to.dispute} event from tnt-incident-core.
     *
     * <p>Automatically opens a new Dispute aggregate when the incident engine determines
     * that an incident requires formal dispute resolution (fraud confirmed, package
     * definitively lost, major damage validated).</p>
     *
     * <p>Expected payload fields:</p>
     * <pre>
     * {
     *   "incidentId":   "uuid",
     *   "missionId":    "uuid",
     *   "tenantId":     "uuid",
     *   "agencyId":     "uuid",          (nullable)
     *   "parcelIds":    ["uuid", ...],   (nullable)
     *   "fraudReason":  "human-readable cause string",
     *   "incidentType": "DRIVER_FRAUD | VEHICLE | PARCEL_CARGO | ..."
     *   "trackingCode": "TNT-YYYYMMDD-XXXXXXXX"  (nullable — absent for incidents reported
     *                                              before tnt-incident-core carried it)
     * }
     * </pre>
     *
     * @param payload the parsed event JSON
     * @return Mono completing when the dispute is opened
     */
    private Mono<Void> handleIncidentEscalatedToDispute(JsonNode payload) {
        String incidentId  = payload.path("incidentId").asText(null);
        String missionId   = payload.path("missionId").asText(null);
        String tenantId    = payload.path("tenantId").asText(null);
        String agencyId    = payload.path("agencyId").asText(null);
        String fraudReason = payload.path("fraudReason").asText("Incident escalated to dispute");
        String incidentType = payload.path("incidentType").asText("");
        String trackingCode = payload.path("trackingCode").asText(null);
        // lot L4: the first affected parcel, so that the parcel goes UNDER_DISPUTE (P12); else resolved from missionId
        JsonNode parcelIds = payload.path("parcelIds");
        String parcelId = parcelIds.isArray() && !parcelIds.isEmpty() ? parcelIds.get(0).asText(null) : null;

        if (tenantId == null || tenantId.isBlank()) {
            log.warn("IncidentEscalatedToDispute event missing tenantId — skipping auto-dispute creation");
            return Mono.empty();
        }

        log.info("Auto-opening dispute from incident escalation: incidentId={} missionId={} tenant={}",
                incidentId, missionId, tenantId);

        // Resolve dispute cause from the incident type and fraud reason
        DisputeCause cause    = resolveDisputeCause(incidentType, fraudReason);
        DisputeCategory category = resolveDisputeCategory(incidentType);
        DisputePriority priority = resolvePriority(incidentType);

        // Build a rich description linking the dispute back to the incident
        String description = AUTO_DISPUTE_DESCRIPTION_PREFIX
                + "IncidentId=" + (incidentId != null ? incidentId : "N/A")
                + " | Mission=" + (missionId != null ? missionId : "N/A")
                + " | Agency=" + (agencyId != null ? agencyId : "N/A")
                + " | IncidentType=" + incidentType
                + " | Reason: " + fraudReason;

        // Respondent: the agency responsible for the mission (or PLATFORM if no agency)
        String respondentId = (agencyId != null && !agencyId.isBlank()) ? agencyId : "PLATFORM";
        RespondentType respondentType = (agencyId != null && !agencyId.isBlank())
                ? RespondentType.AGENCY
                : RespondentType.PLATFORM;

        OpenDisputeCommand command = new OpenDisputeCommand(
                tenantId,
                "SYSTEM",          // claimantId: platform auto-opens on behalf of affected parties
                ClaimantType.SYSTEM,
                respondentId,
                respondentType,
                cause,
                category,
                priority,
                missionId,
                parcelId,          // lot L4: first affected parcel, else the parcel of missionId
                trackingCode,
                description,
                (respondentType == RespondentType.AGENCY) ? agencyId : null, // respondentOrgId
                null,              // impliedSubDelivererId
                Boolean.FALSE,     // subDelivererInvolved
                incidentId         // lot L4: openedFromIncidentId of DisputeOpen
        );

        return commandUseCase.openFromIncident(command)
                .doOnNext(dispute -> log.info(
                        "Auto-dispute created: disputeId={} incidentId={} cause={} priority={}",
                        dispute.getId(), incidentId, cause, priority))
                .doOnError(ex -> log.error(
                        "Failed to auto-open dispute for incidentId={}: {}",
                        incidentId, ex.getMessage()))
                .then();
    }

    // ── Resolution helpers ────────────────────────────────────────────────────

    /**
     * Resolves the {@link DisputeCause} from the incident type and fraud reason string.
     *
     * <p>Mapping rules:</p>
     * <ul>
     *   <li>FRAUD keywords → {@link DisputeCause#FRAUD}</li>
     *   <li>PARCEL_CARGO loss keywords → {@link DisputeCause#PACKAGE_LOST}</li>
     *   <li>PARCEL_CARGO damage keywords → {@link DisputeCause#PACKAGE_DAMAGED}</li>
     *   <li>Driver incidents → {@link DisputeCause#NON_DELIVERY}</li>
     *   <li>HUB incidents → {@link DisputeCause#HUB_INCIDENT}</li>
     *   <li>Fallback → {@link DisputeCause#OTHER}</li>
     * </ul>
     *
     * @param incidentType the incident type string from the escalation payload
     * @param fraudReason  the human-readable fraud reason
     * @return the appropriate DisputeCause
     */
    private DisputeCause resolveDisputeCause(String incidentType, String fraudReason) {
        String combined = (incidentType + " " + fraudReason).toUpperCase();

        if (combined.contains("FRAUD") || combined.contains("SPOOFING") || combined.contains("MULTI_ACCOUNT")) {
            return DisputeCause.FRAUD;
        }
        if (combined.contains("LOST") || combined.contains("PERTE")) {
            return DisputeCause.PACKAGE_LOST;
        }
        if (combined.contains("DAMAGE") || combined.contains("ENDOMMAGE") || combined.contains("BROKEN")) {
            return DisputeCause.PACKAGE_DAMAGED;
        }
        if (combined.contains("NON_DELIVERY") || combined.contains("ABANDON") || combined.contains("WITHDRAWAL")) {
            return DisputeCause.NON_DELIVERY;
        }
        if (combined.contains("HUB") || combined.contains("RELAY_POINT")) {
            return DisputeCause.HUB_INCIDENT;
        }
        if (combined.contains("NETWORK") || combined.contains("LINK")) {
            return DisputeCause.NETWORK_INCIDENT;
        }
        if (combined.contains("DELAY") || combined.contains("SLA")) {
            return DisputeCause.DELIVERY_DELAYED;
        }
        return DisputeCause.OTHER;
    }

    /**
     * Resolves the {@link DisputeCategory} from the incident type string.
     *
     * @param incidentType the incident type string
     * @return the appropriate DisputeCategory
     */
    private DisputeCategory resolveDisputeCategory(String incidentType) {
        String type = incidentType.toUpperCase();
        if (type.startsWith("GO_") || type.contains("FREELANCER")) {
            return DisputeCategory.MISSION_GO;
        }
        if (type.startsWith("AGENCY_") || type.contains("DRIVER") || type.contains("VEHICLE")) {
            return DisputeCategory.MISSION_AGENCY;
        }
        if (type.startsWith("RELAY_POINT") || type.contains("HUB")) {
            return DisputeCategory.HUB_POINT;
        }
        if (type.contains("NETWORK") || type.contains("LINK")) {
            return DisputeCategory.NETWORK_LINK;
        }
        return DisputeCategory.MISSION_AGENCY; // default for unknown incident types
    }

    /**
     * Resolves the {@link DisputePriority} from the incident type.
     * Fraud incidents always get CRITICAL priority; others are HIGH by default.
     *
     * @param incidentType the incident type string
     * @return the appropriate DisputePriority
     */
    private DisputePriority resolvePriority(String incidentType) {
        String type = incidentType.toUpperCase();
        if (type.contains("FRAUD") || type.contains("SPOOFING") || type.contains("MULTI_ACCOUNT")) {
            return DisputePriority.CRITICAL;
        }
        return DisputePriority.HIGH;
    }
}
