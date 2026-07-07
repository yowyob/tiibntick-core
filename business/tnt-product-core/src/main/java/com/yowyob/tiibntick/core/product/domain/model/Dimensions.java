package com.yowyob.tiibntick.core.product.domain.model;

public record Dimensions(
        double lengthCm,
        double widthCm,
        double heightCm
) {
    public Dimensions {
        if (lengthCm <= 0) throw new IllegalArgumentException("lengthCm must be positive");
        if (widthCm <= 0) throw new IllegalArgumentException("widthCm must be positive");
        if (heightCm <= 0) throw new IllegalArgumentException("heightCm must be positive");
    }

    public double volumeM3() {
        return (lengthCm * widthCm * heightCm) / 1_000_000.0;
    }
}
