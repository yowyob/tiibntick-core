package com.yowyob.tiibntick.core.tp.application.port.in.command;

import com.yowyob.tiibntick.core.tp.domain.model.enums.DocumentType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to submit KYC documents for a third party.
 *
 * @author MANFOUO Braun
 */
public record SubmitKycCommand(
        @NotNull UUID tenantId,
        @NotNull UUID thirdPartyId,
        @NotNull DocumentType documentType,
        @NotNull String documentStorageKey,
        String selfieStorageKey,
        String documentNumber,
        LocalDate documentExpiryDate
) {}
