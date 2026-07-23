package com.yowyob.tiibntick.core.accounting.application.service;

import com.yowyob.tiibntick.core.accounting.application.port.in.*;
import com.yowyob.tiibntick.core.accounting.domain.model.Account;
import com.yowyob.tiibntick.core.accounting.application.port.out.*;
import com.yowyob.tiibntick.core.accounting.domain.event.AccountingPeriodClosedEvent;
import com.yowyob.tiibntick.core.accounting.domain.event.JournalEntryPostedEvent;
import com.yowyob.tiibntick.core.accounting.domain.model.*;
import com.yowyob.tiibntick.core.accounting.domain.service.JournalBalanceValidator;
import com.yowyob.tiibntick.core.accounting.domain.service.OhadaChartOfAccountsInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Central application service for tnt-accounting-core.
 *
 * <p>Orchestrates use cases: chart of accounts management, OHADA journal entry posting
 * with balance validation, financial reporting, and period lifecycle management.</p>
 *
 * <p><b>Kernel integration:</b> When posting a journal entry, this service optionally
 * queries the Kernel (RT-comops-accounting-core) via {@link KernelAccountingPort} to
 * resolve a {@code kernelInvoiceId} link. The Kernel lookup is best-effort and
 * non-blocking — journal entries are always posted even when no Kernel link exists.</p>
 *
 * @author MANFOUO Braun
 */
