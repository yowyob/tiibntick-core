package com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.request;

import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.util.Map;

/**
 * REST request DTO for computing a price preview from a billing template.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Data
@Schema(description = "Request to preview a price estimate from a billing policy template")
public class PreviewPriceRequest {

    @NotBlank
    @Schema(description = "Catalog template code", example = "TPL-FRAGILE")
    private String templateCode;

    @NotNull
    @Schema(description = "Type of the requesting actor", example = "FREELANCER_ORG")
    private PolicyOwnerType ownerType;

    @Schema(description = "Optional parameter overrides for the simulation",
            example = "{\"basePrice\": \"800\", \"fragile_surcharge_pct\": \"25\"}")
    private Map<String, String> customizedParameters;

    // ─── Sample scenario ───────────────────────────────────────────────────

    @PositiveOrZero
    @Schema(description = "Simulated delivery distance in km", example = "8.5")
    private double distanceKm = 5.0;

    @PositiveOrZero
    @Schema(description = "Simulated package weight in kg", example = "3.0")
    private double weightKg = 1.0;

    @Schema(description = "Package type", example = "FRAGILE",
            allowableValues = {"STANDARD", "FRAGILE", "PERISHABLE", "REFRIGERATED", "PHARMACEUTICAL",
                               "OVERSIZED", "LUXURY", "DOCUMENTS", "LIVE_ANIMALS", "HAZARDOUS_DECLARED"})
    private String packageType = "STANDARD";

    @Schema(description = "Delivery priority", example = "STANDARD",
            allowableValues = {"STANDARD", "EXPRESS", "URGENT", "PRIORITY"})
    private String priority = "STANDARD";

    @Schema(description = "Client's transaction count (last 30 days) for loyalty rules", example = "5")
    private int clientTransactionCount = 0;

    @Schema(description = "Delivery zone type", example = "URBAN",
            allowableValues = {"URBAN", "PERI_URBAN", "RURAL", "DIPLOMATIC", "PORT_ZONE"})
    private String deliveryZoneType = "URBAN";

    @Schema(description = "Zone access difficulty", example = "LOW",
            allowableValues = {"LOW", "MEDIUM", "HIGH", "VERY_HIGH"})
    private String zoneAccessDifficulty = "LOW";

    @Schema(description = "Weather condition", example = "CLEAR",
            allowableValues = {"CLEAR", "RAIN_LIGHT", "RAIN_HEAVY", "FLOOD", "STORM", "HEAT_EXTREME"})
    private String weatherCondition = "CLEAR";

    @Schema(description = "Payment method", example = "PREPAID",
            allowableValues = {"PREPAID", "CASH_ON_DELIVERY", "WALLET", "MOBILE_MONEY"})
    private String paymentMethod = "PREPAID";

    @Schema(description = "Whether active refrigeration is required", example = "false")
    private boolean requiresRefrigeration = false;

    @Schema(description = "Whether assembly/installation is required at delivery", example = "false")
    private boolean requiresAssembly = false;

    @Schema(description = "Whether recipient ID check is required", example = "false")
    private boolean requiresIDCheck = false;

    @Schema(description = "Time of day in HH:mm format", example = "10:00")
    private String timeOfDay = "10:00";

    @Schema(description = "Day of week", example = "TUESDAY",
            allowableValues = {"MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"})
    private String dayOfWeek = "TUESDAY";

    @Schema(description = "Whether today is a public holiday", example = "false")
    private boolean publicHoliday = false;

    @Schema(description = "Storage hours (for HUB templates)", example = "0")
    private int storageHours = 0;

    @Schema(description = "Network hop count (for LINK/NETWORK templates)", example = "1")
    private int networkHopCount = 1;

    @Schema(description = "Declared value of the package in XAF (for high-value surcharge)", example = "0")
    private double declaredValueXaf = 0.0;

    @Schema(description = "Delivery attempt number (1=first, 2=re-delivery...)", example = "1")
    private int deliveryAttemptNumber = 1;

    @Schema(description = "Number of parcels in the mission", example = "1")
    private int packageCount = 1;
}
