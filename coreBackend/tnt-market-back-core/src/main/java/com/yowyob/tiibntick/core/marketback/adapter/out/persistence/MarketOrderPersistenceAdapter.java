package com.yowyob.tiibntick.core.marketback.adapter.out.persistence;

import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.MarketOrderEntity;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.mapper.MarketOrderMapper;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository.R2dbcMarketOrderRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketOrderRepository;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketOrder;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketOrderId;
import com.yowyob.tiibntick.core.marketback.domain.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Persistence adapter for {@link IMarketOrderRepository} (hexagonal outbound port).
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class MarketOrderPersistenceAdapter implements IMarketOrderRepository {

    private final R2dbcMarketOrderRepository r2dbcRepository;
    private final MarketOrderMapper mapper;

    @Override
    public Mono<MarketOrder> save(MarketOrder order) {
        MarketOrderEntity entity = mapper.toEntity(order);
        return r2dbcRepository.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return r2dbcRepository.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<MarketOrder> findById(MarketOrderId id, String tenantId) {
        return r2dbcRepository.findByIdAndTenantId(id.value(), tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<MarketOrder> findByClientId(UUID clientId, String tenantId) {
        return r2dbcRepository.findByClientIdAndTenantId(clientId, tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<MarketOrder> findByProviderId(UUID providerId, String tenantId) {
        return r2dbcRepository.findByProviderIdAndTenantId(providerId, tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<MarketOrder> findByStatus(OrderStatus status, String tenantId) {
        return r2dbcRepository.findByStatusAndTenantId(status.name(), tenantId).map(mapper::toDomain);
    }

    @Override
    public Flux<MarketOrder> findByListingId(MarketListingId listingId) {
        return r2dbcRepository.findByListingId(listingId.value()).map(mapper::toDomain);
    }

    @Override
    public Mono<Long> countByTenantId(String tenantId) {
        return r2dbcRepository.countByTenantId(tenantId);
    }

    @Override
    public Mono<Long> countCompletedByTenantId(String tenantId) {
        return r2dbcRepository.countCompletedByTenantId(tenantId);
    }

    @Override
    public Mono<Long> countByProviderIdAndTenantId(UUID providerId, String tenantId) {
        return r2dbcRepository.countByProviderIdAndTenantId(providerId, tenantId);
    }

    @Override
    public Mono<BigDecimal> sumRevenueByProviderId(UUID providerId, String tenantId) {
        return r2dbcRepository.sumRevenueByProviderId(providerId, tenantId);
    }

    @Override
    public Mono<Void> delete(MarketOrderId id) {
        return r2dbcRepository.deleteById(id.value());
    }
}
