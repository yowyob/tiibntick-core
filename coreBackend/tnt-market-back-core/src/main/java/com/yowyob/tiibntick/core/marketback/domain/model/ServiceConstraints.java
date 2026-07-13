package com.yowyob.tiibntick.core.marketback.domain.model;

/**
 * Value Object — constraints of a ServiceOffer (what the offer can handle).
 * @author MANFOUO Braun
 */
public record ServiceConstraints(
        double maxWeightKg,
        double maxLengthCm,
        double maxWidthCm,
        double maxHeightCm,
        double maxValueXaf,
        boolean acceptsFragile,
        boolean acceptsPerishable,
        boolean acceptsHazardous,
        boolean requiresInsurance,
        double maxDistanceKm
) {
    public boolean canHandle(ParcelSpec parcel) {
        if (parcel.weightKg() > maxWeightKg) return false;
        if (parcel.lengthCm() > maxLengthCm) return false;
        if (parcel.widthCm()  > maxWidthCm)  return false;
        if (parcel.heightCm() > maxHeightCm) return false;
        if (parcel.valueXaf() > maxValueXaf) return false;
        if (parcel.fragile()    && !acceptsFragile)    return false;
        if (parcel.perishable() && !acceptsPerishable) return false;
        return true;
    }
}
