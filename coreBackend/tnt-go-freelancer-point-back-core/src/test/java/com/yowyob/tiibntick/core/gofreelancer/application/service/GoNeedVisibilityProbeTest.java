package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.AnnouncementController;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerEligibilityUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPriceQuoteUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.MatchingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import org.junit.jupiter.api.DisplayName;
import org.springframework.core.MethodParameter;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import org.springframework.r2dbc.core.RowsFetchSpec;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sonde de comportement « un envoi Go est-il visible des livreurs ? », écrite pour compiler sur la
 * version de référence comme sur la version qui ajoute la publication : le câblage optionnel est
 * recherché par réflexion, exactement comme Spring l'injecterait s'il existait.
 *
 * <ul>
 *   <li>créer un besoin publie une annonce sur devis ({@code QUOTE_REQUEST}) au nom de l'expéditeur ;</li>
 *   <li>{@code GET /api/announcements/nearby} existe et répond une liste.</li>
 * </ul>
 */
@DisplayName("Visibilité d'un envoi Go — sonde")
class GoNeedVisibilityProbeTest {

    /** Réponse neutre par type : Mono → vide, Flux → vide (aucun NPE dans les mocks génériques). */
    private static final Answer<Object> REACTIVE_EMPTY = inv -> {
        Class<?> t = inv.getMethod().getReturnType();
        if (Mono.class.isAssignableFrom(t)) return Mono.empty();
        if (Flux.class.isAssignableFrom(t)) return Flux.empty();
        return null;
    };

    @Test
    @DisplayName("créer un besoin Go publie une annonce QUOTE_REQUEST")
    void creatingNeed_publishesAnnouncement() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID announcementId = UUID.randomUUID();

        IDeliveryNeedRepository needs = mock(IDeliveryNeedRepository.class, REACTIVE_EMPTY);
        when(needs.save(any())).thenAnswer(inv -> Mono.just((DeliveryNeed) inv.getArgument(0)));
        AddressUseCase addresses = mock(AddressUseCase.class, REACTIVE_EMPTY);
        when(addresses.createAddress(any())).thenAnswer(inv -> {
            AddressDTO in = inv.getArgument(0);
            return Mono.just(AddressDTO.builder().id(UUID.randomUUID()).address(in.getAddress()).build());
        });
        GofpUserRepository users = mock(GofpUserRepository.class, REACTIVE_EMPTY);

        DeliveryNeedApplicationService service = new DeliveryNeedApplicationService(
                needs, mock(AdminRelayPointUseCase.class, REACTIVE_EMPTY), mock(PushNotificationPort.class, REACTIVE_EMPTY),
                mock(MatchingUseCase.class, REACTIVE_EMPTY), addresses, users,
                mock(GofpFreelancerRepository.class, REACTIVE_EMPTY), mock(FreelancerPriceQuoteUseCase.class, REACTIVE_EMPTY),
                mock(FreelancerEligibilityUseCase.class, REACTIVE_EMPTY), legacyUserExists());

        IDeliveryAnnouncementPort port = mock(IDeliveryAnnouncementPort.class, REACTIVE_EMPTY);
        when(port.publish(any())).thenAnswer(inv -> {
            PublishAnnouncementPortCommand c = inv.getArgument(0);
            return Mono.just(new AnnouncementSnapshot(
                    announcementId, tenantId, c.clientId(), c.title(), c.description(), AnnouncementStatus.PUBLISHED,
                    Instant.now(), Instant.now(), null, "XAF", c.pricingMode(),
                    c.pickupAddress(), c.deliveryAddress(), 0.0,
                    null, null, null, null, null, null, null, null,
                    "Recipient", "+237000000000", null, null, null, List.of()));
        });
        TenantContextHolder tenant = mock(TenantContextHolder.class, REACTIVE_EMPTY);
        when(tenant.currentTenantId()).thenReturn(Mono.just(tenantId));
        IAnnouncementRepository mirror = mock(IAnnouncementRepository.class, REACTIVE_EMPTY);
        when(mirror.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        wirePublicationIfPresent(service, port, tenant, mirror, needs);

        service.createDeliveryNeed(request(), owner).block();

        verify(port, times(1)).publish(any());
    }

