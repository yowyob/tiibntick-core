package com.yowyob.tiibntick.core.product.domain.model;

public record LogisticsProfile(
        boolean requiresRefrigeration,
        boolean requiresFragileHandling,
        boolean isPerishable,
        boolean isSensitive,
        Integer maxStackHeight,
        String packagingType,
        String hazmatClass,
        String specialInstructions
) {

    public static LogisticsProfile standard() {
        return new LogisticsProfile(false, false, false, false, null, "STANDARD", null, null);
    }

    public static LogisticsProfile fragile(String specialInstructions) {
        return new LogisticsProfile(false, true, false, false, 1, "FRAGILE", null, specialInstructions);
    }

    public static LogisticsProfile refrigerated() {
        return new LogisticsProfile(true, false, true, false, null, "COLD_CHAIN", null, "Keep refrigerated 2-8°C");
    }

    public boolean requiresSpecialHandling() {
        return requiresRefrigeration || requiresFragileHandling || isSensitive || hazmatClass != null;
    }

    public boolean matchesServiceType(ServiceType serviceType) {
        return switch (serviceType) {
            case REFRIGERATED -> requiresRefrigeration;
            case FRAGILE_SPECIALIST -> requiresFragileHandling;
            case STANDARD_DELIVERY, EXPRESS_DELIVERY, SAME_DAY_DELIVERY -> !requiresRefrigeration;
            case FREIGHT_HEAVY -> !requiresFragileHandling;
        };
    }
}
