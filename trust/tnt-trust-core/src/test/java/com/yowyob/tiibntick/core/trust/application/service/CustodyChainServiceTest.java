package com.yowyob.tiibntick.core.trust.application.service;

import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.BlockchainProof;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyVerificationResult;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ParcelCustodyChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CustodyChainService}.
 *
 * <p>Exercises chain reconstruction / cryptographic verification (Proof of
 * Integrity walk over {@code previousCustodyHash} / {@code custodyHash}) for
 * intact chains, broken chains, and the empty-chain case, plus the
 * {@code verifyCustodyChain()} and {@code getCurrentCustodian()} derived
 * use cases.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("tnt-trust — CustodyChainService Tests")
class CustodyChainServiceTest {

    /** Mirrors {@code CustodyChainService.GENESIS_HASH} (64 zero chars). */
    private static final String GENESIS_HASH = "0".repeat(64);

    private static final String PACKAGE_ID = "package-001";
    private static final String TENANT_ID = "tenant-001";
    private static final String CUSTODY_ENTITY_TYPE = "CUSTODY_TRANSFER";

    @Mock
    private TrustProofQueryPort trustProofQueryPort;

    private CustodyChainService custodyChainService;

    @BeforeEach
    void setUp() {
        custodyChainService = new CustodyChainService(trustProofQueryPort);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Builds a {@code PACKAGE_CUSTODY_TRANSFERRED} {@link BlockchainProof} whose
     * naive JSON-payload accessor ({@link BlockchainProof#getPayloadField(String)})
     * will resolve the given transfer fields.
     */
    private BlockchainProof custodyProof(
            final String transferId,
            final String fromActorId,
            final String toActorId,
            final String previousCustodyHash,
            final String custodyHash,
            final LocalDateTime timestamp) {
        final String payload = "{"
                + "\"transferId\":\"" + transferId + "\","
                + "\"trackingCode\":\"TRACK-" + transferId + "\","
                + "\"fromActorId\":\"" + (fromActorId == null ? "" : fromActorId) + "\","
                + "\"toActorId\":\"" + toActorId + "\","
                + "\"transferType\":\"PICKUP_FROM_SENDER\","
                + "\"hubId\":\"hub-001\","
                + "\"gpsLat\":\"3.848\","
                + "\"gpsLng\":\"11.502\","
                + "\"pocHash\":\"poc-" + transferId + "\""
                + "}";
        return new BlockchainProof(
                "proof-" + transferId,
                CUSTODY_ENTITY_TYPE,
                PACKAGE_ID,
                "PACKAGE_CUSTODY_TRANSFERRED",
                "CONFIRMED",
                "tx-" + transferId,
                custodyHash,
                previousCustodyHash,
                payload,
                timestamp);
    }

    /** Builds an intact 3-link chain: genesis -> link2 -> link3. */
    private Flux<BlockchainProof> intactChainProofs() {
        final LocalDateTime t0 = LocalDateTime.of(2026, 7, 1, 8, 0);
        final BlockchainProof link1 = custodyProof(
                "t1", null, "actor-courier-1", GENESIS_HASH, "hash-1", t0);
        final BlockchainProof link2 = custodyProof(
                "t2", "actor-courier-1", "actor-hub-1", "hash-1", "hash-2", t0.plusHours(1));
        final BlockchainProof link3 = custodyProof(
                "t3", "actor-hub-1", "actor-recipient-1", "hash-2", "hash-3", t0.plusHours(2));
        return Flux.just(link1, link2, link3);
    }

    /** Builds a 3-link chain broken at the middle link (link2's previousCustodyHash mismatches link1's custodyHash). */
    private Flux<BlockchainProof> brokenChainProofs() {
        final LocalDateTime t0 = LocalDateTime.of(2026, 7, 1, 8, 0);
        final BlockchainProof link1 = custodyProof(
                "t1", null, "actor-courier-1", GENESIS_HASH, "hash-1", t0);
        final BlockchainProof link2 = custodyProof(
                "t2", "actor-courier-1", "actor-hub-1", "TAMPERED-HASH", "hash-2", t0.plusHours(1));
        final BlockchainProof link3 = custodyProof(
                "t3", "actor-hub-1", "actor-recipient-1", "hash-2", "hash-3", t0.plusHours(2));
        return Flux.just(link1, link2, link3);
    }

    // ── getByPackageId() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getByPackageId() with an intact chain returns chainIntact=true, correct count and ordering")
    void shouldReturnIntactChain() {
        when(trustProofQueryPort.getAuditHistoryWithDetails(PACKAGE_ID, CUSTODY_ENTITY_TYPE, TENANT_ID))
                .thenReturn(intactChainProofs());

        StepVerifier.create(custodyChainService.getByPackageId(PACKAGE_ID, TENANT_ID))
                .assertNext(chain -> {
                    assertThat(chain.chainIntact()).isTrue();
                    assertThat(chain.brokenAtCustodyHash()).isNull();
                    assertThat(chain.transferCount()).isEqualTo(3);
                    assertThat(chain.links().get(0).getTransferId()).isEqualTo("t1");
                    assertThat(chain.links().get(1).getTransferId()).isEqualTo("t2");
                    assertThat(chain.links().get(2).getTransferId()).isEqualTo("t3");
                    assertThat(chain.currentCustodian()).isEqualTo("actor-recipient-1");
                    assertThat(chain.packageId()).isEqualTo(PACKAGE_ID);
                    assertThat(chain.tenantId()).isEqualTo(TENANT_ID);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getByPackageId() with a broken chain returns chainIntact=false and points at the tampered link")
    void shouldReturnBrokenChain() {
        when(trustProofQueryPort.getAuditHistoryWithDetails(PACKAGE_ID, CUSTODY_ENTITY_TYPE, TENANT_ID))
                .thenReturn(brokenChainProofs());

        StepVerifier.create(custodyChainService.getByPackageId(PACKAGE_ID, TENANT_ID))
                .assertNext(chain -> {
                    assertThat(chain.chainIntact()).isFalse();
                    assertThat(chain.brokenAtCustodyHash()).isEqualTo("hash-2");
                    assertThat(chain.transferCount()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getByPackageId() with zero proofs returns ParcelCustodyChain.empty()")
    void shouldReturnEmptyChainWhenNoProofs() {
        when(trustProofQueryPort.getAuditHistoryWithDetails(PACKAGE_ID, CUSTODY_ENTITY_TYPE, TENANT_ID))
                .thenReturn(Flux.empty());

        final ParcelCustodyChain expectedEmpty = ParcelCustodyChain.empty(PACKAGE_ID, TENANT_ID);

        StepVerifier.create(custodyChainService.getByPackageId(PACKAGE_ID, TENANT_ID))
                .assertNext(chain -> {
                    assertThat(chain.packageId()).isEqualTo(expectedEmpty.packageId());
                    assertThat(chain.tenantId()).isEqualTo(expectedEmpty.tenantId());
                    assertThat(chain.trackingCode()).isNull();
                    assertThat(chain.links()).isEmpty();
                    assertThat(chain.chainIntact()).isTrue();
                    assertThat(chain.brokenAtCustodyHash()).isNull();
                    assertThat(chain.currentCustodian()).isNull();
                    assertThat(chain.transferCount()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getByPackageId() with a genesis mismatch (first link's previousCustodyHash != GENESIS_HASH) is broken from the start")
    void shouldReturnBrokenChainWhenGenesisHashMismatches() {
        final LocalDateTime t0 = LocalDateTime.of(2026, 7, 1, 8, 0);
        final BlockchainProof link1 = custodyProof(
                "t1", null, "actor-courier-1", "NOT-GENESIS", "hash-1", t0);

        when(trustProofQueryPort.getAuditHistoryWithDetails(PACKAGE_ID, CUSTODY_ENTITY_TYPE, TENANT_ID))
                .thenReturn(Flux.just(link1));

        StepVerifier.create(custodyChainService.getByPackageId(PACKAGE_ID, TENANT_ID))
                .assertNext(chain -> {
                    assertThat(chain.chainIntact()).isFalse();
                    assertThat(chain.brokenAtCustodyHash()).isEqualTo("hash-1");
                })
                .verifyComplete();
    }

    // ── verifyCustodyChain() ──────────────────────────────────────────────────

    @Test
    @DisplayName("verifyCustodyChain() reflects intact=true for an intact chain")
    void shouldVerifyIntactChain() {
        when(trustProofQueryPort.getAuditHistoryWithDetails(PACKAGE_ID, CUSTODY_ENTITY_TYPE, TENANT_ID))
                .thenReturn(intactChainProofs());

        StepVerifier.create(custodyChainService.verifyCustodyChain(PACKAGE_ID, TENANT_ID))
                .assertNext((CustodyVerificationResult result) -> {
                    assertThat(result.packageId()).isEqualTo(PACKAGE_ID);
                    assertThat(result.chainIntact()).isTrue();
                    assertThat(result.linksVerified()).isEqualTo(3);
                    assertThat(result.brokenAtCustodyHash()).isNull();
                    assertThat(result.reason()).contains("intact");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("verifyCustodyChain() reflects intact=false and the broken hash for a broken chain")
    void shouldVerifyBrokenChain() {
        when(trustProofQueryPort.getAuditHistoryWithDetails(PACKAGE_ID, CUSTODY_ENTITY_TYPE, TENANT_ID))
                .thenReturn(brokenChainProofs());

        StepVerifier.create(custodyChainService.verifyCustodyChain(PACKAGE_ID, TENANT_ID))
                .assertNext((CustodyVerificationResult result) -> {
                    assertThat(result.chainIntact()).isFalse();
                    assertThat(result.linksVerified()).isEqualTo(3);
                    assertThat(result.brokenAtCustodyHash()).isEqualTo("hash-2");
                    assertThat(result.reason()).contains("hash-2");
                })
                .verifyComplete();
    }

    // ── getCurrentCustodian() ─────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentCustodian() returns the last link's toActorId for a non-empty chain")
    void shouldReturnCurrentCustodianForNonEmptyChain() {
        when(trustProofQueryPort.getAuditHistoryWithDetails(PACKAGE_ID, CUSTODY_ENTITY_TYPE, TENANT_ID))
                .thenReturn(intactChainProofs());

        StepVerifier.create(custodyChainService.getCurrentCustodian(PACKAGE_ID, TENANT_ID))
                .expectNext("actor-recipient-1")
                .verifyComplete();
    }

    @Test
    @DisplayName("getCurrentCustodian() returns empty string for an empty chain")
    void shouldReturnEmptyStringForEmptyChain() {
        when(trustProofQueryPort.getAuditHistoryWithDetails(PACKAGE_ID, CUSTODY_ENTITY_TYPE, TENANT_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(custodyChainService.getCurrentCustodian(PACKAGE_ID, TENANT_ID))
                .expectNext("")
                .verifyComplete();
    }
}
