package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC entity mapped to {@code tnt_market.market_listings}.
 *
 * <p>Ported from {@code tiibntick-market-backend}'s
 * {@code adapter.outbound.persistence.entity.MarketListingEntity}, adapted to
 * the {@link Persistable} + Lombok builder convention used across this
 * module (see {@code NetworkNodeEntity} in tnt-link-back-core).</p>
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_market", value = "market_listings")
public class MarketListingEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private String tenantId;

    @Column("provider_id")
    private UUID providerId;

    @Column("provider_type")
    private String providerType;

    @Column("organization_id")
    private UUID organizationId;

    @Column("status")
    private String status;

    @Column("visibility")
    private String visibility;

    @Column("seo_slug")
    private String seoSlug;

    /** Not yet populated by the mapper — reserved for the future QR-code vitrine feature (kept for parity with the original schema). */
    @Column("qr_code")
    private String qrCode;

    // Vitrine fields (flattened)
    @Column("display_name")
    private String displayName;

    @Column("tagline")
    private String tagline;

    @Column("description")
    private String description;

    @Column("logo_url")
    private String logoUrl;

    @Column("banner_url")
    private String bannerUrl;

    @Column("contact_email")
    private String contactEmail;

    @Column("contact_phone")
    private String contactPhone;

    @Column("website_url")
    private String websiteUrl;

    @Column("founded_year")
    private Integer foundedYear;

    // Coverage zone
    @Column("coverage_radius_km")
    private Double coverageRadiusKm;

    @Column("coverage_center_lat")
    private Double coverageCenterLat;

    @Column("coverage_center_lng")
    private Double coverageCenterLng;

    /** JSON array of city strings. */
    @Column("coverage_cities")
    private String coverageCities;

    // SEO
    @Column("seo_title")
    private String seoTitle;

    @Column("seo_description")
    private String seoDescription;

    /** JSON array of keyword strings. */
    @Column("seo_keywords")
    private String seoKeywords;

    // Stats
    @Column("view_count")
    private long viewCount;

    @Column("conversion_count")
    private long conversionCount;

    @Column("average_rating")
    private double averageRating;

    @Column("total_reviews")
    private int totalReviews;

    // Moderation
    @Column("moderated_by")
    private UUID moderatedBy;

    @Column("moderated_at")
    private LocalDateTime moderatedAt;

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("published_at")
    private LocalDateTime publishedAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
