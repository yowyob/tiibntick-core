package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.MerchantContractEntity;
import com.yowyob.tiibntick.core.marketback.domain.model.ContractId;
import com.yowyob.tiibntick.core.marketback.domain.model.ContractStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.ContractTerms;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.MerchantContract;
import com.yowyob.tiibntick.core.marketback.domain.model.VolumeTier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Maps between the MerchantContract aggregate and its R2DBC entity.
 * Volume tiers (a {@code List<VolumeTier>}) are persisted as a JSON array column.
 *
 * @author MANFOUO Braun
 */
@Component
public class MerchantContractMapper {

    private final ObjectMapper objectMapper;

    public MerchantContractMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MerchantContractEntity toEntity(MerchantContract contract, boolean isNew) {
        ContractTerms terms = contract.getTerms();
        MerchantContractEntity.MerchantContractEntityBuilder builder = MerchantContractEntity.builder()
                .id(contract.getId().value())
                .isNew(isNew)
                .tenantId(contract.getTenantId())
                .merchantId(contract.getMerchantId())
                .providerId(contract.getProviderId())
                .listingId(contract.getListingId().value())
                .status(contract.getStatus().name())
                .volumeTiers(toJson(contract.getVolumeTiers()))
                .terminationReason(contract.getTerminationReason())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .signedAt(contract.getSignedAt())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt());
        if (terms != null) {
            builder.baseDiscountPct(terms.baseDiscountPct())
                    .maxMonthlyOrders(terms.maxMonthlyOrders())
                    .minMonthlyOrders(terms.minMonthlyOrders())
                    .paymentTermDays(terms.paymentTermDays())
                    .dslExpressionOverride(terms.dslExpressionOverride())
                    .specialConditions(terms.specialConditions());
        }
        return builder.build();
    }

    public MerchantContract toDomain(MerchantContractEntity entity) {
        ContractTerms terms = new ContractTerms(entity.getBaseDiscountPct(), entity.getMaxMonthlyOrders(),
                entity.getMinMonthlyOrders(), entity.getPaymentTermDays(), entity.getDslExpressionOverride(),
                entity.getSpecialConditions());
        List<VolumeTier> tiers = fromJsonTiers(entity.getVolumeTiers());
        return MerchantContract.reconstitute(
                ContractId.of(entity.getId()), entity.getTenantId(), entity.getMerchantId(), entity.getProviderId(),
                MarketListingId.of(entity.getListingId()), ContractStatus.valueOf(entity.getStatus()),
                terms, tiers, entity.getTerminationReason(),
                entity.getStartDate(), entity.getEndDate(), entity.getSignedAt(),
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

    private List<VolumeTier> fromJsonTiers(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, VolumeTier.class));
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
