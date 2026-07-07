package com.yowyob.tiibntick.core.billing.templates.config;

import com.yowyob.tiibntick.core.billing.templates.domain.model.*;
import com.yowyob.tiibntick.core.billing.templates.port.outbound.IPolicyTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Application startup component that seeds the billing policy template catalog
 * with the 15 pre-defined default templates if the catalog is empty.
 *
 * <p>This seeder runs once at application startup. If templates already exist
 * (detected via {@link IPolicyTemplateRepository#countActive()}), seeding is
 * skipped to avoid duplicates.
 *
 * <p>Templates seeded:
 * <ol>
 *   <li>TPL-BASE-STD — Standard Base Pricing</li>
 *   <li>TPL-BASE-ZONE — Zone-Based Pricing</li>
 *   <li>TPL-FRAGILE — Fragile & Precious Specialist</li>
 *   <li>TPL-PERISHABLE — Perishable & Refrigerated Delivery</li>
 *   <li>TPL-EXPRESS — Express & Urgent Delivery</li>
 *   <li>TPL-BULK — Bulk & High Volume Pricing</li>
 *   <li>TPL-INTER_CITY — Inter-City Delivery</li>
 *   <li>TPL-LOYALTY_PROGRESSIVE — Progressive Loyalty Discounts</li>
 *   <li>TPL-VOLUME_SLAB — Volume Slab Discounts</li>
 *   <li>TPL-TIME_SLOTS — Time-Slot Based Pricing</li>
 *   <li>TPL-WEATHER_ADAPTIVE — Weather-Adaptive Surcharges</li>
 *   <li>TPL-HUB_STORAGE — Hub Point Relay Storage Fees</li>
 *   <li>TPL-NETWORK_TRANSIT — Inter-Node Network Transit</li>
 *   <li>TPL-MARKETPLACE_COMMISSION — Platform Marketplace Commission</li>
 *   <li>TPL-COMMISSION_DELIVERER — Deliverer Remuneration Model</li>
 * </ol>
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateCatalogSeeder implements ApplicationRunner {

    private final IPolicyTemplateRepository templateRepository;

    //private static final List<PolicyOwnerType> ALL_PROVIDERS = List.of(
    //        PolicyOwnerType.AGENCY, PolicyOwnerType.FREELANCER_ORG,
    //        PolicyOwnerType.POINT, PolicyOwnerType.LINK, PolicyOwnerType.ADMIN, PolicyOwnerType.MARKET);

    private static final List<PolicyOwnerType> DELIVERY_PROVIDERS = List.of(
            PolicyOwnerType.AGENCY, PolicyOwnerType.FREELANCER_ORG);

    @Override
    public void run(ApplicationArguments args) {
        templateRepository.countActive()
                .flatMap(count -> {
                    if (count > 0) {
                        log.info("Template catalog already has {} active templates. Skipping seed.", count);
                        return Mono.empty();
                    }
                    log.info("Template catalog is empty. Seeding {} default templates...", 15);
                    return seedAll();
                })
                .subscribe(
                        null,
                        ex -> log.error("Template catalog seeding failed", ex),
                        () -> log.info("Template catalog seeding completed.")
                );
    }

    private Mono<Void> seedAll() {
        List<PolicyTemplate> templates = buildAllTemplates();
        return Flux.fromIterable(templates)
                .flatMap(templateRepository::save)
                .doOnNext(t -> log.debug("Seeded template: {}", t.getTemplateCode()))
                .then();
    }

    // ─── Template builders ─────────────────────────────────────────────────

    private List<PolicyTemplate> buildAllTemplates() {
        return List.of(
                buildBaseStd(),
                buildBaseZone(),
                buildFragile(),
                buildPerishable(),
                buildExpress(),
                buildBulk(),
                buildInterCity(),
                buildLoyaltyProgressive(),
                buildVolumeSlab(),
                buildTimeSlots(),
                buildWeatherAdaptive(),
                buildHubStorage(),
                buildNetworkTransit(),
                buildMarketplaceCommission(),
                buildCommissionDeliverer()
        );
    }

    private PolicyTemplate buildBaseStd() {
        return PolicyTemplate.createNew(
                "TPL-BASE-STD", "Standard Base Pricing",
                "Simple pricing with base price + distance rate + weight rate. "
                        + "Suitable for any actor starting without complexity. "
                        + "Recommended for beginners and for actors who do not need zone-specific rates.",
                TemplateCategory.BASE,
                List.of(PolicyOwnerType.AGENCY, PolicyOwnerType.FREELANCER_ORG,
                        PolicyOwnerType.POINT, PolicyOwnerType.LINK, PolicyOwnerType.ADMIN),
                List.of(
                        param("basePrice", "Prix de base de la livraison", "Base delivery price", "500", "100", "50000", "XAF", ParameterType.MONEY, "Minimum amount charged for any delivery"),
                        param("perKmRate", "Tarif kilométrique", "Per-km rate", "50", "0", "500", "XAF/km", ParameterType.MONEY, "Amount added per kilometer of delivery distance"),
                        param("perKgRate", "Tarif au kilogramme", "Per-kg rate", "30", "0", "200", "XAF/kg", ParameterType.MONEY, "Amount added per kilogram of package weight"),
                        param("minPrice", "Prix minimum appliqué", "Minimum price floor", "500", "0", "10000", "XAF", ParameterType.MONEY, "The minimum total price, applied if the calculation is below this value"),
                        param("maxDistance", "Distance maximum acceptée", "Maximum accepted distance", "50", "1", "500", "km", ParameterType.INTEGER, "Maximum delivery distance in km for this policy")
                ),
                "IF weight >= 0 AND distance >= 0\n  THEN SET_BASE(500 XAF) + ADD_KM(50 XAF) + ADD_KG(30 XAF)\n  MIN(500 XAF)"
        );
    }

    private PolicyTemplate buildBaseZone() {
        return PolicyTemplate.createNew(
                "TPL-BASE-ZONE", "Zone-Based Pricing",
                "Different base prices depending on the delivery zone (Urban, Peri-Urban, Rural, Diplomatic). "
                        + "Ideal for actors operating in multiple neighborhoods with varying accessibility.",
                TemplateCategory.BASE,
                DELIVERY_PROVIDERS,
                List.of(
                        param("zoneA_basePrice", "Prix zone A (urbaine proche)", "Zone A base price (urban, easy)", "500", "100", "10000", "XAF", ParameterType.MONEY, "Urban, easy access zone"),
                        param("zoneB_basePrice", "Prix zone B (péri-urbaine)", "Zone B base price (peri-urban)", "800", "200", "15000", "XAF", ParameterType.MONEY, "Peri-urban medium access zone"),
                        param("zoneC_basePrice", "Prix zone C (distante/difficile)", "Zone C base price (distant/difficult)", "1200", "300", "25000", "XAF", ParameterType.MONEY, "Distant or difficult access zone"),
                        param("zoneD_basePrice", "Prix zone D (rurale/enclavée)", "Zone D base price (rural/isolated)", "2000", "500", "50000", "XAF", ParameterType.MONEY, "Rural or isolated zone"),
                        param("perKmRate", "Tarif kilométrique (toutes zones)", "Per-km rate (all zones)", "50", "0", "500", "XAF/km", ParameterType.MONEY, "Common per-km rate applied across all zones")
                ),
                "IF deliveryZoneType == URBAN THEN SET_BASE(500 XAF) + ADD_KM(50 XAF)"
        );
    }

    private PolicyTemplate buildFragile() {
        return PolicyTemplate.createNew(
                "TPL-FRAGILE", "Fragile & Precious Specialist",
                "Policy for fragile and high-value packages (glass, ceramics, electronics, jewelry). "
                        + "Includes automatic surcharges and recommended insurance coverage.",
                TemplateCategory.SPECIALTY,
                DELIVERY_PROVIDERS,
                List.of(
                        param("basePrice", "Prix de base", "Base price", "800", "200", "50000", "XAF", ParameterType.MONEY, "Higher base price for specialist handling"),
                        param("perKmRate", "Tarif kilométrique", "Per-km rate", "60", "0", "500", "XAF/km", ParameterType.MONEY, "Higher per-km rate reflecting careful transport"),
                        param("fragile_surcharge_pct", "Surcharge fragile (%)", "Fragile surcharge (%)", "20", "0", "100", "%", ParameterType.PERCENTAGE, "Percentage surcharge applied to subtotal for FRAGILE packages"),
                        param("precious_surcharge_fixed", "Surcharge précieux fixe", "Precious item fixed surcharge", "500", "0", "5000", "XAF", ParameterType.MONEY, "Fixed amount added for precious/luxury items"),
                        param("luxury_surcharge_pct", "Surcharge luxe (%)", "Luxury surcharge (%)", "30", "0", "100", "%", ParameterType.PERCENTAGE, "Additional percentage surcharge for LUXURY packages"),
                        param("insurance_included_amount", "Couverture assurance incluse", "Included insurance coverage", "5000", "0", "500000", "XAF", ParameterType.MONEY, "Maximum covered value for included insurance")
                ),
                "IF packageType == FRAGILE THEN SET_BASE(800 XAF) + ADD_KM(60 XAF) + ADD_PCT(20)"
        );
    }

    private PolicyTemplate buildPerishable() {
        return PolicyTemplate.createNew(
                "TPL-PERISHABLE", "Perishable & Refrigerated Delivery",
                "For actors specializing in perishable goods delivery: fresh produce, dairy, meat, "
                        + "temperature-sensitive pharmaceuticals. Requires refrigeration equipment.",
                TemplateCategory.SPECIALTY,
                DELIVERY_PROVIDERS,
                List.of(
                        param("basePrice", "Prix de base", "Base price", "1000", "300", "50000", "XAF", ParameterType.MONEY, "High base price covers refrigeration equipment cost"),
                        param("perKmRate", "Tarif kilométrique", "Per-km rate", "80", "0", "500", "XAF/km", ParameterType.MONEY, "Fuel + refrigeration maintenance cost per km"),
                        param("perishable_surcharge_fixed", "Surcharge équipement réfrigéré", "Refrigerated equipment surcharge", "700", "0", "5000", "XAF", ParameterType.MONEY, "Fixed surcharge for active refrigeration equipment"),
                        param("perishable_time_limit_hours", "Délai maximum garanti (h)", "Guaranteed max delivery time (h)", "4", "1", "24", "h", ParameterType.INTEGER, "Maximum hours guaranteed for perishable delivery"),
                        param("overtime_penalty_per_30min", "Pénalité dépassement (par 30min)", "Overtime penalty per 30min", "200", "0", "2000", "XAF", ParameterType.MONEY, "Penalty charged if delivery exceeds guaranteed time"),
                        param("pharmaceutical_surcharge", "Surcharge pharmaceutique", "Pharmaceutical surcharge", "500", "0", "3000", "XAF", ParameterType.MONEY, "Additional fixed surcharge for pharmaceutical packages")
                ),
                "IF packageType == PERISHABLE THEN SET_BASE(1000 XAF) + ADD_KM(80 XAF) + ADD_FIXED(700 XAF)"
        );
    }

    private PolicyTemplate buildExpress() {
        return PolicyTemplate.createNew(
                "TPL-EXPRESS", "Express & Urgent Delivery",
                "Premium policy for same-day and urgent deliveries (2h or 1h guarantee). "
                        + "Includes night and weekend surcharges.",
                TemplateCategory.SPECIALTY,
                DELIVERY_PROVIDERS,
                List.of(
                        param("basePrice", "Prix de base express", "Express base price", "700", "200", "20000", "XAF", ParameterType.MONEY, "Base price for express service"),
                        param("perKmRate", "Tarif kilométrique express", "Express per-km rate", "70", "0", "500", "XAF/km", ParameterType.MONEY, "Per-km rate for express delivery"),
                        param("express_2h_surcharge_pct", "Surcharge garantie 2h (%)", "2h guarantee surcharge (%)", "50", "0", "200", "%", ParameterType.PERCENTAGE, "Surcharge applied for 2-hour delivery guarantee"),
                        param("express_1h_surcharge_pct", "Surcharge garantie 1h (%)", "1h guarantee surcharge (%)", "100", "0", "300", "%", ParameterType.PERCENTAGE, "Surcharge applied for 1-hour delivery guarantee"),
                        param("night_surcharge_fixed", "Surcharge nuit fixe", "Night delivery surcharge", "500", "0", "3000", "XAF", ParameterType.MONEY, "Fixed surcharge for deliveries between 22:00 and 06:00"),
                        param("weekend_surcharge_fixed", "Surcharge weekend fixe", "Weekend delivery surcharge", "300", "0", "2000", "XAF", ParameterType.MONEY, "Fixed surcharge for Saturday and Sunday deliveries")
                ),
                "IF priority == EXPRESS THEN SET_BASE(700 XAF) + ADD_KM(70 XAF) + ADD_PCT(50)"
        );
    }

    private PolicyTemplate buildBulk() {
        return PolicyTemplate.createNew(
                "TPL-BULK", "Bulk & High Volume Pricing",
                "For large volume B2B shipments (warehouses, e-commerce). "
                        + "Includes degressif pricing and multi-parcel discounts.",
                TemplateCategory.SPECIALTY,
                List.of(PolicyOwnerType.AGENCY, PolicyOwnerType.ADMIN),
                List.of(
                        param("basePrice", "Prix de base vrac", "Bulk base price", "1500", "500", "20000", "XAF", ParameterType.MONEY, "Minimum base price for bulk missions"),
                        param("perKmRate", "Tarif kilométrique vrac", "Bulk per-km rate", "100", "0", "500", "XAF/km", ParameterType.MONEY, "Per-km rate for bulk delivery"),
                        param("perKgRate", "Tarif au kg (dégressif)", "Per-kg rate (degressif)", "20", "0", "100", "XAF/kg", ParameterType.MONEY, "Per-kg rate, lower for bulk due to economies of scale"),
                        param("bulk_threshold_kg", "Seuil déclenchant tarif vrac (kg)", "Bulk threshold weight (kg)", "20", "5", "200", "kg", ParameterType.INTEGER, "Package weight above which bulk discount activates"),
                        param("bulk_discount_pct", "Remise vrac (%)", "Bulk discount (%)", "15", "0", "50", "%", ParameterType.PERCENTAGE, "Discount applied when weight exceeds bulk threshold"),
                        param("multi_parcel_discount_per_unit", "Remise par colis supplémentaire", "Discount per extra parcel", "50", "0", "500", "XAF/unit", ParameterType.MONEY, "Amount deducted per parcel beyond 5 units in the mission")
                ),
                "IF weight >= 20 THEN SET_BASE(1500 XAF) + ADD_KM(100 XAF) + ADD_KG(20 XAF) + DISCOUNT_PCT(15)"
        );
    }

    private PolicyTemplate buildInterCity() {
        return PolicyTemplate.createNew(
                "TPL-INTER_CITY", "Inter-City Delivery",
                "For deliveries between cities (Yaoundé-Douala, Bafoussam-Garoua, etc.). "
                        + "Accounts for tolls, fuel supplements, and potential overnight stays.",
                TemplateCategory.SPECIALTY,
                DELIVERY_PROVIDERS,
                List.of(
                        param("basePrice_intercity", "Prix de base inter-villes", "Inter-city base price", "3000", "1000", "100000", "XAF", ParameterType.MONEY, "Minimum base price for inter-city missions"),
                        param("perKmRate_intercity", "Tarif/km inter-villes (dégressif)", "Inter-city per-km rate", "40", "0", "200", "XAF/km", ParameterType.MONEY, "Degressif rate for long-distance kms"),
                        param("toll_allowance", "Forfait péages estimé", "Estimated toll allowance", "500", "0", "5000", "XAF", ParameterType.MONEY, "Fixed amount to cover estimated toll fees"),
                        param("fuel_supplement_long_dist", "Supplément carburant longue distance", "Long-distance fuel supplement", "1000", "0", "10000", "XAF", ParameterType.MONEY, "Extra fuel cost for long-distance trips"),
                        param("overnight_surcharge", "Surcharge nuit inter-villes", "Overnight stay surcharge", "2000", "0", "20000", "XAF", ParameterType.MONEY, "Surcharge when delivery requires an overnight stay"),
                        param("return_trip_pct", "% prix aller si retour à vide (%)", "Return trip empty % of going price", "30", "0", "80", "%", ParameterType.PERCENTAGE, "Percentage of the one-way price charged for empty return trip")
                ),
                "IF distance >= 50 THEN SET_BASE(3000 XAF) + ADD_KM(40 XAF) + ADD_FIXED(500 XAF) + ADD_FIXED(1000 XAF)"
        );
    }

    private PolicyTemplate buildLoyaltyProgressive() {
        return PolicyTemplate.createNew(
                "TPL-LOYALTY_PROGRESSIVE", "Progressive Loyalty Discounts",
                "Tiered loyalty discounts that grow with the client's transaction history "
                        + "(Bronze → Silver → Gold → Platinum). Encourages repeat business.",
                TemplateCategory.LOYALTY,
                DELIVERY_PROVIDERS,
                List.of(
                        param("bronze_min_tx", "Transactions min Bronze", "Bronze tier min transactions", "3", "1", "50", "tx", ParameterType.INTEGER, "Minimum transactions in 30 days to reach Bronze tier"),
                        param("bronze_discount_pct", "Remise Bronze (%)", "Bronze discount (%)", "3", "0", "20", "%", ParameterType.PERCENTAGE, "Discount percentage for Bronze tier clients"),
                        param("silver_min_tx", "Transactions min Argent", "Silver tier min transactions", "10", "2", "100", "tx", ParameterType.INTEGER, "Minimum transactions to reach Silver tier"),
                        param("silver_discount_pct", "Remise Argent (%)", "Silver discount (%)", "7", "0", "30", "%", ParameterType.PERCENTAGE, "Discount percentage for Silver tier clients"),
                        param("gold_min_tx", "Transactions min Or", "Gold tier min transactions", "20", "5", "200", "tx", ParameterType.INTEGER, "Minimum transactions to reach Gold tier"),
                        param("gold_discount_pct", "Remise Or (%)", "Gold discount (%)", "12", "0", "40", "%", ParameterType.PERCENTAGE, "Discount percentage for Gold tier clients"),
                        param("platinum_min_tx", "Transactions min Platine", "Platinum tier min transactions", "50", "10", "500", "tx", ParameterType.INTEGER, "Minimum transactions to reach Platinum tier"),
                        param("platinum_discount_pct", "Remise Platine (%)", "Platinum discount (%)", "20", "0", "50", "%", ParameterType.PERCENTAGE, "Discount percentage for Platinum tier clients")
                ),
                "IF clientTxCount >= 3 THEN DISCOUNT_PCT(3)\nIF clientTxCount >= 10 THEN DISCOUNT_PCT(7)"
        );
    }

    private PolicyTemplate buildVolumeSlab() {
        return PolicyTemplate.createNew(
                "TPL-VOLUME_SLAB", "Volume Slab Discounts (B2B)",
                "Discounts based on monthly order volume (XAF total). "
                        + "Designed for B2B contracts with regular large clients.",
                TemplateCategory.LOYALTY,
                List.of(PolicyOwnerType.AGENCY, PolicyOwnerType.ADMIN),
                List.of(
                        param("slab1_min", "Volume min palier 1 (XAF)", "Slab 1 minimum volume (XAF)", "50000", "10000", "10000000", "XAF", ParameterType.MONEY, "Minimum monthly volume to reach Slab 1"),
                        param("slab1_discount_pct", "Remise palier 1 (%)", "Slab 1 discount (%)", "5", "0", "30", "%", ParameterType.PERCENTAGE, "Discount at Slab 1"),
                        param("slab2_min", "Volume min palier 2 (XAF)", "Slab 2 minimum volume (XAF)", "150000", "20000", "10000000", "XAF", ParameterType.MONEY, "Minimum monthly volume to reach Slab 2"),
                        param("slab2_discount_pct", "Remise palier 2 (%)", "Slab 2 discount (%)", "10", "0", "40", "%", ParameterType.PERCENTAGE, "Discount at Slab 2"),
                        param("slab3_min", "Volume min palier 3 (XAF)", "Slab 3 minimum volume (XAF)", "500000", "50000", "100000000", "XAF", ParameterType.MONEY, "Minimum monthly volume to reach Slab 3"),
                        param("slab3_discount_pct", "Remise palier 3 (%)", "Slab 3 discount (%)", "15", "0", "50", "%", ParameterType.PERCENTAGE, "Discount at Slab 3"),
                        param("slab4_min", "Volume min palier 4 (XAF)", "Slab 4 minimum volume (XAF)", "2000000", "100000", "1000000000", "XAF", ParameterType.MONEY, "Minimum monthly volume to reach Slab 4"),
                        param("slab4_discount_pct", "Remise palier 4 (%)", "Slab 4 discount (%)", "20", "0", "60", "%", ParameterType.PERCENTAGE, "Discount at Slab 4")
                ),
                "IF monthlyVolume >= 50000 THEN DISCOUNT_PCT(5)\nIF monthlyVolume >= 150000 THEN DISCOUNT_PCT(10)"
        );
    }

    private PolicyTemplate buildTimeSlots() {
        return PolicyTemplate.createNew(
                "TPL-TIME_SLOTS", "Time-Slot Based Pricing",
                "Variable pricing by time slot. Higher rates during peak hours and nights, "
                        + "discounts for off-peak. Helps smooth out delivery demand.",
                TemplateCategory.TIME,
                DELIVERY_PROVIDERS,
                List.of(
                        param("night_multiplier", "Multiplicateur nuit (00h-06h)", "Night multiplier (00h-06h)", "1.5", "1.0", "3.0", "x", ParameterType.MULTIPLIER, "Price multiplier for night deliveries"),
                        param("morning_rush_multiplier", "Multiplicateur rush matin (06h-09h)", "Morning rush multiplier", "1.2", "1.0", "2.5", "x", ParameterType.MULTIPLIER, "Price multiplier for morning rush hours"),
                        param("evening_rush_multiplier", "Multiplicateur rush soir (17h-20h)", "Evening rush multiplier", "1.3", "1.0", "2.5", "x", ParameterType.MULTIPLIER, "Price multiplier for evening rush hours"),
                        param("late_evening_multiplier", "Multiplicateur soirée (20h-00h)", "Late evening multiplier", "1.2", "1.0", "2.5", "x", ParameterType.MULTIPLIER, "Price multiplier for late evening hours"),
                        param("weekend_multiplier", "Multiplicateur weekend", "Weekend multiplier", "1.15", "1.0", "2.0", "x", ParameterType.MULTIPLIER, "Price multiplier for Saturdays and Sundays"),
                        param("holiday_multiplier", "Multiplicateur jours fériés", "Public holiday multiplier", "1.5", "1.0", "3.0", "x", ParameterType.MULTIPLIER, "Price multiplier for official public holidays")
                ),
                "IF timeOfDay TIME_IS_BETWEEN 00:00 AND 06:00 THEN MULTIPLY(1.5)"
        );
    }

    private PolicyTemplate buildWeatherAdaptive() {
        return PolicyTemplate.createNew(
                "TPL-WEATHER_ADAPTIVE", "Weather-Adaptive Surcharges",
                "Dynamic surcharges triggered by weather conditions. "
                        + "Reflects extra effort and vehicle wear in difficult weather. "
                        + "Relevant in tropical/rainy context (Cameroon).",
                TemplateCategory.WEATHER,
                DELIVERY_PROVIDERS,
                List.of(
                        param("rain_light_surcharge", "Surcharge pluie légère", "Light rain surcharge", "200", "0", "2000", "XAF", ParameterType.MONEY, "Fixed surcharge for light rain conditions"),
                        param("rain_heavy_surcharge", "Surcharge pluie forte", "Heavy rain surcharge", "500", "0", "5000", "XAF", ParameterType.MONEY, "Fixed surcharge for heavy rain conditions"),
                        param("flood_surcharge", "Surcharge inondations locales", "Local flooding surcharge", "1000", "0", "10000", "XAF", ParameterType.MONEY, "Fixed surcharge for local flooding conditions"),
                        param("storm_surcharge", "Surcharge tempête / vents forts", "Storm/strong wind surcharge", "800", "0", "8000", "XAF", ParameterType.MONEY, "Fixed surcharge for storm or strong wind conditions"),
                        param("heat_surcharge", "Surcharge chaleur extrême (> 38°C)", "Extreme heat surcharge", "300", "0", "3000", "XAF", ParameterType.MONEY, "Fixed surcharge for extreme heat conditions")
                ),
                "IF weatherCondition == RAIN_HEAVY THEN ADD_FIXED(500 XAF)"
        );
    }

    private PolicyTemplate buildHubStorage() {
        return PolicyTemplate.createNew(
                "TPL-HUB_STORAGE", "Hub Point Relay Storage Fees",
                "Storage fee schedule for package holding at a relay hub point. "
                        + "Free first 24h, then tiered fees per 12h interval. "
                        + "Applicable to POINT relay operators only.",
                TemplateCategory.HUB,
                List.of(PolicyOwnerType.POINT, PolicyOwnerType.ADMIN),
                List.of(
                        param("free_storage_hours", "Durée stockage gratuit (h)", "Free storage period (h)", "24", "0", "72", "h", ParameterType.INTEGER, "Number of free storage hours before fees apply"),
                        param("storage_fee_per_12h_slab2", "Frais stockage 24h-48h (par 12h)", "Storage fee 24h-48h (per 12h)", "100", "0", "2000", "XAF/12h", ParameterType.MONEY, "Fee per 12-hour interval in the 24h-48h period"),
                        param("storage_fee_per_12h_slab3", "Frais stockage 48h-72h (par 12h)", "Storage fee 48h-72h (per 12h)", "200", "0", "3000", "XAF/12h", ParameterType.MONEY, "Fee per 12-hour interval in the 48h-72h period"),
                        param("storage_fee_per_12h_slab4", "Frais stockage > 72h (par 12h)", "Storage fee > 72h (per 12h)", "500", "0", "5000", "XAF/12h", ParameterType.MONEY, "Fee per 12-hour interval beyond 72 hours"),
                        param("hazardous_storage_multiplier", "Multiplicateur colis dangereux", "Hazardous package multiplier", "3", "1", "10", "x", ParameterType.MULTIPLIER, "Multiplier applied to storage fee for hazardous packages"),
                        param("refrigerated_storage_extra", "Surcharge stockage réfrigéré (par 12h)", "Refrigerated storage extra (per 12h)", "300", "0", "3000", "XAF/12h", ParameterType.MONEY, "Extra fee per 12h for packages requiring active refrigeration"),
                        param("abandonment_penalty", "Pénalité abandon (> 7 jours)", "Abandonment penalty (> 7 days)", "5000", "0", "50000", "XAF", ParameterType.MONEY, "One-time penalty if package is unclaimed after 7 days")
                ),
                "IF storageHours > 24 AND storageHours <= 48 THEN ADD_PER_INTERVAL(100 XAF, 12h)"
        );
    }

    private PolicyTemplate buildNetworkTransit() {
        return PolicyTemplate.createNew(
                "TPL-NETWORK_TRANSIT", "Inter-Node Network Transit Fees",
                "Fee schedule for package routing through a Link network. "
                        + "Includes per-hop fees and optional inter-city transit fees. "
                        + "Applicable to LINK network operators only.",
                TemplateCategory.NETWORK,
                List.of(PolicyOwnerType.LINK, PolicyOwnerType.ADMIN),
                List.of(
                        param("node_handling_fee", "Frais traitement par nœud", "Node handling fee", "200", "0", "2000", "XAF", ParameterType.MONEY, "Fixed handling fee charged at each network node"),
                        param("per_hop_fee", "Frais par saut réseau", "Per network hop fee", "150", "0", "1500", "XAF", ParameterType.MONEY, "Fee charged for each hop between nodes"),
                        param("inter_city_transit_fee", "Frais transit inter-villes", "Inter-city transit fee", "500", "0", "5000", "XAF", ParameterType.MONEY, "Additional fee when a hop crosses city boundaries"),
                        param("priority_network_surcharge", "Surcharge traitement prioritaire", "Priority network surcharge", "300", "0", "3000", "XAF", ParameterType.MONEY, "Surcharge for priority routing through the network"),
                        param("fragile_handling_surcharge", "Surcharge manutention fragile au nœud", "Fragile handling surcharge at node", "400", "0", "4000", "XAF", ParameterType.MONEY, "Extra handling fee per node for FRAGILE packages")
                ),
                "SET_BASE(200 XAF)\nADD_PER_HOP(150 XAF)"
        );
    }

    private PolicyTemplate buildMarketplaceCommission() {
        return PolicyTemplate.createNew(
                "TPL-MARKETPLACE_COMMISSION", "Platform Marketplace Commission",
                "Commission model for TiiBnTick Market operator. "
                        + "Defines platform fees charged on each transaction processed through the marketplace.",
                TemplateCategory.MARKETPLACE,
                List.of(PolicyOwnerType.MARKET, PolicyOwnerType.ADMIN),
                List.of(
                        param("platform_commission_pct", "Commission plateforme (%)", "Platform commission (%)", "5", "0", "30", "%", ParameterType.PERCENTAGE, "Percentage of delivery price charged as platform commission"),
                        param("payment_processing_fee", "Frais traitement paiement", "Payment processing fee", "100", "0", "1000", "XAF", ParameterType.MONEY, "Fixed fee per transaction for payment processing"),
                        param("new_provider_rebate_pct", "Remise commission nouveaux prestataires (%)", "New provider commission rebate (%)", "50", "0", "100", "%", ParameterType.PERCENTAGE, "Commission rebate during the first 3 months for new providers"),
                        param("premium_listing_surcharge", "Surcharge visibilité premium", "Premium listing surcharge", "200", "0", "2000", "XAF", ParameterType.MONEY, "Surcharge for premium placement in the marketplace")
                ),
                "PLATFORM_COMMISSION(5%)\nADD_FIXED(100 XAF)"
        );
    }

    private PolicyTemplate buildCommissionDeliverer() {
        return PolicyTemplate.createNew(
                "TPL-COMMISSION_DELIVERER", "Deliverer Remuneration Model",
                "Commission distribution model for deliverers. "
                        + "Defines base commission, performance bonuses, and late delivery penalties. "
                        + "Used by Agency (permanent deliverers) and FreelancerOrg (sub-deliverers).",
                TemplateCategory.COMMISSION,
                DELIVERY_PROVIDERS,
                List.of(
                        param("base_commission_pct", "Commission de base livreur (%)", "Deliverer base commission (%)", "60", "10", "90", "%", ParameterType.PERCENTAGE, "Percentage of mission total paid to the deliverer"),
                        param("bonus_ontime_pct", "Bonus livraison dans les délais (%)", "On-time delivery bonus (%)", "10", "0", "30", "%", ParameterType.PERCENTAGE, "Bonus percentage added to commission for on-time delivery"),
                        param("penalty_late_pct", "Pénalité retard (> 30min) (%)", "Late delivery penalty (> 30min) (%)", "5", "0", "30", "%", ParameterType.PERCENTAGE, "Commission penalty for unexcused late deliveries"),
                        param("bonus_rating_threshold", "Note minimale déclenchant le bonus", "Rating threshold for bonus", "4.5", "3.0", "5.0", "/5", ParameterType.DECIMAL, "Minimum rating needed to trigger the rating bonus"),
                        param("bonus_rating_pct", "Bonus note élevée (%)", "High rating bonus (%)", "5", "0", "20", "%", ParameterType.PERCENTAGE, "Additional bonus when deliverer rating exceeds threshold"),
                        param("minimum_commission", "Commission minimum garantie", "Guaranteed minimum commission", "300", "0", "5000", "XAF", ParameterType.MONEY, "Minimum commission the deliverer receives regardless of calculation")
                ),
                "DELIVERER_BASE_COMMISSION(60%)\nDELIVERER_MINIMUM_COMMISSION(300 XAF)"
        );
    }

    // ─── Parameter builder helper ──────────────────────────────────────────

    private TemplateParameter param(String key, String labelFr, String labelEn,
                                     String defaultValue, String minValue, String maxValue,
                                     String unit, ParameterType type, String helpText) {
        return TemplateParameter.builder()
                .key(key).labelFr(labelFr).labelEn(labelEn)
                .defaultValue(defaultValue).minValue(minValue).maxValue(maxValue)
                .unit(unit).type(type).helpText(helpText)
                .build();
    }
}
