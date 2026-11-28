package com.yowyob.tiibntick.core.gofreelancer.config;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerGridPolicyMapper;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleCategory;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleType;
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

    /**
     * Keyed by the app word of a category (moto, tricycle, taxi, camion, bus) or of a precise type priced on its own
     * grid (velo = BIKE, scooter = SCOOTER, camionnette = VAN; decision D-058). A precise type without its own grid is
     * priced on its category's grid.
     */
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
        /** Minimum price of a delivery; {@code null} = none. */
        private Double minimumFee;
        /** Price per minute of waiting; {@code null} = 0. */
        private Double waitingFeePerMinute;

        public Grid() { }

        Grid(double baseFee, double pricePerKm, double pricePerKg) {
            this.baseFee = baseFee;
            this.pricePerKm = pricePerKm;
            this.pricePerKg = pricePerKg;
        }

        Grid(double baseFee, double pricePerKm, double pricePerKg, double minimumFee, double pricePerCbm,
             double fragileSurcharge, double perishableSurcharge, double waitingFeePerMinute) {
            this(baseFee, pricePerKm, pricePerKg);
            this.minimumFee = minimumFee;
            this.pricePerCbm = pricePerCbm;
            this.fragileSurcharge = fragileSurcharge;
            this.perishableSurcharge = perishableSurcharge;
            this.waitingFeePerMinute = waitingFeePerMinute;
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

    /** App word of the precise types priced on their own grid (D-058). */
    static final Map<VehicleType, String> TYPE_KEYS = Map.of(
            VehicleType.BIKE, "velo",
            VehicleType.SCOOTER, "scooter",
            VehicleType.VAN, "camionnette");

    /**
     * Platform grid by vehicle (decision D-058, {@code docs/architecture/chantiers/tarifs-par-vehicule.md} §1, XAF):
     * base, /km, /kg, minimum, /m³, fragile, perishable, waiting/min. A proposal only: every courier can redefine his
     * own prices ({@code PUT /api/freelancers/{id}/policies}).
     */
    static Map<String, Grid> defaults() {
        Map<String, Grid> m = new java.util.LinkedHashMap<>();
        m.put("velo", new Grid(300, 75, 50, 500, 2000, 300, 300, 15));
        m.put("moto", new Grid(600, 100, 75, 1000, 2000, 500, 500, 25));
        m.put("scooter", new Grid(500, 90, 75, 900, 2000, 500, 500, 25));
        m.put("tricycle", new Grid(1000, 150, 15, 1500, 1000, 500, 500, 25));
        m.put("taxi", new Grid(1000, 200, 25, 2000, 1500, 750, 750, 50));
        m.put("camionnette", new Grid(2500, 300, 10, 5000, 1000, 1000, 1000, 75));
        m.put("camion", new Grid(5000, 400, 5, 10000, 500, 2000, 2000, 100));
        m.put("bus", new Grid(1000, 10, 100, 1500, 2000, 500, 500, 0));
        return m;
    }

    /** The platform grid of a category ({@code null} = moto), as the flat grid the billing engine prices. */
    public FreelancerPricingDTO gridFor(VehicleCategory category) {
        VehicleCategory c = category == null ? VehicleCategory.MOTO : category;
        Grid g = lookup(c.appWord());
        if (g == null) g = lookup(VehicleCategory.MOTO.appWord());
        if (g == null) g = defaults().get(c.appWord());
        return toDto(g);
    }

    /**
     * The platform grid of a vehicle given in either vocabulary (core type name such as {@code SCOOTER}, app word such
     * as {@code scooter} or {@code moto}): the precise type's own grid when it has one, else its category's grid
     * (unknown or absent = moto).
     */
    public FreelancerPricingDTO gridForVehicle(String rawVehicleType) {
        String key = preciseKey(rawVehicleType);
        if (key != null) {
            Grid own = lookup(key);
            if (own == null && grids == null) own = defaults().get(key);
            if (own != null) return toDto(own);
        }
        return gridFor(VehicleCategory.parse(rawVehicleType));
    }

    /** Key of the precise type's own grid, or {@code null} when the vehicle is priced by its category. */
    static String preciseKey(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        for (Map.Entry<VehicleType, String> e : TYPE_KEYS.entrySet()) {
            if (e.getKey().name().equalsIgnoreCase(v) || e.getValue().equalsIgnoreCase(v)) return e.getValue();
        }
        return null;
    }

    private static FreelancerPricingDTO toDto(Grid g) {
        FreelancerPricingDTO base = FreelancerGridPolicyMapper.PLATFORM_DEFAULT_GRID;
        return FreelancerPricingDTO.builder()
                .baseFee(g.getBaseFee()).pricePerKm(g.getPricePerKm()).pricePerKg(g.getPricePerKg())
                .pricePerCbm(g.getPricePerCbm() != null ? g.getPricePerCbm() : base.getPricePerCbm())
                .fragileSurcharge(g.getFragileSurcharge() != null ? g.getFragileSurcharge() : base.getFragileSurcharge())
                .perishableSurcharge(g.getPerishableSurcharge() != null
                        ? g.getPerishableSurcharge() : base.getPerishableSurcharge())
                .minimumFee(g.getMinimumFee())
                .waitingFeePerMinute(g.getWaitingFeePerMinute() != null ? g.getWaitingFeePerMinute() : 0.0)
                .currency(FreelancerGridPolicyMapper.DEFAULT_CURRENCY)
                .build();
    }

    private Grid lookup(String key) {
        if (grids == null) return null;
        Grid g = grids.get(key);
        if (g == null) g = grids.get(key.toUpperCase(Locale.ROOT));
        return g;
    }

    /** Every category's grid, for diagnostics. */
    public Map<VehicleCategory, FreelancerPricingDTO> allGrids() {
        Map<VehicleCategory, FreelancerPricingDTO> m = new EnumMap<>(VehicleCategory.class);
        for (VehicleCategory c : VehicleCategory.values()) m.put(c, gridFor(c));
        return m;
    }

    /** The own grid of each precise type (velo, scooter, camionnette), for diagnostics. */
    public Map<VehicleType, FreelancerPricingDTO> preciseGrids() {
        Map<VehicleType, FreelancerPricingDTO> m = new EnumMap<>(VehicleType.class);
        for (VehicleType t : TYPE_KEYS.keySet()) m.put(t, gridForVehicle(t.name()));
        return m;
    }
}
