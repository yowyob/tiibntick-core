package com.yowyob.tiibntick.core.accounting.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representing an account in the OHADA chart of accounts.
 * Maintains the current running balance (debit/credit depending on AccountType).
 * Author: MANFOUO Braun
 */
public final class Account {

    private final UUID id;
    private final UUID tenantId;
    private final String code;
    private final String name;
    private final AccountType type;
    private final AccountCategory category;
    private final String currency;
    private final BigDecimal balance;
    private final boolean active;
    private final UUID parentAccountId;
    private final int ohadaClass;
    private final Instant createdAt;
    private final Instant updatedAt;

    // ── : FreelancerOrg-scoped accounts ────────────────────────────────────
    /**
     * UUID of the FreelancerOrganization owning this account.
     * Non-null for accounts of the form 411-FRL-{uuid}, 421-FRL-{uuid}, 706-FRL-{uuid}.
     * Null for standard platform/agency accounts.
     * References tnt-organization-core UUID — pure integration key (no join).
     */
    private final String ownerOrgId;

    /**
     * Type of the owning entity: {@code "FREELANCER_ORG"} or {@code "AGENCY"}.
     * Null for accounts not linked to a specific org (shared platform accounts).
     */
    private final String ownerOrgType;

    private Account(UUID id, UUID tenantId, String code, String name, AccountType type,
                    AccountCategory category, String currency, BigDecimal balance,
                    boolean active, UUID parentAccountId, int ohadaClass,
                    Instant createdAt, Instant updatedAt,
                    String ownerOrgId, String ownerOrgType) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.code = requireText(code, "code");
        this.name = requireText(name, "name");
        this.type = Objects.requireNonNull(type, "type is required");
        this.category = Objects.requireNonNull(category, "category is required");
        this.currency = requireText(currency, "currency").toUpperCase();
        this.balance = Objects.requireNonNull(balance, "balance is required");
        this.active = active;
        this.parentAccountId = parentAccountId;
        this.ohadaClass = ohadaClass;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        this.ownerOrgId = ownerOrgId;
        this.ownerOrgType = ownerOrgType;
    }

    public static Account create(UUID tenantId, String code, String name, AccountType type,
                                  String currency, UUID parentAccountId) {
        AccountCategory category = AccountCategory.fromAccountCode(code);
        int ohadaClass = Character.getNumericValue(code.charAt(0));
        Instant now = Instant.now();
        return new Account(UUID.randomUUID(), tenantId, code, name, type, category,
                currency, BigDecimal.ZERO, true, parentAccountId, ohadaClass, now, now, null, null);
    }

    public static Account rehydrate(UUID id, UUID tenantId, String code, String name, AccountType type,
                                     AccountCategory category, String currency, BigDecimal balance,
                                     boolean active, UUID parentAccountId, int ohadaClass,
                                     Instant createdAt, Instant updatedAt) {
        return new Account(id, tenantId, code, name, type, category, currency, balance,
                active, parentAccountId, ohadaClass, createdAt, updatedAt, null, null);
    }

    /**
     * Full rehydration factory including  FreelancerOrg ownership context.
     */
    public static Account rehydrateFull(UUID id, UUID tenantId, String code, String name, AccountType type,
                                         AccountCategory category, String currency, BigDecimal balance,
                                         boolean active, UUID parentAccountId, int ohadaClass,
                                         Instant createdAt, Instant updatedAt,
                                         String ownerOrgId, String ownerOrgType) {
        return new Account(id, tenantId, code, name, type, category, currency, balance,
                active, parentAccountId, ohadaClass, createdAt, updatedAt, ownerOrgId, ownerOrgType);
    }

    /**
     * Creates a FreelancerOrg-scoped account ().
     * Used by InitializeFreelancerOrgAccountsUseCase to seed the 3 org-specific accounts.
     *
     * @param tenantId    tenant scope
     * @param code        OHADA account code (e.g., "411-FRL-{orgId}")
     * @param name        display name (e.g., "Clients FreelancerOrg — Moto Express Biyem")
     * @param type        AccountType (ASSET, LIABILITY, REVENUE)
     * @param currency    currency code (XAF)
     * @param freelancerOrgId the FreelancerOrg UUID (from tnt-organization-core)
     * @return new active Account linked to this FreelancerOrg
     */
    public static Account createForFreelancerOrg(UUID tenantId, String code, String name,
                                                   AccountType type, String currency,
                                                   String freelancerOrgId) {
        String digitsOnly = code.replaceAll("[^0-9]", "");
        String accountCodePrefix = digitsOnly.substring(0, Math.min(3, digitsOnly.length()));
        
        AccountCategory category = AccountCategory.fromAccountCode(accountCodePrefix);
        int ohadaClass = Character.getNumericValue(code.charAt(0));
        Instant now = Instant.now();
        return new Account(UUID.randomUUID(), tenantId, code, name, type, category,
                currency, BigDecimal.ZERO, true, null, ohadaClass, now, now,
                freelancerOrgId, "FREELANCER_ORG");
    }

    /**
     * Returns a new Account with the balance updated by a debit posting.
     * Debiting an asset/expense account increases its balance;
     * debiting a liability/equity/revenue account decreases it.
     */
    public Account debit(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        BigDecimal next = type.isDebitNormal() ? balance.add(amount) : balance.subtract(amount);
        return withBalance(next);
    }

    /**
     * Returns a new Account with the balance updated by a credit posting.
     */
    public Account credit(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        BigDecimal next = type.isCreditNormal() ? balance.add(amount) : balance.subtract(amount);
        return withBalance(next);
    }

    public Account deactivate() {
        return new Account(id, tenantId, code, name, type, category, currency, balance,
                false, parentAccountId, ohadaClass, createdAt, Instant.now(), ownerOrgId, ownerOrgType);
    }

    public Account rename(String newName) {
        return new Account(id, tenantId, code, newName, type, category, currency, balance,
                active, parentAccountId, ohadaClass, createdAt, Instant.now(), ownerOrgId, ownerOrgType);
    }

    private Account withBalance(BigDecimal newBalance) {
        return new Account(id, tenantId, code, name, type, category, currency, newBalance,
                active, parentAccountId, ohadaClass, createdAt, Instant.now(), ownerOrgId, ownerOrgType);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public AccountType getType() { return type; }
    public AccountCategory getCategory() { return category; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public boolean isActive() { return active; }
    public UUID getParentAccountId() { return parentAccountId; }
    public int getOhadaClass() { return ohadaClass; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    //  getters
    public String getOwnerOrgId() { return ownerOrgId; }
    public String getOwnerOrgType() { return ownerOrgType; }
    public boolean isFreelancerOrgAccount() { return ownerOrgId != null && "FREELANCER_ORG".equals(ownerOrgType); }
}
