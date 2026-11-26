package com.yowyob.tiibntick.core.gofreelancer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.NotificationRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Notification;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Lot C-36 — in-app inbox of the three roles: the P1 events, "mine only", retention. */
class InAppNotificationsTest {

    /** In-memory notifications table, same semantics as the SQL of the R2DBC repository. */
    static final class FakeNotifications implements NotificationRepository {
        final List<Notification> rows = new ArrayList<>();

        @Override public Mono<Notification> save(Notification n) {
            if (n.getId() == null) {
                if (n.getEventKey() != null && rows.stream().anyMatch(r -> n.getEventKey().equals(r.getEventKey()))) {
                    return Mono.error(new org.springframework.dao.DuplicateKeyException(n.getEventKey()));
                }
                n.setId(UUID.randomUUID());
                rows.add(n);
            }
            return Mono.just(n);
        }
        @Override public Mono<Notification> findById(UUID id) {
            return Mono.justOrEmpty(rows.stream().filter(r -> r.getId().equals(id)).findFirst());
        }
        @Override public Mono<Boolean> existsByEventKey(String key) {
            return Mono.just(rows.stream().anyMatch(r -> key.equals(r.getEventKey())));
        }
        @Override public Flux<Notification> findInbox(UUID personId, int limit, long offset) {
            return Flux.fromStream(rows.stream().filter(r -> personId.equals(r.getPersonId()))
                    .sorted(Comparator.comparing((Notification r) -> r.getReadAt() != null)
                            .thenComparing(Notification::getCreatedAt, Comparator.reverseOrder()))
                    .skip(offset).limit(limit));
        }
        @Override public Mono<Long> countUnread(UUID personId) {
            return Mono.just(rows.stream().filter(r -> personId.equals(r.getPersonId()) && r.getReadAt() == null).count());
        }
        @Override public Mono<Long> markAllRead(UUID personId, Instant at) {
            long n = rows.stream().filter(r -> personId.equals(r.getPersonId()) && r.getReadAt() == null)
                    .peek(r -> r.setReadAt(at)).count();
            return Mono.just(n);
        }
        @Override public Mono<Void> deleteById(UUID id) {
            rows.removeIf(r -> r.getId().equals(id));
            return Mono.empty();
        }
        @Override public Mono<Long> deleteExpired(Instant readBefore, Instant createdBefore) {
            int before = rows.size();
            rows.removeIf(r -> (r.getReadAt() != null && r.getReadAt().isBefore(readBefore))
                    || (r.getCreatedAt() != null && r.getCreatedAt().isBefore(createdBefore)));
            return Mono.just((long) (before - rows.size()));
        }

        List<Notification> of(UUID user) {
            return rows.stream().filter(r -> user.equals(r.getPersonId())).toList();
        }
    }

    static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    FakeNotifications table;
    InAppNotificationService inbox;

    @BeforeEach
    void setUpInbox() {
        table = new FakeNotifications();
        inbox = new InAppNotificationService(table, 30, 90);
        inbox.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Notification stored(UUID owner, Duration age, Duration readAgo) {
        Notification n = new Notification();
        n.setPersonId(owner);
        n.setNotificationType(NotificationType.PARCEL_PICKED_UP);
        n.setTitle("t");
        n.setMessage("m");
        n.setCreatedAt(NOW.minus(age));
        n.setReadAt(readAgo == null ? null : NOW.minus(readAgo));
        n.setEventKey(UUID.randomUUID().toString());
        return table.save(n).block();
    }

    // ── "mine only" ──────────────────────────────────────────────────────

    @Nested
    class MineOnly {
        final UUID me = UUID.randomUUID();
        final UUID other = UUID.randomUUID();

        @Test
        void listsOnlyMyNotificationsUnreadFirst() {
            Notification read = stored(me, Duration.ofHours(1), Duration.ofMinutes(5));
            Notification unreadOld = stored(me, Duration.ofDays(2), null);
            stored(other, Duration.ofMinutes(1), null);

            InAppNotificationService.Inbox box = inbox.mine(me, 0, 30).block();
            assertThat(box.items()).extracting(Notification::getId).containsExactly(unreadOld.getId(), read.getId());
            assertThat(box.unread()).isEqualTo(1);
        }

        @Test
        void someoneElsesNotificationCannotBeReadOrDeleted() {
            Notification theirs = stored(other, Duration.ofMinutes(1), null);

            StepVerifier.create(inbox.markRead(theirs.getId(), me))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 404)
                    .verify();
            StepVerifier.create(inbox.delete(theirs.getId(), me))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 404)
                    .verify();
            assertThat(theirs.getReadAt()).isNull();
            assertThat(table.of(other)).hasSize(1);
        }

