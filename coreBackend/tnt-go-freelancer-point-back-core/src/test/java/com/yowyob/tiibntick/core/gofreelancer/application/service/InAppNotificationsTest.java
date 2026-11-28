package com.yowyob.tiibntick.core.gofreelancer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yowyob.kernel.event.application.port.out.ProcessedEventStorePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IInAppInboxPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.InAppNotification;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Lot C-36 — in-app inbox of the three roles, held by the Kernel (decision D-157): the P1 events, "mine only",
 * idempotency through the shared table of processed events.
 */
class InAppNotificationsTest {

    /**
     * In-memory Kernel inbox: messages of every user; {@code mine()} answers for {@link #caller}, as the Kernel
     * answers for the user of the request's token.
     */
    static final class FakeKernelInbox implements IInAppInboxPort {
        final List<InAppNotification> rows = new ArrayList<>();
        UUID caller;
        Instant clock = NOW;

        @Override public Mono<Void> send(UUID recipient, String title, String message, Map<String, String> meta) {
            clock = clock.plusSeconds(1);
            rows.add(new InAppNotification(UUID.randomUUID(), recipient, NotificationType.valueOf(meta.get(META_TYPE)),
                    title, message, meta.get(META_PARAMS), meta.get(META_LINK_KIND),
                    meta.get(META_LINK_ID) == null ? null : UUID.fromString(meta.get(META_LINK_ID)), clock, null));
            return Mono.empty();
        }
        @Override public Flux<InAppNotification> mine() {
            return Flux.fromIterable(List.copyOf(rows)).filter(r -> r.recipientUserId().equals(caller));
        }
        @Override public Mono<InAppNotification> markRead(UUID id) {
            for (int i = 0; i < rows.size(); i++) {
                InAppNotification r = rows.get(i);
                if (r.id().equals(id) && r.recipientUserId().equals(caller)) {
                    InAppNotification read = new InAppNotification(r.id(), r.recipientUserId(), r.type(), r.title(),
                            r.message(), r.params(), r.linkKind(), r.linkId(), r.createdAt(), NOW.plusSeconds(3600));
                    rows.set(i, read);
                    return Mono.just(read);
                }
            }
            return Mono.error(new IllegalArgumentException("not in the caller's inbox"));
        }
        InAppNotification stored(UUID owner, Duration age, boolean read) {
            InAppNotification n = new InAppNotification(UUID.randomUUID(), owner, NotificationType.PARCEL_PICKED_UP,
                    "t", "m", null, null, null, NOW.minus(age), read ? NOW : null);
            rows.add(n);
            return n;
        }
        List<InAppNotification> of(UUID user) {
            return rows.stream().filter(r -> user.equals(r.recipientUserId())).toList();
        }
        InAppNotification byId(UUID id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
        }
    }

    /** In-memory event_bus.processed_events. */
    static final class FakeProcessed implements ProcessedEventStorePort {
        final Set<String> ids = new HashSet<>();
        @Override public Mono<Boolean> isProcessed(String group, String topic, String eventId) {
            return Mono.just(ids.contains(group + "/" + topic + "/" + eventId));
        }
        @Override public Mono<Void> markProcessed(String group, String topic, String eventId) {
            ids.add(group + "/" + topic + "/" + eventId);
            return Mono.empty();
        }
        @Override public Mono<Long> purgeOlderThan(Duration retention) {
            return Mono.just(0L);
        }
    }

    @SuppressWarnings("unchecked")
    static ObjectProvider<ProcessedEventStorePort> provider(ProcessedEventStorePort store) {
        ObjectProvider<ProcessedEventStorePort> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(store);
        return p;
    }

    static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    FakeKernelInbox kernel;
    FakeProcessed processed;
    InAppNotificationService inbox;

    @BeforeEach
    void setUpInbox() {
        kernel = new FakeKernelInbox();
        processed = new FakeProcessed();
        inbox = new InAppNotificationService(kernel, provider(processed));
    }

    // ── "mine only" ──────────────────────────────────────────────────────

    @Nested
    class MineOnly {
        final UUID me = UUID.randomUUID();
        final UUID other = UUID.randomUUID();

        @BeforeEach
        void asMe() {
            kernel.caller = me;
        }

        @Test
        void listsOnlyMyNotificationsUnreadFirst() {
            InAppNotification read = kernel.stored(me, Duration.ofHours(1), true);
            InAppNotification unreadOld = kernel.stored(me, Duration.ofDays(2), false);
            kernel.stored(other, Duration.ofMinutes(1), false);

            InAppNotificationService.Inbox box = inbox.mine(me, 0, 30).block();
            assertThat(box.items()).extracting(InAppNotification::id).containsExactly(unreadOld.id(), read.id());
            assertThat(box.unread()).isEqualTo(1);
            assertThat(inbox.mine(me, 1, 1).block().items()).extracting(InAppNotification::id)
                    .as("second page of one").containsExactly(read.id());
        }

