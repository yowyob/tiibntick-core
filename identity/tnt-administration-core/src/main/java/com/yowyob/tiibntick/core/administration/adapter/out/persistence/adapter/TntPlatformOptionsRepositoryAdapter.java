package com.yowyob.tiibntick.core.administration.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.administration.adapter.out.persistence.entity.TntPlatformOptionsEntity;
import com.yowyob.tiibntick.core.administration.adapter.out.persistence.repository.TntPlatformOptionsR2dbcRepository;
import com.yowyob.tiibntick.core.administration.application.port.out.TntPlatformOptionsRepository;
import com.yowyob.tiibntick.core.administration.domain.model.TntPlatformOptions;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2DBC adapter implementing TntPlatformOptionsRepository port.
 * Author: MANFOUO Braun
 */
@Component
public class TntPlatformOptionsRepositoryAdapter implements TntPlatformOptionsRepository {

    private final TntPlatformOptionsR2dbcRepository repository;
    private final R2dbcEntityTemplate entityTemplate;

    public TntPlatformOptionsRepositoryAdapter(TntPlatformOptionsR2dbcRepository repository,
                                               R2dbcEntityTemplate entityTemplate) {
        this.repository = repository;
        this.entityTemplate = entityTemplate;
    }

    @Override
    public Mono<TntPlatformOptions> findByTenantId(UUID tenantId) {
        return repository.findByTenantId(tenantId).map(this::toDomain);
    }

    @Override
    public Mono<TntPlatformOptions> save(TntPlatformOptions options) {
        var entity = toEntity(options);
        return repository.existsById(entity.id())
                .flatMap(exists -> exists
                        ? entityTemplate.update(entity)
                        : entityTemplate.insert(entity))
                .map(this::toDomain);
    }

    private TntPlatformOptionsEntity toEntity(TntPlatformOptions o) {
        return new TntPlatformOptionsEntity(o.getId(), o.getTenantId(),
                o.isBlockchainEnabled(), o.isSmartDisputeResolutionEnabled(), o.getBlockchainNetwork(),
                o.isFreelancerModeEnabled(), o.isRequireFreelancerApproval(),
                o.getMaxFreelancerConcurrentMissions(), o.isPointRelaisModeEnabled(),
                o.getRelayPointMaxStorageHours(), o.isAnnouncementMarketplaceEnabled(),
                o.getMaxCourierAnnouncementResponses(), o.getTvaRate(), o.getDefaultCurrency(),
                o.isDisputeManagementEnabled(), o.getDisputeFilingWindowDays(),
                o.isFreelancerOrgModeEnabled(), o.getMaxFreelancerOrgFleetSize(),
                o.isBillingTemplatesEnabled(), o.getMaxBillingTemplateDslLevel(),
                o.getCreatedAt(), o.getUpdatedAt());
    }

    private TntPlatformOptions toDomain(TntPlatformOptionsEntity e) {
        return TntPlatformOptions.rehydrateFull(e.id(), e.tenantId(),
                e.blockchainEnabled(), e.smartDisputeResolutionEnabled(), e.blockchainNetwork(),
                e.freelancerModeEnabled(), e.requireFreelancerApproval(),
                e.maxFreelancerConcurrentMissions(), e.pointRelaisModeEnabled(),
                e.relayPointMaxStorageHours(), e.announcementMarketplaceEnabled(),
                e.maxCourierAnnouncementResponses(), e.tvaRate(), e.defaultCurrency(),
                e.disputeManagementEnabled(), e.disputeFilingWindowDays(),
                e.freelancerOrgModeEnabled(), e.maxFreelancerOrgFleetSize(),
                e.billingTemplatesEnabled(), e.maxBillingTemplateDslLevel(),
                e.createdAt(), e.updatedAt());
    }
}