        @Test
        void markReadDeleteAndReadAllActOnMineOnly() {
            Notification a = stored(me, Duration.ofMinutes(3), null);
            Notification b = stored(me, Duration.ofMinutes(2), null);
            Notification theirs = stored(other, Duration.ofMinutes(1), null);

            assertThat(inbox.markRead(a.getId(), me).block().getReadAt()).isEqualTo(NOW);
            inbox.delete(b.getId(), me).block();
            stored(me, Duration.ofMinutes(1), null);
            assertThat(inbox.markAllRead(me).block()).isEqualTo(1);

            assertThat(table.of(me)).hasSize(2).allMatch(n -> n.getReadAt() != null);
            assertThat(theirs.getReadAt()).isNull();
        }
    }

    // ── retention ────────────────────────────────────────────────────────

    @Test
    void retentionDeletesReadAfter30DaysAndEverythingAfter90() {
        UUID me = UUID.randomUUID();
        Notification read31 = stored(me, Duration.ofDays(40), Duration.ofDays(31));
        Notification unread31 = stored(me, Duration.ofDays(31), null);
        Notification read10 = stored(me, Duration.ofDays(20), Duration.ofDays(10));
        Notification unread91 = stored(me, Duration.ofDays(91), null);

        assertThat(inbox.purge().block()).isEqualTo(2);
        assertThat(table.rows).extracting(Notification::getId)
                .containsExactlyInAnyOrder(unread31.getId(), read10.getId())
                .doesNotContain(read31.getId(), unread91.getId());
        // idempotent
        assertThat(inbox.purge().block()).isZero();
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
            return table.of(user).stream().map(Notification::getNotificationType).toList();
        }

        private void nobodyElse(UUID... recipients) {
            List<UUID> allowed = List.of(recipients);
            assertThat(table.rows).allMatch(n -> allowed.contains(n.getPersonId()));
            assertThat(table.of(stranger)).isEmpty();
        }

        @Test
        void candidateApplied_toSenderOnce() {
            twice(events.candidateApplied(announcementId, courier.getCoreFreelancerId(), new BigDecimal("2500")));
            assertThat(table.of(sender)).singleElement().satisfies(n -> {
                assertThat(n.getNotificationType()).isEqualTo(NotificationType.CANDIDATE_APPLIED);
                assertThat(n.getMessage()).isEqualTo("Nouveau candidat : Paul Biya, 2500 XAF");
                assertThat(n.getLinkKind()).isEqualTo(InAppEvents.LINK_SHIPMENT);
                assertThat(n.getLinkId()).isEqualTo(needId);
            });
            nobodyElse(sender);
        }

        @Test
        void courseOffered_toInvitedCourierOnce() {
            NeedInvitation inv = NeedInvitation.builder().id(UUID.randomUUID()).deliveryNeedId(needId)
                    .freelancerId(courier.getId()).status(NeedInvitation.OPEN).estimatedPrice(3000.0).build();
            twice(events.courseOffered(inv));
            assertThat(table.of(courierUser)).singleElement().satisfies(n -> {
                assertThat(n.getNotificationType()).isEqualTo(NotificationType.COURSE_OFFERED);
                assertThat(n.getMessage()).startsWith("Course proposée pour toi : ").endsWith(", 3000 XAF");
            });
            nobodyElse(courierUser);
        }

        @Test
        void courierAssigned_toSenderAndChosenCourierOnce() {
            twice(events.courierAssigned(need, courier));
            assertThat(typesOf(sender)).containsExactly(NotificationType.COURSE_ACCEPTED);
            assertThat(table.of(sender).get(0).getMessage()).isEqualTo("Paul Biya a accepté ta course");
            assertThat(typesOf(courierUser)).containsExactly(NotificationType.COURIER_SELECTED);
            assertThat(table.of(courierUser).get(0).getMessage())
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
            assertThat(table.of(sender)).extracting(Notification::getMessage).containsExactlyInAnyOrder(
                    "Paul Biya a décliné : choisis un autre livreur",
                    "L'invitation a expiré : choisis un autre livreur");
            nobodyElse(sender);
        }

        @Test
        void acceptedInvitationTellsNobodyAsClosed() {
            events.invitationClosed(NeedInvitation.builder().id(UUID.randomUUID()).deliveryNeedId(needId)
                    .freelancerId(courier.getId()).status(NeedInvitation.ACCEPTED).build()).block();
            assertThat(table.rows).isEmpty();
        }

        @Test
        void relayChosen_toRelayOwnerOnce() {
            twice(events.relayChosen(need));
            assertThat(table.of(relayUser)).singleElement().satisfies(n -> {
                assertThat(n.getNotificationType()).isEqualTo(NotificationType.RELAY_PARCEL_INCOMING);
                assertThat(n.getMessage()).isEqualTo("Un colis va arriver : réf " + InAppEvents.ref(needId));
            });
            nobodyElse(relayUser);
        }

        @Test
        void pickedUp_toSenderOnce() {
            twice(events.pickedUp(needId));
            assertThat(table.of(sender)).singleElement()
                    .satisfies(n -> assertThat(n.getMessage()).isEqualTo("Ton colis est en route"));
            nobodyElse(sender);
        }

        @Test
        void depositedAtRelay_toSenderAndRelayOnce() {
            twice(events.depositedAtRelay(needId, relay.getCoreRelayPointId()));
            assertThat(table.of(sender)).singleElement()
                    .satisfies(n -> assertThat(n.getMessage()).isEqualTo("Ton colis est au relais Boutique Awa"));
            assertThat(table.of(relayUser)).singleElement()
                    .satisfies(n -> assertThat(n.getMessage()).isEqualTo("Colis reçu, en attente de retrait"));
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
            assertThat(table.of(relayUser)).isEmpty();
            nobodyElse(sender, courierUser);
        }

        @Test
        void aFailingStoreNeverFailsTheAction() {
            NotificationRepository broken = mock(NotificationRepository.class);
            lenient().when(broken.existsByEventKey(any())).thenReturn(Mono.error(new IllegalStateException("db down")));
            InAppEvents withBrokenStore = new InAppEvents(new InAppNotificationService(broken, 30, 90),
                    needs, announcements, freelancers, relays, users, addresses);
            StepVerifier.create(withBrokenStore.pickedUp(needId)).verifyComplete();
            assertThat(Objects.requireNonNull(table.rows)).isEmpty();
        }
    }
}
