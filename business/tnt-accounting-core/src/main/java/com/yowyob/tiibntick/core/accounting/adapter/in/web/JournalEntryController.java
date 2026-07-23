package com.yowyob.tiibntick.core.accounting.adapter.in.web;

import com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.request.PostJournalEntryRequest;
import com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.response.JournalEntryResponse;
import com.yowyob.tiibntick.core.accounting.application.port.in.JournalEntryLineCommand;
import com.yowyob.tiibntick.core.accounting.application.port.in.PostJournalEntryCommand;
import com.yowyob.tiibntick.core.accounting.application.service.AccountingApplicationService;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for journal entries management.
 * Base path: /api/accounting/journal-entries
 * Author: MANFOUO Braun
 */
@RestController
@RequestMapping("/api/accounting/journal-entries")
public class JournalEntryController {

    private final AccountingApplicationService service;

    public JournalEntryController(AccountingApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('accounting:write')")
    public Mono<ResponseEntity<JournalEntryResponse>> postJournalEntry(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader("X-Organization-Id") UUID organizationId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody PostJournalEntryRequest req) {
        List<JournalEntryLineCommand> lines = req.lines().stream()
                .map(l -> new JournalEntryLineCommand(l.accountId(), l.accountCode(), l.label(),
                        l.debitAmount() != null ? l.debitAmount() : java.math.BigDecimal.ZERO,
                        l.creditAmount() != null ? l.creditAmount() : java.math.BigDecimal.ZERO,
                        l.currency()))
                .toList();
        PostJournalEntryCommand cmd = new PostJournalEntryCommand(
                currentUser.tenantId(), organizationId, req.type(), req.referenceType(), req.referenceId(),
                lines, req.description(), userId);
        return service.postJournalEntry(cmd)
                .map(e -> ResponseEntity.status(HttpStatus.CREATED).body(JournalEntryResponse.from(e)));
    }

    @GetMapping("/{journalEntryId}")
    @PreAuthorize("hasAuthority('accounting:read')")
    public Mono<ResponseEntity<JournalEntryResponse>> getJournalEntry(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID journalEntryId) {
        return service.getJournalEntry(currentUser.tenantId(), journalEntryId)
                .map(e -> ResponseEntity.ok(JournalEntryResponse.from(e)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('accounting:read')")
    public Mono<ResponseEntity<List<JournalEntryResponse>>> listJournalEntries(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader("X-Organization-Id") UUID organizationId,
            @RequestParam(required = false) String period) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        return service.listJournalEntries(currentUser.tenantId(), organizationId, ym)
                .map(JournalEntryResponse::from)
                .collectList()
                .map(ResponseEntity::ok);
    }
}
