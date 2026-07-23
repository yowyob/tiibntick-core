package com.yowyob.tiibntick.core.accounting.adapter.in.web;

import com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.response.FinancialStatementResponse;
import com.yowyob.tiibntick.core.accounting.application.service.AccountingApplicationService;
import com.yowyob.tiibntick.core.accounting.domain.model.StatementType;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.UUID;

/**
 * REST controller for financial reporting (trial balance, P&amp;L, balance sheet).
 * Base path: /api/accounting/reports
 * Author: MANFOUO Braun
 */
@RestController
@RequestMapping("/api/accounting/reports")
public class AccountingReportController {

    private final AccountingApplicationService service;

    public AccountingReportController(AccountingApplicationService service) {
        this.service = service;
    }

    @GetMapping("/trial-balance")
    @PreAuthorize("hasAuthority('accounting:read')")
    public Mono<ResponseEntity<FinancialStatementResponse>> getTrialBalance(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam(required = false) String period) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        return service.getTrialBalance(currentUser.tenantId(), ym)
                .map(s -> ResponseEntity.ok(FinancialStatementResponse.from(s)));
    }

    @GetMapping("/income-statement")
    @PreAuthorize("hasAuthority('accounting:read')")
    public Mono<ResponseEntity<FinancialStatementResponse>> getIncomeStatement(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam(required = false) String period) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        return service.getFinancialStatement(currentUser.tenantId(), ym, StatementType.INCOME_STATEMENT)
                .map(s -> ResponseEntity.ok(FinancialStatementResponse.from(s)));
    }

    @GetMapping("/balance-sheet")
    @PreAuthorize("hasAuthority('accounting:read')")
    public Mono<ResponseEntity<FinancialStatementResponse>> getBalanceSheet(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam(required = false) String period) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        return service.getFinancialStatement(currentUser.tenantId(), ym, StatementType.BALANCE_SHEET)
                .map(s -> ResponseEntity.ok(FinancialStatementResponse.from(s)));
    }

    @PostMapping("/periods/{year}/{month}/close")
    @PreAuthorize("hasAuthority('accounting:admin')")
    public Mono<ResponseEntity<Void>> closePeriod(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable int year,
            @PathVariable int month) {
        return service.closePeriod(currentUser.tenantId(), year, month, userId)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
