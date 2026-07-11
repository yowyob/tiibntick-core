package com.yowyob.tiibntick.core.trust.adapter.out.dispute;

import com.yowyob.tiibntick.core.trust.application.port.in.RecordDisputeEvidenceUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.CustodyTransferCacheRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DisputeBlockchainProofAdapter}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeBlockchainProofAdapter — IBlockchainProofPort implementation")
class DisputeBlockchainProofAdapterTest {

    @Mock
    private RecordDisputeEvidenceUseCase recordDisputeEvidence;
    @Mock
    private CustodyTransferCacheRepository custodyTransferCacheRepository;
    @Mock
    private TrustProofQueryPort trustProofQueryPort;

    private DisputeBlockchainProofAdapter adapter() {
        return new DisputeBlockchainProofAdapter(
                recordDisputeEvidence, custodyTransferCacheRepository, trustProofQueryPort);
    }

    private CustodyTransferRecord recordWithHash(String hash) {
        CustodyTransferRecord record = new CustodyTransferRecord(
                "transfer-1", "package-1", "TRACK-001", "tenant-1",
                "actor-a", "actor-b", CustodyTransferType.TRANSFER_TO_HUB, "hub-1",
                LocalDateTime.now());
        if (hash != null) {
            record.confirmOnChain(hash);
        }
        return record;
    }

    @Test
    @DisplayName("getDeliveryProofHash returns the latest confirmed custody-transfer hash for the tracking code")
    void shouldReturnLatestConfirmedHash() {
        when(custodyTransferCacheRepository.findByTrackingCode("TRACK-001", "tenant-1"))
                .thenReturn(Flux.just(recordWithHash(null), recordWithHash("tx-hash-old"), recordWithHash("tx-hash-latest")));

        StepVerifier.create(adapter().getDeliveryProofHash("TRACK-001", "tenant-1"))
                .expectNext("tx-hash-latest")
                .verifyComplete();
    }

    @Test
    @DisplayName("getDeliveryProofHash is empty when no custody transfer has a confirmed hash")
    void shouldBeEmptyWhenNoConfirmedHash() {
        when(custodyTransferCacheRepository.findByTrackingCode("TRACK-001", "tenant-1"))
                .thenReturn(Flux.just(recordWithHash(null)));

        StepVerifier.create(adapter().getDeliveryProofHash("TRACK-001", "tenant-1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("anchorEvidence delegates to RecordDisputeEvidenceUseCase")
    void shouldDelegateAnchorEvidence() {
        when(recordDisputeEvidence.record(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just("tx-hash-001"));

        StepVerifier.create(adapter().anchorEvidence(
                        "evidence-1", "file-key", "dispute-1", "tenant-1", "hash-abc"))
                .expectNext("tx-hash-001")
                .verifyComplete();
    }

    @Test
    @DisplayName("anchorEvidence never fails the caller when trust anchoring errors")
    void shouldContainAnchoringErrors() {
        when(recordDisputeEvidence.record(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("trust unavailable")));

        StepVerifier.create(adapter().anchorEvidence(
                        "evidence-1", "file-key", "dispute-1", "tenant-1", "hash-abc"))
                .verifyComplete();
    }

    @Test
    @DisplayName("verifyProof falls back to a structural presence check when no expectedHash is supplied")
    void shouldVerifyProofPresenceWithoutExpectedHash() {
        StepVerifier.create(adapter().verifyProof("tx-hash-001", null)).expectNext(true).verifyComplete();
        StepVerifier.create(adapter().verifyProof("", null)).expectNext(false).verifyComplete();
        StepVerifier.create(adapter().verifyProof(null, null)).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("verifyProof delegates to TrustProofQueryPort for a real comparison when expectedHash is supplied")
    void shouldDelegateVerifyProofToTrustProofQueryPort() {
        when(trustProofQueryPort.verifyProof("tx-hash-001", "hash-abc")).thenReturn(Mono.just(true));

        StepVerifier.create(adapter().verifyProof("tx-hash-001", "hash-abc"))
                .expectNext(true)
                .verifyComplete();
    }
}