    @Test
    @DisplayName("GET /api/announcements/nearby existe et répond une liste")
    void nearbyRouteExists() throws Exception {
        Constructor<?> ctor = AnnouncementController.class.getConstructors()[0];
        Object[] args = new Object[ctor.getParameterCount()];
        for (int i = 0; i < args.length; i++) args[i] = mock(ctor.getParameterTypes()[i], REACTIVE_EMPTY);
        // Le caller authentifié, comme le fournit le résolveur @CurrentUser de l'application.
        TntSecurityContext caller = TntSecurityContext.builder().userId(UUID.randomUUID()).build();
        HandlerMethodArgumentResolver currentUser = new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter p) {
                return p.getParameterType().equals(TntSecurityContext.class);
            }
            @Override public Mono<Object> resolveArgument(MethodParameter p, BindingContext b, ServerWebExchange e) {
                return Mono.just(caller);
            }
        };
        WebTestClient client = WebTestClient.bindToController(ctor.newInstance(args))
                .argumentResolvers(cfg -> cfg.addCustomResolver(currentUser))
                .build();

        client.get().uri("/api/announcements/nearby").exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    /** Ce que Spring ferait : injecter le service de publication s'il existe dans ce code. */
    private static void wirePublicationIfPresent(DeliveryNeedApplicationService service, IDeliveryAnnouncementPort port,
                                                 TenantContextHolder tenant, IAnnouncementRepository mirror,
                                                 IDeliveryNeedRepository needs) throws Exception {
        Class<?> linkType;
        try {
            linkType = Class.forName("com.yowyob.tiibntick.core.gofreelancer.application.service.NeedAnnouncementService");
        } catch (ClassNotFoundException absent) {
            return;
        }
        Constructor<?> ctor = linkType.getConstructors()[0];
        Object[] args = new Object[ctor.getParameterCount()];
        Object[] known = {port, tenant, mirror, needs};
        for (int i = 0; i < args.length; i++) {
            Class<?> type = ctor.getParameterTypes()[i];
            for (Object k : known) if (type.isInstance(k)) args[i] = k;
            if (args[i] == null) args[i] = mock(type, REACTIVE_EMPTY);
        }
        Object link = ctor.newInstance(args);
        Method setter = DeliveryNeedApplicationService.class.getDeclaredMethod("setNeedAnnouncements", linkType);
        setter.setAccessible(true);
        setter.invoke(service, link);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DatabaseClient legacyUserExists() {
        DatabaseClient db = mock(DatabaseClient.class);
        DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class);
        RowsFetchSpec rows = mock(RowsFetchSpec.class);
        FetchSpec fetch = mock(FetchSpec.class);
        when(db.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.map(any(java.util.function.BiFunction.class))).thenReturn(rows);
        when(rows.first()).thenReturn(Mono.just(1));
        when(spec.fetch()).thenReturn(fetch);
        when(fetch.rowsUpdated()).thenReturn(Mono.just(1L));
        return db;
    }

    private static DeliveryNeedRequestDTO request() {
        DeliveryNeedRequestDTO r = new DeliveryNeedRequestDTO();
        r.setTitle("Colis vêtements");
        r.setDescription("2 kg");
        r.setPaymentMethod("CASH");
        r.setPickupAddress(AddressDTO.builder().address(Address.builder().street("Rue Joss").city("Douala").country("CM")
                .coordinates(GeoCoordinates.of(4.0511, 9.7679)).build()).build());
        r.setDeliveryAddress(AddressDTO.builder().address(Address.builder().landmark("Carrefour Ndokoti")
                .city("Douala").country("CM").build()).build());
        return r;
    }
}
