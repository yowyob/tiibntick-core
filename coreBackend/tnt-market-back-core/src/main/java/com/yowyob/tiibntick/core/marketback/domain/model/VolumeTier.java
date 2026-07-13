package com.yowyob.tiibntick.core.marketback.domain.model;

/**
 * Entity — VolumeTier inside a MerchantContract.
 * @author MANFOUO Braun
 */
public class VolumeTier {

    private final String id;
    private final int minOrders;
    private final int maxOrders;
    private final double discountPct;
    private final long flatRateXaf;

    public VolumeTier(String id, int minOrders, int maxOrders, double discountPct, long flatRateXaf) {
        this.id = id;
        this.minOrders = minOrders;
        this.maxOrders = maxOrders;
        this.discountPct = discountPct;
        this.flatRateXaf = flatRateXaf;
    }

    public boolean appliesTo(int volume) {
        return volume >= minOrders && (maxOrders <= 0 || volume <= maxOrders);
    }

    public String getId() { return id; }
    public int getMinOrders() { return minOrders; }
    public int getMaxOrders() { return maxOrders; }
    public double getDiscountPct() { return discountPct; }
    public long getFlatRateXaf() { return flatRateXaf; }
}
