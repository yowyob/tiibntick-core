package com.yowyob.tiibntick.core.accounting.adapter.in.web;

import com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.request.CreateAccountRequest;
import com.yowyob.tiibntick.core.accounting.adapter.in.web.dto.response.AccountResponse;
import com.yowyob.tiibntick.core.accounting.application.port.in.CreateAccountCommand;
import com.yowyob.tiibntick.core.accounting.application.service.AccountingApplicationService;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing the OHADA chart of accounts.
 * Base path: /api/accounting/accounts
 * Author: MANFOUO Braun
 */
@RestController
@RequestMapping("/api/accounting/accounts")
public class AccountController {

    private final AccountingApplicationService service;

    public AccountController(AccountingApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('accounting:write')")
    public Mono<ResponseEntity<AccountResponse>> createAccount(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader("X-Organization-Id") UUID organizationId,
            @Valid @RequestBody CreateAccountRequest req) {
        CreateAccountCommand cmd = new CreateAccountCommand(currentUser.tenantId(), organizationId,
                req.code(), req.name(), req.type(), req.currency(), req.parentAccountId());
        return service.createAccount(cmd)
                .map(a -> ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(a)));
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAuthority('accounting:read')")
    public Mono<ResponseEntity<AccountResponse>> getAccount(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID accountId) {
        return service.getAccount(currentUser.tenantId(), accountId)
                .map(a -> ResponseEntity.ok(AccountResponse.from(a)));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAuthority('accounting:read')")
    public Mono<ResponseEntity<AccountResponse>> getAccountByCode(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String code) {
        return service.getAccountByCode(currentUser.tenantId(), code)
                .map(a -> ResponseEntity.ok(AccountResponse.from(a)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('accounting:read')")
    public Mono<ResponseEntity<List<AccountResponse>>> listAccounts(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.listAccounts(currentUser.tenantId(), activeOnly)
                .map(AccountResponse::from)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @PostMapping("/initialize-ohada")
    @PreAuthorize("hasAuthority('accounting:admin')")
    public Mono<ResponseEntity<Void>> initializeOhada(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam(defaultValue = "XAF") String currency) {
        return service.initializeForTenant(currentUser.tenantId(), currency)
                .thenReturn(ResponseEntity.noContent().build());
    }
    // ── : FreelancerOrg account initialization ─────────────────────────────

    /**
     * POST /api/accounting/accounts/freelancer-org/{orgId}/initialize
     *
     * <p>Initializes the 3 OHADA accounts for a verified FreelancerOrg.
     * Idempotent: returns existing accounts if already initialized.
     * Called by tnt-administration-core when KYC is approved.
     *
     * @param currentUser      resolves the tenant scope from the JWT
     * @param freelancerOrgId  the FreelancerOrg UUID (from tnt-organization-core)
     * @param orgTradeName     the org's commercial trade name
     * @param currency         currency code (default: XAF)
     */
    @PostMapping("/freelancer-org/{freelancerOrgId}/initialize")
    @PreAuthorize("hasAnyAuthority('accounting:write', 'tnt:platform:admin')")
    public Flux<AccountResponse> initializeFreelancerOrgAccounts(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String freelancerOrgId,
            @RequestParam String orgTradeName,
            @RequestParam(defaultValue = "XAF") String currency) {
        return service.initializeFreelancerOrgAccounts(currentUser.tenantId(), freelancerOrgId, orgTradeName, currency)
                .map(AccountResponse::from);
    }

    /**
     * GET /api/accounting/accounts/freelancer-org/{orgId}
     *
     * <p>Returns all OHADA accounts owned by a FreelancerOrg ().
     */
    @GetMapping("/freelancer-org/{freelancerOrgId}")
    @PreAuthorize("hasAnyAuthority('accounting:read', 'tnt:platform:admin')")
    public Flux<AccountResponse> getFreelancerOrgAccounts(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable String freelancerOrgId) {
        return service.findAccountsByOwnerOrg(currentUser.tenantId(), freelancerOrgId)
                .map(AccountResponse::from);
    }

}