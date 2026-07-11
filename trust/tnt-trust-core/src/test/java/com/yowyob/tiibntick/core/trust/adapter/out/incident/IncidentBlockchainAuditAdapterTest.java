package com.yowyob.tiibntick.core.trust.adapter.out.incident;

import com.yowyob.tiibntick.core.incident.port.outbound.IBlockchainAuditPort;
import com.yowyob.tiibntick.core.trust.adapter.out.persistence.IncidentBlockchainRecordEntity;
import com.yowyob.tiibntick.core.trust.adapter.out.persistence.IncidentBlockchainRecordPersistenceAdapter;
import com.yowyob.tiibntick.core.trust.application.port.out.CustodyTransferCacheRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.DeliveryProofCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IncidentBlockchainAuditAdapter} — verifies it satisfies
 * tnt-incident-core's {@link IBlockchainAuditPort} contract.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentBlockchainAuditAdapter — IBlockchainAuditPort implementation")
class IncidentBlockchainAuditAdapterTest {

    @Mock
    private IncidentBlockchainRecordPersistenceAdapter persistenceAdapter;
    @Mock
    private DeliveryProofCacheRepository deliveryProofCacheRepository;
    @Mock
    private CustodyTransferCacheRepository custodyTransferCacheRepository;

    private IncidentBlockchainAuditAdapter adapter() {
        return new IncidentBlockchainAuditAdapter(
                persistenceAdapter, deliveryProofCacheRepository, custodyTransferCacheRepository);
    }

    @Test
    @DisplayName("is a valid IBlockchainAuditPort implementation")
    void implementsPort() {
        assertThat(adapter()).isInstanceOf(IBlockchainAuditPort.class);
    }

    @Test
    @DisplayName("writeIncidentEvent appends a genesis block when the chain is new")
    void shouldWriteGenesisBlockForNewChain() {
        final UUID incidentId = UUID.randomUUID();
        final String chainId = "INC-" + UUID.randomUUID();

        when(persistenceAdapter.findLatestByChainId(chainId)).thenReturn(Mono.empty());

        final IncidentBlockchainRecordEntity saved = mock(IncidentBlockchainRecordEntity.class);
        when(saved.getCurrentHash()).thenReturn("computed-hash");
        when(persistenceAdapter.saveBlock(
                anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(Mono.just(saved));

        StepVerifier.create(adapter().writeIncidentEvent(incidentId, chainId, "INCIDENT_CREATED", "{}"))
                .expectNext("computed-hash")
                .verifyComplete();
    }

    @Test
    @DisplayName("verifyChain reports true for an empty chain")
    void shouldReportTrueForEmptyChain() {
        final String chainId = "INC-" + UUID.randomUUID();
        when(persistenceAdapter.findAllByChainIdAsc(chainId)).thenReturn(Flux.empty());

        StepVerifier.create(adapter().verifyChain(chainId))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("getParcelChainTailHash falls back to GENESIS when no anchored record exists")
    void shouldFallBackToGenesis() {
        final UUID parcelId = UUID.randomUUID();
        when(deliveryProofCacheRepository.findLatestConfirmedHashByParcelId(parcelId.toString()))
                .thenReturn(Mono.empty());
        when(custodyTransferCacheRepository.findLatestConfirmedHashByParcelId(parcelId.toString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(adapter().getParcelChainTailHash(parcelId))
                .expectNext("GENESIS")
                .verifyComplete();
    }

}
