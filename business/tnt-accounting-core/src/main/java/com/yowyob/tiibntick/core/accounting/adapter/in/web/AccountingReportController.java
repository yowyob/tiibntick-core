package com.yowyob.tiibntick.core.accounting.adapter.in.web;

import com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.response.FinancialStatementResponse;
import com.yowyob.tiibntick.core.accounting.application.service.AccountingApplicationService;
import com.yowyob.tiibntick.core.accounting.domain.model.StatementType;
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
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) String period) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        return service.getTrialBalance(tenantId, ym)
                .map(s -> ResponseEntity.ok(FinancialStatementResponse.from(s)));
    }

    @GetMapping("/income-statement")
    @PreAuthorize("hasAuthority('accounting:read')")
    public Mono<ResponseEntity<FinancialStatementResponse>> getIncomeStatement(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) String period) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        return service.getFinancialStatement(tenantId, ym, StatementType.INCOME_STATEMENT)
                .map(s -> ResponseEntity.ok(FinancialStatementResponse.from(s)));
    }

    @GetMapping("/balance-sheet")
    @PreAuthorize("hasAuthority('accounting:read')")
    public Mono<ResponseEntity<FinancialStatementResponse>> getBalanceSheet(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) String period) {
        YearMonth ym = period != null ? YearMonth.parse(period) : YearMonth.now();
        return service.getFinancialStatement(tenantId, ym, StatementType.BALANCE_SHEET)
                .map(s -> ResponseEntity.ok(FinancialStatementResponse.from(s)));
    }

    @PostMapping("/periods/{year}/{month}/close")
    @PreAuthorize("hasAuthority('accounting:admin')")
    public Mono<ResponseEntity<Void>> closePeriod(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable int year,
            @PathVariable int month) {
        return service.closePeriod(tenantId, year, month, userId)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
