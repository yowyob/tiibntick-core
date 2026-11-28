package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Split of a delivery price held in escrow (spec §4.1): the platform levy, optional merchant parts (relay storage fee,
 * agency share of the courier — QO-19) and the courier's part, the rest. The three sum to the price.
 */
public record EscrowShares(BigDecimal price, BigDecimal platform, List<Part> merchants, BigDecimal courier) {

    public EscrowShares {
        Objects.requireNonNull(price, "price");
        merchants = merchants == null ? List.of() : List.copyOf(merchants);
    }

    /** A merchant part: who (a Kernel wallet) and how much. */
    public record Part(UUID walletId, BigDecimal amount) {
        public Part {
            Objects.requireNonNull(walletId, "walletId");
            Objects.requireNonNull(amount, "amount");
        }
    }

    public BigDecimal merchantTotal() {
        return merchants.stream().map(Part::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
