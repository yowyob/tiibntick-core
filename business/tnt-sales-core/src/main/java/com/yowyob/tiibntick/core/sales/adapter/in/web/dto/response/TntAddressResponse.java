package com.yowyob.tiibntick.core.sales.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.sales.domain.model.TntAddress;

/**
 * API response DTO for a TntAddress.
 * Author: MANFOUO Braun
 */
public record TntAddressResponse(
        String street, String quartier, String city, String country,
        String landmark, Double latitude, Double longitude,
        String recipientName, String recipientPhone) {

    public static TntAddressResponse from(TntAddress a) {
        if (a == null) return null;
        return new TntAddressResponse(a.street(), a.quartier(), a.city(), a.country(),
                a.landmark(), a.latitude(), a.longitude(), a.recipientName(), a.recipientPhone());
    }
}
