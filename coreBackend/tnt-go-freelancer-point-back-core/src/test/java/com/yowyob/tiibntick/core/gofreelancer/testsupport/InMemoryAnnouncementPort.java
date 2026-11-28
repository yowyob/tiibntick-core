package com.yowyob.tiibntick.core.gofreelancer.testsupport;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.query.NearbyCriteria;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementExtras;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.RespondToAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.UpdateAnnouncementPortCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory stand-in for delivery-core behind {@link IDeliveryAnnouncementPort}: the announcement is
 * the only state (no gofp mirror), with the core's lifecycle rules (open → ASSIGNED on selection,
 * which creates the delivery; CANCELLED on cancel; terms frozen once assigned). Ids handed out can
 * be fixed in advance with {@link #nextAnnouncementId}, {@link #nextResponseId}, {@link #nextDeliveryId}.
 * Wrap it in a Mockito {@code spy} to verify calls.
 */
public class InMemoryAnnouncementPort implements IDeliveryAnnouncementPort {

    private final Map<UUID, AnnouncementSnapshot> store = new LinkedHashMap<>();
    private final Deque<UUID> nextAnnouncementIds = new ArrayDeque<>();
    private final Deque<UUID> nextResponseIds = new ArrayDeque<>();
    private final Deque<UUID> nextDeliveryIds = new ArrayDeque<>();

    public InMemoryAnnouncementPort nextAnnouncementId(UUID id) { nextAnnouncementIds.add(id); return this; }
    public InMemoryAnnouncementPort nextResponseId(UUID id) { nextResponseIds.add(id); return this; }
    public InMemoryAnnouncementPort nextDeliveryId(UUID id) { nextDeliveryIds.add(id); return this; }

    public AnnouncementSnapshot get(UUID id) { return store.get(id); }
    public Collection<AnnouncementSnapshot> all() { return store.values(); }
    public void put(AnnouncementSnapshot s) { store.put(s.id(), s); }

    /** Adds a response directly (as if a courier had applied). */
    public void addResponse(UUID announcementId, AnnouncementResponseSnapshot r) {
        AnnouncementSnapshot a = store.get(announcementId);
        List<AnnouncementResponseSnapshot> rs = new ArrayList<>(a.responses());
        rs.add(r);
        store.put(a.id(), copy(a, a.status() == AnnouncementStatus.PUBLISHED ? AnnouncementStatus.IN_NEGOTIATION : a.status(),
                rs, a.selectedResponseId(), a.createdDeliveryId(), a.offeredAmount(), a.currency(), a.extras()));
    }

    private static UUID take(Deque<UUID> q) { return q.isEmpty() ? UUID.randomUUID() : q.poll(); }

    @Override
    public Mono<AnnouncementSnapshot> publish(PublishAnnouncementPortCommand c) {
        return Mono.fromSupplier(() -> {
            AnnouncementExtras x = c.extras();
            if (x.originRefId() != null && store.values().stream().anyMatch(a ->
                    Objects.equals(a.extras().originRefType(), x.originRefType())
                            && Objects.equals(a.extras().originRefId(), x.originRefId()))) {
                // As the core does through uq_tnt_ann_origin_ref (translated by Spring R2DBC).
                throw new org.springframework.dao.DuplicateKeyException("duplicate origin " + x.originRefId());
            }
            Instant now = Instant.now();
            AnnouncementExtras stored = new AnnouncementExtras(x.senderName(), x.senderPhone(), x.senderEmail(),
                    x.recipientEmail(), x.urgency() == null || "NORMAL".equalsIgnoreCase(x.urgency()) ? "STANDARD"
                            : x.urgency().toUpperCase(), x.requiredVehicleType(), x.paymentMethod(),
                    x.transportMethod(), x.distanceKm(), x.estimatedDurationMin(), x.destinationHubId(),
                    x.handoverSignatureRef(), x.serviceFee(), x.originRefType(), x.originRefId());
            AnnouncementSnapshot s = new AnnouncementSnapshot(take(nextAnnouncementIds), c.tenantId(), c.clientId(),
                    c.title(), c.description(), AnnouncementStatus.PUBLISHED, now, now, c.offeredAmount(),
                    c.currency(), c.pricingMode(), c.pickupAddress(), c.deliveryAddress(), 0.0,
                    c.packageWeightKg(), c.packageWidthCm(), c.packageHeightCm(), c.packageLengthCm(),
                    c.fragile(), c.perishable(), c.packageDescription(), null, c.recipientName(),
                    c.recipientPhone(), null, null, null, List.of(), stored);
            store.put(s.id(), s);
            return s;
        });
    }

    @Override
    public Mono<Void> cancel(UUID tenantId, UUID announcementId, UUID clientId) {
        return Mono.fromRunnable(() -> {
            AnnouncementSnapshot a = store.get(announcementId);
            if (a != null) {
                store.put(a.id(), copy(a, AnnouncementStatus.CANCELLED, a.responses(), a.selectedResponseId(),
                        a.createdDeliveryId(), a.offeredAmount(), a.currency(), a.extras()));
            }
        });
    }

    @Override
    public Mono<AnnouncementSnapshot> respond(RespondToAnnouncementPortCommand c) {
        return Mono.fromSupplier(() -> {
            addResponse(c.announcementId(), new AnnouncementResponseSnapshot(take(nextResponseIds), c.freelancerId(),
                    c.proposedPrice(), c.proposedCurrency(), "SENT", Instant.now()));
            return store.get(c.announcementId());
        });
    }

    @Override
    public Mono<AnnouncementSnapshot> selectResponse(UUID tenantId, UUID announcementId, UUID clientId, UUID responseId) {
        return Mono.fromSupplier(() -> {
            AnnouncementSnapshot a = store.get(announcementId);
            if (a.status() != AnnouncementStatus.PUBLISHED && a.status() != AnnouncementStatus.IN_NEGOTIATION) {
                throw new IllegalStateException("announcement not open: " + a.status());
            }
            AnnouncementSnapshot s = copy(a, AnnouncementStatus.ASSIGNED, a.responses(), responseId,
                    take(nextDeliveryIds), a.offeredAmount(), a.currency(), a.extras());
            store.put(s.id(), s);
            return s;
        });
    }

    @Override
    public Mono<BigDecimal> resolveEscrowAmount(UUID tenantId, UUID announcementId, UUID responseId) {
        return Mono.justOrEmpty(store.get(announcementId)).mapNotNull(AnnouncementSnapshot::offeredAmount);
    }

    @Override
    public Flux<AnnouncementSnapshot> findOpenAnnouncements(UUID tenantId) {
        return Flux.fromIterable(new ArrayList<>(store.values())).filter(a ->
                a.status() == AnnouncementStatus.PUBLISHED || a.status() == AnnouncementStatus.IN_NEGOTIATION);
    }

    @Override
    public Flux<AnnouncementSnapshot> findOpenAnnouncementsNear(UUID tenantId, NearbyCriteria criteria) {
        return findOpenAnnouncements(tenantId);
    }

    @Override
    public Mono<AnnouncementSnapshot> findById(UUID tenantId, UUID announcementId) {
        return Mono.fromSupplier(() -> store.get(announcementId));
    }

    @Override
    public Flux<AnnouncementSnapshot> findByClient(UUID tenantId, UUID clientId) {
        return Flux.fromIterable(new ArrayList<>(store.values())).filter(a -> clientId.equals(a.clientId()));
    }

    @Override
    public Mono<Boolean> hasResponse(UUID announcementId, UUID freelancerId) {
        return Mono.fromSupplier(() -> store.get(announcementId) != null && store.get(announcementId).responses()
                .stream().anyMatch(r -> freelancerId.equals(r.deliveryPersonId())));
    }

    @Override
    public Mono<AnnouncementSnapshot> update(UpdateAnnouncementPortCommand c) {
        return Mono.fromSupplier(() -> {
            AnnouncementSnapshot a = store.get(c.announcementId());
            boolean open = a.status() == AnnouncementStatus.DRAFT || a.status() == AnnouncementStatus.PUBLISHED
                    || a.status() == AnnouncementStatus.IN_NEGOTIATION;
            if ((c.offeredAmount() != null || c.currency() != null) && !open) {
                throw new IllegalStateException("terms frozen: " + a.status());
            }
            AnnouncementExtras o = a.extras();
            AnnouncementExtras p = c.extras();
            AnnouncementExtras merged = new AnnouncementExtras(
                    or(p.senderName(), o.senderName()), or(p.senderPhone(), o.senderPhone()),
                    or(p.senderEmail(), o.senderEmail()), or(p.recipientEmail(), o.recipientEmail()), o.urgency(),
                    or(p.requiredVehicleType(), o.requiredVehicleType()), or(p.paymentMethod(), o.paymentMethod()),
                    or(p.transportMethod(), o.transportMethod()), or(p.distanceKm(), o.distanceKm()),
                    or(p.estimatedDurationMin(), o.estimatedDurationMin()), or(p.destinationHubId(), o.destinationHubId()),
                    or(p.handoverSignatureRef(), o.handoverSignatureRef()), or(p.serviceFee(), o.serviceFee()),
                    o.originRefType() != null ? o.originRefType() : p.originRefType(),
                    o.originRefId() != null ? o.originRefId() : p.originRefId());
            AnnouncementSnapshot s = copy(a, a.status(), a.responses(), a.selectedResponseId(), a.createdDeliveryId(),
                    or(c.offeredAmount(), a.offeredAmount()), or(c.currency(), a.currency()), merged);
            store.put(s.id(), s);
            return s;
        });
    }

    private static <T> T or(T a, T b) { return a != null ? a : b; }

    @Override
    public Mono<AnnouncementSnapshot> findByOrigin(UUID tenantId, String originType, UUID originId) {
        return findByOrigins(tenantId, originType, List.of(originId)).next();
    }

    @Override
    public Flux<AnnouncementSnapshot> findByOrigins(UUID tenantId, String originType, Collection<UUID> originIds) {
        return Flux.fromIterable(new ArrayList<>(store.values())).filter(a ->
                originType.equals(a.extras().originRefType()) && originIds.contains(a.extras().originRefId()));
    }

    @Override
    public Flux<AnnouncementSnapshot> findRespondedBy(UUID tenantId, Collection<UUID> deliveryPersonIds) {
        return Flux.fromIterable(new ArrayList<>(store.values())).filter(a ->
                a.responses().stream().anyMatch(r -> deliveryPersonIds.contains(r.deliveryPersonId())));
    }

    private static AnnouncementSnapshot copy(AnnouncementSnapshot a, AnnouncementStatus status,
                                             List<AnnouncementResponseSnapshot> responses, UUID selected,
                                             UUID delivery, BigDecimal amount, String currency,
                                             AnnouncementExtras extras) {
        return new AnnouncementSnapshot(a.id(), a.tenantId(), a.clientId(), a.title(), a.description(), status,
                a.createdAt(), Instant.now(), amount, currency, a.pricingMode(), a.pickupAddress(),
                a.deliveryAddress(), a.packetVolumetricWeightDm3(), a.packetWeightKg(), a.packetWidthCm(),
                a.packetHeightCm(), a.packetLengthCm(), a.packetFragile(), a.packetPerishable(),
                a.packetDescription(), a.packetPhotoUrl(), a.recipientName(), a.recipientPhone(), selected,
                delivery, a.trackingCode(), List.copyOf(responses), extras);
    }
}
