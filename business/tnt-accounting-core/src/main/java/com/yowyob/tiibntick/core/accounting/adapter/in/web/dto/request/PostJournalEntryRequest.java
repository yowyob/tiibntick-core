package com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.request;

import com.yowyob.tiibntick.core.accounting.domain.model.JournalType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * HTTP request body for posting a journal entry.
 * Author: MANFOUO Braun
 */
public record PostJournalEntryRequest(
        @NotNull JournalType type,
        String referenceType,
        String referenceId,
        @NotEmpty @Valid List<JournalEntryLineRequest> lines,
        String description
) {}