        @Test
        void someoneElsesNotificationCannotBeReadOrDeleted() {
            InAppNotification theirs = kernel.stored(other, Duration.ofMinutes(1), false);

            StepVerifier.create(inbox.markRead(theirs.id(), me))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 404)
                    .verify();
            StepVerifier.create(inbox.delete(theirs.id(), me))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 404)
                    .verify();
            assertThat(kernel.byId(theirs.id()).readAt()).isNull();
        }

        @Test
        void markReadDeleteAndReadAllActOnMineOnly() {
            InAppNotification a = kernel.stored(me, Duration.ofMinutes(3), false);
            InAppNotification b = kernel.stored(me, Duration.ofMinutes(2), false);
            InAppNotification theirs = kernel.stored(other, Duration.ofMinutes(1), false);

            assertThat(inbox.markRead(a.id(), me).block().readAt()).isNotNull();
            inbox.delete(b.id(), me).block();
            assertThat(kernel.byId(b.id()).readAt()).as("the Kernel has no deletion: marked read").isNotNull();
            kernel.stored(me, Duration.ofMinutes(1), false);
            assertThat(inbox.markAllRead(me).block()).isEqualTo(1);

            assertThat(kernel.of(me)).hasSize(3).allMatch(n -> n.readAt() != null);
            assertThat(kernel.byId(theirs.id()).readAt()).isNull();
        }

        @Test
        void kernelDownIsA503NotAnEmptyInbox() {
            IInAppInboxPort down = mock(IInAppInboxPort.class);
            when(down.mine()).thenReturn(Flux.error(new IllegalStateException("kernel down")));
            StepVerifier.create(new InAppNotificationService(down, provider(processed)).mine(me, 0, 30))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 503)
                    .verify();
        }
    }

    @Test
    void idempotentAcrossInstancesAndPerRecipient() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        inbox.emit(me, NotificationType.PARCEL_PICKED_UP, "picked-up:x", "t", "m", Map.of(), null, null).block();
        // a second instance (restart, other node) shares event_bus.processed_events
        new InAppNotificationService(kernel, provider(processed))
                .emit(me, NotificationType.PARCEL_PICKED_UP, "picked-up:x", "t", "m", Map.of(), null, null).block();
        inbox.emit(other, NotificationType.PARCEL_PICKED_UP, "picked-up:x", "t", "m", Map.of(), null, null).block();
        assertThat(kernel.of(me)).hasSize(1);
        assertThat(kernel.of(other)).hasSize(1);
    }

    @Test
    void metadataCarriesTheGoFields() {
        UUID me = UUID.randomUUID();
        UUID need = UUID.randomUUID();
        inbox.emit(me, NotificationType.CANDIDATE_APPLIED, "applied:a:b", "Nouveau candidat", "m",
                InAppNotificationService.params("name", "Paul \"Le\" Biya", "price", "2500"), InAppEvents.LINK_SHIPMENT, need).block();
        assertThat(kernel.of(me)).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(NotificationType.CANDIDATE_APPLIED);
            assertThat(n.linkKind()).isEqualTo(InAppEvents.LINK_SHIPMENT);
            assertThat(n.linkId()).isEqualTo(need);
            assertThat(n.params()).isEqualTo("{\"name\":\"Paul \\\"Le\\\" Biya\",\"price\":\"2500\"}");
        });
    }

    // ── P1 events ────────────────────────────────────────────────────────

    @Nested
    class Events {
        final IDeliveryNeedRepository needs = mock(IDeliveryNeedRepository.class);
        final IAnnouncementRepository announcements = mock(IAnnouncementRepository.class);
        final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
        final GofpRelayPointRepository relays = mock(GofpRelayPointRepository.class);
        final GofpUserRepository users = mock(GofpUserRepository.class);
        final AddressUseCase addresses = mock(AddressUseCase.class);
        InAppEvents events;

        final UUID sender = UUID.randomUUID();
        final UUID courierUser = UUID.randomUUID();
        final UUID relayUser = UUID.randomUUID();
        final UUID stranger = UUID.randomUUID();
        final UUID needId = UUID.randomUUID();
        final UUID announcementId = UUID.randomUUID();
        final GofpFreelancer courier = GofpFreelancer.builder().id(UUID.randomUUID())
                .coreFreelancerId(UUID.randomUUID()).coreUserId(courierUser).build();
        final GofpFreelancer relayOwner = GofpFreelancer.builder().id(UUID.randomUUID())
                .coreFreelancerId(UUID.randomUUID()).coreUserId(relayUser).build();
        final GofpRelayPoint relay = GofpRelayPoint.builder().id(UUID.randomUUID()).coreRelayPointId(UUID.randomUUID())
                .coreFreelancerId(relayOwner.getCoreFreelancerId()).name("Boutique Awa").build();
        DeliveryNeed need;

        @BeforeEach
        void setUp() {
            events = new InAppEvents(inbox, needs, announcements, freelancers, relays, users, addresses);
            need = DeliveryNeed.builder().id(needId).userId(sender).targetRelayPointId(relay.getId()).build();
            lenient().when(needs.findById(needId)).thenReturn(Mono.just(need));
            lenient().when(needs.findById(any(UUID.class))).thenAnswer(i ->
                    needId.equals(i.getArgument(0)) ? Mono.just(need) : Mono.empty());
            Announcement a = new Announcement();
            a.setId(announcementId);
            a.setDeliveryNeedId(needId);
            lenient().when(announcements.findById(announcementId)).thenReturn(Mono.just(a));
            lenient().when(freelancers.findById(any(UUID.class))).thenAnswer(i -> Mono.justOrEmpty(
                    courier.getId().equals(i.getArgument(0)) ? courier : null));
            lenient().when(freelancers.findByCoreFreelancerId(any(UUID.class))).thenAnswer(i -> {
                UUID id = i.getArgument(0);
                if (courier.getCoreFreelancerId().equals(id)) return Mono.just(courier);
                if (relayOwner.getCoreFreelancerId().equals(id)) return Mono.just(relayOwner);
                return Mono.empty();
            });
            lenient().when(relays.findById(any(UUID.class))).thenAnswer(i -> Mono.justOrEmpty(
                    relay.getId().equals(i.getArgument(0)) ? relay : null));
            lenient().when(relays.findByCoreRelayPointId(any(UUID.class))).thenAnswer(i -> Mono.justOrEmpty(
                    relay.getCoreRelayPointId().equals(i.getArgument(0)) ? relay : null));
            GofpUser u = GofpUser.builder().coreUserId(courierUser).firstName("Paul").lastName("Biya").build();
            lenient().when(users.findByCoreUserId(courierUser)).thenReturn(Mono.just(u));
            lenient().when(users.findByCoreUserId(any(UUID.class))).thenAnswer(i ->
                    courierUser.equals(i.getArgument(0)) ? Mono.just(u) : Mono.empty());
            lenient().when(addresses.getAddressById(any())).thenReturn(Mono.empty());
        }

        private void twice(Mono<Void> event) {
            event.block();
            event.block();
        }

        private List<NotificationType> typesOf(UUID user) {
            return kernel.of(user).stream().map(InAppNotification::type).toList();
        }

        private void nobodyElse(UUID... recipients) {
            List<UUID> allowed = List.of(recipients);
            assertThat(kernel.rows).allMatch(n -> allowed.contains(n.recipientUserId()));
            assertThat(kernel.of(stranger)).isEmpty();
        }

        @Test
        void candidateApplied_toSenderOnce() {
            twice(events.candidateApplied(announcementId, courier.getCoreFreelancerId(), new BigDecimal("2500")));
            assertThat(kernel.of(sender)).singleElement().satisfies(n -> {
                assertThat(n.type()).isEqualTo(NotificationType.CANDIDATE_APPLIED);
                assertThat(n.message()).isEqualTo("Nouveau candidat : Paul Biya, 2500 XAF");
                assertThat(n.linkKind()).isEqualTo(InAppEvents.LINK_SHIPMENT);
                assertThat(n.linkId()).isEqualTo(needId);
            });
            nobodyElse(sender);
        }

        @Test
        void courseOffered_toInvitedCourierOnce() {
            NeedInvitation inv = NeedInvitation.builder().id(UUID.randomUUID()).deliveryNeedId(needId)
                    .freelancerId(courier.getId()).status(NeedInvitation.OPEN).estimatedPrice(3000.0).build();
            twice(events.courseOffered(inv));
            assertThat(kernel.of(courierUser)).singleElement().satisfies(n -> {
                assertThat(n.type()).isEqualTo(NotificationType.COURSE_OFFERED);
                assertThat(n.message()).startsWith("Course proposée pour toi : ").endsWith(", 3000 XAF");
            });
            nobodyElse(courierUser);
        }

        @Test
        void courierAssigned_toSenderAndChosenCourierOnce() {
            twice(events.courierAssigned(need, courier));
            assertThat(typesOf(sender)).containsExactly(NotificationType.COURSE_ACCEPTED);
            assertThat(kernel.of(sender).get(0).message()).isEqualTo("Paul Biya a accepté ta course");
            assertThat(typesOf(courierUser)).containsExactly(NotificationType.COURIER_SELECTED);
            assertThat(kernel.of(courierUser).get(0).message())
                    .isEqualTo("Tu as été choisi pour la course " + InAppEvents.ref(needId));
            nobodyElse(sender, courierUser);
        }

        @Test
        void invitationDeclinedOrExpired_toSenderOnce() {
            NeedInvitation declined = NeedInvitation.builder().id(UUID.randomUUID()).deliveryNeedId(needId)
                    .freelancerId(courier.getId()).status(NeedInvitation.DECLINED).build();
            NeedInvitation expired = NeedInvitation.builder().id(UUID.randomUUID()).deliveryNeedId(needId)
                    .freelancerId(courier.getId()).status(NeedInvitation.EXPIRED).build();
            twice(events.invitationClosed(declined));
            twice(events.invitationClosed(expired));
            assertThat(typesOf(sender)).containsExactlyInAnyOrder(
                    NotificationType.INVITATION_DECLINED, NotificationType.INVITATION_EXPIRED);
            assertThat(kernel.of(sender)).extracting(InAppNotification::message).containsExactlyInAnyOrder(
                    "Paul Biya a décliné : choisis un autre livreur",
                    "L'invitation a expiré : choisis un autre livreur");
            nobodyElse(sender);
        }

        @Test
        void acceptedInvitationTellsNobodyAsClosed() {
            events.invitationClosed(NeedInvitation.builder().id(UUID.randomUUID()).deliveryNeedId(needId)
                    .freelancerId(courier.getId()).status(NeedInvitation.ACCEPTED).build()).block();
            assertThat(kernel.rows).isEmpty();
        }

        @Test
        void relayChosen_toRelayOwnerOnce() {
            twice(events.relayChosen(need));
            assertThat(kernel.of(relayUser)).singleElement().satisfies(n -> {
                assertThat(n.type()).isEqualTo(NotificationType.RELAY_PARCEL_INCOMING);
                assertThat(n.message()).isEqualTo("Un colis va arriver : réf " + InAppEvents.ref(needId));
            });
            nobodyElse(relayUser);
        }

        @Test
        void pickedUp_toSenderOnce() {
            twice(events.pickedUp(needId));
            assertThat(kernel.of(sender)).singleElement()
                    .satisfies(n -> assertThat(n.message()).isEqualTo("Ton colis est en route"));
            nobodyElse(sender);
        }

        @Test
        void depositedAtRelay_toSenderAndRelayOnce() {
            twice(events.depositedAtRelay(needId, relay.getCoreRelayPointId()));
            assertThat(kernel.of(sender)).singleElement()
                    .satisfies(n -> assertThat(n.message()).isEqualTo("Ton colis est au relais Boutique Awa"));
            assertThat(kernel.of(relayUser)).singleElement()
                    .satisfies(n -> assertThat(n.message()).isEqualTo("Colis reçu, en attente de retrait"));
            nobodyElse(sender, relayUser);
        }

        @Test
        void handedOver_toSenderCourierAndRelayOnce() {
            twice(events.handedOver(needId, courier.getId(), relay.getCoreRelayPointId()));
            assertThat(typesOf(sender)).containsExactly(NotificationType.PARCEL_HANDED_OVER);
            assertThat(typesOf(courierUser)).containsExactly(NotificationType.PARCEL_HANDED_OVER);
            assertThat(typesOf(relayUser)).containsExactly(NotificationType.PARCEL_HANDED_OVER);
            nobodyElse(sender, courierUser, relayUser);
        }

        @Test
        void directHandOverDoesNotTellAnyRelay() {
            events.handedOver(needId, courier.getId(), null).block();
            assertThat(kernel.of(relayUser)).isEmpty();
            nobodyElse(sender, courierUser);
        }

        @Test
        void aFailingInboxNeverFailsTheAction() {
            IInAppInboxPort broken = mock(IInAppInboxPort.class);
            lenient().when(broken.send(any(), any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("kernel down")));
            InAppEvents withBrokenInbox = new InAppEvents(new InAppNotificationService(broken, provider(processed)),
                    needs, announcements, freelancers, relays, users, addresses);
            StepVerifier.create(withBrokenInbox.pickedUp(needId)).verifyComplete();
            assertThat(kernel.rows).isEmpty();
            assertThat(processed.ids).as("a failed send is not recorded: it will be retried by a replay").isEmpty();
        }
    }
}
