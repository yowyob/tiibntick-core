package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.CustodyHolderType;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelReturnCause;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.ParcelCustody;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.ParcelEntity;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;

/**
 * Manual mapper for {@code Parcel} ↔ {@code ParcelEntity}.
 *
 * @author MANFOUO Braun
 */
public final class ParcelPersistenceMapper {

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /** JSON array of UUIDs (no Jackson needed for this fixed shape). */
    static String writeIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }
        return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    static List<UUID> readIds(String json) {
        List<UUID> ids = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return ids;
        }
        for (String part : json.replace("[", "").replace("]", "").split(",")) {
            String trimmed = part.trim().replace("\"", "");
            if (!trimmed.isEmpty()) {
                ids.add(UUID.fromString(trimmed));
            }
        }
        return ids;
    }

    private ParcelPersistenceMapper() {}

    public static ParcelEntity toEntity(Parcel p) {
        if (p == null) return null;
        PackageSpecification spec = p.getSpecification();
        return ParcelEntity.builder()
                .id(p.getId())
                .weightKg(spec.weightKg())
                .widthCm(spec.widthCm())
                .heightCm(spec.heightCm())
                .lengthCm(spec.lengthCm())
                .fragile(spec.fragile())
                .perishable(spec.perishable())
                .description(spec.description())
                .designation(spec.designation())
                .thicknessCm(spec.thicknessCm())
                .photoUrl(p.getPhotoUrl())
                .trackingCode(p.getTrackingCode())
                .status(name(p.getStatus()))
                .statusChangedAt(p.getStatusChangedAt())
                .previousStatusBeforeDispute(name(p.getPreviousStatusBeforeDispute()))
                .openDisputeId(p.getOpenDisputeId())
                .disputeFreezesDelivery(p.isDisputeFreezesDelivery())
                .currentDeliveryId(p.getCurrentDeliveryId())
                .deliveryIds(writeIds(p.getDeliveryIds()))
                .custodyHolderType(p.getCustody() != null ? p.getCustody().holderType().name() : null)
                .custodyHolderId(p.getCustody() != null ? p.getCustody().holderId() : null)
                .custodyHubId(p.getCustody() != null ? p.getCustody().hubId() : null)
                .custodyHubPackageEntryId(p.getCustody() != null ? p.getCustody().hubPackageEntryId() : null)
                .returnCause(name(p.getReturnCause()))
                .returnCostPayer(name(p.getReturnCostPayer()))
                .closedReason(p.getClosedReason())
                .failedReturnCount(p.getFailedReturnCount())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .version(p.getVersion())
                .build();
    }

    public static Parcel toDomain(ParcelEntity e) {
        if (e == null) return null;
        PackageSpecification spec = new PackageSpecification(
                e.getWeightKg(), e.getWidthCm(), e.getHeightCm(), e.getLengthCm(),
                Boolean.TRUE.equals(e.getFragile()), Boolean.TRUE.equals(e.getPerishable()),
                e.getDescription(), e.getDesignation(), e.getThicknessCm());
        return Parcel.builder()
                .id(e.getId())
                .specification(spec)
                .photoUrl(e.getPhotoUrl())
                .trackingCode(e.getTrackingCode())
                .status(e.getStatus() != null ? ParcelStatus.valueOf(e.getStatus()) : ParcelStatus.CREATED)
                .statusChangedAt(e.getStatusChangedAt())
                .previousStatusBeforeDispute(e.getPreviousStatusBeforeDispute() != null
                        ? ParcelStatus.valueOf(e.getPreviousStatusBeforeDispute()) : null)
                .openDisputeId(e.getOpenDisputeId())
                .disputeFreezesDelivery(Boolean.TRUE.equals(e.getDisputeFreezesDelivery()))
                .currentDeliveryId(e.getCurrentDeliveryId())
                .deliveryIds(readIds(e.getDeliveryIds()))
                .custody(e.getCustodyHolderType() != null
                        ? new ParcelCustody(CustodyHolderType.valueOf(e.getCustodyHolderType()), e.getCustodyHolderId(),
                                e.getCustodyHubId(), e.getCustodyHubPackageEntryId())
                        : ParcelCustody.none())
                .returnCause(e.getReturnCause() != null ? ParcelReturnCause.valueOf(e.getReturnCause()) : null)
                .returnCostPayer(e.getReturnCostPayer() != null
                        ? ResponsibleParty.valueOf(e.getReturnCostPayer()) : null)
                .closedReason(e.getClosedReason())
                .failedReturnCount(e.getFailedReturnCount() != null ? e.getFailedReturnCount() : 0)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .version(e.getVersion())
                .build();
    }
}
