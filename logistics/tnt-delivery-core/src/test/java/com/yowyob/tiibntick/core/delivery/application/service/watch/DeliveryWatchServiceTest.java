package com.yowyob.tiibntick.core.delivery.application.service.watch;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryTransitionExecutor;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort.Alerts;
import com.yowyob.tiibntick.core.delivery.config.DeliveryAlertProperties;
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryAlertType;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Lot L3, delivery alerts (spec §3.5, decision D.2, thresholds QO-18): detections are {@code DeliveryAlertRaised} /
 * {@code DeliveryAlertCleared} events without transition, raised once while active; lateness also sets {@code late}.
 */
class DeliveryWatchServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();

    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final DeliveryTransitionExecutor executor = mock(DeliveryTransitionExecutor.class);
    private final DeliveryAlertProperties properties = new DeliveryAlertProperties();
    private final DeliveryThresholdsPort thresholds = mock(DeliveryThresholdsPort.class);
    private DeliveryWatchService watch;

    @BeforeEach
    void setUp() {
        when(executor.saveAndPublish(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(thresholds.forAgency(any())).thenReturn(Mono.just(new DeliveryThresholdsPort.Thresholds(3, 3, 1)));
        watch = new DeliveryWatchService(repository, executor, properties, thresholds);
    }

    @Test
    @DisplayName("PICKUP_LATE after pickup window end + 15 min, once; late flag set")
    void pickupLate() {
        Delivery d = assigned();
        Instant windowEnd = Instant.now().minus(Duration.ofHours(1));
        d.recordAgreedTerms(null, null, windowEnd.minus(Duration.ofHours(1)), windowEnd, null, null);

        assertThat(watch.evaluate(d, Alerts.DEFAULT, windowEnd.plus(Duration.ofMinutes(10)))).as("within grace").isFalse();
        assertThat(watch.evaluate(d, Alerts.DEFAULT, windowEnd.plus(Duration.ofMinutes(16)))).isTrue();
        assertThat(watch.evaluate(d, Alerts.DEFAULT, windowEnd.plus(Duration.ofMinutes(30)))).as("raised once").isFalse();
        assertThat(d.hasActiveAlert(DeliveryAlertType.PICKUP_LATE)).isTrue();
        assertThat(d.isLate()).isTrue();
        assertThat(alerts(d)).singleElement().satisfies(e -> {
            assertThat(e.eventType()).isEqualTo("DeliveryAlertRaised");
            assertThat(e.isTransition()).isFalse();
            assertThat(e.details()).containsEntry("alertType", "PICKUP_LATE");
        });
    }

    @Test
    @DisplayName("DELIVERY_LATE without window: creation + urgency delay (STANDARD 48 h, D-132); ETA_EXCEEDED at ETA + 30 min")
    void deliveryLateAndEtaExceeded() {
        Delivery d = assigned();
        d.confirmPickup();
        Instant eta = Instant.now().plus(Duration.ofMinutes(20));
        d.startTransit(EtaEstimate.of(eta, 3.0, 20));
        Instant created = d.getCreatedAt();

        assertThat(watch.evaluate(d, Alerts.DEFAULT, eta.plus(Duration.ofMinutes(31)))).isTrue();
        assertThat(d.hasActiveAlert(DeliveryAlertType.ETA_EXCEEDED)).isTrue();
        assertThat(d.hasActiveAlert(DeliveryAlertType.DELIVERY_LATE)).isFalse();

        assertThat(watch.evaluate(d, Alerts.DEFAULT, created.plus(Duration.ofHours(49)))).isTrue();
        assertThat(d.hasActiveAlert(DeliveryAlertType.DELIVERY_LATE)).isTrue();
        assertThat(d.getActiveAlerts()).isEqualTo("ETA_EXCEEDED,DELIVERY_LATE");
    }

    @Test
    @DisplayName("GPS_SIGNAL_LOST after 10 min without position, cleared by the next position")
    void gpsLostThenBack() {
        Delivery d = assigned();
        d.confirmPickup();
        Instant last = Instant.now().minus(Duration.ofMinutes(15));
        d.recordPosition(last);
        assertThat(watch.evaluate(d, Alerts.DEFAULT, Instant.now())).isTrue();
        assertThat(d.hasActiveAlert(DeliveryAlertType.GPS_SIGNAL_LOST)).isTrue();

        when(repository.findById(TENANT, d.getId())).thenReturn(Mono.just(d));
        watch.onPosition(TENANT, d.getId(), Instant.now(), false, false).block();
        assertThat(d.hasActiveAlert(DeliveryAlertType.GPS_SIGNAL_LOST)).isFalse();
        assertThat(d.getAggregateEvents()).extracting(AggregateEvent::eventType).contains("DeliveryAlertCleared");
        verify(executor).saveAndPublish(d);
    }

    @Test
    @DisplayName("positions without alert change are written at most once a minute")
    void positionWritesAreThrottled() {
        Delivery d = assigned();
        d.confirmPickup();
        when(repository.findById(TENANT, d.getId())).thenReturn(Mono.just(d));
        Instant t0 = Instant.now();
        watch.onPosition(TENANT, d.getId(), t0, false, false).block();
        watch.onPosition(TENANT, d.getId(), t0.plusSeconds(5), false, false).block();
        watch.onPosition(TENANT, d.getId(), t0.plusSeconds(10), true, false).block();   // alert → written
        watch.onPosition(TENANT, d.getId(), t0.plusSeconds(70), true, false).block();   // > 60 s → written
        verify(executor, times(3)).saveAndPublish(d);
        assertThat(d.hasActiveAlert(DeliveryAlertType.TRAJECTORY_DEVIATION)).isTrue();
    }

    @Test
    @DisplayName("a DANGER zone raises GEOFENCE_EXIT once; unknown and terminal deliveries are ignored")
    void geofenceAndIgnoredDeliveries() {
        Delivery d = assigned();
        when(repository.findById(TENANT, d.getId())).thenReturn(Mono.just(d));
        watch.onGeofence(TENANT, d.getId(), "DANGER_ZONE").block();
        watch.onGeofence(TENANT, d.getId(), "DANGER_ZONE").block();
        assertThat(alerts(d)).hasSize(1);
        verify(executor, times(1)).saveAndPublish(d);

        UUID unknown = UUID.randomUUID();
        when(repository.findById(TENANT, unknown)).thenReturn(Mono.empty());
        watch.onGeofence(TENANT, unknown, "DANGER_ZONE").block();

        Delivery cancelled = assigned();
        cancelled.cancel("x");
        assertThat(cancelled.raiseAlert(DeliveryAlertType.GEOFENCE_EXIT, null, null, Instant.now())).isFalse();
    }

    @Test
    @DisplayName("scan saves only the deliveries that got a new alert")
    void scanSavesOnlyChanged() {
        Delivery late = assigned();
        Instant windowEnd = Instant.now().minus(Duration.ofHours(2));
        late.recordAgreedTerms(null, null, windowEnd.minus(Duration.ofHours(1)), windowEnd, null, null);
        Delivery onTime = assigned();
        when(repository.findWatchCandidates(anyInt())).thenReturn(Flux.just(late, onTime));

        assertThat(watch.scan(Instant.now()).block()).isEqualTo(1L);
        verify(executor).saveAndPublish(late);
        verify(executor, never()).saveAndPublish(onTime);
    }

    @Test
    @DisplayName("D-132: the delay without window follows the urgency (EXPRESS 24 h, SAME_DAY 8 h); window end + grace")
    void deliveryDeadlineByUrgencyAndWindow() {
        Delivery d = assigned();
        Instant created = d.getCreatedAt();
        assertThat(DeliveryWatchService.deliveryDeadline(d, Alerts.DEFAULT)).isEqualTo(created.plus(Duration.ofHours(48)));
        assertThat(Alerts.DEFAULT.deliveryLateFallback(DeliveryUrgency.EXPRESS)).isEqualTo(Duration.ofHours(24));
        assertThat(Alerts.DEFAULT.deliveryLateFallback(DeliveryUrgency.SAME_DAY)).isEqualTo(Duration.ofHours(8));
        assertThat(Alerts.DEFAULT.deliveryLateFallback(DeliveryUrgency.SCHEDULED)).isEqualTo(Duration.ofHours(48));
        Instant windowEnd = Instant.now().plus(Duration.ofHours(3));
        d.recordAgreedTerms(null, null, null, null, windowEnd.minus(Duration.ofHours(1)), windowEnd);
        Alerts graced = new Alerts(Duration.ofMinutes(15), Duration.ofMinutes(45), Duration.ofHours(48),
                Duration.ofHours(24), Duration.ofHours(8), Duration.ofMinutes(30), Duration.ofMinutes(10),
                Duration.ofMinutes(20));
        assertThat(DeliveryWatchService.deliveryDeadline(d, graced)).isEqualTo(windowEnd.plus(Duration.ofMinutes(45)));
    }

    @Test
    @DisplayName("D-133: the scan applies the thresholds of the delivery's organisation (agency override)")
    void scanUsesTheOrganisationThresholds() {
        Delivery d = assigned();
        Instant windowEnd = Instant.now().minus(Duration.ofMinutes(20));
        d.recordAgreedTerms(null, null, windowEnd.minus(Duration.ofHours(1)), windowEnd, null, null);
        when(repository.findWatchCandidates(anyInt())).thenReturn(Flux.just(d));
        Alerts patient = new Alerts(Duration.ofMinutes(60), Duration.ZERO, Duration.ofHours(48), Duration.ofHours(24),
                Duration.ofHours(8), Duration.ofMinutes(30), Duration.ofMinutes(10), Duration.ofMinutes(20));
        when(thresholds.forAgency(any())).thenReturn(Mono.just(new DeliveryThresholdsPort.Thresholds(3, 3, 1, patient)));
        assertThat(watch.scan(Instant.now()).block()).as("20 min late, the organisation grants 60").isZero();

        when(thresholds.forAgency(any())).thenReturn(Mono.error(new IllegalStateException("admin down")));
        assertThat(watch.scan(Instant.now()).block()).as("unreadable -> platform 15 min").isEqualTo(1L);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private static List<AggregateEvent> alerts(Delivery d) {
        return d.getAggregateEvents().stream().filter(e -> e.eventType().startsWith("DeliveryAlert")).toList();
    }

    private static Delivery assigned() {
        Parcel parcel = Parcel.create(new PackageSpecification(1, 10, 10, 10, false, false, "Box"), TENANT, SENDER,
                TntEventActor.of(TntEventActor.Type.SENDER, SENDER));
        Delivery d = Delivery.create(TENANT, null, SENDER, parcel,
                new DeliveryAddress(null, "a", "Mokolo", "Yaoundé", "CM", new GeoCoordinates(3.87, 11.51)),
                new DeliveryAddress(null, "b", "Bastos", "Yaoundé", "CM", new GeoCoordinates(3.88, 11.52)),
                new RecipientInfo("Jean", "+237690000001", null), DeliveryUrgency.STANDARD, null, null);
        d.assignDeliveryPerson(COURIER, null, 3.0, null);
        return d;
    }
}
