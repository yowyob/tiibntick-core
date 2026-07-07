package com.yowyob.tiibntick.core.incident.adapter.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.incident.application.command.ReportIncidentCommand;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.port.inbound.IReportIncidentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka listener auto-detecting incidents from GPS anomalies, geofence triggers and SLA breaches.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Slf4j
@Component("incidentIncidentEventConsumer")
@RequiredArgsConstructor
public class IncidentEventConsumer {

    private final IReportIncidentUseCase reportIncidentUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "tnt.delivery.mission.status.changed",
            groupId = "tnt-incident-core",
            containerFactory = "incidentKafkaListenerContainerFactory"
    )
    /**
     * Consumes mission status change events and auto-creates SLA breach incidents
     * when the mission transitions to TIMED_OUT or SLA_BREACHED.
     *
     * @param record the Kafka consumer record from tnt.delivery.mission.status.changed
     */
    public void onMissionStatusChanged(ConsumerRecord<String, String> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String status = payload.path("newStatus").asText();

            if ("TIMED_OUT".equals(status) || "SLA_BREACHED".equals(status)) {
                UUID missionId = UUID.fromString(payload.path("missionId").asText());
                UUID tenantId = UUID.fromString(payload.path("tenantId").asText());
                UUID agencyId = UUID.fromString(payload.path("agencyId").asText());
                String platformStr = payload.path("platform").asText("AGENCY");

                // : Extract FreelancerOrg context from the event payload
                String freelancerOrgId = null;
                String responsibleOrgType = "AGENCY";
                if (!payload.path("freelancerOrgId").isMissingNode()) {
                    freelancerOrgId = payload.path("freelancerOrgId").asText(null);
                    if (freelancerOrgId != null && !freelancerOrgId.isBlank()) {
                        responsibleOrgType = "FREELANCER_ORG";
                    }
                }

                reportIncidentUseCase.execute(
                        ReportIncidentCommand.builder()
                                .tenantId(tenantId)
                                .agencyId(agencyId)
                                .missionId(missionId)
                                .platform(parsePlatform(platformStr))
                                .type(IncidentType.SLA_BREACH_TRAFFIC_DELAY)
                                .description("Automatic SLA breach detection from mission " + missionId)
                                .reportedByActorId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                                .reportedByRole(ActorRole.SYSTEM)
                                .responsibleOrgId(freelancerOrgId)
                                .responsibleOrgType(freelancerOrgId != null ? responsibleOrgType : null)
                                .build()
                ).subscribe(
                        inc -> log.info("Auto-created SLA incident {} for mission {}", inc.getReferenceCode(), missionId),
                        err -> log.error("Failed to auto-create incident for mission {}: {}", missionId, err.getMessage())
                );
            }
        } catch (Exception e) {
            log.error("Error processing mission status changed event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = "tnt.realtime.gps.position.updated",
            groupId = "tnt-incident-core",
            containerFactory = "incidentKafkaListenerContainerFactory"
    )
    /**
     * Consumes GPS position updates and auto-creates incidents when trajectory
     * anomalies or prolonged suspicious stops are detected.
     *
     * @param record the Kafka consumer record from tnt.realtime.gps.position.updated
     */
    public void onGpsPositionUpdated(ConsumerRecord<String, String> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            boolean anomaly = payload.path("trajectoryAnomaly").asBoolean(false);
            boolean prolongedStop = payload.path("prolongedStop").asBoolean(false);

            if (anomaly || prolongedStop) {
                UUID missionId = UUID.fromString(payload.path("missionId").asText());
                UUID tenantId = UUID.fromString(payload.path("tenantId").asText());
                UUID agencyId = UUID.fromString(payload.path("agencyId").asText());
                UUID driverId = UUID.fromString(payload.path("driverId").asText());
                IncidentType type = anomaly
                        ? IncidentType.DRIVER_TRAJECTORY_DEVIATION
                        : IncidentType.DRIVER_PROLONGED_STOP_SUSPECT;

                reportIncidentUseCase.execute(
                        ReportIncidentCommand.builder()
                                .tenantId(tenantId).agencyId(agencyId)
                                .missionId(missionId)
                                .platform(PlatformType.AGENCY)
                                .type(type)
                                .description("GPS anomaly detected: " + (anomaly ? "trajectory deviation" : "prolonged stop"))
                                .reportedByActorId(driverId)
                                .reportedByRole(ActorRole.SYSTEM)
                                .currentLat(payload.path("lat").asDouble())
                                .currentLng(payload.path("lng").asDouble())
                                .build()
                ).subscribe(
                        inc -> log.info("Auto-created GPS anomaly incident {} for mission {}", inc.getReferenceCode(), missionId),
                        err -> log.error("Failed to auto-create GPS incident: {}", err.getMessage())
                );
            }
        } catch (Exception e) {
            log.error("Error processing GPS position event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = "tnt.realtime.geofence.triggered",
            groupId = "tnt-incident-core",
            containerFactory = "incidentKafkaListenerContainerFactory"
    )
    /**
     * Consumes geofence trigger events and auto-creates incidents when a driver
     * enters a danger or restricted zone.
     *
     * @param record the Kafka consumer record from tnt.realtime.geofence.triggered
     */
    public void onGeofenceTriggered(ConsumerRecord<String, String> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String zoneType = payload.path("zoneType").asText("");

            if ("DANGER_ZONE".equals(zoneType) || "RESTRICTED_ZONE".equals(zoneType)) {
                UUID missionId = UUID.fromString(payload.path("missionId").asText());
                UUID tenantId = UUID.fromString(payload.path("tenantId").asText());
                UUID agencyId = UUID.fromString(payload.path("agencyId").asText());
                UUID driverId = UUID.fromString(payload.path("driverId").asText());

                reportIncidentUseCase.execute(
                        ReportIncidentCommand.builder()
                                .tenantId(tenantId).agencyId(agencyId)
                                .missionId(missionId).platform(PlatformType.AGENCY)
                                .type(IncidentType.DRIVER_REFUSED_DANGEROUS_ZONE)
                                .description("Geofence alert: driver entered " + zoneType)
                                .reportedByActorId(driverId)
                                .reportedByRole(ActorRole.SYSTEM)
                                .currentLat(payload.path("lat").asDouble())
                                .currentLng(payload.path("lng").asDouble())
                                .build()
                ).subscribe(
                        inc -> log.info("Auto-created geofence incident {} for mission {}", inc.getReferenceCode(), missionId),
                        err -> log.error("Failed to auto-create geofence incident: {}", err.getMessage())
                );
            }
        } catch (Exception e) {
            log.error("Error processing geofence event: {}", e.getMessage(), e);
        }
    }

    private PlatformType parsePlatform(String platform) {
        try { return PlatformType.valueOf(platform); }
        catch (Exception e) { return PlatformType.AGENCY; }
    }
}
