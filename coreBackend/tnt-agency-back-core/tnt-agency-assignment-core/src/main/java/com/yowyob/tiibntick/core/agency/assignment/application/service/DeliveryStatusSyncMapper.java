package com.yowyob.tiibntick.core.agency.assignment.application.service;

import com.yowyob.tiibntick.core.agency.assignment.adapter.out.clients.DeliveryCorePort;
import com.yowyob.tiibntick.core.agency.assignment.domain.AgencyMission;
import com.yowyob.tiibntick.core.agency.assignment.domain.vo.MissionStatus;

import java.time.Instant;
import java.util.UUID;

public final class DeliveryStatusSyncMapper {

    private DeliveryStatusSyncMapper() {}

    public static MissionStatus toAgencyStatus(String coreStatus) {
        if (coreStatus == null || coreStatus.isBlank()) {
            return MissionStatus.PENDING;
        }
        return switch (coreStatus.trim().toUpperCase()) {
            case "CREATED" -> MissionStatus.PENDING;
            case "PICKED_UP" -> MissionStatus.ASSIGNED;
            case "IN_TRANSIT", "PAUSED_BY_INCIDENT", "TIMED_OUT", "SLA_BREACHED" -> MissionStatus.IN_TRANSIT;
            case "AT_RELAY_POINT" -> MissionStatus.AT_HUB;
            case "DELIVERED" -> MissionStatus.DELIVERED;
            case "FAILED" -> MissionStatus.FAILED;
            case "CANCELLED" -> MissionStatus.CANCELLED;
            default -> MissionStatus.PENDING;
        };
    }

    public static void applyCoreView(AgencyMission mission, DeliveryCorePort.DeliveryView view, Instant now) {
        applyCoreProjection(mission, toAgencyStatus(view.status()), view.actualPickupTime(),
                view.actualDeliveryTime(), view.deliveryPersonId(), now);
    }

    public static void applyCoreStatus(AgencyMission mission, String coreStatus, Instant now) {
        applyCoreProjection(mission, toAgencyStatus(coreStatus), null, null, null, now);
    }

    public static void applyCoreProjection(
            AgencyMission mission,
            MissionStatus status,
            Instant pickupTime,
            Instant deliveryTime,
            UUID deliveryPersonId,
            Instant now) {
        mission.applyCoreProjection(status, pickupTime, deliveryTime, deliveryPersonId, now);
    }
}
