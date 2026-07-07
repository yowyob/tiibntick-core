package com.yowyob.tiibntick.core.tp.adapter.in.web.dto.request;

import com.yowyob.tiibntick.core.tp.domain.model.enums.TntThirdPartyRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * HTTP request body for registering a new TiiBnTick client profile.
 *
 * @author MANFOUO Braun
 */
public record RegisterClientProfileRequest(
        @NotNull UUID thirdPartyId,
        @NotEmpty Set<TntThirdPartyRole> roles,
        String preferredLocale,
        String preferredCurrency
) {}
