package com.yowyob.tiibntick.core.gofreelancer.adapter.in.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryStatusApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationRepository;
import com.yowyob.tiibntick.core.sync.application.port.out.ISyncEventPublisher;
import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOpId;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpStatus;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import com.yowyob.tiibntick.core.sync.domain.service.ConflictResolverService;
import com.yowyob.tiibntick.core.sync.domain.service.OfflineQueueDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A livreur's offline queue pushed through the REAL sync domain service and the gofp applier:
 * ordering, replay after a lost response, a stranger's queue, and a refused transition.
 * Only the persistence ports and the delivery status use-case are stood in for.
 */
@DisplayName("Offline queue replay: sync-core + gofp applier")
class GofpOfflineQueueReplayTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Map<String, OfflineOperation> journal = new HashMap<>();
    private final Map<String, EntityVersionRecord> versions = new HashMap<>();
    private final List<DeliveryStatus> appliedTransitions = new ArrayList<>();
    private final AtomicInteger statusCalls = new AtomicInteger();
    private Delivery row;
    private OfflineQueueDomainService sync;

    @BeforeEach
    void setUp() {
        row = new Delivery();
        row.setId(deliveryId);
        row.setStatus(DeliveryStatus.CREATED);

        DeliveryRepository deliveries = mock(DeliveryRepository.class);
        when(deliveries.findById(deliveryId)).thenAnswer(i -> Mono.just(row));

        DeliveryStatusApplicationService statusService = mock(DeliveryStatusApplicationService.class);
        when(statusService.assertCallerIsAssignedDeliveryPerson(any(), any())).thenAnswer(i ->
                i.getArgument(1).equals(owner) ? Mono.empty() : Mono.error(new AccessDeniedException("not assigned")));
        when(statusService.updateStatus(any(), any())).thenAnswer(i -> {
            statusCalls.incrementAndGet();
            var dto = (com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO) i.getArgument(1);
            // what the delivery-core state machine would refuse
            if (row.getStatus() == DeliveryStatus.DELIVERED) {
                return Mono.error(new IllegalStateException("Cannot transition delivery from [DELIVERED] to [" + dto.getStatus() + "]"));
            }
            if (dto.getStatus() == DeliveryStatus.PICKED_UP && !"123456".equals(dto.getConfirmationCode())) {
                return Mono.error(new IllegalArgumentException("Invalid pickup code"));
            }
            row.setStatus(dto.getStatus());
            appliedTransitions.add(dto.getStatus());
            return Mono.just(row);
        });

        IOfflineOperationRepository ops = new IOfflineOperationRepository() {
            @Override public Mono<Void> save(OfflineOperation o) { journal.put(o.getId().value(), o); return Mono.empty(); }
            @Override public Mono<Void> saveAll(List<OfflineOperation> l) { l.forEach(o -> journal.put(o.getId().value(), o)); return Mono.empty(); }
            @Override public Flux<OfflineOperation> findPendingByUser(String u, String t) { return Flux.empty(); }
            @Override public Flux<OfflineOperation> findBySessionId(String s) { return Flux.empty(); }
            @Override public Mono<Void> updateStatus(String id, OfflineOpStatus s, String e) { return Mono.empty(); }
            @Override public Mono<Boolean> isAlreadyApplied(String id) {
                OfflineOperation o = journal.get(id);
                return Mono.just(o != null && o.getStatus() == OfflineOpStatus.APPLIED);
            }
        };
        IEntityVersionRepository entityVersions = new IEntityVersionRepository() {
            @Override public Mono<Void> upsert(EntityVersionRecord r) { versions.put(r.compositeKey(), r); return Mono.empty(); }
            @Override public Mono<EntityVersionRecord> findCurrent(String t, String a, String id) {
                return Mono.justOrEmpty(versions.values().stream()
                        .filter(v -> v.tenantId().equals(t) && v.aggregateType().equals(a) && v.aggregateId().equals(id)).findFirst());
            }
            @Override public Flux<EntityVersionRecord> findChangedSince(String t, LocalDateTime s, Set<String> f, int l) { return Flux.empty(); }
            @Override public Mono<Long> countChangedSince(String t, LocalDateTime s) { return Mono.just(0L); }
            @Override public Flux<EntityVersionRecord> findAllCurrentByType(String t, String a) { return Flux.empty(); }
        };
        ISyncEventPublisher events = e -> Mono.empty();

        sync = new OfflineQueueDomainService(ops, entityVersions, new ConflictResolverService(), events,
                List.of(new GofpDeliveryOfflineOperationApplier(statusService, deliveries, mock(DeliveryProofService.class), mapper)), mapper);
    }

    private OfflineOperation op(String id, long seq, OfflineOpType type, String payload, UUID user) {
        return new OfflineOperation(OfflineOpId.of(id), user.toString(), tenant.toString(), "phone-1",
                type, "DELIVERY", deliveryId.toString(), payload, LocalDateTime.now().minusMinutes(10).plusSeconds(seq), seq);
    }

    private List<OfflineOperation> tour() {
        return List.of(
                op("op-3", 3, OfflineOpType.DELIVERY_CONFIRMATION, "{\"confirmationCode\":\"654321\"}", owner),
                op("op-1", 1, OfflineOpType.MISSION_STATUS_UPDATE, "{\"status\":\"PICKED_UP\",\"confirmationCode\":\"123456\"}", owner),
                op("op-2", 2, OfflineOpType.MISSION_STATUS_UPDATE, "{\"status\":\"IN_TRANSIT\"}", owner));
    }

    @Test
    @DisplayName("a whole offline tour is replayed in queue order, whatever the order received")
    void orderedReplay() {
        StepVerifier.create(sync.processOperations(tour(), "s1"))
                .assertNext(r -> assertThat(r.applied()).isEqualTo(3))
                .verifyComplete();
        assertThat(appliedTransitions).containsExactly(DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED);
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(journal.values()).allMatch(o -> o.getStatus() == OfflineOpStatus.APPLIED);
        // the server-side result (not the client's echo) is what delta-pull will serve
        assertThat(versions.values()).singleElement().satisfies(v -> assertThat(v.payloadJson()).contains("\"status\":\"DELIVERED\""));
    }

    @Test
    @DisplayName("pushing the same queue again (lost response) changes nothing and calls no use-case")
    void replayAfterLostResponse() {
        sync.processOperations(tour(), "s1").block();
        int callsAfterFirst = statusCalls.get();

        StepVerifier.create(sync.processOperations(tour(), "s2"))
                .assertNext(r -> assertThat(r.applied()).isEqualTo(3))
                .verifyComplete();

        assertThat(statusCalls.get()).isEqualTo(callsAfterFirst);
        assertThat(appliedTransitions).hasSize(3);
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    @DisplayName("a new operation id repeating a reached step is absorbed without re-running the use-case")
    void freshIdSameStep() {
        sync.processOperations(List.of(op("op-1", 1, OfflineOpType.MISSION_STATUS_UPDATE,
                "{\"status\":\"PICKED_UP\",\"confirmationCode\":\"123456\"}", owner)), "s1").block();
        int calls = statusCalls.get();
        StepVerifier.create(sync.processOperations(List.of(op("op-1-bis", 5, OfflineOpType.MISSION_STATUS_UPDATE,
                        "{\"status\":\"PICKED_UP\",\"confirmationCode\":\"123456\"}", owner)), "s2"))
                .assertNext(r -> assertThat(r.applied()).isEqualTo(1))
                .verifyComplete();
        assertThat(statusCalls.get()).isEqualTo(calls);
    }

    @Test
    @DisplayName("somebody else's queue never moves the delivery and is reported as not applied")
    void strangerQueue() {
        UUID stranger = UUID.randomUUID();
        StepVerifier.create(sync.processOperations(List.of(
                        op("x-1", 1, OfflineOpType.MISSION_STATUS_UPDATE, "{\"status\":\"PICKED_UP\",\"confirmationCode\":\"123456\"}", stranger)), "s1"))
                .assertNext(r -> {
                    assertThat(r.applied()).isZero();
                    assertThat(r.discarded()).isEqualTo(1);
                })
                .verifyComplete();
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.CREATED);
        assertThat(statusCalls.get()).isZero();
        assertThat(journal.get("x-1").getStatus()).isEqualTo(OfflineOpStatus.FAILED);
        assertThat(journal.get("x-1").getError()).contains("not assigned");
    }

    @Test
    @DisplayName("a wrong OTP typed offline fails that operation only; the rest of the queue still runs")
    void wrongOtpIsolated() {
        StepVerifier.create(sync.processOperations(List.of(
                        op("a", 1, OfflineOpType.MISSION_STATUS_UPDATE, "{\"status\":\"PICKED_UP\",\"confirmationCode\":\"000000\"}", owner),
                        op("b", 2, OfflineOpType.MISSION_STATUS_UPDATE, "{\"status\":\"PICKED_UP\",\"confirmationCode\":\"123456\"}", owner)), "s1"))
                .assertNext(r -> {
                    assertThat(r.applied()).isEqualTo(1);
                    assertThat(r.discarded()).isEqualTo(1);
                })
                .verifyComplete();
        assertThat(journal.get("a").getStatus()).isEqualTo(OfflineOpStatus.FAILED);
        assertThat(journal.get("a").getError()).contains("Invalid pickup code");
        assertThat(journal.get("b").getStatus()).isEqualTo(OfflineOpStatus.APPLIED);
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
    }

    @Test
    @DisplayName("a failure reported after the delivery was completed is refused by the core and recorded")
    void contradiction() {
        sync.processOperations(tour(), "s1").block();
        StepVerifier.create(sync.processOperations(List.of(
                        op("late-fail", 9, OfflineOpType.MISSION_STATUS_UPDATE, "{\"status\":\"FAILED\",\"reason\":\"absent\"}", owner)), "s2"))
                .assertNext(r -> assertThat(r.discarded()).isEqualTo(1))
                .verifyComplete();
        assertThat(row.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(journal.get("late-fail").getError()).contains("Cannot transition");
    }
}
