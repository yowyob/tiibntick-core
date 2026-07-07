package com.yowyob.tiibntick.core.accounting.domain;

import com.yowyob.tiibntick.core.accounting.domain.model.Account;
import com.yowyob.tiibntick.core.accounting.domain.model.AccountType;
import com.yowyob.tiibntick.core.accounting.domain.service.OhadaChartOfAccountsInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  extensions to tnt-accounting-core.
 *
 * @author MANFOUO Braun
 */
class AccountFreelancerOrgTest {

    @Nested
    @DisplayName("Account.createForFreelancerOrg")
    class CreateForFreelancerOrg {

        @Test
        @DisplayName("Should set ownerOrgId and ownerOrgType=FREELANCER_ORG")
        void shouldSetFreelancerOrgContext() {
            UUID tenantId = UUID.randomUUID();
            String orgId = "FRL-ORG-001";
            Account account = Account.createForFreelancerOrg(
                    tenantId, "411-FRL-FRLORG01", "Clients FreelancerOrg — Test Org",
                    AccountType.ASSET, "XAF", orgId);

            assertThat(account.getOwnerOrgId()).isEqualTo(orgId);
            assertThat(account.getOwnerOrgType()).isEqualTo("FREELANCER_ORG");
            assertThat(account.isFreelancerOrgAccount()).isTrue();
            assertThat(account.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Account.create — backward compat")
    class CreateBackwardCompat {

        @Test
        @DisplayName("Standard create() should have null org context")
        void standardCreateHasNullContext() {
            Account account = Account.create(
                    UUID.randomUUID(), "411000", "Clients", AccountType.ASSET, "XAF", null);
            assertThat(account.getOwnerOrgId()).isNull();
            assertThat(account.getOwnerOrgType()).isNull();
            assertThat(account.isFreelancerOrgAccount()).isFalse();
        }
    }

    @Nested
    @DisplayName("OhadaChartOfAccountsInitializer.buildFreelancerOrgAccounts")
    class BuildFreelancerOrgAccounts {

        @Test
        @DisplayName("Should create exactly 3 accounts for a FreelancerOrg")
        void shouldCreate3Accounts() {
            OhadaChartOfAccountsInitializer init = new OhadaChartOfAccountsInitializer();
            UUID tenantId = UUID.randomUUID();
            String orgId = UUID.randomUUID().toString();
            List<Account> accounts = init.buildFreelancerOrgAccounts(
                    tenantId, orgId, "Moto Express Biyem", "XAF");

            assertThat(accounts).hasSize(3);
        }

        @Test
        @DisplayName("Account codes should follow 411-FRL-{id}, 421-FRL-{id}, 706-FRL-{id} pattern")
        void accountCodesShouldFollowPattern() {
            OhadaChartOfAccountsInitializer init = new OhadaChartOfAccountsInitializer();
            UUID tenantId = UUID.randomUUID();
            String orgId = "550e8400-e29b-41d4-a716-446655440000";
            List<Account> accounts = init.buildFreelancerOrgAccounts(tenantId, orgId, "Test Org", "XAF");

            List<String> codes = accounts.stream().map(Account::getCode).toList();
            assertThat(codes).anySatisfy(code -> assertThat(code).startsWith("411-FRL-"));
            assertThat(codes).anySatisfy(code -> assertThat(code).startsWith("421-FRL-"));
            assertThat(codes).anySatisfy(code -> assertThat(code).startsWith("706-FRL-"));
        }

        @Test
        @DisplayName("All 3 accounts should have the FreelancerOrg owner context")
        void allAccountsShouldHaveOrgContext() {
            OhadaChartOfAccountsInitializer init = new OhadaChartOfAccountsInitializer();
            String orgId = UUID.randomUUID().toString();
            List<Account> accounts = init.buildFreelancerOrgAccounts(
                    UUID.randomUUID(), orgId, "Test Org", "XAF");

            accounts.forEach(a -> {
                assertThat(a.getOwnerOrgId()).isEqualTo(orgId);
                assertThat(a.getOwnerOrgType()).isEqualTo("FREELANCER_ORG");
                assertThat(a.isFreelancerOrgAccount()).isTrue();
            });
        }

        @Test
        @DisplayName("Accounts should have correct OHADA types")
        void accountsShouldHaveCorrectTypes() {
            OhadaChartOfAccountsInitializer init = new OhadaChartOfAccountsInitializer();
            String orgId = UUID.randomUUID().toString();
            List<Account> accounts = init.buildFreelancerOrgAccounts(
                    UUID.randomUUID(), orgId, "Test Org", "XAF");

            // 411 = ASSET, 421 = LIABILITY, 706 = REVENUE
            assertThat(accounts.stream().filter(a -> a.getCode().startsWith("411"))
                    .findFirst().orElseThrow().getType()).isEqualTo(AccountType.ASSET);
            assertThat(accounts.stream().filter(a -> a.getCode().startsWith("421"))
                    .findFirst().orElseThrow().getType()).isEqualTo(AccountType.LIABILITY);
            assertThat(accounts.stream().filter(a -> a.getCode().startsWith("706"))
                    .findFirst().orElseThrow().getType()).isEqualTo(AccountType.REVENUE);
        }
    }
}