@Service
public class AccountingApplicationService implements
        CreateAccountUseCase,
        PostJournalEntryUseCase,
        GetAccountUseCase,
        ListAccountsUseCase,
        InitializeOhadaChartUseCase,
        InitializeFreelancerOrgAccountsUseCase,
        GetTrialBalanceUseCase,
        GetFinancialStatementUseCase,
        CloseAccountingPeriodUseCase,
        GetJournalEntryUseCase {

    private static final Logger log = LoggerFactory.getLogger(AccountingApplicationService.class);

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AccountingEventPublisher eventPublisher;
    private final JournalSequencePort journalSequencePort;
    private final JournalBalanceValidator balanceValidator;
    private final OhadaChartOfAccountsInitializer chartInitializer;

    /**
     * Outbound port to the Yowyob Kernel accounting domain.
     * Optional — used during journal entry posting for best-effort Kernel link resolution.
     */
    private final KernelAccountingPort kernelAccountingPort;

    public AccountingApplicationService(
            AccountRepository accountRepository,
            JournalEntryRepository journalEntryRepository,
            AccountingPeriodRepository accountingPeriodRepository,
            AccountingEventPublisher eventPublisher,
            JournalSequencePort journalSequencePort,
            JournalBalanceValidator balanceValidator,
            OhadaChartOfAccountsInitializer chartInitializer,
            KernelAccountingPort kernelAccountingPort) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.eventPublisher = eventPublisher;
        this.journalSequencePort = journalSequencePort;
        this.balanceValidator = balanceValidator;
        this.chartInitializer = chartInitializer;
        this.kernelAccountingPort = kernelAccountingPort;
    }

    // ─── CreateAccountUseCase ─────────────────────────────────────────────────

    @Override
    public Mono<Account> createAccount(CreateAccountCommand command) {
        return accountRepository.existsByCode(command.tenantId(), command.code())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalStateException(
                                "Account code already exists: " + command.code()));
                    }
                    Account account = Account.create(command.tenantId(), command.code(), command.name(),
                            command.type(), command.currency(), command.parentAccountId());
                    return accountRepository.save(account);
                });
    }

    // ─── PostJournalEntryUseCase ──────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Generates a unique journal number, optionally resolves a Kernel invoice link
     * (best-effort — failure proceeds without a link), validates balance, posts, and
     * persists the entry. Account balances are updated reactively after posting.</p>
     */
    @Transactional
    @Override
    public Mono<JournalEntry> postJournalEntry(PostJournalEntryCommand command) {
        int year = LocalDate.now().getYear();
        return journalSequencePort.nextSequence(command.tenantId(), year)
                .flatMap(seq -> {
                    JournalNumber number = JournalNumber.generate(
                            command.tenantId().toString().substring(0, 3).toUpperCase(), seq);
                    List<JournalEntryLine> lines = buildLines(command.lines());

                    // Optionally resolve a Kernel invoice link from the referenceId
                    Mono<java.util.Optional<UUID>> kernelInvoiceIdMono = resolveKernelInvoiceId(
                            command.tenantId(), command.referenceId());

                    return kernelInvoiceIdMono.flatMap(kernelInvoiceIdOpt -> {
                        UUID kernelInvoiceId = kernelInvoiceIdOpt.orElse(null);
                        JournalEntry draft = kernelInvoiceId != null
                                ? JournalEntry.createWithKernelInvoice(
                                        command.tenantId(), command.organizationId(), number,
                                        command.type(), command.referenceType(), command.referenceId(),
                                        lines, command.description(), command.createdByUserId(),
                                        kernelInvoiceId)
                                : JournalEntry.create(
                                        command.tenantId(), command.organizationId(), number,
                                        command.type(), command.referenceType(), command.referenceId(),
                                        lines, command.description(), command.createdByUserId());

                        balanceValidator.validateOrThrow(draft);
                        JournalEntry posted = draft.validate().post();
                        return journalEntryRepository.save(posted);
                    });
                })
                .flatMap(saved -> updateAccountBalances(saved)
                        .then(eventPublisher.publishJournalEntryPosted(JournalEntryPostedEvent.of(saved)))
                        .thenReturn(saved));
    }

    /**
     * Attempts to resolve a Kernel invoice UUID from the billing referenceId.
     * Returns a Mono that always emits (possibly with null value) — never errors.
     */
    private Mono<java.util.Optional<UUID>> resolveKernelInvoiceId(UUID tenantId, String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return Mono.just(java.util.Optional.empty());
        }
        return kernelAccountingPort
                .findInvoiceByReferenceId(tenantId, referenceId)
                .map(dto -> {
                    log.debug("Linked journal entry to Kernel invoiceId={}", dto.kernelInvoiceId());
                    return java.util.Optional.of(dto.kernelInvoiceId());
                })
                .defaultIfEmpty(java.util.Optional.empty())
                .onErrorResume(e -> { log.debug("Kernel invoice lookup failed (non-fatal): {}", e.getMessage()); return Mono.just(java.util.Optional.empty()); });
    }

    private List<JournalEntryLine> buildLines(List<JournalEntryLineCommand> commandLines) {
        List<JournalEntryLine> lines = new ArrayList<>();
        for (int i = 0; i < commandLines.size(); i++) {
            JournalEntryLineCommand cmd = commandLines.get(i);
            lines.add(new JournalEntryLine(i + 1, cmd.accountId(), cmd.accountCode(),
                    cmd.label(), cmd.debitAmount(), cmd.creditAmount(), cmd.currency()));
        }
        return lines;
    }

    private Mono<Void> updateAccountBalances(JournalEntry posted) {
        return Flux.fromIterable(posted.getLines())
                .concatMap(line ->
                        accountRepository.findByCode(posted.getTenantId(), line.accountCode())
                                .flatMap(account -> {
                                    Account updated = line.isDebit()
                                            ? account.debit(line.debitAmount())
                                            : account.credit(line.creditAmount());
                                    return accountRepository.save(updated);
                                })
                                .onErrorResume(e -> Mono.empty()))
                .then();
    }

    // ─── GetAccountUseCase ────────────────────────────────────────────────────

    @Override
    public Mono<Account> getAccount(UUID tenantId, UUID accountId) {
        return accountRepository.findById(tenantId, accountId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId)));
    }

    @Override
    public Mono<Account> getAccountByCode(UUID tenantId, String code) {
        return accountRepository.findByCode(tenantId, code)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found for code: " + code)));
    }

    // ─── ListAccountsUseCase ──────────────────────────────────────────────────

    @Override
    public Flux<Account> listAccounts(UUID tenantId, boolean activeOnly) {
        return accountRepository.findByTenantId(tenantId, activeOnly);
    }

    // ─── InitializeOhadaChartUseCase ──────────────────────────────────────────

    @Override
    public Mono<Void> initializeForTenant(UUID tenantId, String defaultCurrency) {
        return accountRepository.existsByCode(tenantId, "101000")
                .flatMap(alreadySeeded -> {
                    if (alreadySeeded) return Mono.empty();
                    List<Account> accounts = chartInitializer.buildDefaultAccounts(tenantId, defaultCurrency);
                    return accountRepository.saveAll(accounts).then();
                });
    }

    // ─── GetTrialBalanceUseCase ───────────────────────────────────────────────

    @Override
    public Mono<FinancialStatement> getTrialBalance(UUID tenantId, YearMonth period) {
        return accountRepository.findByTenantId(tenantId, true)
                .map(TrialBalanceLine::from)
                .collectList()
                .map(lines -> {
                    BigDecimal totalDebit = lines.stream()
                            .map(TrialBalanceLine::debitBalance)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalCredit = lines.stream()
                            .map(TrialBalanceLine::creditBalance)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    List<FinancialLine> fLines = lines.stream()
                            .map(tbl -> new FinancialLine(tbl.accountCode(),
                                    tbl.accountName() + " (" + tbl.accountCode() + ")",
                                    tbl.netBalance(), tbl.category().name()))
                            .toList();
                    return FinancialStatement.generate(tenantId, StatementType.TRIAL_BALANCE,
                            period, "XAF", fLines, totalDebit, totalCredit);
                });
    }

    // ─── GetFinancialStatementUseCase ─────────────────────────────────────────

    @Override
    public Mono<FinancialStatement> getFinancialStatement(UUID tenantId, YearMonth period, StatementType type) {
        return switch (type) {
            case TRIAL_BALANCE -> getTrialBalance(tenantId, period);
            case INCOME_STATEMENT -> buildIncomeStatement(tenantId, period);
            case BALANCE_SHEET -> buildBalanceSheet(tenantId, period);
            default -> Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported statement type: " + type));
        };
    }

    private Mono<FinancialStatement> buildIncomeStatement(UUID tenantId, YearMonth period) {
        return accountRepository.findByTenantId(tenantId, true).collectList().map(accounts -> {
            List<FinancialLine> lines = new ArrayList<>();
            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal totalExpense = BigDecimal.ZERO;
            for (Account a : accounts) {
                if (a.getType() == AccountType.REVENUE || a.getType() == AccountType.CONTRA_REVENUE) {
                    lines.add(new FinancialLine(a.getCode(), a.getName(), a.getBalance(), "REVENUE"));
                    totalRevenue = totalRevenue.add(a.getBalance());
                } else if (a.getType() == AccountType.EXPENSE) {
                    lines.add(new FinancialLine(a.getCode(), a.getName(), a.getBalance(), "EXPENSE"));
                    totalExpense = totalExpense.add(a.getBalance());
                }
            }
            return FinancialStatement.generate(tenantId, StatementType.INCOME_STATEMENT,
                    period, "XAF", lines, totalExpense, totalRevenue);
        });
    }

    private Mono<FinancialStatement> buildBalanceSheet(UUID tenantId, YearMonth period) {
        return accountRepository.findByTenantId(tenantId, true).collectList().map(accounts -> {
            List<FinancialLine> lines = new ArrayList<>();
            BigDecimal totalAssets = BigDecimal.ZERO;
            BigDecimal totalLiabilities = BigDecimal.ZERO;
            for (Account a : accounts) {
                if (a.getType() == AccountType.ASSET || a.getType() == AccountType.CONTRA_ASSET) {
                    lines.add(new FinancialLine(a.getCode(), a.getName(), a.getBalance(), "ASSET"));
                    totalAssets = totalAssets.add(a.getBalance());
                } else if (a.getType() == AccountType.LIABILITY || a.getType() == AccountType.EQUITY) {
                    lines.add(new FinancialLine(a.getCode(), a.getName(), a.getBalance(), "LIABILITY_EQUITY"));
                    totalLiabilities = totalLiabilities.add(a.getBalance());
                }
            }
            return FinancialStatement.generate(tenantId, StatementType.BALANCE_SHEET,
                    period, "XAF", lines, totalAssets, totalLiabilities);
        });
    }

    // ─── CloseAccountingPeriodUseCase ─────────────────────────────────────────

    @Transactional
    @Override
    public Mono<AccountingPeriod> closePeriod(UUID tenantId, int year, int month, String closedByUserId) {
        return accountingPeriodRepository.findByTenantIdAndYearAndMonth(tenantId, year, month)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Accounting period not found: " + year + "-" + month)))
                .map(AccountingPeriod::close)
                .flatMap(accountingPeriodRepository::save)
                .flatMap(closed -> eventPublisher
                        .publishPeriodClosed(AccountingPeriodClosedEvent.of(closed))
                        .thenReturn(closed));
    }

    // ─── GetJournalEntryUseCase ───────────────────────────────────────────────

    @Override
    public Mono<JournalEntry> getJournalEntry(UUID tenantId, UUID journalEntryId) {
        return journalEntryRepository.findById(tenantId, journalEntryId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "JournalEntry not found: " + journalEntryId)));
    }

    @Override
    public Flux<JournalEntry> listJournalEntries(UUID tenantId, UUID organizationId, YearMonth period) {
        return journalEntryRepository.findByTenantIdAndOrganizationIdAndPeriod(
                tenantId, organizationId, period);
    }
    // ── : FreelancerOrg account initialization ─────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Seeds 3 OHADA accounts for the FreelancerOrg. Idempotent — existing
     * accounts (identified by code) are returned without creating duplicates.
     */
    @Override
    public Flux<Account> initializeFreelancerOrgAccounts(UUID tenantId, String freelancerOrgId,
                                                          String orgTradeName, String defaultCurrency) {
        log.info("Initializing FreelancerOrg OHADA accounts for orgId={} tenant={}", freelancerOrgId, tenantId);
        java.util.List<Account> seeds = chartInitializer.buildFreelancerOrgAccounts(
                tenantId, freelancerOrgId, orgTradeName, defaultCurrency);

        return Flux.fromIterable(seeds)
                .flatMap(account -> accountRepository.existsByCode(tenantId, account.getCode())
                        .flatMap(exists -> {
                            if (Boolean.TRUE.equals(exists)) {
                                log.debug("Account {} already exists for org={}, skipping", account.getCode(), freelancerOrgId);
                                return accountRepository.findByCode(tenantId, account.getCode());
                            }
                            return accountRepository.save(account)
                                    .doOnSuccess(a -> log.debug("Created FreelancerOrg account: {} ({})", a.getCode(), a.getName()));
                        }));
    }

    /**
     * Returns all OHADA accounts owned by a specific FreelancerOrg ().
     */
    public reactor.core.publisher.Flux<Account> findAccountsByOwnerOrg(UUID tenantId, String freelancerOrgId) {
        return accountRepository.findByOwnerOrgId(tenantId, freelancerOrgId);
    }

}