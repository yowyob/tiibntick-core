package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.ProviderReviewEntity;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.ProviderReview;
import com.yowyob.tiibntick.core.marketback.domain.model.Rating;
import com.yowyob.tiibntick.core.marketback.domain.model.ReviewId;
import com.yowyob.tiibntick.core.marketback.domain.model.ReviewStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.ReviewTag;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Maps between the ProviderReview aggregate and its R2DBC entity.
 * Tags (a {@code List<ReviewTag>}) are persisted as a JSON array column,
 * since no other module in this codebase persists set/list-valued columns as native arrays.
 *
 * @author MANFOUO Braun
 */
@Component
public class ProviderReviewMapper {

    private final ObjectMapper objectMapper;

    public ProviderReviewMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProviderReviewEntity toEntity(ProviderReview review, boolean isNew) {
        Rating rating = review.getRating();
        return ProviderReviewEntity.builder()
                .id(review.getId().value())
                .isNew(isNew)
                .tenantId(review.getTenantId())
                .clientId(review.getClientId())
                .providerId(review.getProviderId())
                .listingId(review.getListingId().value())
                .orderId(review.getOrderId() != null ? review.getOrderId().value() : null)
                .status(review.getStatus().name())
                .comment(review.getComment())
                .tags(toJson(review.getTags()))
                .overall(rating != null ? rating.overall() : 0)
                .punctuality(rating != null ? rating.punctuality() : 0)
                .communication(rating != null ? rating.communication() : 0)
                .packaging(rating != null ? rating.packaging() : 0)
                .value(rating != null ? rating.value() : 0)
                .moderatedBy(review.getModeratedBy())
                .moderatedAt(review.getModeratedAt())
                .rejectionReason(review.getRejectionReason())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    public ProviderReview toDomain(ProviderReviewEntity entity) {
        Rating rating = new Rating(entity.getOverall(), entity.getPunctuality(),
                entity.getCommunication(), entity.getPackaging(), entity.getValue());
        List<ReviewTag> tags = fromJsonTags(entity.getTags());
        return ProviderReview.reconstitute(
                ReviewId.of(entity.getId()), entity.getTenantId(), entity.getClientId(), entity.getProviderId(),
                MarketListingId.of(entity.getListingId()), entity.getOrderId(),
                rating, entity.getComment(), tags, ReviewStatus.valueOf(entity.getStatus()),
                entity.getModeratedBy(), entity.getModeratedAt(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String toJson(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<ReviewTag> fromJsonTags(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ReviewTag>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
