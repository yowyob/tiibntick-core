package com.yowyob.tiibntick.core.accounting.domain.service;

import com.yowyob.tiibntick.core.accounting.domain.model.Account;
import com.yowyob.tiibntick.core.accounting.domain.model.AccountType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain service that provides the standard OHADA plan comptable (chart of accounts)
 * skeleton entries used to initialize a new tenant's accounting system.
 * Only the most commonly used accounts for the TiiBnTick logistics context are seeded.
 * Author: MANFOUO Braun
 */
@Component
public class OhadaChartOfAccountsInitializer {

    public record AccountSeed(String code, String name, AccountType type, String currency) {}

    public List<Account> buildDefaultAccounts(UUID tenantId, String defaultCurrency) {
        List<AccountSeed> seeds = buildSeeds();
        List<Account> accounts = new ArrayList<>();
        for (AccountSeed seed : seeds) {
            String currency = seed.currency() != null ? seed.currency() : defaultCurrency;
            accounts.add(Account.create(tenantId, seed.code(), seed.name(), seed.type(), currency, null));
        }
        return accounts;
    }

    private List<AccountSeed> buildSeeds() {
        return List.of(
                // ─── CLASS 1 — Ressources durables ────────────────────────────
                new AccountSeed("101000", "Capital social", AccountType.EQUITY, null),
                new AccountSeed("111000", "Réserves légales", AccountType.EQUITY, null),
                new AccountSeed("120000", "Report à nouveau (solde créditeur)", AccountType.EQUITY, null),
                new AccountSeed("130000", "Résultat net de l'exercice", AccountType.EQUITY, null),
                new AccountSeed("161000", "Emprunts et dettes financières", AccountType.LIABILITY, null),

                // ─── CLASS 2 — Actif immobilisé ───────────────────────────────
                new AccountSeed("211000", "Terrains", AccountType.ASSET, null),
                new AccountSeed("215000", "Matériel de transport", AccountType.ASSET, null),
                new AccountSeed("218000", "Matériel informatique", AccountType.ASSET, null),
                new AccountSeed("281500", "Amort. matériel de transport", AccountType.CONTRA_ASSET, null),
                new AccountSeed("281800", "Amort. matériel informatique", AccountType.CONTRA_ASSET, null),

                // ─── CLASS 3 — Stocks ─────────────────────────────────────────
                new AccountSeed("300000", "Stocks de marchandises", AccountType.ASSET, null),
                new AccountSeed("310000", "Stocks de matières premières", AccountType.ASSET, null),
                new AccountSeed("370000", "Stocks de produits finis", AccountType.ASSET, null),

                // ─── CLASS 4 — Tiers (AR / AP) ────────────────────────────────
                new AccountSeed("401000", "Fournisseurs", AccountType.LIABILITY, null),
                new AccountSeed("408000", "Fournisseurs — factures non parvenues", AccountType.LIABILITY, null),
                new AccountSeed("411000", "Clients", AccountType.ASSET, null),
                new AccountSeed("419000", "Clients — avances et acomptes", AccountType.LIABILITY, null),
                new AccountSeed("421000", "Personnel — rémunérations dues", AccountType.LIABILITY, null),
                new AccountSeed("431000", "Sécurité sociale", AccountType.LIABILITY, null),
                new AccountSeed("441000", "État — impôts et taxes", AccountType.LIABILITY, null),
                new AccountSeed("447100", "TVA collectée (19,25%)", AccountType.LIABILITY, null),
                new AccountSeed("445200", "TVA déductible", AccountType.ASSET, null),
                new AccountSeed("471000", "Compte de régularisation (transitoire)", AccountType.ASSET, null),
                new AccountSeed("481000", "Compte de liaison — livreurs (commission)", AccountType.LIABILITY, null),

                // ─── CLASS 5 — Trésorerie ─────────────────────────────────────
                new AccountSeed("521000", "Banque — compte principal", AccountType.ASSET, null),
                new AccountSeed("521100", "Mobile Money — MTN MoMo", AccountType.ASSET, null),
                new AccountSeed("521200", "Mobile Money — Orange Money", AccountType.ASSET, null),
                new AccountSeed("571000", "Caisse principale", AccountType.ASSET, null),
                new AccountSeed("580000", "Virements internes", AccountType.ASSET, null),

                // ─── CLASS 6 — Charges ────────────────────────────────────────
                new AccountSeed("601000", "Achats de marchandises", AccountType.EXPENSE, null),
                new AccountSeed("604000", "Achats d'études et prestations", AccountType.EXPENSE, null),
                new AccountSeed("606000", "Achats non stockés de matières", AccountType.EXPENSE, null),
                new AccountSeed("611000", "Transport sur achats", AccountType.EXPENSE, null),
                new AccountSeed("613000", "Locations et charges locatives", AccountType.EXPENSE, null),
                new AccountSeed("615000", "Entretien et réparations", AccountType.EXPENSE, null),
                new AccountSeed("616000", "Primes d'assurance", AccountType.EXPENSE, null),
                new AccountSeed("618000", "Frais de télécommunications", AccountType.EXPENSE, null),
                new AccountSeed("620000", "Charges de personnel", AccountType.EXPENSE, null),
                new AccountSeed("621000", "Rémunérations du personnel", AccountType.EXPENSE, null),
                new AccountSeed("631000", "Commissions sur achats", AccountType.EXPENSE, null),
                new AccountSeed("641000", "Impôts et taxes", AccountType.EXPENSE, null),
                new AccountSeed("651000", "Commissions livreurs", AccountType.EXPENSE, null),
                new AccountSeed("661000", "Intérêts des emprunts", AccountType.EXPENSE, null),
                new AccountSeed("681000", "Dotations aux amortissements", AccountType.EXPENSE, null),

                // ─── CLASS 7 — Produits ───────────────────────────────────────
                new AccountSeed("701000", "Ventes de marchandises", AccountType.REVENUE, null),
                new AccountSeed("704000", "Prestations de services de livraison", AccountType.REVENUE, null),
                new AccountSeed("706000", "Services annexes", AccountType.REVENUE, null),
                new AccountSeed("708000", "Produits des activités annexes", AccountType.REVENUE, null),
                new AccountSeed("770000", "Produits financiers", AccountType.REVENUE, null),
                new AccountSeed("791000", "Subventions d'exploitation", AccountType.REVENUE, null)
        );
    }
    /**
     * Builds the 3 OHADA accounts for a specific FreelancerOrganization ().
     *
     * <p>Account codes follow the pattern: {@code {base}-FRL-{truncatedOrgId}}
     * to remain under the typical 20-char account code limit.
     * <ul>
     *   <li>{@code 411-FRL-{id}} — Clients FreelancerOrg (receivables)</li>
     *   <li>{@code 421-FRL-{id}} — Sub-Deliverers rémunérations (payables)</li>
     *   <li>{@code 706-FRL-{id}} — Revenus de prestations FreelancerOrg (revenue)</li>
     * </ul>
     *
     * @param tenantId        tenant scope
     * @param freelancerOrgId the FreelancerOrg UUID (from tnt-organization-core)
     * @param orgTradeName    commercial trade name for display in account names
     * @param defaultCurrency currency code (XAF)
     * @return list of 3 new accounts for this FreelancerOrg
     */
    public List<Account> buildFreelancerOrgAccounts(UUID tenantId, String freelancerOrgId,
                                                     String orgTradeName, String defaultCurrency) {
        // Use first 8 chars of orgId to keep codes concise
        String shortId = freelancerOrgId.replace("-", "").substring(0, Math.min(8, freelancerOrgId.replace("-", "").length()));

        return List.of(
                Account.createForFreelancerOrg(tenantId,
                        "411-FRL-" + shortId,
                        "Clients FreelancerOrg — " + orgTradeName,
                        AccountType.ASSET, defaultCurrency, freelancerOrgId),

                Account.createForFreelancerOrg(tenantId,
                        "421-FRL-" + shortId,
                        "Sub-Livreurs rémunérations — " + orgTradeName,
                        AccountType.LIABILITY, defaultCurrency, freelancerOrgId),

                Account.createForFreelancerOrg(tenantId,
                        "706-FRL-" + shortId,
                        "Revenus prestations FreelancerOrg — " + orgTradeName,
                        AccountType.REVENUE, defaultCurrency, freelancerOrgId)
        );
    }

}