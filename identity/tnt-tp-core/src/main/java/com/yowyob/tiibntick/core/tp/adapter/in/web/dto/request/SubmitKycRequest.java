package com.yowyob.tiibntick.core.tp.adapter.in.web.dto.request;

import com.yowyob.tiibntick.core.tp.domain.model.enums.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * HTTP request body for submitting KYC documents.
 *
 * @author MANFOUO Braun
 */
public record SubmitKycRequest(
        @NotNull DocumentType documentType,
        @NotBlank String documentStorageKey,
        String selfieStorageKey,
        String documentNumber,
        LocalDate documentExpiryDate
) {}
