package com.yowyob.tiibntick.core.gofreelancer.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryAssistanceDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryTrackingDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.entity.AddressEntity;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.AddressReactiveRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.CachePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.realtime.application.port.in.IGetPresenceUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.PresenceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that tracking and assistance presence lookups use:
 *   (F-R3) the tenant from the reactive security context, not SYSTEM_TENANT;
 *   (F-R3) the resolved coreUserId (JWT sub proxy), not the raw assignedFreelancerId.
 *
 * <p>Covers: resolved presence lookup (C1), SYSTEM_TENANT warn log (C2),
 * tenant-scoped cache keys (C3), SSE context propagation (C4),
 * delivery-need path with and without delivery row (T2).
 *
 * @author MANFOUO BRAUN
 */
@ExtendWith(MockitoExtension.class)
class DeliveryApplicationServiceTest {

    @Mock private DeliveryRepository deliveryRepository;
    @Mock private IDeliveryNeedRepository deliveryNeedRepository;
    @Mock private IAnnouncementRepository announcementRepository;
    @Mock private AddressReactiveRepository addressRepository;
    @Mock private IGetPresenceUseCase getPresenceUseCase;
    @Mock private CachePort cachePort;
    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private GofpFreelancerRepository gofpFreelancerRepository;

    private DeliveryApplicationService service;

    private final UUID tenantId        = UUID.randomUUID();
    private final UUID announcementId  = UUID.randomUUID();
    private final UUID deliveryId      = UUID.randomUUID();
    private final UUID deliveryNeedId  = UUID.randomUUID();
    private final UUID freelancerId    = UUID.randomUUID(); // gofp-local or coreFreelancerId
    private final UUID resolvedUserId  = UUID.randomUUID(); // coreUserId = JWT sub proxy
    private final UUID pickupAddrId    = UUID.randomUUID();
    private final UUID delivAddrId     = UUID.randomUUID();

    private final List<LogCapture> logCaptures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DeliveryApplicationService(
                deliveryRepository, deliveryNeedRepository, announcementRepository,
                addressRepository, getPresenceUseCase, cachePort, tenantContextHolder,
                gofpFreelancerRepository);
        // These tests verify tracking behaviour, not access control; the ownership guard
        // is tested separately in GofpDeliveryControllerTrackingGuardTest.
        ReflectionTestUtils.setField(service, "ownershipGuardEnabled", false);

        lenient().when(cachePort.get(anyString(), any())).thenReturn(Mono.empty());
        lenient().when(cachePort.set(anyString(), any(), any())).thenReturn(Mono.just(true));

