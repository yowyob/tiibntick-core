package com.yowyob.tiibntick.core.organization.application.service;

import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgDidAnchorPort;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgEventPublisherPort;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgRepositoryPort;
import com.yowyob.tiibntick.core.organization.domain.model.FreelancerOrganization;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FreelancerOrgService}.
 *
 * <p>Uses Mockito to stub the outbound ports so the service logic is tested
 * in isolation (no Spring context, no database, no Kafka).
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class FreelancerOrgServiceTest {

    @Mock
    private FreelancerOrgRepositoryPort repository;

    @Mock
    private FreelancerOrgEventPublisherPort eventPublisher;

    @Mock
    private FreelancerOrgDidAnchorPort didAnchorPort;

    private FreelancerOrgService service;

    @BeforeEach
    void setUp() {
        service = new FreelancerOrgService(repository, eventPublisher, didAnchorPort);
    }

    @Test
    @DisplayName("registerFreelancerOrg() — saves and publishes created event")
    void registerFreelancerOrg_savesAndPublishesEvent() {
        UUID ownerActorId = UUID.randomUUID();
        FreelancerOrganization expected =
                FreelancerOrganization.register(null, ownerActorId, "Test Org");

        when(repository.existsByTradeName(anyString())).thenReturn(Mono.just(false));
        when(repository.save(any())).thenReturn(Mono.just(expected));
        when(eventPublisher.publishFreelancerOrgCreated(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.registerFreelancerOrg(null, ownerActorId, "Test Org"))
                .expectNextMatches(org ->
                        org.getTradeName().equals("Test Org") &&
                        org.getOwnerActorId().equals(ownerActorId))
                .verifyComplete();
    }

    @Test
    @DisplayName("registerFreelancerOrg() — rejects duplicate trade name")
    void registerFreelancerOrg_rejectsDuplicateTradeName() {
        when(repository.existsByTradeName(anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(service.registerFreelancerOrg(null, UUID.randomUUID(), "Existing Org"))
                .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.CONFLICT
                        && rse.getReason() != null && rse.getReason().contains("already exists"))
                .verify();
    }

    @Test
    @DisplayName("findById() — delegates to repository")
    void findById_delegatesToRepository() {
        OrganizationId id = OrganizationId.generate();
        FreelancerOrganization org = FreelancerOrganization.register(null, UUID.randomUUID(), "My Org");
        when(repository.findById(id)).thenReturn(Mono.just(org));

        StepVerifier.create(service.findById(id))
                .expectNext(org)
                .verifyComplete();
    }

    @Test
    @DisplayName("findById() — returns empty when not found")
    void findById_emptyWhenNotFound() {
        OrganizationId id = OrganizationId.generate();
        when(repository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(service.findById(id))
                .verifyComplete();
    }

    @Test
    @DisplayName("upgradeKycToBasic() — applies KYC upgrade and publishes event")
    void upgradeKycToBasic_appliesUpgrade() {
        UUID ownerActorId = UUID.randomUUID();
        FreelancerOrganization org = FreelancerOrganization.register(null, ownerActorId, "KYC Org");
        OrganizationId id = org.getId();

        when(repository.findById(id)).thenReturn(Mono.just(org));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishKycLevelUpgraded(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.upgradeKycToBasic(id))
                .expectNextMatches(updated ->
                        updated.getKycLevel().name().equals("BASIC"))
                .verifyComplete();
    }

    @Test
    @DisplayName("upgradeKycToBasic() — returns error when org not found")
    void upgradeKycToBasic_orgNotFound() {
        OrganizationId id = OrganizationId.generate();
        when(repository.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(service.upgradeKycToBasic(id))
                .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.NOT_FOUND
                        && rse.getReason() != null && rse.getReason().contains("not found"))
                .verify();
    }

    @Test
    @DisplayName("verifyFreelancerOrg() — anchors blockchain DID after verification")
    void verifyFreelancerOrg_anchorsBlockchainDid() {
        UUID ownerActorId = UUID.randomUUID();
        UUID adminActorId = UUID.randomUUID();
        FreelancerOrganization org = FreelancerOrganization.register(null, ownerActorId, "DID Org");
        OrganizationId id = org.getId();
        String expectedDid = "did:tiibntick:" + org.getTenantId() + ":org:" + id.value();

        when(repository.findById(id)).thenReturn(Mono.just(org));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishFreelancerOrgVerified(any())).thenReturn(Mono.empty());
        when(didAnchorPort.issueDid(any())).thenReturn(Mono.just(expectedDid));

        StepVerifier.create(service.verifyFreelancerOrg(id, adminActorId))
                .expectNextMatches(updated -> expectedDid.equals(updated.getBlockchainDid()))
                .verifyComplete();
    }

    @Test
    @DisplayName("verifyFreelancerOrg() — swallows DID anchoring failure (best-effort)")
    void verifyFreelancerOrg_swallowsAnchorFailure() {
        UUID ownerActorId = UUID.randomUUID();
        UUID adminActorId = UUID.randomUUID();
        FreelancerOrganization org = FreelancerOrganization.register(null, ownerActorId, "Resilient Org");
        OrganizationId id = org.getId();

        when(repository.findById(id)).thenReturn(Mono.just(org));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishFreelancerOrgVerified(any())).thenReturn(Mono.empty());
        when(didAnchorPort.issueDid(any())).thenReturn(Mono.error(new RuntimeException("Fabric unavailable")));

        StepVerifier.create(service.verifyFreelancerOrg(id, adminActorId))
                .expectNextMatches(updated -> updated.getBlockchainDid() == null)
                .verifyComplete();
    }
}
