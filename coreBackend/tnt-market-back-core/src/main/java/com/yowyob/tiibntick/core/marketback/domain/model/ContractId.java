package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.UUID;

/**
 * Value Object — Strongly-typed identifier for Contract.
 * @author MANFOUO Braun
 */
public record ContractId(UUID value) {

    public static ContractId generate() {
        return new ContractId(UUID.randomUUID());
    }

    public static ContractId of(UUID value) {
        if (value == null) throw new IllegalArgumentException("ContractId value must not be null");
        return new ContractId(value);
    }

    public static ContractId of(String value) {
        return of(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
