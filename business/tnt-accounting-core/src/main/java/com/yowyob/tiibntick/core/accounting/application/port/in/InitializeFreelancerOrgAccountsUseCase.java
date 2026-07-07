package com.yowyob.tiibntick.core.accounting.application.port.in;

import com.yowyob.tiibntick.core.accounting.domain.model.Account;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Use case — Seed the 3 OHADA accounts for a newly verified FreelancerOrganization.
 *
 * <p>Called when a FreelancerOrg is verified (KYC approved) by the admin.
 * Creates 3 org-specific accounts:
 * <ul>
 *   <li>{@code 411-FRL-{id}} — Clients FreelancerOrg (accounts receivable)</li>
 *   <li>{@code 421-FRL-{id}} — Sub-Deliverers rémunérations (accounts payable)</li>
 *   <li>{@code 706-FRL-{id}} — Revenus de prestations (revenue)</li>
 * </ul>
 *
 * <p>Idempotent: already-existing accounts are skipped (identified by account code).
 *
 * <p> — New use case introduced with the FreelancerOrganization model.
 *
 * @author MANFOUO Braun
 */
public interface InitializeFreelancerOrgAccountsUseCase {

    /**
     * Initializes (or verifies existence of) the 3 FreelancerOrg OHADA accounts.
     *
     * @param tenantId        tenant scope for this FreelancerOrg
     * @param freelancerOrgId the FreelancerOrg UUID from tnt-organization-core
     * @param orgTradeName    commercial trade name for display in account names
     * @param defaultCurrency the default currency (XAF)
     * @return Flux of created (or already existing) accounts
     */
    Flux<Account> initializeFreelancerOrgAccounts(UUID tenantId, String freelancerOrgId,
                                                   String orgTradeName, String defaultCurrency);
}
