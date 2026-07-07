package com.yowyob.tiibntick.core.tp.application.port.in.command;

import com.yowyob.tiibntick.core.tp.domain.model.enums.TntThirdPartyRole;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Command to register a new TiiBnTick client profile for an existing kernel ThirdParty.
 *
 * @author MANFOUO Braun
 */
public record RegisterTntClientProfileCommand(
        @NotNull UUID tenantId,
        @NotNull UUID thirdPartyId,
        @NotNull Set<TntThirdPartyRole> roles,
        String preferredLocale,
        String preferredCurrency
) {
    public RegisterTntClientProfileCommand {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (thirdPartyId == null) throw new IllegalArgumentException("thirdPartyId is required");
        if (roles == null || roles.isEmpty()) throw new IllegalArgumentException("at least one role is required");
        if (preferredLocale == null) preferredLocale = "fr";
        if (preferredCurrency == null) preferredCurrency = "XAF";
    }
}
