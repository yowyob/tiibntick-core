package com.yowyob.tiibntick.core.agency.sync.application.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Strict Agency offline push contracts: exact {@link OfflineOpType} names and JSON payloads.
 *
 * <h2>Supported types and payloads</h2>
 * <ul>
 *   <li>{@code MISSION_STATUS_UPDATE} (pickup) —
 *       {@code {"delivererId":"<uuid>","action":"PICKUP?"}} —
 *       optional {@code action} must be {@code PICKUP} when present</li>
 *   <li>{@code DELIVERY_CONFIRMATION} —
 *       {@code {"delivererId":"<uuid>","proofReference":"<string?>"}}</li>
 *   <li>{@code HUB_DEPOSIT} —
 *       {@code {"delivererId":"<uuid>","hubId":"<uuid>","trackingCode":"<string?>"}}</li>
 *   <li>{@code ANOMALY_REPORT} —
 *       {@code {"delivererId":"<uuid>","anomalyType":"<string>","description":"<string?>","fatal":&lt;boolean&gt;}}</li>
 *   <li>{@code GPS_UPDATE} —
 *       {@code {"delivererId":"<uuid>","latitude":n,"longitude":n,"accuracyMeters":n?,
 *       "speedKmh":n?,"bearing":n?,"missionId":"<uuid>?","timestamp":"<iso>"}}</li>
 * </ul>
 *
 * <p>Mission ops require {@code aggregateType=MISSION}; GPS requires {@code aggregateType=GPS}
 * with {@code aggregateId} equal to the deliverer id.</p>
 */
public final class AgencyOfflineOpContracts {

    public static final String AGGREGATE_MISSION = "MISSION";
    public static final String AGGREGATE_GPS = "GPS";

    public static final Set<OfflineOpType> MISSION_TYPES = EnumSet.of(
            OfflineOpType.MISSION_STATUS_UPDATE,
            OfflineOpType.DELIVERY_CONFIRMATION,
            OfflineOpType.HUB_DEPOSIT,
            OfflineOpType.ANOMALY_REPORT
    );

    public static final Set<OfflineOpType> SUPPORTED_TYPES = EnumSet.of(
            OfflineOpType.MISSION_STATUS_UPDATE,
            OfflineOpType.DELIVERY_CONFIRMATION,
            OfflineOpType.HUB_DEPOSIT,
            OfflineOpType.ANOMALY_REPORT,
            OfflineOpType.GPS_UPDATE
    );

    private AgencyOfflineOpContracts() {
    }

    public static OfflineOpType requireExactType(String type) {
        if (type == null || type.isBlank()) {
            throw new TntValidationException("Offline operation type is required");
        }
        for (OfflineOpType candidate : OfflineOpType.values()) {
            if (candidate.name().equals(type)) {
                if (!SUPPORTED_TYPES.contains(candidate)) {
                    throw new TntValidationException(
                            "Unsupported Agency offline operation type: " + type
                                    + " (supported: " + SUPPORTED_TYPES + ")");
                }
                return candidate;
            }
        }
        throw new TntValidationException(
                "Unknown offline operation type '" + type
                        + "'; must be an exact OfflineOpType enum name");
    }

    public static void requireMissionAggregate(String aggregateType, String aggregateId) {
        if (!AGGREGATE_MISSION.equals(aggregateType)) {
            throw new TntValidationException(
                    "Agency mission offline ops require aggregateType=MISSION, got: " + aggregateType);
        }
        parseUuid(aggregateId, "aggregateId");
    }

    public static void requireGpsAggregate(String aggregateType, String aggregateId) {
        if (!AGGREGATE_GPS.equals(aggregateType)) {
            throw new TntValidationException(
                    "Agency GPS offline ops require aggregateType=GPS, got: " + aggregateType);
        }
        parseUuid(aggregateId, "aggregateId");
    }

    public static boolean isGpsType(OfflineOpType type) {
        return type == OfflineOpType.GPS_UPDATE;
    }

