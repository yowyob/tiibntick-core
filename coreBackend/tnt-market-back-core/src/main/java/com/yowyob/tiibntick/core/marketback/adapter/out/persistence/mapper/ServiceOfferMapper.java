package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.ServiceOfferEntity;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.Money;
import com.yowyob.tiibntick.core.marketback.domain.model.OfferAvailability;
import com.yowyob.tiibntick.core.marketback.domain.model.OfferStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.PricingRules;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceConstraints;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceOffer;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceOfferId;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Entity &lt;-&gt; domain mapper for {@link ServiceOffer}.
 * Ported from the standalone app's {@code ServiceOfferMapper} — nested value
 * objects that don't map to flat columns ({@code daysOfWeek},
 * {@code exceptionalClosures}) are serialized/deserialized as JSON via Jackson.
 *
 * @author MANFOUO Braun
 */
@Component
public class ServiceOfferMapper {

    private final ObjectMapper objectMapper;

    public ServiceOfferMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ServiceOfferEntity toEntity(ServiceOffer o) {
        ServiceOfferEntity.ServiceOfferEntityBuilder builder = ServiceOfferEntity.builder()
                .id(o.getId().value())
                .tenantId(o.getTenantId())
                .listingId(o.getListingId().value())
                .providerId(o.getProviderId())
                .name(o.getName())
                .description(o.getDescription())
                .serviceType(o.getServiceType().name())
                .status(o.getStatus().name())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt());

        PricingRules p = o.getPricingRules();
        if (p != null) {
            builder.basePrice(toBigDecimal(p.basePrice()))
                    .perKmRate(toBigDecimal(p.perKmRate()))
                    .perKgRate(toBigDecimal(p.perKgRate()))
                    .currency(p.currency())
                    .minimumPrice(toBigDecimal(p.minimumPrice()))
                    .maximumPrice(toBigDecimal(p.maximumPrice()))
                    .pricingDslExpression(p.pricingDslExpression());
        }

        ServiceConstraints c = o.getServiceConstraints();
        if (c != null) {
            builder.maxWeightKg(c.maxWeightKg())
                    .maxLengthCm(c.maxLengthCm())
                    .maxWidthCm(c.maxWidthCm())
                    .maxHeightCm(c.maxHeightCm())
                    .maxValueXaf(c.maxValueXaf())
                    .acceptsFragile(c.acceptsFragile())
                    .acceptsPerishable(c.acceptsPerishable())
                    .acceptsHazardous(c.acceptsHazardous())
                    .requiresInsurance(c.requiresInsurance())
                    .maxDistanceKm(c.maxDistanceKm());
        }

        OfferAvailability a = o.getAvailability();
        if (a != null) {
            builder.daysOfWeek(toJson(a.daysOfWeek()))
                    .openTime(a.openTime())
                    .closeTime(a.closeTime())
                    .exceptionalClosures(toJson(a.exceptionalClosures()))
                    .expressAvailable(a.expressAvailable())
                    .sameDayAvailable(a.sameDayAvailable());
        }

        return builder.build();
    }

    public ServiceOffer toDomain(ServiceOfferEntity e) {
        String currency = e.getCurrency() != null ? e.getCurrency() : "XAF";
        PricingRules pricing = new PricingRules(
                toMoney(e.getBasePrice(), currency),
                toMoney(e.getPerKmRate(), currency),
                toMoney(e.getPerKgRate(), currency),
                toMoney(e.getMinimumPrice(), currency),
                toMoney(e.getMaximumPrice(), currency),
                currency,
                e.getPricingDslExpression());

        ServiceConstraints constraints = new ServiceConstraints(
                nvl(e.getMaxWeightKg()), nvl(e.getMaxLengthCm()), nvl(e.getMaxWidthCm()), nvl(e.getMaxHeightCm()),
                nvl(e.getMaxValueXaf()), e.isAcceptsFragile(), e.isAcceptsPerishable(),
                e.isAcceptsHazardous(), e.isRequiresInsurance(), nvl(e.getMaxDistanceKm()));

        Set<DayOfWeek> days = Set.copyOf(fromJson(e.getDaysOfWeek(), DayOfWeek.class));
        List<LocalDate> closures = fromJson(e.getExceptionalClosures(), LocalDate.class);
        OfferAvailability availability = new OfferAvailability(days, e.getOpenTime(), e.getCloseTime(),
                closures, e.isExpressAvailable(), e.isSameDayAvailable());

        return ServiceOffer.reconstitute(
                new ServiceOfferId(e.getId()), e.getTenantId(),
                new MarketListingId(e.getListingId()), e.getProviderId(),
                e.getName(), e.getDescription(), ServiceType.valueOf(e.getServiceType()),
                OfferStatus.valueOf(e.getStatus()), pricing, constraints, availability,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private BigDecimal toBigDecimal(Money m) {
        return m != null ? BigDecimal.valueOf(m.amount()) : null;
    }

    private Money toMoney(BigDecimal amount, String currency) {
        return amount != null ? new Money(amount.longValue(), currency) : null;
    }

    private double nvl(Double v) {
        return v != null ? v : 0.0;
    }

    private String toJson(Object obj) {
        if (obj == null) return "[]";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private <T> List<T> fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
