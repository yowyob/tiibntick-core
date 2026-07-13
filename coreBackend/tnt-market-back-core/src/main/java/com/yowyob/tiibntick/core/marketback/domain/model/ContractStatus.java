package com.yowyob.tiibntick.core.marketback.domain.model;

/** Lifecycle status of a MerchantContract. @author MANFOUO Braun */
public enum ContractStatus {
    DRAFT,
    NEGOTIATING,
    AWAITING_SIGNATURES,
    ACTIVE,
    SUSPENDED,
    TERMINATED,
    EXPIRED
}
