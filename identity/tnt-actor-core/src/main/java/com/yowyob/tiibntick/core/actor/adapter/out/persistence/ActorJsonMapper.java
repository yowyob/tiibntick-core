package com.yowyob.tiibntick.core.actor.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.actor.domain.model.AvailabilitySlot;
import com.yowyob.tiibntick.core.actor.domain.model.Badge;
import com.yowyob.tiibntick.core.actor.domain.model.ServiceZoneId;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ActorJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private ActorJsonMapper() {}

    static String badgesToJson(Set<Badge> badges) {
        if (badges == null || badges.isEmpty()) return "[]";
        try {
            List<Map<String, Object>> list = badges.stream().map(b -> Map.<String, Object>of(
                    "code", b.code(),
                    "label", b.label(),
                    "earnedAt", b.earnedAt().toString(),
                    "txHash", b.blockchainTxHash() != null ? b.blockchainTxHash() : ""
            )).toList();
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    static Set<Badge> badgesFromJson(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return Set.of();
        try {
            List<Map<String, Object>> list = MAPPER.readValue(json, new TypeReference<>() {});
            Set<Badge> badges = new LinkedHashSet<>();
            for (Map<String, Object> map : list) {
                String txHash = (String) map.get("txHash");
                badges.add(Badge.of(
                        (String) map.get("code"),
                        (String) map.get("label"),
                        Instant.parse((String) map.get("earnedAt")),
                        (txHash == null || txHash.isBlank()) ? null : txHash));
            }
            return Set.copyOf(badges);
        } catch (Exception e) {
            return Set.of();
        }
    }

    static String serviceZoneIdsToJson(List<ServiceZoneId> zones) {
        if (zones == null || zones.isEmpty()) return "[]";
        try {
            List<String> ids = zones.stream().map(z -> z.value().toString()).toList();
            return MAPPER.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    static List<ServiceZoneId> serviceZoneIdsFromJson(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
        try {
            List<String> ids = MAPPER.readValue(json, new TypeReference<>() {});
            return ids.stream().map(id -> ServiceZoneId.of(UUID.fromString(id))).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    static String availabilitySlotsToJson(List<AvailabilitySlot> slots) {
        if (slots == null || slots.isEmpty()) return "[]";
        try {
            List<Map<String, String>> list = slots.stream().map(s -> Map.of(
                    "day", s.dayOfWeek().name(),
                    "start", s.startTime().toString(),
                    "end", s.endTime().toString()
            )).toList();
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    static List<AvailabilitySlot> availabilitySlotsFromJson(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
        try {
            List<Map<String, String>> list = MAPPER.readValue(json, new TypeReference<>() {});
            List<AvailabilitySlot> slots = new ArrayList<>();
            for (Map<String, String> map : list) {
                slots.add(AvailabilitySlot.of(
                        DayOfWeek.valueOf(map.get("day")),
                        LocalTime.parse(map.get("start")),
                        LocalTime.parse(map.get("end"))));
            }
            return List.copyOf(slots);
        } catch (Exception e) {
            return List.of();
        }
    }

    static String associatedAgencyIdsToJson(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "[]";
        try {
            return MAPPER.writeValueAsString(ids.stream().map(UUID::toString).toList());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    static Set<UUID> associatedAgencyIdsFromJson(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return Set.of();
        try {
            List<String> list = MAPPER.readValue(json, new TypeReference<>() {});
            return list.stream().map(UUID::fromString).collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            return Set.of();
        }
    }

    static String favoriteAddressIdsToJson(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "[]";
        try {
            return MAPPER.writeValueAsString(ids.stream().map(UUID::toString).toList());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    static List<UUID> favoriteAddressIdsFromJson(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
        try {
            List<String> list = MAPPER.readValue(json, new TypeReference<>() {});
            return list.stream().map(UUID::fromString).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
