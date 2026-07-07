package com.yowyob.tiibntick.core.actor.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class Badge {

    private final String code;
    private final String label;
    private final Instant earnedAt;
    private final String blockchainTxHash;

    private Badge(String code, String label, Instant earnedAt, String blockchainTxHash) {
        this.code = Objects.requireNonNull(code, "badge code must not be null").trim().toUpperCase();
        this.label = Objects.requireNonNull(label, "badge label must not be null");
        this.earnedAt = Objects.requireNonNull(earnedAt, "earnedAt must not be null");
        this.blockchainTxHash = blockchainTxHash;
    }

    public static Badge of(String code, String label, Instant earnedAt, String blockchainTxHash) {
        return new Badge(code, label, earnedAt, blockchainTxHash);
    }

    public static Badge earn(String code, String label) {
        return new Badge(code, label, Instant.now(), null);
    }

    public Badge withBlockchainProof(String txHash) {
        return new Badge(this.code, this.label, this.earnedAt, txHash);
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public Instant earnedAt() {
        return earnedAt;
    }

    public String blockchainTxHash() {
        return blockchainTxHash;
    }

    public boolean hasBlockchainProof() {
        return blockchainTxHash != null && !blockchainTxHash.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Badge other)) return false;
        return code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return "Badge{code=" + code + ", earned=" + earnedAt + "}";
    }
}
