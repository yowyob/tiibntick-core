package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.application.port.in.ParcelUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RegisterParcelCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.ParcelPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.ParcelDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Registers and reads parcels through {@code tnt-delivery-core}'s {@link ParcelUseCase}. */
@Component
@RequiredArgsConstructor
public class ParcelPortAdapter implements ParcelPort {

    /** Neutral values applied to a missing weight / dimension, the ones the announcement path always used. */
    static final double DEFAULT_WEIGHT_KG = 1.0;
    static final double DEFAULT_DIMENSION_CM = 10.0;

    private final ParcelUseCase parcels;
    /** Lot evt-l2: tenant copied on ParcelCreated (an event without tenant cannot be published). */
    private final org.springframework.beans.factory.ObjectProvider<
            com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder> tenants;

    @Override
    public Mono<UUID> register(ParcelDetails details) {
        var holder = tenants.getIfAvailable();
        Mono<java.util.Optional<UUID>> tenant = holder == null ? Mono.just(java.util.Optional.empty())
                : holder.currentTenantId().map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty());
        return tenant.flatMap(t -> parcels.registerParcel(new RegisterParcelCommand(toSpecification(details),
                        details.photoUrl(), t.orElse(null), null)))
                .map(Parcel::getId);
    }

    @Override
    public Mono<ParcelDetails> find(UUID parcelId) {
        return parcels.findParcelById(parcelId).map(ParcelPortAdapter::toDetails);
    }

    static PackageSpecification toSpecification(ParcelDetails d) {
        return new PackageSpecification(
                positiveOr(d.weightKg(), DEFAULT_WEIGHT_KG),
                positiveOr(d.widthCm(), DEFAULT_DIMENSION_CM),
                positiveOr(d.heightCm(), DEFAULT_DIMENSION_CM),
                positiveOr(d.lengthCm(), DEFAULT_DIMENSION_CM),
                Boolean.TRUE.equals(d.fragile()),
                Boolean.TRUE.equals(d.perishable()),
                d.description(),
                blankToNull(d.designation()),
                d.thicknessCm() != null && d.thicknessCm() > 0 ? d.thicknessCm() : null);
    }

    static ParcelDetails toDetails(Parcel parcel) {
        PackageSpecification s = parcel.getSpecification();
        return new ParcelDetails(parcel.getId(), s.designation(), s.description(), s.weightKg(), s.widthCm(),
                s.heightCm(), s.lengthCm(), s.thicknessCm(), s.fragile(), s.perishable(), parcel.getPhotoUrl());
    }

    private static double positiveOr(Double value, double fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
