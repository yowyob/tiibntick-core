package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryEntity;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.FreelancerRole;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;

/**
 * Manual mapper between {@code Delivery} domain aggregate and {@code DeliveryEntity}.
 * Manual mapping is preferred here to keep full control over complex value objects
 * and avoid implicit Lombok/MapStruct conflicts with record types.
 *
 * @author MANFOUO Braun
 */
public final class DeliveryPersistenceMapper {

    private DeliveryPersistenceMapper() {}

    public static DeliveryEntity toEntity(Delivery delivery, Parcel parcel) {
        DeliveryAddress pickup  = delivery.getPickupAddress();
        DeliveryAddress dest    = delivery.getDeliveryAddress();
        RecipientInfo recip   = delivery.getRecipient();
        DeliveryCost estCost = delivery.getEstimatedCost();
        EtaEstimate eta     = delivery.getCurrentEta();

        return DeliveryEntity.builder()
                .id(delivery.getId())
                .tenantId(delivery.getTenantId())
                .announcementId(delivery.getAnnouncementId())
                .parcelId(parcel != null ? parcel.getId() : null)
                .senderId(delivery.getSenderId())
                .deliveryPersonId(delivery.getDeliveryPersonId())
                .status(delivery.getStatus().name())
                .urgency(delivery.getUrgency().name())
                // Pickup
                .pickupStreet(pickup.street())
                .pickupLandmark(pickup.landmark())
                .pickupDistrict(pickup.district())
                .pickupCity(pickup.city())
                .pickupCountry(pickup.country())
                .pickupLatitude(pickup.coordinates() != null ? pickup.coordinates().latitude() : null)
                .pickupLongitude(pickup.coordinates() != null ? pickup.coordinates().longitude() : null)
                // Delivery
                .deliveryStreet(dest.street())
                .deliveryLandmark(dest.landmark())
                .deliveryDistrict(dest.district())
                .deliveryCity(dest.city())
                .deliveryCountry(dest.country())
                .deliveryLatitude(dest.coordinates() != null ? dest.coordinates().latitude() : null)
                .deliveryLongitude(dest.coordinates() != null ? dest.coordinates().longitude() : null)
                // Recipient
                .recipientName(recip.name())
                .recipientPhone(recip.phoneNumber())
                .recipientAltPhone(recip.alternativePhone())
                // Cost
                .estimatedCostDistance(estCost != null ? estCost.distanceCost() : null)
                .estimatedCostTime(estCost != null ? estCost.timeCost() : null)
                .estimatedCostRoad(estCost != null ? estCost.roadPenibilityCost() : null)
                .estimatedCostWeather(estCost != null ? estCost.weatherRiskCost() : null)
                .estimatedCostFuel(estCost != null ? estCost.fuelCost() : null)
                .costCurrency(estCost != null ? estCost.currency() : null)
                .finalCostTotal(delivery.getFinalCost() != null ? delivery.getFinalCost().total() : null)
                .estimatedDistanceKm(delivery.getEstimatedDistanceKm())
                // ETA
                .etaEstimatedArrival(eta != null ? eta.estimatedArrival() : null)
                .etaLowerBound(eta != null ? eta.lowerBound() : null)
                .etaUpperBound(eta != null ? eta.upperBound() : null)
                .etaConfidence(eta != null ? eta.confidenceScore() : null)
                .etaRemainingMinutes(eta != null ? eta.remainingMinutes() : null)
                // Temporal
                .scheduledPickupTime(delivery.getScheduledPickupTime())
                .estimatedDeliveryTime(delivery.getEstimatedDeliveryTime())
                .actualPickupTime(delivery.getActualPickupTime())
                .actualDeliveryTime(delivery.getActualDeliveryTime())
                .pausedByIncidentId(delivery.getPausedByIncidentId())
                .previousStatusBeforePause(
                        delivery.getPreviousStatusBeforePause() != null
                        ? delivery.getPreviousStatusBeforePause().name() : null)
                .platform(delivery.getPlatform() != null ? delivery.getPlatform() : "AGENCY")
                .agencyId(delivery.getAgencyId())
                //  FreelancerOrg fields
                .assignedFreelancerOrgId(delivery.getAssignedFreelancerOrgId())
                .assignedFreelancerRole(delivery.getAssignedFreelancerRole() != null
                        ? delivery.getAssignedFreelancerRole().name() : null)
                .selectedVehicleId(delivery.getSelectedVehicleId())
                .activeEquipmentIdsJson(serializeList(delivery.getActiveEquipmentIds()))
                .deliveryAttemptNumber(delivery.getDeliveryAttemptNumber())
                .requiresRefrigeration(delivery.isRequiresRefrigeration())
                .requiresAssembly(delivery.isRequiresAssembly())
                .requiresIDCheck(delivery.isRequiresIDCheck())
                .notes(delivery.getNotes())
                .createdAt(delivery.getCreatedAt())
                .updatedAt(delivery.getUpdatedAt())
                .version(delivery.getVersion())
                .build();
    }

