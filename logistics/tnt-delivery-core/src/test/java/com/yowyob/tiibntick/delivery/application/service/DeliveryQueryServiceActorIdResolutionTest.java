package com.yowyob.tiibntick.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryQueryService;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsClass;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * Unit tests for DeliveryQueryService.resolveActorIdForDeliveryPerson — the two error paths:
 * profile not found, and profile found but actor_id column is NULL.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryQueryService.resolveActorIdForDeliveryPerson — error paths")
class DeliveryQueryServiceActorIdResolutionTest {

    @Mock private DeliveryRepository deliveryRepository;
    @Mock private DeliveryAnnouncementRepository announcementRepository;
    @Mock private DeliveryPersonRepository deliveryPersonRepository;

    private DeliveryQueryService service;

    private final UUID tenantId          = UUID.randomUUID();
    private final UUID deliveryPersonId  = UUID.randomUUID();
    private final UUID actorId           = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DeliveryQueryService(
                deliveryRepository, announcementRepository, deliveryPersonRepository);
    }

    @Test
    @DisplayName("DeliveryPerson not found → DeliveryNotFoundException with deliveryPersonId")
    void resolveActorId_personNotFound_throwsDeliveryNotFoundException() {
        when(deliveryPersonRepository.findById(tenantId, deliveryPersonId))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.resolveActorIdForDeliveryPerson(tenantId, deliveryPersonId))
                .expectErrorMatches(e -> e instanceof DeliveryNotFoundException
                        && e.getMessage().contains(deliveryPersonId.toString()))
                .verify();
    }

    @Test
    @DisplayName("DeliveryPerson found but actor_id is NULL → DeliveryNotFoundException with column name")
    void resolveActorId_actorIdNull_throwsDeliveryNotFoundExceptionWithColumnName() {
        DeliveryPerson dpWithNullActor = DeliveryPerson.builder()
                .id(deliveryPersonId)
                .tenantId(tenantId)
                .actorId(null)   // the broken data case
                .logisticsType(LogisticsType.MOTORBIKE)
                .logisticsClass(LogisticsClass.STANDARD)
                .tankCapacity(50.0)
                .grossFloor(0.0)
                .totalSeatNumber(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(deliveryPersonRepository.findById(tenantId, deliveryPersonId))
                .thenReturn(Mono.just(dpWithNullActor));

        StepVerifier.create(service.resolveActorIdForDeliveryPerson(tenantId, deliveryPersonId))
                .expectErrorMatches(e -> e instanceof DeliveryNotFoundException
                        && e.getMessage().contains("actor_id"))
                .verify();
    }

    @Test
    @DisplayName("DeliveryPerson found with non-null actor_id → returns the actorId")
    void resolveActorId_validPerson_returnsActorId() {
        DeliveryPerson validDp = DeliveryPerson.builder()
                .id(deliveryPersonId)
                .tenantId(tenantId)
                .actorId(actorId)
                .logisticsType(LogisticsType.MOTORBIKE)
                .logisticsClass(LogisticsClass.STANDARD)
                .tankCapacity(50.0)
                .grossFloor(0.0)
                .totalSeatNumber(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(deliveryPersonRepository.findById(tenantId, deliveryPersonId))
                .thenReturn(Mono.just(validDp));

        StepVerifier.create(service.resolveActorIdForDeliveryPerson(tenantId, deliveryPersonId))
                .expectNext(actorId)
                .verifyComplete();
    }
}
