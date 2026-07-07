package com.yowyob.tiibntick.core.accounting.application.port.in;

import com.yowyob.tiibntick.core.accounting.domain.model.JournalType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Command to validate and post a balanced journal entry.
 * Author: MANFOUO Braun
 */
public record PostJournalEntryCommand(
        @NotNull UUID tenantId,
        @NotNull UUID organizationId,
        @NotNull JournalType type,
        String referenceType,
        String referenceId,
        @NotEmpty @Valid List<JournalEntryLineCommand> lines,
        String description,
        @NotBlank String createdByUserId
) {}
