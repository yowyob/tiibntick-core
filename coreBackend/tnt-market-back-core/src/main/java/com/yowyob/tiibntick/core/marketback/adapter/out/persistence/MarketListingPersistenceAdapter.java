package com.yowyob.tiibntick.core.marketback.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.MarketListingEntity;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository.R2dbcMarketListingRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketListingRepository;
import com.yowyob.tiibntick.core.marketback.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Persistence adapter for {@link IMarketListingRepository} (hexagonal outbound port).
 *
 * <p>Ported from {@code tiibntick-market-backend}'s
 * {@code adapter.outbound.persistence.adapter.MarketListingRepositoryAdapter}
 * and its companion {@code MarketListingMapper}. Entity&lt;-&gt;domain mapping is
 * inlined here rather than extracted into a separate MapStruct mapper —
 * mirroring {@code NetworkNodePersistenceAdapter} in tnt-link-back-core —
 * since the only non-trivial conversion (JSON list columns) still requires
 * hand-written helper methods either way.</p>
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class MarketListingPersistenceAdapter implements IMarketListingRepository {

    private final R2dbcMarketListingRepository r2dbcRepo;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<MarketListing> save(MarketListing listing) {
        MarketListingEntity entity = toEntity(listing);
        return r2dbcRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return r2dbcRepo.save(entity);
                })
                .map(this::toDomain);
    }

    @Override
    public Mono<MarketListing> findById(MarketListingId id, String tenantId) {
        return r2dbcRepo.findByIdAndTenantId(id.value(), tenantId).map(this::toDomain);
    }

    @Override
    public Mono<MarketListing> findByProviderIdAndTenantId(UUID providerId, String tenantId) {
        return r2dbcRepo.findByProviderIdAndTenantId(providerId, tenantId).map(this::toDomain);
    }

    @Override
    public Flux<MarketListing> findByTenantId(String tenantId) {
        return r2dbcRepo.findByTenantId(tenantId).map(this::toDomain);
    }

    @Override
    public Flux<MarketListing> findByStatus(ListingStatus status, String tenantId) {
        return r2dbcRepo.findByStatusAndTenantId(status.name(), tenantId).map(this::toDomain);
    }

    @Override
    public Flux<MarketListing> findByProviderType(ProviderType providerType, String tenantId) {
        return r2dbcRepo.findByProviderTypeAndTenantId(providerType.name(), tenantId)
                .map(this::toDomain);
    }

    @Override
    public Mono<MarketListing> findBySeoSlug(String slug, String tenantId) {
        return r2dbcRepo.findBySeoSlugAndTenantId(slug, tenantId).map(this::toDomain);
    }

    @Override
    public Mono<MarketListing> findByQrCode(String qrCode, String tenantId) {
        return r2dbcRepo.findByQrCodeAndTenantId(qrCode, tenantId).map(this::toDomain);
    }

    @Override
    public Flux<MarketListing> findByMinRating(double minRating, String tenantId) {
        return r2dbcRepo.findByMinRating(minRating, tenantId).map(this::toDomain);
    }

    @Override
    public Flux<MarketListing> findNearby(double lat, double lng, double radiusKm,
                                           ServiceType serviceType, String tenantId) {
        return r2dbcRepo.findNearby(lat, lng, radiusKm,
                        serviceType != null ? serviceType.name() : null, tenantId)
                .map(this::toDomain);
    }

    @Override
    public Mono<Void> updateRating(MarketListingId id, String tenantId, double avg, long count) {
        return r2dbcRepo.findByIdAndTenantId(id.value(), tenantId)
                .flatMap(e -> {
                    e.setAverageRating(avg);
                    e.setTotalReviews((int) count);
                    return r2dbcRepo.save(e);
                })
                .then();
    }

    @Override
    public Mono<Long> countByTenantId(String tenantId) {
        return r2dbcRepo.countByTenantId(tenantId);
    }

    @Override
    public Mono<Boolean> existsByProviderIdAndTenantId(UUID providerId, String tenantId) {
        return r2dbcRepo.existsByProviderIdAndTenantId(providerId, tenantId);
    }

    @Override
    public Mono<Void> delete(MarketListingId id, String tenantId) {
        return r2dbcRepo.deleteById(id.value());
    }

    // -------------------------------------------------------
    // Entity <-> Domain mapping
    // -------------------------------------------------------

    private MarketListingEntity toEntity(MarketListing listing) {
        VitrineProfile v = listing.getVitrine();
        CoverageZone z = listing.getCoverageZone();
        SeoMetadata seo = listing.getSeoMetadata();

        return MarketListingEntity.builder()
                .id(listing.getId().value())
                .tenantId(listing.getTenantId())
                .providerId(listing.getProviderId())
                .providerType(listing.getProviderType().name())
                .organizationId(listing.getOrganizationId())
                .status(listing.getStatus().name())
                .visibility(listing.getVisibility().name())
                .seoSlug(listing.getSeoSlug())
                .displayName(v != null ? v.displayName() : null)
                .tagline(v != null ? v.tagline() : null)
                .description(v != null ? v.description() : null)
                .logoUrl(v != null ? v.logoKey() : null)
                .bannerUrl(v != null ? v.bannerKey() : null)
                .contactEmail(v != null ? v.contactEmail() : null)
                .contactPhone(v != null ? v.contactPhone() : null)
                .websiteUrl(v != null ? v.websiteUrl() : null)
                .foundedYear(v != null ? v.foundedYear() : null)
                .coverageRadiusKm(z != null ? z.radiusKm() : null)
                .coverageCenterLat(z != null ? z.centerLat() : null)
                .coverageCenterLng(z != null ? z.centerLng() : null)
                .coverageCities(z != null ? toJson(z.cities()) : null)
                .seoTitle(seo != null ? seo.metaTitle() : null)
                .seoDescription(seo != null ? seo.metaDescription() : null)
                .seoKeywords(seo != null ? toJson(seo.keywords()) : null)
                .viewCount(listing.getViewCount())
                .conversionCount(listing.getConversionCount())
                .averageRating(listing.getAverageRating())
                .totalReviews(listing.getTotalReviews())
                .moderatedBy(listing.getModeratedBy())
                .moderatedAt(listing.getModeratedAt())
                .rejectionReason(listing.getRejectionReason())
                .publishedAt(listing.getPublishedAt())
                .createdAt(listing.getCreatedAt())
                .updatedAt(listing.getUpdatedAt())
                .build();
    }

    private MarketListing toDomain(MarketListingEntity e) {
        // NOTE(market-migration): socialLinks/certificationIds are not persisted —
        // faithfully preserved from the original MarketListingMapper, which never
        // wrote/read these VitrineProfile fields to/from the entity either.
        VitrineProfile vitrine = new VitrineProfile(
                e.getDisplayName(), e.getTagline(), e.getDescription(),
                e.getLogoUrl(), e.getBannerUrl(), e.getContactEmail(),
                e.getContactPhone(), e.getWebsiteUrl(), null,
                null, e.getFoundedYear());
        CoverageZone zone = new CoverageZone(
                fromJson(e.getCoverageCities()), Collections.emptyList(),
                e.getCoverageRadiusKm(), e.getCoverageCenterLat(), e.getCoverageCenterLng(), null);
        SeoMetadata seo = new SeoMetadata(e.getSeoTitle(), e.getSeoDescription(), fromJson(e.getSeoKeywords()), null, null);

        return MarketListing.reconstitute(
                new MarketListingId(e.getId()),
                e.getTenantId(),
                e.getProviderId(),
                e.getOrganizationId(),
                ProviderType.valueOf(e.getProviderType()),
                ListingStatus.valueOf(e.getStatus()),
                ListingVisibility.valueOf(e.getVisibility()),
                vitrine, zone, seo, e.getSeoSlug(),
                e.getViewCount(), e.getConversionCount(),
                e.getAverageRating(), e.getTotalReviews(),
                e.getModeratedBy(), e.getModeratedAt(), e.getRejectionReason(),
                e.getPublishedAt(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private String toJson(Object obj) {
        if (obj == null) return "[]";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
