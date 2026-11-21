package com.yowyob.tiibntick.core.gofreelancer.config;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerGridPolicyMapper;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleCategory;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lot C-54 — the platform price grid of each vehicle category ({@code tnt.gofp.courier.grids.<moto|
 * tricycle|taxi|camion|bus>}) and the loyalty thresholds ({@code tnt.gofp.courier.loyalty}). Every value
 * has a default here, so no environment variable becomes mandatory; {@code application.yml} only
 * repeats them so they can be changed without code.
 *
 * <p>Volume, fragile and perishable keep the values of the former single grid
 * ({@link FreelancerGridPolicyMapper#PLATFORM_DEFAULT_GRID}). A courier without a known vehicle is
 * priced on the moto grid.
 */
@Getter
@Setter
@ConfigurationProperties("tnt.gofp.courier")
public class GofpCourierPricingProperties {

    /** Keyed by the app word (moto, tricycle, taxi, camion, bus). */
    private Map<String, Grid> grids = defaults();

    private Loyalty loyalty = new Loyalty();

    @Getter
    @Setter
    public static class Grid {
        private double baseFee;
        private double pricePerKm;
        private double pricePerKg;
        private Double pricePerCbm;
        private Double fragileSurcharge;
        private Double perishableSurcharge;

        public Grid() { }

        Grid(double baseFee, double pricePerKm, double pricePerKg) {
            this.baseFee = baseFee;
            this.pricePerKm = pricePerKm;
            this.pricePerKg = pricePerKg;
        }
    }

    /** Badge and ranking thresholds. */
    @Getter
    @Setter
    public static class Loyalty {
        /** Below this many delivered parcels the courier is {@code NEW}. */
        private int newBelowDeliveries = 5;
        /** From this many delivered parcels the courier is {@code CONFIRMED} (lot C-54b: 5, no gap after NEW). */
        private int confirmedFromDeliveries = 5;
        /** {@code LOYAL} needs this many delivered parcels… */
        private int loyalFromDeliveries = 50;
        /** …and a profile at least this many months old. */
        private int loyalFromMonths = 6;
        /** Deliveries at which the volume half of the loyalty score saturates. */
        private int scoreDeliveriesCap = 50;
        /** Profile age (days) at which the tenure half of the loyalty score saturates. */
        private int scoreTenureDaysCap = 365;
    }

    static Map<String, Grid> defaults() {
        Map<String, Grid> m = new java.util.LinkedHashMap<>();
        m.put("moto", new Grid(500, 100, 50));
        m.put("tricycle", new Grid(800, 150, 40));
        m.put("taxi", new Grid(1000, 200, 30));
        m.put("camion", new Grid(3000, 400, 20));
        m.put("bus", new Grid(2000, 300, 30));
        return m;
    }

    /** The platform grid of a category ({@code null} = moto), as the flat grid the billing engine prices. */
    public FreelancerPricingDTO gridFor(VehicleCategory category) {
        VehicleCategory c = category == null ? VehicleCategory.MOTO : category;
        Grid g = lookup(c);
        if (g == null) g = lookup(VehicleCategory.MOTO);
        if (g == null) g = defaults().get(c.appWord());
        FreelancerPricingDTO base = FreelancerGridPolicyMapper.PLATFORM_DEFAULT_GRID;
        return FreelancerPricingDTO.builder()
                .baseFee(g.getBaseFee()).pricePerKm(g.getPricePerKm()).pricePerKg(g.getPricePerKg())
                .pricePerCbm(g.getPricePerCbm() != null ? g.getPricePerCbm() : base.getPricePerCbm())
                .fragileSurcharge(g.getFragileSurcharge() != null ? g.getFragileSurcharge() : base.getFragileSurcharge())
                .perishableSurcharge(g.getPerishableSurcharge() != null
                        ? g.getPerishableSurcharge() : base.getPerishableSurcharge())
                .waitingFeePerMinute(0.0)
                .currency(FreelancerGridPolicyMapper.DEFAULT_CURRENCY)
                .build();
    }

    private Grid lookup(VehicleCategory c) {
        if (grids == null) return null;
        Grid g = grids.get(c.appWord());
        if (g == null) g = grids.get(c.appWord().toUpperCase(Locale.ROOT));
        return g;
    }

    /** Every category's grid, for diagnostics. */
    public Map<VehicleCategory, FreelancerPricingDTO> allGrids() {
        Map<VehicleCategory, FreelancerPricingDTO> m = new EnumMap<>(VehicleCategory.class);
        for (VehicleCategory c : VehicleCategory.values()) m.put(c, gridFor(c));
        return m;
    }
}