        // Default: findById resolves to a GofpFreelancer with coreUserId = resolvedUserId;
        // findByCoreFreelancerId returns empty (not needed unless freelancerId is a core FK).
        lenient().when(gofpFreelancerRepository.findById(freelancerId))
                .thenReturn(Mono.just(gofpFreelancer(freelancerId, resolvedUserId)));
        lenient().when(gofpFreelancerRepository.findByCoreFreelancerId(any()))
                .thenReturn(Mono.empty());
    }

    @AfterEach
    void tearDown() {
        for (LogCapture capture : logCaptures) {
            capture.logger().detachAppender(capture.appender());
        }
        logCaptures.clear();
    }

    // ══════════════════════════════════════════════════════════════════════
    // C1 — getDeliveryAssistance calls getPresence with resolved coreUserId
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void getDeliveryAssistance_readsPresenceUnderContextTenant_notSystemTenant() {
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));

        Delivery delivery = deliveryWithFreelancer();
        DeliveryNeed need = deliveryNeed();

        when(deliveryRepository.findById(deliveryId)).thenReturn(Mono.just(delivery));
        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        // Lenient: unused if mutation routes to wrong key
        lenient().when(getPresenceUseCase.getPresence(eq(resolvedUserId.toString()), eq(tenantId.toString())))
                .thenReturn(Mono.just(presenceAt(3.880, 11.518)));
        // Wrong-key stub: mutation using getId() calls freelancerId → presence absent → AssertionError
        lenient().when(getPresenceUseCase.getPresence(eq(freelancerId.toString()), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.getDeliveryAssistance(deliveryId))
                .expectNextMatches(dto ->
                        dto.getCurrentLatitude() != null
                        && dto.getFreelancerPositionAt() != null)
                .verifyComplete();

        // Presence queried with resolved coreUserId, NOT with the raw freelancerId
        verify(getPresenceUseCase).getPresence(resolvedUserId.toString(), tenantId.toString());
        verify(getPresenceUseCase, never())
                .getPresence(eq(freelancerId.toString()), anyString());
        verify(getPresenceUseCase, never())
                .getPresence(anyString(), eq(TenantContextHolder.SYSTEM_TENANT.toString()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // C1 — trackDelivery calls getPresence with resolved coreUserId
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void trackDelivery_readsPresenceUnderContextTenant_notSystemTenant() {
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));

        Announcement announcement = announcement();
        Delivery delivery = delivery();
        DeliveryNeed need = deliveryNeed();

        when(announcementRepository.findById(announcementId)).thenReturn(Mono.just(announcement));
        when(deliveryRepository.findByAnnouncementId(announcementId)).thenReturn(Mono.just(delivery));
        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        // Lenient: unused if mutation routes to wrong key
        lenient().when(getPresenceUseCase.getPresence(eq(resolvedUserId.toString()), eq(tenantId.toString())))
                .thenReturn(Mono.just(presenceAt(3.880, 11.518)));
        // Wrong-key stub: mutation using getId() calls freelancerId → presence absent → AssertionError
        lenient().when(getPresenceUseCase.getPresence(eq(freelancerId.toString()), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.trackDelivery(announcementId))
                .expectNextMatches(dto ->
                        dto.getFreelancerLatitude() != null
                        && dto.getFreelancerLatitude() != 0f
                        && dto.getFreelancerPositionAt() != null)
                .verifyComplete();

        verify(getPresenceUseCase).getPresence(resolvedUserId.toString(), tenantId.toString());
        verify(getPresenceUseCase, never())
                .getPresence(eq(freelancerId.toString()), anyString());
        verify(getPresenceUseCase, never())
                .getPresence(anyString(), eq(TenantContextHolder.SYSTEM_TENANT.toString()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // C3 — cache key includes tenant; two different tenants yield distinct keys
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void trackDelivery_cacheKeyIncludesTenant_twoDifferentTenantsYieldDifferentKeys() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        List<String> capturedKeys = new ArrayList<>();

        when(tenantContextHolder.currentTenantId())
                .thenReturn(Mono.just(tenantA))
                .thenReturn(Mono.just(tenantB));

        Announcement announcement = announcement();
        Delivery delivery = delivery();
        DeliveryNeed need = deliveryNeed();

        lenient().when(announcementRepository.findById(announcementId)).thenReturn(Mono.just(announcement));
        lenient().when(deliveryRepository.findByAnnouncementId(announcementId)).thenReturn(Mono.just(delivery));
        lenient().when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        lenient().when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        lenient().when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        lenient().when(getPresenceUseCase.getPresence(anyString(), anyString())).thenReturn(Mono.empty());

        when(cachePort.get(anyString(), any())).thenReturn(Mono.empty());
        when(cachePort.set(anyString(), any(), any())).thenAnswer(inv -> {
            capturedKeys.add(inv.getArgument(0));
            return Mono.just(true);
        });

        StepVerifier.create(service.trackDelivery(announcementId)).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.trackDelivery(announcementId)).expectNextCount(1).verifyComplete();

        assertThat(capturedKeys).hasSize(2);
        assertThat(capturedKeys.get(0)).contains(tenantA.toString()).contains(announcementId.toString());
        assertThat(capturedKeys.get(1)).contains(tenantB.toString()).contains(announcementId.toString());
        assertThat(capturedKeys.get(0)).isNotEqualTo(capturedKeys.get(1));
    }

    // ══════════════════════════════════════════════════════════════════════
    // C2 — SYSTEM_TENANT fallback emits warn on getDeliveryAssistance
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void getDeliveryAssistance_systemTenantFallback_emitsWarnLog() {
        when(tenantContextHolder.currentTenantId())
                .thenReturn(Mono.just(TenantContextHolder.SYSTEM_TENANT));

        Delivery delivery = deliveryWithFreelancer();
        DeliveryNeed need = deliveryNeed();

        when(deliveryRepository.findById(deliveryId)).thenReturn(Mono.just(delivery));
        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        lenient().when(getPresenceUseCase.getPresence(anyString(), anyString())).thenReturn(Mono.empty());

        ListAppender<ILoggingEvent> appender = attachLogAppender(DeliveryApplicationService.class);

        StepVerifier.create(service.getDeliveryAssistance(deliveryId))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(appender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> e.getFormattedMessage().contains("SYSTEM_TENANT")
                        && e.getFormattedMessage().contains(deliveryId.toString()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // C2 — SYSTEM_TENANT fallback emits warn on trackDelivery (buildTrackingDTO)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void trackDelivery_systemTenantFallback_emitsWarnLog() {
        when(tenantContextHolder.currentTenantId())
                .thenReturn(Mono.just(TenantContextHolder.SYSTEM_TENANT));

        Announcement announcement = announcement();
        Delivery delivery = delivery();
        DeliveryNeed need = deliveryNeed();

        when(announcementRepository.findById(announcementId)).thenReturn(Mono.just(announcement));
        when(deliveryRepository.findByAnnouncementId(announcementId)).thenReturn(Mono.just(delivery));
        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        lenient().when(getPresenceUseCase.getPresence(anyString(), anyString())).thenReturn(Mono.empty());

        ListAppender<ILoggingEvent> appender = attachLogAppender(DeliveryApplicationService.class);

        StepVerifier.create(service.trackDelivery(announcementId))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(appender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> e.getFormattedMessage().contains("SYSTEM_TENANT")
                        && e.getFormattedMessage().contains(announcementId.toString()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // C4 — SSE stream: both ticks read presence under real tenant with resolved userId
    // ══════════════════════════════════════════════════════════════════════

    /**
     * The tenant is resolved once at subscription time and captured in the closure.
     * The virtual clock is deterministic: exactly two ticks, exactly two presence calls.
     */
    @Test
    void trackDeliveryStream_twoTicks_bothReadPresenceUnderRealTenant() {
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));

        Announcement announcement = announcement();
        Delivery delivery = delivery();
        DeliveryNeed need = deliveryNeed();

        when(announcementRepository.findById(announcementId)).thenReturn(Mono.just(announcement));
        when(deliveryRepository.findByAnnouncementId(announcementId)).thenReturn(Mono.just(delivery));
        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        when(getPresenceUseCase.getPresence(eq(resolvedUserId.toString()), eq(tenantId.toString())))
                .thenReturn(Mono.empty());

        StepVerifier.withVirtualTime(() -> service.trackDeliveryStream(announcementId))
                .thenAwait(Duration.ofSeconds(5))
                .expectNextCount(1)
                .thenAwait(Duration.ofSeconds(5))
                .thenCancel()
                .verify(Duration.ofSeconds(10));

        verify(getPresenceUseCase, times(2))
                .getPresence(resolvedUserId.toString(), tenantId.toString());
        verify(getPresenceUseCase, never())
                .getPresence(anyString(), eq(TenantContextHolder.SYSTEM_TENANT.toString()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // T2-a — trackDeliveryByNeed with deliveryId == null: no exception, DTO returned
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void trackDeliveryByNeed_deliveryIdNull_returnsDtoWithoutError() {
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));

        DeliveryNeed need = deliveryNeed(); // deliveryId = null, assignedFreelancerId = freelancerId

        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        when(getPresenceUseCase.getPresence(anyString(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.trackDeliveryByNeed(deliveryNeedId, null))
                .expectNextMatches(dto ->
                        dto.getDeliveryNeedId().equals(deliveryNeedId)
                        && dto.getDeliveryId() == null          // no delivery yet
                        && dto.getPickupLatitude() != null)     // addresses populated
                .verifyComplete();
    }

    // ══════════════════════════════════════════════════════════════════════
    // T2-b — trackDeliveryByNeed fills freelancer GPS when presence exists
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void trackDeliveryByNeed_withPresence_fillsFreelancerCoordinates() {
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));

        DeliveryNeed need = deliveryNeed();

        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        // Lenient: unused if mutation routes to wrong key
        lenient().when(getPresenceUseCase.getPresence(eq(resolvedUserId.toString()), eq(tenantId.toString())))
                .thenReturn(Mono.just(presenceAt(3.880, 11.518)));
        // Wrong-key stub: mutation using getId() calls freelancerId → presence absent → AssertionError
        lenient().when(getPresenceUseCase.getPresence(eq(freelancerId.toString()), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.trackDeliveryByNeed(deliveryNeedId, null))
                .expectNextMatches(dto ->
                        dto.getFreelancerLatitude() != null
                        && dto.getFreelancerLatitude() != 0f
                        && dto.getFreelancerLongitude() != null
                        && dto.getFreelancerPositionAt() != null)
                .verifyComplete();

        // Resolved userId was used, not the raw assignedFreelancerId
        verify(getPresenceUseCase).getPresence(resolvedUserId.toString(), tenantId.toString());
    }

    // ══════════════════════════════════════════════════════════════════════
    // T2-c — trackDeliveryByNeed with null assignedFreelancerId: GPS stays null, no error
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void trackDeliveryByNeed_nullAssignedFreelancerId_gpsNullNoError() {
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));

        // Need with no freelancer assigned yet
        DeliveryNeed need = DeliveryNeed.builder()
                .id(deliveryNeedId)
                .pickupAddressId(pickupAddrId)
                .deliveryAddressId(delivAddrId)
                .assignedFreelancerId(null)
                .build();

        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));

        StepVerifier.create(service.trackDeliveryByNeed(deliveryNeedId, null))
                .expectNextMatches(dto ->
                        dto.getFreelancerLatitude() == null
                        && dto.getFreelancerLongitude() == null
                        && dto.getPickupLatitude() != null)
                .verifyComplete();

        verify(getPresenceUseCase, never()).getPresence(anyString(), anyString());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HA-1 — getDeliveryAssistance: presence absent → freelancerPositionAt is null
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void getDeliveryAssistance_withoutPresence_freelancerPositionAtIsNull() {
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));

        Delivery delivery = deliveryWithFreelancer();
        DeliveryNeed need = deliveryNeed();

        when(deliveryRepository.findById(deliveryId)).thenReturn(Mono.just(delivery));
        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        lenient().when(getPresenceUseCase.getPresence(anyString(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.getDeliveryAssistance(deliveryId))
                .expectNextMatches(dto -> dto.getFreelancerPositionAt() == null)
                .verifyComplete();
    }

    // ══════════════════════════════════════════════════════════════════════
    // HA-2 — trackDelivery: presence absent → freelancerPositionAt is null
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void trackDelivery_withoutPresence_freelancerPositionAtIsNull() {
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));

        Announcement announcement = announcement();
        Delivery delivery = delivery();
        DeliveryNeed need = deliveryNeed();

        when(announcementRepository.findById(announcementId)).thenReturn(Mono.just(announcement));
        when(deliveryRepository.findByAnnouncementId(announcementId)).thenReturn(Mono.just(delivery));
        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        lenient().when(getPresenceUseCase.getPresence(anyString(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.trackDelivery(announcementId))
                .expectNextMatches(dto -> dto.getFreelancerPositionAt() == null)
                .verifyComplete();
    }

    // ══════════════════════════════════════════════════════════════════════
    // HA-3 — trackDeliveryByNeed: presence absent → freelancerPositionAt is null
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void trackDeliveryByNeed_withoutPresence_freelancerPositionAtIsNull() {
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));

        DeliveryNeed need = deliveryNeed();

        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        lenient().when(getPresenceUseCase.getPresence(anyString(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.trackDeliveryByNeed(deliveryNeedId, null))
                .expectNextMatches(dto -> dto.getFreelancerPositionAt() == null)
                .verifyComplete();
    }

    // ══════════════════════════════════════════════════════════════════════
    // T2-d — two tenants produce two distinct cache keys on the delivery-need path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void trackDeliveryByNeed_cacheKeyIncludesTenant_twoDifferentTenantsYieldDifferentKeys() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        List<String> capturedKeys = new ArrayList<>();

        when(tenantContextHolder.currentTenantId())
                .thenReturn(Mono.just(tenantA))
                .thenReturn(Mono.just(tenantB));

        DeliveryNeed need = deliveryNeed();

        lenient().when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        lenient().when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        lenient().when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        lenient().when(getPresenceUseCase.getPresence(anyString(), anyString())).thenReturn(Mono.empty());

        when(cachePort.get(anyString(), any())).thenReturn(Mono.empty());
        when(cachePort.set(anyString(), any(), any())).thenAnswer(inv -> {
            capturedKeys.add(inv.getArgument(0));
            return Mono.just(true);
        });

        StepVerifier.create(service.trackDeliveryByNeed(deliveryNeedId, null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.trackDeliveryByNeed(deliveryNeedId, null)).expectNextCount(1).verifyComplete();

        assertThat(capturedKeys).hasSize(2);
        assertThat(capturedKeys.get(0)).contains(tenantA.toString()).contains(deliveryNeedId.toString());
        assertThat(capturedKeys.get(1)).contains(tenantB.toString()).contains(deliveryNeedId.toString());
        assertThat(capturedKeys.get(0)).isNotEqualTo(capturedKeys.get(1));
    }

    // ══════════════════════════════════════════════════════════════════════
    // F-R3 Convergence R1 — resolvePresenceUserId uses getCoreUserId(), not getId()
    //
    // Critical: sub, gofpLocalId and coreFreelancerId are all DISTINCT UUIDs.
    // The presence mock is keyed on sub (not on gofpLocalId).
    // If resolvePresenceUserId switched to gofp.getId(), it would look up the
    // presence under gofpLocalId → no position in the DTO → test goes RED.
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void convergence_R1_trackDeliveryByNeed_presenceKeyIsCoreUserId_notGofpLocalId() {
        UUID sub         = UUID.randomUUID(); // the JWT sub = what presence is keyed on
        UUID gofpLocalId = UUID.randomUUID(); // the gofp-internal UUID (GofpFreelancer.id)

        // Freelancer row: gofp-local id ≠ JWT sub — the two identities are explicitly distinct
        GofpFreelancer freelancer = GofpFreelancer.builder()
                .id(gofpLocalId)
                .coreFreelancerId(UUID.randomUUID())
                .coreUserId(sub)
                .build();

        // DeliveryNeed points to the gofp-local id as assignedFreelancerId
        DeliveryNeed need = DeliveryNeed.builder()
                .id(deliveryNeedId)
                .pickupAddressId(pickupAddrId)
                .deliveryAddressId(delivAddrId)
                .assignedFreelancerId(gofpLocalId)
                .build();

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        when(gofpFreelancerRepository.findById(gofpLocalId)).thenReturn(Mono.just(freelancer));

        // Presence is stored under sub — if getId() were used it would be under gofpLocalId
        PresenceRecord presence = new PresenceRecord(sub.toString(), tenantId.toString(), null);
        presence.updateLocation(GeoCoordinates.of(3.880, 11.518));
        // Lenient: unused if mutation routes to the wrong key — must not cause a framework error
        lenient().when(getPresenceUseCase.getPresence(sub.toString(), tenantId.toString()))
                .thenReturn(Mono.just(presence));
        // Wrong key stub: if mutation calls getId() instead of getCoreUserId(), presence is absent
        lenient().when(getPresenceUseCase.getPresence(eq(gofpLocalId.toString()), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.trackDeliveryByNeed(deliveryNeedId, null))
                .expectNextMatches(dto ->
                        dto.getFreelancerLatitude() != null
                        && dto.getFreelancerLatitude() != 0f)
                .verifyComplete();

        // resolvePresenceUserId must have used getCoreUserId() = sub, never getId() = gofpLocalId
        verify(getPresenceUseCase).getPresence(sub.toString(), tenantId.toString());
        verify(getPresenceUseCase, never()).getPresence(eq(gofpLocalId.toString()), anyString());
    }

    // ══════════════════════════════════════════════════════════════════════
    // F-R3 Convergence R2 — resolvePresenceUserId via findByCoreFreelancerId fallback
    //
    // assignedFreelancerId does not match any gofp-local id → findById returns empty
    // → switchIfEmpty triggers findByCoreFreelancerId → still returns getCoreUserId()
    // If getId() were used instead of getCoreUserId(), getId() = gofpLocalId ≠ sub
    // and the presence stub (keyed on sub) would never be hit → DTO has no GPS → RED.
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void convergence_R2_trackDeliveryByNeed_viaCoreFreelancerIdFallback_presenceKeyIsCoreUserId() {
        UUID sub           = UUID.randomUUID(); // JWT sub = presence key
        UUID coreFlId      = UUID.randomUUID(); // used as assignedFreelancerId (a coreFreelancerId)
        UUID gofpLocalId   = UUID.randomUUID(); // the actual gofp row id (distinct from sub and coreFlId)

        GofpFreelancer freelancer = GofpFreelancer.builder()
                .id(gofpLocalId)
                .coreFreelancerId(coreFlId)
                .coreUserId(sub)
                .build();

        DeliveryNeed need = DeliveryNeed.builder()
                .id(deliveryNeedId)
                .pickupAddressId(pickupAddrId)
                .deliveryAddressId(delivAddrId)
                .assignedFreelancerId(coreFlId)   // not a gofp-local id → findById returns empty
                .build();

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryNeedRepository.findById(deliveryNeedId)).thenReturn(Mono.just(need));
        when(addressRepository.findById(pickupAddrId)).thenReturn(Mono.just(address(3.866, 11.517)));
        when(addressRepository.findById(delivAddrId)).thenReturn(Mono.just(address(3.900, 11.520)));
        // findById misses → fallback to findByCoreFreelancerId
        when(gofpFreelancerRepository.findById(coreFlId)).thenReturn(Mono.empty());
        when(gofpFreelancerRepository.findByCoreFreelancerId(coreFlId)).thenReturn(Mono.just(freelancer));

        PresenceRecord presence = new PresenceRecord(sub.toString(), tenantId.toString(), null);
        presence.updateLocation(GeoCoordinates.of(3.880, 11.518));
        // Lenient: unused if mutation routes to the wrong key — must not cause a framework error
        lenient().when(getPresenceUseCase.getPresence(sub.toString(), tenantId.toString()))
                .thenReturn(Mono.just(presence));
        // Wrong key stubs: if mutation calls getId() or getCoreFreelancerId() instead of getCoreUserId(),
        // presence is absent → DTO has no coordinates → AssertionError (not PotentialStubbingProblem)
        lenient().when(getPresenceUseCase.getPresence(eq(gofpLocalId.toString()), anyString()))
                .thenReturn(Mono.empty());
        lenient().when(getPresenceUseCase.getPresence(eq(coreFlId.toString()), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.trackDeliveryByNeed(deliveryNeedId, null))
                .expectNextMatches(dto ->
                        dto.getFreelancerLatitude() != null
                        && dto.getFreelancerLatitude() != 0f)
                .verifyComplete();

        verify(getPresenceUseCase).getPresence(sub.toString(), tenantId.toString());
        verify(getPresenceUseCase, never()).getPresence(eq(gofpLocalId.toString()), anyString());
        verify(getPresenceUseCase, never()).getPresence(eq(coreFlId.toString()), anyString());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Fixtures
    // ══════════════════════════════════════════════════════════════════════

    private Delivery delivery() {
        Delivery d = new Delivery();
        d.setId(deliveryId);
        d.setAnnouncementId(announcementId);
        d.setFreelancerId(freelancerId);
        d.setDeliveryNeedId(deliveryNeedId);
        d.setStatus(DeliveryStatus.CREATED);
        return d;
    }

    private Delivery deliveryWithFreelancer() {
        Delivery d = new Delivery();
        d.setId(deliveryId);
        d.setFreelancerId(freelancerId);
        d.setDeliveryNeedId(deliveryNeedId);
        d.setStatus(DeliveryStatus.CREATED);
        return d;
    }

    private Announcement announcement() {
        return Announcement.builder()
                .id(announcementId)
                .assignedFreelancerId(freelancerId)
                .build();
    }

    /** DeliveryNeed with assignedFreelancerId set but NO deliveryId (typical Go scenario). */
    private DeliveryNeed deliveryNeed() {
        return DeliveryNeed.builder()
                .id(deliveryNeedId)
                .pickupAddressId(pickupAddrId)
                .deliveryAddressId(delivAddrId)
                .assignedFreelancerId(freelancerId)
                .build();
    }

    private AddressEntity address(double lat, double lon) {
        return AddressEntity.builder().latitude(lat).longitude(lon).build();
    }

    private PresenceRecord presenceAt(double lat, double lon) {
        PresenceRecord record = new PresenceRecord(resolvedUserId.toString(), tenantId.toString(), null);
        record.updateLocation(GeoCoordinates.of(lat, lon));
        return record;
    }

    private GofpFreelancer gofpFreelancer(UUID id, UUID coreUserId) {
        return GofpFreelancer.builder()
                .id(id)
                .coreFreelancerId(UUID.randomUUID())
                .coreUserId(coreUserId)
                .build();
    }

    private ListAppender<ILoggingEvent> attachLogAppender(Class<?> loggerClass) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logCaptures.add(new LogCapture(logger, appender));
        return appender;
    }

    private record LogCapture(Logger logger, ListAppender<ILoggingEvent> appender) {}
}
