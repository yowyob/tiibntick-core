package com.yowyob.tiibntick.core.tp.adapter.out.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.tp.adapter.out.persistence.entity.*;
import com.yowyob.tiibntick.core.tp.domain.model.*;
import com.yowyob.tiibntick.core.tp.domain.model.enums.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bidirectional mapper between domain models and R2DBC persistence entities for tnt-tp-core.
 *
 * <p>All {@code toDomain()} methods use the {@code reconstitute()} factory on domain
 * aggregates — <strong>never</strong> {@code create()} — to avoid re-generating IDs
 * or emitting spurious domain events during persistence-to-domain reconstruction.
 *
 * @author MANFOUO Braun
 */
@Component
public class TntTpPersistenceMapper {

    private final ObjectMapper objectMapper;

    /**
     * Constructor injection.
     *
     * @param objectMapper Jackson ObjectMapper (must have JavaTimeModule registered)
     */
    public TntTpPersistenceMapper(@Qualifier("tntTpObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ─── TntClientProfile ────────────────────────────────────────────────────

    /**
     * Maps a {@link TntClientProfile} domain aggregate to a {@link TntClientProfileEntity}.
     *
     * @param profile the domain aggregate
     * @return the corresponding R2DBC entity
     */
    public TntClientProfileEntity toEntity(TntClientProfile profile) {
        TntClientProfileEntity entity = new TntClientProfileEntity();
        entity.setId(profile.getId());
        entity.setTenantId(profile.getTenantId());
        entity.setThirdPartyId(profile.getThirdPartyId());
        entity.setTntRoles(serializeRoles(profile.getTntRoles()));
        entity.setKycStatus(profile.getKycStatus().name());
        entity.setPhoneAlias(profile.getPhoneAlias());
        entity.setPhoneMasked(profile.isPhoneMasked());
        entity.setAverageRating(profile.getAverageRating());
        entity.setRatingCount(profile.getRatingCount());
        entity.setTotalDeliveries(profile.getTotalDeliveries());
        entity.setPreferredLocale(profile.getPreferredLocale());
        entity.setPreferredCurrency(profile.getPreferredCurrency());
        entity.setLoyaltyTier(profile.getLoyaltyTier().name());
        entity.setActive(profile.isActive());
        entity.setProviderLinksJson(serializeMap(profile.getProviderLinks()));
        entity.setCreatedAt(profile.getCreatedAt());
        entity.setUpdatedAt(profile.getUpdatedAt());
        return entity;
    }

    /**
     * Reconstitutes a {@link TntClientProfile} from a {@link TntClientProfileEntity}.
     *
     * <p>Uses {@link TntClientProfile#reconstitute} to preserve the original UUID and
     * avoid emitting domain events during database-to-domain reconstruction.
     *
     * @param entity the R2DBC entity from the database
     * @return the reconstituted domain aggregate
     */
    public TntClientProfile toDomain(TntClientProfileEntity entity) {
        return TntClientProfile.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getThirdPartyId(),
                deserializeRoles(entity.getTntRoles()),
                KycStatus.valueOf(entity.getKycStatus()),
                entity.getPhoneAlias(),
                entity.isPhoneMasked(),
                entity.getAverageRating(),
                entity.getRatingCount(),
                entity.getTotalDeliveries(),
                entity.getPreferredLocale(),
                entity.getPreferredCurrency(),
                LoyaltyTier.valueOf(entity.getLoyaltyTier()),
                deserializeMap(entity.getProviderLinksJson()),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    // ─── KycRecord ───────────────────────────────────────────────────────────

    /**
     * Maps a {@link KycRecord} domain entity to a {@link KycRecordEntity}.
     *
     * @param record the domain entity
     * @return the corresponding R2DBC entity
     */
    public KycRecordEntity toEntity(KycRecord record) {
        KycRecordEntity entity = new KycRecordEntity();
        entity.setId(record.getId());
        entity.setTenantId(record.getTenantId());
        entity.setThirdPartyId(record.getThirdPartyId());
        entity.setDocumentType(record.getDocumentType().name());
        entity.setDocumentStorageKey(record.getDocumentStorageKey());
        entity.setSelfieStorageKey(record.getSelfieStorageKey());
        entity.setDocumentNumber(record.getDocumentNumber());
        entity.setDocumentExpiryDate(record.getDocumentExpiryDate());
        entity.setStatus(record.getStatus().name());
        entity.setRejectionReason(record.getRejectionReason());
        entity.setReviewedBy(record.getReviewedBy());
        entity.setSubmittedAt(record.getSubmittedAt());
        entity.setReviewedAt(record.getReviewedAt());
        entity.setCreatedAt(record.getCreatedAt());
        entity.setUpdatedAt(record.getUpdatedAt());
        return entity;
    }

    /**
     * Reconstitutes a {@link KycRecord} from a {@link KycRecordEntity}.
     *
     * <p>Uses {@link KycRecord#reconstitute} to preserve original UUID and all state.
     *
     * @param entity the R2DBC entity from the database
     * @return the reconstituted domain entity
     */
    public KycRecord toDomain(KycRecordEntity entity) {
        return KycRecord.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getThirdPartyId(),
                DocumentType.valueOf(entity.getDocumentType()),
                entity.getDocumentStorageKey(),
                entity.getSelfieStorageKey(),
                entity.getDocumentNumber(),
                entity.getDocumentExpiryDate(),
                KycStatus.valueOf(entity.getStatus()),
                entity.getRejectionReason(),
                entity.getReviewedBy(),
                entity.getSubmittedAt(),
                entity.getReviewedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    // ─── LoyaltyAccount ──────────────────────────────────────────────────────

    /**
     * Maps a {@link LoyaltyAccount} domain aggregate to a {@link LoyaltyAccountEntity}.
     *
     * @param account the domain aggregate
     * @return the corresponding R2DBC entity
     */
    public LoyaltyAccountEntity toEntity(LoyaltyAccount account) {
        LoyaltyAccountEntity entity = new LoyaltyAccountEntity();
        entity.setId(account.getId());
        entity.setTenantId(account.getTenantId());
        entity.setThirdPartyId(account.getThirdPartyId());
        entity.setAvailablePoints(account.getAvailablePoints());
        entity.setLifetimePoints(account.getLifetimePoints());
        entity.setRedeemedPoints(account.getRedeemedPoints());
        entity.setExpiredPoints(account.getExpiredPoints());
        entity.setCurrentTier(account.getCurrentTier().name());
        entity.setCreatedAt(account.getCreatedAt());
        entity.setUpdatedAt(account.getUpdatedAt());
        return entity;
    }

    /**
     * Reconstitutes a {@link LoyaltyAccount} from a {@link LoyaltyAccountEntity}.
     *
     * <p>Uses {@link LoyaltyAccount#reconstitute} to preserve original state.
     * Transactions are not loaded here (separate repository call if needed).
     *
     * @param entity the R2DBC entity from the database
     * @return the reconstituted domain aggregate
     */
    public LoyaltyAccount toDomain(LoyaltyAccountEntity entity) {
        return LoyaltyAccount.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getThirdPartyId(),
                entity.getAvailablePoints(),
                entity.getLifetimePoints(),
                entity.getRedeemedPoints(),
                entity.getExpiredPoints(),
                LoyaltyTier.valueOf(entity.getCurrentTier()),
                Collections.emptyList(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    // ─── ThirdPartyRating ────────────────────────────────────────────────────

    /**
     * Maps a {@link ThirdPartyRating} domain entity to a {@link ThirdPartyRatingEntity}.
     *
     * @param rating the domain entity
     * @return the corresponding R2DBC entity
     */
    public ThirdPartyRatingEntity toEntity(ThirdPartyRating rating) {
        ThirdPartyRatingEntity entity = new ThirdPartyRatingEntity();
        entity.setId(rating.getId());
        entity.setTenantId(rating.getTenantId());
        entity.setRatedThirdPartyId(rating.getRatedThirdPartyId());
        entity.setRaterActorId(rating.getRaterActorId());
        entity.setMissionId(rating.getMissionId());
        entity.setScore(rating.getScore());
        entity.setComment(rating.getComment());
        entity.setCreatedAt(rating.getCreatedAt());
        return entity;
    }

    /**
     * Reconstitutes a {@link ThirdPartyRating} from a {@link ThirdPartyRatingEntity}.
     *
     * <p>Uses {@link ThirdPartyRating#reconstitute} to preserve original UUID and timestamp.
     *
     * @param entity the R2DBC entity from the database
     * @return the reconstituted domain entity
     */
    public ThirdPartyRating toDomain(ThirdPartyRatingEntity entity) {
        return ThirdPartyRating.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getRatedThirdPartyId(),
                entity.getRaterActorId(),
                entity.getMissionId(),
                entity.getScore(),
                entity.getComment(),
                entity.getCreatedAt()
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Serializes a set of {@link TntThirdPartyRole} to a JSON array string.
     *
     * @param roles the roles to serialize
     * @return JSON string (e.g., {@code ["SENDER","DELIVERER"]})
     */
    private String serializeRoles(Set<TntThirdPartyRole> roles) {
        try {
            return objectMapper.writeValueAsString(
                    roles.stream().map(TntThirdPartyRole::name).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TNT roles", e);
        }
    }

    /**
     * Deserializes a JSON role array string back to a {@link Set} of {@link TntThirdPartyRole}.
     * Falls back to {@code {SENDER}} if the JSON is null or blank.
     *
     * @param json the JSON string from the database
     * @return unmodifiable set of roles
     */
    private Set<TntThirdPartyRole> deserializeRoles(String json) {
        if (json == null || json.isBlank()) {
            return Set.of(TntThirdPartyRole.SENDER);
        }
        try {
            List<String> names = objectMapper.readValue(json, new TypeReference<>() {});
            Set<TntThirdPartyRole> roles = new HashSet<>();
            for (String name : names) {
                roles.add(TntThirdPartyRole.valueOf(name));
            }
            return Collections.unmodifiableSet(roles);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize TNT roles from: " + json, e);
        }
    }
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static String serializeMap(java.util.Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        try { return MAPPER.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, String> deserializeMap(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return java.util.Map.of();
        try { return MAPPER.readValue(json, java.util.Map.class); }
        catch (Exception e) { return java.util.Map.of(); }
    }

}