    public static Delivery toDomain(DeliveryEntity e, Parcel parcel) {
        GeoCoordinates pickupCoords = coords(e.getPickupLatitude(), e.getPickupLongitude());
        GeoCoordinates destCoords   = coords(e.getDeliveryLatitude(), e.getDeliveryLongitude());

        DeliveryAddress pickup = new DeliveryAddress(e.getPickupStreet(), e.getPickupLandmark(),
                e.getPickupDistrict(), e.getPickupCity(), e.getPickupCountry(), pickupCoords);
        DeliveryAddress dest   = new DeliveryAddress(e.getDeliveryStreet(), e.getDeliveryLandmark(),
                e.getDeliveryDistrict(), e.getDeliveryCity(), e.getDeliveryCountry(), destCoords);
        RecipientInfo   recip  = new RecipientInfo(e.getRecipientName(), e.getRecipientPhone(),
                e.getRecipientAltPhone());

        DeliveryCost estCost = null;
        if (e.getEstimatedCostDistance() != null) {
            estCost = DeliveryCost.ofXaf(
                    e.getEstimatedCostDistance(),
                    nvl(e.getEstimatedCostTime()),
                    nvl(e.getEstimatedCostRoad()),
                    nvl(e.getEstimatedCostWeather()),
                    nvl(e.getEstimatedCostFuel()));
        }

        EtaEstimate eta = null;
        if (e.getEtaEstimatedArrival() != null) {
            eta = new EtaEstimate(e.getEtaEstimatedArrival(), e.getEtaLowerBound(),
                    e.getEtaUpperBound(),
                    e.getEtaConfidence() != null ? e.getEtaConfidence() : 0.80,
                    e.getEstimatedDistanceKm() != null ? e.getEstimatedDistanceKm() : 0,
                    e.getEtaRemainingMinutes() != null ? e.getEtaRemainingMinutes() : 0);
        }

        return Delivery.builder()
                .id(e.getId())
                .tenantId(e.getTenantId())
                .announcementId(e.getAnnouncementId())
                .parcel(parcel)
                .senderId(e.getSenderId())
                .deliveryPersonId(e.getDeliveryPersonId())
                .pickupAddress(pickup)
                .deliveryAddress(dest)
                .recipient(recip)
                .status(DeliveryStatus.valueOf(e.getStatus()))
                .urgency(DeliveryUrgency.valueOf(e.getUrgency()))
                .estimatedCost(estCost)
                .estimatedDistanceKm(e.getEstimatedDistanceKm() != null ? e.getEstimatedDistanceKm() : 0.0)
                .currentEta(eta)
                .scheduledPickupTime(e.getScheduledPickupTime())
                .estimatedDeliveryTime(e.getEstimatedDeliveryTime())
                .actualPickupTime(e.getActualPickupTime())
                .actualDeliveryTime(e.getActualDeliveryTime())
                .pausedByIncidentId(e.getPausedByIncidentId())
                .previousStatusBeforePause(
                        e.getPreviousStatusBeforePause() != null
                        ? com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus
                            .valueOf(e.getPreviousStatusBeforePause()) : null)
                .platform(e.getPlatform() != null ? e.getPlatform() : "AGENCY")
                .agencyId(e.getAgencyId())
                //  FreelancerOrg fields
                .assignedFreelancerOrgId(e.getAssignedFreelancerOrgId())
                .assignedFreelancerRole(e.getAssignedFreelancerRole() != null
                        ? FreelancerRole.valueOf(e.getAssignedFreelancerRole()) : null)
                .selectedVehicleId(e.getSelectedVehicleId())
                .activeEquipmentIds(deserializeList(e.getActiveEquipmentIdsJson()))
                .deliveryAttemptNumber(e.getDeliveryAttemptNumber())
                .requiresRefrigeration(e.isRequiresRefrigeration())
                .requiresAssembly(e.isRequiresAssembly())
                .requiresIDCheck(e.isRequiresIDCheck())
                .notes(e.getNotes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .version(e.getVersion())
                .build();
    }

    private static GeoCoordinates coords(Double lat, Double lon) {
        if (lat == null || lon == null) return null;
        return new GeoCoordinates(lat, lon);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String serializeList(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try { return MAPPER.writeValueAsString(list); }
        catch (Exception e) { return "[]"; }
    }

    private static java.util.List<String> deserializeList(String json) {
        if (json == null || json.isBlank()) return new java.util.ArrayList<>();
        try { return MAPPER.readValue(json, new TypeReference<java.util.List<String>>() {}); }
        catch (Exception e) { return new java.util.ArrayList<>(); }
    }
}
