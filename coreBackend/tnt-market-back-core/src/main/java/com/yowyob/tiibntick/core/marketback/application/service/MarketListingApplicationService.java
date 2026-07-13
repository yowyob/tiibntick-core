package com.yowyob.tiibntick.core.marketback.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketListingUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateMarketListingCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.UpdateMarketListingCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MarketListingResponse;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketEventPublisher;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketListingRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketNotificationPort;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketSearchIndexPort;
import com.yowyob.tiibntick.core.marketback.domain.exception.InvalidOrganizationException;
import com.yowyob.tiibntick.core.marketback.domain.exception.InvalidProviderException;
import com.yowyob.tiibntick.core.marketback.domain.exception.ListingNotFoundException;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;
import com.yowyob.tiibntick.core.marketback.domain.model.*;
import com.yowyob.tiibntick.core.organization.application.port.out.KernelOrganizationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Application service — manages the full lifecycle of MarketListings.
 *
 * <p>Orchestrates domain operations, persistence, event publishing,
 * notifications and search indexing for the listing bounded context.</p>
 *
 * <p>Ported from {@code tiibntick-market-backend}'s
 * {@code application.service.MarketListingService}, repackaged to implement
 * the already-ported {@link IManageMarketListingUseCase} inbound port.</p>
 *
 * <p>TODO(market-migration): tnt-media-core's {@code IUploadMediaUseCase} is not
 * wired here — {@link VitrineProfile#logoKey()}/{@link VitrineProfile#bannerKey()}
 * are plain string keys and neither {@link CreateMarketListingCommand} nor
 * {@link UpdateMarketListingCommand} carries raw image bytes (or even a URL) to
 * upload; wire it once the commands carry uploadable image data.</p>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketListingApplicationService implements IManageMarketListingUseCase {

    private final IMarketListingRepository listingRepository;
    private final IMarketEventPublisher eventPublisher;
    private final IMarketNotificationPort notificationPort;
    private final IMarketSearchIndexPort searchIndexPort;
    private final SeoSlugService seoSlugService;
    private final IKernelActorPort actorPort;
    private final KernelOrganizationPort organizationPort;

    @Override
    public Mono<MarketListingResponse> createListing(CreateMarketListingCommand command) {
        log.debug("Creating MarketListing for provider={} tenant={}", command.providerId(), command.tenantId());

        return validateProviderExists(command.providerId())
                .then(validateOrganizationIfPresent(command.organizationId()))
                .then(listingRepository.existsByProviderIdAndTenantId(command.providerId(), command.tenantId()))
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.<MarketListing>error(new MarketDomainException(
                                "Provider " + command.providerId() + " already has a listing for tenant " + command.tenantId()));
                    }

                    VitrineProfile vitrine = new VitrineProfile(
                            command.displayName(), command.tagline(), command.description(),
                            null, null, command.contactEmail(), command.contactPhone(),
                            command.websiteUrl(), command.socialLinks(),
                            command.certificationIds(), command.foundedYear());

                    MarketListing listing = MarketListing.create(
                            command.tenantId(), command.providerId(), command.providerType(), vitrine);

                    if (command.cities() != null || command.radiusKm() != null) {
                        CoverageZone zone = buildValidatedCoverageZone(
                                command.cities(), command.radiusKm(), command.centerLat(), command.centerLng());
                        listing.updateCoverage(zone);
                    }

                    return listingRepository.save(listing);
                })
                .flatMap(saved -> {
                    List<Object> events = saved.pullDomainEvents();
                    return Flux.fromIterable(events)
                            .flatMap(eventPublisher::publish)
                            .then(Mono.just(saved));
                })
                .map(this::toResponse);
    }

    /**
     * Validates that {@code providerId} exists as a Kernel actor (tnt-actor-core's
     * {@link IKernelActorPort}). {@code IKernelActorPort.exists} is fail-open on
     * Kernel unavailability (returns {@code false} rather than propagating), so a
     * {@code false} result here can mean either "genuinely missing" or "Kernel
     * unreachable" — either way listing creation is business-critical and must not
     * proceed against an unverifiable provider.
     */
    private Mono<Void> validateProviderExists(UUID providerId) {
        return actorPort.exists(providerId)
                .defaultIfEmpty(false)
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.<Void>empty()
                        : Mono.error(new InvalidProviderException(String.valueOf(providerId))));
    }

    /**
     * Validates that {@code organizationId}, when present, exists and is active in
     * the Kernel (tnt-organization-core's {@link KernelOrganizationPort}). Listings
     * may be provider-only (freelancer/relay point without an organization), so a
     * {@code null} organizationId is skipped entirely rather than treated as invalid.
     */
    private Mono<Void> validateOrganizationIfPresent(UUID organizationId) {
        if (organizationId == null) {
            return Mono.empty();
        }
        return organizationPort.existsAndActive(organizationId)
                .defaultIfEmpty(false)
                .flatMap(active -> Boolean.TRUE.equals(active)
                        ? Mono.<Void>empty()
                        : Mono.error(new InvalidOrganizationException(String.valueOf(organizationId))));
    }

    /**
     * Builds a {@link CoverageZone}, validating the optional center coordinates via
     * tnt-geo-core's {@link GeoPoint} (WGS-84 bounds check) instead of trusting raw
     * doubles — {@code CoverageZone} itself performs no range validation, only
     * distance math.
     */
    private CoverageZone buildValidatedCoverageZone(List<String> cities, Double radiusKm,
                                                      Double centerLat, Double centerLng) {
        if (centerLat != null && centerLng != null) {
            try {
                GeoPoint.of(centerLat, centerLng);
            } catch (IllegalArgumentException ex) {
                throw new MarketDomainException("Invalid coverage zone center coordinates: " + ex.getMessage(), ex);
            }
        }
        return new CoverageZone(cities, List.of(), radiusKm, centerLat, centerLng, null);
    }

    @Override
    public Mono<MarketListingResponse> updateListing(UUID listingId, UpdateMarketListingCommand command, String tenantId) {
        return listingRepository.findById(MarketListingId.of(listingId), tenantId)
                .switchIfEmpty(Mono.error(new ListingNotFoundException(listingId.toString())))
                .flatMap(listing -> {
                    VitrineProfile newProfile = new VitrineProfile(
                            command.displayName() != null ? command.displayName() : listing.getVitrine().displayName(),
                            command.tagline() != null ? command.tagline() : listing.getVitrine().tagline(),
                            command.description() != null ? command.description() : listing.getVitrine().description(),
                            listing.getVitrine().logoKey(),
                            listing.getVitrine().bannerKey(),
                            command.contactEmail() != null ? command.contactEmail() : listing.getVitrine().contactEmail(),
                            command.contactPhone() != null ? command.contactPhone() : listing.getVitrine().contactPhone(),
                            command.websiteUrl() != null ? command.websiteUrl() : listing.getVitrine().websiteUrl(),
                            command.socialLinks() != null ? command.socialLinks() : listing.getVitrine().socialLinks(),
                            command.certificationIds() != null ? command.certificationIds() : listing.getVitrine().certificationIds(),
                            command.foundedYear() != null ? command.foundedYear() : listing.getVitrine().foundedYear());
                    listing.updateProfile(newProfile);

                    if (command.cities() != null) {
                        CoverageZone zone = buildValidatedCoverageZone(
                                command.cities(), command.radiusKm(), command.centerLat(), command.centerLng());
                        listing.updateCoverage(zone);
                    }
                    return listingRepository.save(listing);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketListingResponse> submitForReview(UUID listingId, String tenantId) {
        return listingRepository.findById(MarketListingId.of(listingId), tenantId)
                .switchIfEmpty(Mono.error(new ListingNotFoundException(listingId.toString())))
                .flatMap(listing -> {
                    listing.submitForReview();
                    return listingRepository.save(listing);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketListingResponse> approveListing(UUID listingId, UUID adminId, String tenantId) {
        return listingRepository.findById(MarketListingId.of(listingId), tenantId)
                .switchIfEmpty(Mono.error(new ListingNotFoundException(listingId.toString())))
                .flatMap(listing -> {
                    // Generate SEO slug before publishing
                    String slug = seoSlugService.generate(listing.getVitrine().displayName());
                    SeoMetadata seo = new SeoMetadata(
                            listing.getVitrine().displayName() + " | TiiBnTick Market",
                            listing.getVitrine().tagline(),
                            List.of(listing.getVitrine().displayName(), "livraison", "transport"),
                            null, null);
                    listing.setSeoMetadata(seo, slug);
                    listing.approve(adminId);
                    return listingRepository.save(listing);
                })
                .flatMap(saved -> {
                    List<Object> events = saved.pullDomainEvents();
                    return eventPublisher.publishAll(events)
                            .then(searchIndexPort.indexListing(saved))
                            .then(notificationPort.notifyListingApproved(tenantId, saved.getProviderId(), saved.getId().toString()))
                            .thenReturn(saved);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketListingResponse> rejectListing(UUID listingId, UUID adminId, String reason, String tenantId) {
        return listingRepository.findById(MarketListingId.of(listingId), tenantId)
                .switchIfEmpty(Mono.error(new ListingNotFoundException(listingId.toString())))
                .flatMap(listing -> {
                    listing.reject(adminId, reason);
                    return listingRepository.save(listing);
                })
                .flatMap(saved -> {
                    List<Object> events = saved.pullDomainEvents();
                    return eventPublisher.publishAll(events)
                            .then(notificationPort.notifyListingRejected(tenantId, saved.getProviderId(), saved.getId().toString(), reason))
                            .thenReturn(saved);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketListingResponse> unpublishListing(UUID listingId, String tenantId) {
        return listingRepository.findById(MarketListingId.of(listingId), tenantId)
                .switchIfEmpty(Mono.error(new ListingNotFoundException(listingId.toString())))
                .flatMap(listing -> {
                    listing.unpublish();
                    return listingRepository.save(listing);
                })
                .flatMap(saved -> searchIndexPort.removeListing(saved.getId()).thenReturn(saved))
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketListingResponse> suspendListing(UUID listingId, String reason, String tenantId) {
        return listingRepository.findById(MarketListingId.of(listingId), tenantId)
                .switchIfEmpty(Mono.error(new ListingNotFoundException(listingId.toString())))
                .flatMap(listing -> {
                    listing.suspend(reason);
                    return listingRepository.save(listing);
                })
                .flatMap(saved -> searchIndexPort.removeListing(saved.getId()).thenReturn(saved))
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketListingResponse> getListing(UUID listingId, String tenantId) {
        return listingRepository.findById(MarketListingId.of(listingId), tenantId)
                .switchIfEmpty(Mono.error(new ListingNotFoundException(listingId.toString())))
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketListingResponse> getListingBySeoSlug(String slug, String tenantId) {
        return listingRepository.findBySeoSlug(slug, tenantId)
                .switchIfEmpty(Mono.error(new ListingNotFoundException("slug=" + slug)))
                .map(this::toResponse);
    }

    @Override
    public Flux<MarketListingResponse> getListingsByProvider(UUID providerId, String tenantId) {
        return listingRepository.findByTenantId(tenantId)
                .filter(l -> l.getProviderId().equals(providerId))
                .map(this::toResponse);
    }

    @Override
    public Flux<MarketListingResponse> getListingsPendingModeration(String tenantId) {
        return listingRepository.findByStatus(ListingStatus.PENDING_REVIEW, tenantId)
                .map(this::toResponse);
    }

    @Override
    public Mono<Void> trackView(UUID listingId, String tenantId) {
        MarketListingId id = MarketListingId.of(listingId);
        return listingRepository.findById(id, tenantId)
                .switchIfEmpty(Mono.error(new ListingNotFoundException(listingId.toString())))
                .flatMap(listing -> {
                    listing.recordView();
                    return listingRepository.save(listing);
                })
                .then();
    }

    @Override
    public Mono<Void> deleteListing(UUID listingId, String tenantId) {
        MarketListingId id = MarketListingId.of(listingId);
        return listingRepository.findById(id, tenantId)
                .switchIfEmpty(Mono.error(new ListingNotFoundException(listingId.toString())))
                .flatMap(listing -> {
                    listing.archive();
                    return listingRepository.save(listing);
                })
                .then(searchIndexPort.removeListing(id))
                .then();
    }

    // -------------------------------------------------------
    // Mapper
    // -------------------------------------------------------

    private MarketListingResponse toResponse(MarketListing l) {
        CoverageZone zone = l.getCoverageZone();
        return new MarketListingResponse(
                l.getId().value(),
                l.getTenantId(),
                l.getProviderId(),
                l.getProviderType(),
                l.getStatus(),
                l.getVitrine().displayName(),
                l.getVitrine().tagline(),
                l.getVitrine().description(),
                l.getVitrine().logoKey(),
                l.getVitrine().bannerKey(),
                l.getVitrine().contactPhone(),
                l.getVitrine().contactEmail(),
                l.getVitrine().websiteUrl(),
                zone != null ? zone.cities() : List.of(),
                zone != null ? zone.radiusKm() : null,
                zone != null ? zone.centerLat() : null,
                zone != null ? zone.centerLng() : null,
                l.getSeoSlug(),
                l.getAverageRating(),
                l.getTotalReviews(),
                l.getViewCount(),
                l.getConversionCount(),
                l.getPublishedAt(),
                l.getCreatedAt(),
                l.getUpdatedAt());
    }
}
