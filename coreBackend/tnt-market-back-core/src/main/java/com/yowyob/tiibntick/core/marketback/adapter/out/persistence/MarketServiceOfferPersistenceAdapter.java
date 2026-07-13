package com.yowyob.tiibntick.core.marketback.adapter.out.persistence;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.ServiceOfferEntity;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.mapper.ServiceOfferMapper;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository.R2dbcServiceOfferRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IServiceOfferRepository;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.OfferStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceOffer;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceOfferId;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Outbound adapter implementing {@link IServiceOfferRepository} on top of R2DBC.
 * Ported from the standalone app's {@code ServiceOfferRepositoryAdapter}.
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class MarketServiceOfferPersistenceAdapter implements IServiceOfferRepository {

    private final R2dbcServiceOfferRepository r2dbcRepository;
    private final ServiceOfferMapper mapper;

    @Override
    public Mono<ServiceOffer> save(ServiceOffer offer) {
        ServiceOfferEntity entity = mapper.toEntity(offer);
        return r2dbcRepository.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return r2dbcRepository.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<ServiceOffer> findById(ServiceOfferId id) {
        return r2dbcRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Flux<ServiceOffer> findByListingId(MarketListingId listingId) {
        return r2dbcRepository.findByListingId(listingId.value()).map(mapper::toDomain);
    }

    @Override
    public Flux<ServiceOffer> findActiveByListingId(MarketListingId listingId) {
        return r2dbcRepository.findByListingIdAndStatus(listingId.value(), OfferStatus.ACTIVE.name())
                .map(mapper::toDomain);
    }

    @Override
    public Flux<ServiceOffer> findByServiceType(ServiceType type, String tenantId) {
        return r2dbcRepository.findByServiceTypeAndTenantId(type.name(), tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<ServiceOffer> findByPriceRange(long minPriceXaf, long maxPriceXaf, String tenantId) {
        return r2dbcRepository.findByPriceRange(
                        BigDecimal.valueOf(minPriceXaf), BigDecimal.valueOf(maxPriceXaf), "XAF", tenantId)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> delete(ServiceOfferId id) {
        return r2dbcRepository.deleteById(id.value());
    }
}
