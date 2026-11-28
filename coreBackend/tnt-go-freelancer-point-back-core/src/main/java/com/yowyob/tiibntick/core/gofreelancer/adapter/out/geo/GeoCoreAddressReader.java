package com.yowyob.tiibntick.core.gofreelancer.adapter.out.geo;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.entity.AddressEntity;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.AddressReactiveRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageAddressUseCase;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.Address;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Reads addresses from the core address book, in the tenant of the current context. */
@Component
@RequiredArgsConstructor
public class GeoCoreAddressReader implements AddressReactiveRepository {

    private final IManageAddressUseCase addresses;
    private final TenantContextHolder tenants;

    @Override
    public Mono<AddressEntity> findById(UUID id) {
        return tenants.currentTenantId()
                .defaultIfEmpty(TenantContextHolder.SYSTEM_TENANT)
                .flatMap(tenant -> addresses.findById(tenant, id))
                .onErrorResume(GeoNotFoundException.class, e -> Mono.empty())
                .map(GeoCoreAddressReader::toEntity);
    }

    static AddressEntity toEntity(Address a) {
        return AddressEntity.builder()
                .id(a.id())
                .street(a.street())
                .landmark(a.landmark())
                .quarter(a.district())
                .city(a.city())
                .region(a.region())
                .country(a.country())
                .postalCode(a.postalCode())
                .latitude(a.latitude())
                .longitude(a.longitude())
                .build();
    }
}
