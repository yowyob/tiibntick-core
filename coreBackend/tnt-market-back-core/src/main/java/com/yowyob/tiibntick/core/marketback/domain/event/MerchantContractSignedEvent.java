package com.yowyob.tiibntick.core.marketback.domain.event;

import com.yowyob.tiibntick.core.marketback.domain.model.ContractId;
import java.time.LocalDateTime;
import java.util.UUID;

/** Domain event — fired when both parties sign a MerchantContract. @author MANFOUO Braun */
public record MerchantContractSignedEvent(
        ContractId contractId, UUID merchantId, UUID providerId, LocalDateTime occurredAt) implements MarketDomainEvent {
    @Override
    public String aggregateId() { return contractId.toString(); }
}
