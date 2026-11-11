package com.yowyob.tiibntick.core.gofreelancer.adapter.in.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;

import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService.NewProof;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryProof.Kind;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Wire contract of the Go / Freelancer delivery person's offline queue.
 *
 * <p>An operation targets aggregate {@value #AGGREGATE_DELIVERY}, {@code aggregateId} is the delivery id and
 * {@code payload} is a JSON object. Status operations carry the same fields as
 * {@code PATCH /api/deliveries/{id}/status} ({@code status}, {@code confirmationCode}, {@code reason},
 * {@code relayPointId}, {@code clientId}, {@code storageFee}); the OTP typed offline is verified when the
 * queue is replayed, never trusted client side.</p>
 */
public final class GofpOfflineOpContracts {

    public static final String AGGREGATE_DELIVERY = "DELIVERY";

    /** Operations that move the delivery through its lifecycle. */
    public static final Set<OfflineOpType> STATUS_TYPES = EnumSet.of(
            OfflineOpType.MISSION_STATUS_UPDATE, OfflineOpType.DELIVERY_CONFIRMATION, OfflineOpType.HUB_DEPOSIT);

    /** Operations journaled for the owner only (no lifecycle change). */
    public static final Set<OfflineOpType> JOURNAL_TYPES = EnumSet.of(
            OfflineOpType.GPS_UPDATE, OfflineOpType.ANOMALY_REPORT);

    /** Targets a delivery person may request; the Core-only states (pause, timeout...) are refused. */
    private static final Set<DeliveryStatus> ALLOWED_TARGETS = EnumSet.of(
            DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.AT_RELAY_POINT,
            DeliveryStatus.DELIVERED, DeliveryStatus.FAILED, DeliveryStatus.CANCELLED);

    private GofpOfflineOpContracts() {
    }

    public static UUID parseUuid(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException e) {
            throw new TntValidationException("Invalid " + field + ": " + raw);
        }
    }

    /** Position of a status on the forward path, or -1 when it is not on it. */
    public static int forwardRank(DeliveryStatus status) {
        return switch (status) {
            case CREATED -> 0;
            case PICKED_UP -> 1;
            case IN_TRANSIT -> 2;
            case AT_RELAY_POINT -> 3;
            case DELIVERED -> 4;
            default -> -1;
        };
    }

    /** Decodes and validates a status operation's payload into the DTO the online endpoint takes. */
    public static DeliveryStatusUpdateDTO parseStatusUpdate(OfflineOpType type, String payload, ObjectMapper mapper) {
        JsonNode root = readObject(payload, mapper);
        DeliveryStatusUpdateDTO dto = new DeliveryStatusUpdateDTO();

        String rawStatus = text(root, "status");
        DeliveryStatus status;
        if (rawStatus == null) {
            status = switch (type) {
                case DELIVERY_CONFIRMATION -> DeliveryStatus.DELIVERED;
                case HUB_DEPOSIT -> DeliveryStatus.AT_RELAY_POINT;
                default -> throw new TntValidationException("Offline payload requires a status for " + type);
            };
        } else {
            try {
                status = DeliveryStatus.fromValue(rawStatus);
            } catch (IllegalArgumentException e) {
                throw new TntValidationException("Unknown delivery status: " + rawStatus);
            }
        }
        if (!ALLOWED_TARGETS.contains(status)) {
            throw new TntValidationException("A delivery person cannot set status " + status);
        }
        dto.setStatus(status);
        dto.setConfirmationCode(text(root, "confirmationCode"));
        dto.setReason(text(root, "reason"));
        String relay = text(root, "relayPointId");
        if (relay != null) dto.setRelayPointId(parseUuid(relay, "relayPointId"));
        String client = text(root, "clientId");
        if (client != null) dto.setClientId(parseUuid(client, "clientId"));
        if (root.hasNonNull("storageFee")) dto.setStorageFee(root.get("storageFee").asDouble(0.0));

        if (type == OfflineOpType.HUB_DEPOSIT && dto.getRelayPointId() == null) {
            throw new TntValidationException("HUB_DEPOSIT requires relayPointId");
        }
        return dto;
    }

    /**
     * Proofs captured on the device ({@code "proofs": [{kind, fileId, note?, capturedAt?}]}); the files were
     * uploaded to the evidence storage first, only their ids travel in the queue. {@code capturedAt} is an ISO
     * instant or epoch milliseconds.
     */
    public static List<NewProof> parseProofs(String payload, ObjectMapper mapper) {
        JsonNode list = readObject(payload, mapper).get("proofs");
        List<NewProof> out = new ArrayList<>();
        if (list == null || list.isNull()) {
            return out;
        }
        if (!list.isArray()) {
            throw new TntValidationException("proofs must be an array");
        }
        for (JsonNode n : list) {
            Kind kind;
            try {
                kind = Kind.valueOf(String.valueOf(text(n, "kind")).toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new TntValidationException("Unknown proof kind: " + text(n, "kind"));
            }
            String file = text(n, "fileId");
            if (file == null) {
                throw new TntValidationException("Proof fileId is required");
            }
            out.add(new NewProof(kind, parseUuid(file, "fileId"), text(n, "note"), capturedAt(n.get("capturedAt"))));
        }
        return out;
    }

    private static Instant capturedAt(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        try {
            return n.isNumber() ? Instant.ofEpochMilli(n.asLong()) : Instant.parse(n.asText().trim());
        } catch (RuntimeException e) {
            throw new TntValidationException("Invalid capturedAt: " + n.asText());
        }
    }

    private static JsonNode readObject(String payload, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new TntValidationException("Offline payload must be a JSON object");
            }
            return root;
        } catch (TntValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new TntValidationException("Invalid offline payload JSON: " + e.getMessage());
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root == null ? null : root.get(field);
        return n == null || n.isNull() || n.asText().isBlank() ? null : n.asText().trim();
    }
}
