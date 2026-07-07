package com.yowyob.tiibntick.core.sales.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * HTTP request body representing a delivery/billing address.
 * Adapted to Cameroonian informal context with quartier and landmark.
 * Author: MANFOUO Braun
 */
public record DeliveryAddressRequest(
        String street,
        String quartier,
        @NotBlank String city,
        @NotBlank String country,
        String landmark,
        Double latitude,
        Double longitude,
        String recipientName,
        String recipientPhone
) {}
