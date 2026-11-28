package com.yowyob.tiibntick.core.delivery.adapter.out.messaging;

import com.yowyob.tiibntick.common.kafka.TntEventMasking;
import com.yowyob.tiibntick.common.kafka.TntEventMasking.MaskedAddress;
import com.yowyob.tiibntick.core.delivery.domain.event.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliverySnapshot;
import com.yowyob.tiibntick.core.delivery.domain.event.ParcelSnapshot;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.ParcelCustody;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the published {@code payload} of the parcel, delivery and announcement events from the unmasked snapshots
 * (spec §3.6 schemas, field by field masking §3.4 [G.3]): no name (display name only), no phone, no e-mail, no street
 * or landmark, rounded coordinates, amounts as decimal strings, null fields present.
 */
public final class AggregateEventPayloads {

    private AggregateEventPayloads() {
    }

    /** {@code delivery.payload} (spec §3.6). */
    public static Map<String, Object> delivery(DeliverySnapshot s, TntEventMasking masking) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("deliveryId", str(s.deliveryId()));
        p.put("parcelId", str(s.parcelId()));
        p.put("kind", name(s.kind()));
        p.put("parentDeliveryId", str(s.parentDeliveryId()));
        p.put("status", name(s.status()));
        p.put("previousStatusBeforeIncident", name(s.previousStatusBeforeIncident()));
        p.put("announcementId", str(s.announcementId()));
        p.put("agencyId", str(s.agencyId()));
        p.put("platform", s.platform());
        p.put("senderId", str(s.senderId()));
        p.put("senderDisplayName", null);
        p.put("recipientActorId", null);
        p.put("recipientDisplayName", s.recipient() != null ? TntEventMasking.displayName(s.recipient().name()) : null);
        p.put("courierId", str(s.courierId()));
        p.put("vehicleId", s.vehicleId());
        p.put("freelancerOrgId", s.freelancerOrgId());
        p.put("pickupAddress", address(s.pickupAddress(), masking));
        p.put("deliveryAddress", address(s.deliveryAddress(), masking));
        p.put("relayHubId", str(s.relayHubId()));
        p.put("pickupWindow", window(s.pickupWindowStart(), s.pickupWindowEnd()));
        p.put("deliveryWindow", window(s.deliveryWindowStart(), s.deliveryWindowEnd()));
        p.put("eta", time(s.eta()));
        p.put("attemptCount", s.attemptCount());
        p.put("maxAttempts", s.maxAttempts());
        p.put("failureReason", name(s.failureReason()));
        p.put("lastAttemptReason", name(s.lastAttemptReason()));
        p.put("cancellationReason", s.cancellationReason());
        p.put("returnCostPayer", name(s.returnCostPayer()));
        p.put("late", s.late());
        p.put("lateSince", time(s.lateSince()));
        p.put("activeAlerts", s.activeAlerts() == null ? List.of() : s.activeAlerts());
        p.put("openIncidentId", str(s.openIncidentId()));
        p.put("frozenByDispute", s.frozenByDispute());
        p.put("agreedAmount", TntEventMasking.amount(s.agreedAmount()));
        p.put("currency", s.currency());
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("mode", "NONE");   // escrow / cash state: lot L5
        payment.put("status", null);
        p.put("payment", payment);
        p.put("relayStorageExpiresAt", time(s.relayStorageExpiresAt()));
        p.put("createdAt", time(s.createdAt()));
        p.put("updatedAt", time(s.updatedAt()));
        p.put("deliveredAt", time(s.deliveredAt()));
        p.put("failedAt", time(s.failedAt()));
        p.put("cancelledAt", time(s.cancelledAt()));
        return p;
    }

    /** {@code parcel.payload} (spec §3.6). */
    public static Map<String, Object> parcel(ParcelSnapshot s) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("parcelId", str(s.parcelId()));
        p.put("trackingCode", s.trackingCode());
        p.put("status", name(s.status()));
        p.put("previousStatusBeforeDispute", name(s.previousStatusBeforeDispute()));
        p.put("openDisputeId", str(s.openDisputeId()));
        p.put("currentDeliveryId", str(s.currentDeliveryId()));
        List<String> ids = new ArrayList<>();
        s.deliveryIds().forEach(id -> ids.add(id.toString()));
        p.put("deliveryIds", ids);
        p.put("custody", custody(s.custody()));
        p.put("specification", specification(s.specification()));
        p.put("hasPhoto", s.hasPhoto());
        p.put("returnCause", s.returnCause());
        p.put("returnCostPayer", name(s.returnCostPayer()));
        p.put("closedReason", s.closedReason());
        p.put("failedReturnCount", s.failedReturnCount());
        p.put("createdAt", time(s.createdAt()));
        p.put("updatedAt", time(s.updatedAt()));
        p.put("statusChangedAt", time(s.statusChangedAt()));
        return p;
    }

    /** {@code announcement.payload} (spec §3.6). */
    public static Map<String, Object> announcement(AnnouncementSnapshot s, TntEventMasking masking) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("announcementId", str(s.announcementId()));
        p.put("kind", name(s.kind()));
        p.put("creatorId", str(s.creatorId()));
        p.put("creatorRole", name(s.creatorRole()));
        p.put("parcelId", str(s.parcelId()));
        p.put("status", name(s.status()));
        p.put("pricingMode", name(s.pricingMode()));
        p.put("proposedPrice", TntEventMasking.amount(s.proposedPrice()));
        p.put("currency", s.currency());
        p.put("pickupAddress", address(s.pickupAddress(), masking));
        p.put("deliveryAddress", address(s.deliveryAddress(), masking));
        p.put("relayHubId", str(s.relayHubId()));
        p.put("pickupWindow", null);
        p.put("deliveryWindow", null);
        p.put("requestedStorageDays", null);
        List<Map<String, Object>> responses = new ArrayList<>();
        for (AnnouncementSnapshot.Response r : s.responses()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("responseId", str(r.responseId()));
            m.put("courierId", str(r.courierId()));
            m.put("price", TntEventMasking.amount(r.price()));
            m.put("status", name(r.status()));
            m.put("createdAt", time(r.createdAt()));
            responses.add(m);
        }
        p.put("responses", responses);
        p.put("selectedResponseId", str(s.selectedResponseId()));
        p.put("deliveryId", str(s.deliveryId()));
        p.put("outcome", name(s.outcome()));
        p.put("publishedAt", time(s.publishedAt()));
        p.put("assignedAt", time(s.assignedAt()));
        p.put("closedAt", time(s.closedAt()));
        return p;
    }

    /** Detail or previous-state values: custody records become maps, enums names, the rest as is. */
    public static Object plain(Object value) {
        if (value instanceof ParcelCustody c) {
            return custody(c);
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof UUID || value instanceof Instant) {
            return value.toString();
        }
        return value;
    }

    /** @return a copy of {@code map} with {@link #plain(Object)} values */
    public static Map<String, Object> plain(Map<String, Object> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(k, plain(v)));
        return out;
    }

    private static MaskedAddress address(DeliveryAddress a, TntEventMasking masking) {
        if (a == null) {
            return masking.address(null, null, null, (Double) null, null);
        }
        return a.coordinates() == null
                ? masking.address(a.district(), a.city(), a.country(), (Double) null, null)
                : masking.address(a.district(), a.city(), a.country(), a.coordinates().latitude(),
                a.coordinates().longitude());
    }

    private static Map<String, Object> custody(ParcelCustody c) {
        if (c == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("holderType", c.holderType().name());
        m.put("holderId", str(c.holderId()));
        m.put("hubId", str(c.hubId()));
        m.put("hubPackageEntryId", str(c.hubPackageEntryId()));
        return m;
    }

    private static Map<String, Object> specification(PackageSpecification spec) {
        if (spec == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("weightKg", spec.weightKg());
        Map<String, Object> dims = new LinkedHashMap<>();
        dims.put("l", spec.lengthCm());
        dims.put("w", spec.widthCm());
        dims.put("h", spec.heightCm());
        m.put("dimensionsCm", dims);
        m.put("category", spec.designation());
        m.put("fragile", spec.fragile());
        m.put("declaredValue", null);
        return m;
    }

    private static Map<String, Object> window(Instant start, Instant end) {
        if (start == null && end == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("start", time(start));
        m.put("end", time(end));
        return m;
    }

    private static String str(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static String time(Instant t) {
        return t == null ? null : t.toString();
    }
}