    public static ParsedMissionPayload parseAndValidatePayload(
            OfflineOpType type, String payloadJson, ObjectMapper objectMapper) {
        if (type == OfflineOpType.GPS_UPDATE) {
            throw new TntValidationException("Use parseAndValidateGpsPayload for GPS_UPDATE");
        }
        JsonNode root = readObject(payloadJson, objectMapper);
        UUID delivererId = requireUuid(root, "delivererId");
        return switch (type) {
            case MISSION_STATUS_UPDATE -> new ParsedMissionPayload(delivererId, null, null, null, null, false);
            case DELIVERY_CONFIRMATION -> new ParsedMissionPayload(
                    delivererId,
                    optionalText(root, "proofReference"),
                    null, null, null, false);
            case HUB_DEPOSIT -> new ParsedMissionPayload(
                    delivererId,
                    optionalText(root, "trackingCode"),
                    requireUuid(root, "hubId"),
                    null, null, false);
            case ANOMALY_REPORT -> {
                String anomalyType = requireText(root, "anomalyType");
                boolean fatal = requireBoolean(root, "fatal");
                yield new ParsedMissionPayload(
                        delivererId,
                        null,
                        null,
                        anomalyType,
                        optionalText(root, "description"),
                        fatal);
            }
            default -> throw new TntValidationException("Unsupported Agency offline type: " + type);
        };
    }

    public static ParsedGpsPayload parseAndValidateGpsPayload(String payloadJson, ObjectMapper objectMapper) {
        JsonNode root = readObject(payloadJson, objectMapper);
        UUID delivererId = requireUuid(root, "delivererId");
        double latitude = requireDouble(root, "latitude");
        double longitude = requireDouble(root, "longitude");
        double accuracyMeters = optionalDouble(root, "accuracyMeters", 0);
        double speedKmh = optionalDouble(root, "speedKmh", 0);
        double bearing = optionalDouble(root, "bearing", 0);
        UUID missionId = optionalUuid(root, "missionId");
        LocalDateTime timestamp = requireTimestamp(root, "timestamp");
        return new ParsedGpsPayload(
                delivererId, missionId, latitude, longitude, accuracyMeters, speedKmh, bearing, timestamp);
    }

    private static JsonNode readObject(String payloadJson, ObjectMapper objectMapper) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new TntValidationException("Offline operation payload is required");
        }
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            if (root == null || !root.isObject()) {
                throw new TntValidationException("Offline operation payload must be a JSON object");
            }
            return root;
        } catch (TntValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new TntValidationException("Invalid offline operation payload JSON: " + e.getMessage());
        }
    }

    private static UUID requireUuid(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual() && !node.isIntegralNumber()) {
            throw new TntValidationException("payload." + field + " is required (UUID)");
        }
        return parseUuid(node.asText(), "payload." + field);
    }

    private static UUID optionalUuid(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual() && !node.isIntegralNumber()) {
            throw new TntValidationException("payload." + field + " must be a UUID when present");
        }
        String raw = node.asText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseUuid(raw, "payload." + field);
    }

    private static String requireText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            throw new TntValidationException("payload." + field + " is required (non-blank string)");
        }
        return node.asText();
    }

    private static String optionalText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new TntValidationException("payload." + field + " must be a string when present");
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    private static boolean requireBoolean(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isBoolean()) {
            throw new TntValidationException("payload." + field + " is required (boolean)");
        }
        return node.asBoolean();
    }

    private static double requireDouble(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            throw new TntValidationException("payload." + field + " is required (number)");
        }
        return node.asDouble();
    }

    private static double optionalDouble(JsonNode root, String field, double defaultValue) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (!node.isNumber()) {
            throw new TntValidationException("payload." + field + " must be a number when present");
        }
        return node.asDouble();
    }

    private static LocalDateTime requireTimestamp(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            throw new TntValidationException("payload." + field + " is required (ISO timestamp)");
        }
        if (node.isNumber()) {
            long epochMs = node.asLong();
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC);
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new TntValidationException("payload." + field + " must be an ISO timestamp string");
        }
        String raw = node.asText().trim();
        try {
            return LocalDateTime.ofInstant(Instant.parse(raw), ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(raw);
            } catch (DateTimeParseException e) {
                throw new TntValidationException("payload." + field + " must be a valid ISO timestamp: " + raw);
            }
        }
    }

    public static UUID parseUuid(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new TntValidationException(field + " is required (UUID)");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new TntValidationException(field + " must be a valid UUID: " + raw);
        }
    }

    public record ParsedMissionPayload(
            UUID delivererId,
            String proofOrTracking,
            UUID hubId,
            String anomalyType,
            String description,
            boolean fatal
    ) {
    }

    public record ParsedGpsPayload(
            UUID delivererId,
            UUID missionId,
            double latitude,
            double longitude,
            double accuracyMeters,
            double speedKmh,
            double bearing,
            LocalDateTime timestamp
    ) {
    }
}
