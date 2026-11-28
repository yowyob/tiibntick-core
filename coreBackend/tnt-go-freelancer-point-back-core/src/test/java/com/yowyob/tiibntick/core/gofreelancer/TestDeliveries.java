package com.yowyob.tiibntick.core.gofreelancer;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Test helper: a gofp delivery row no longer owns its status, courier or price (they are read from
 * {@code tnt-delivery-core}), so tests that need them set them through {@link Delivery#readThrough}, the
 * same way the repository adapter fills a row from the core.
 */
public final class TestDeliveries {

    private TestDeliveries() {
    }

    public static Delivery status(Delivery d, DeliveryStatus status) {
        return d.readThrough(status, d.getFreelancerId(), tarif(d), d.getPickupMinTime(), d.getPickupMaxTime(),
                d.getDeliveryMinTime(), d.getDeliveryMaxTime(), d.getActualPickupTime(), d.getActualDeliveryTime());
    }

    public static Delivery driver(Delivery d, UUID deliveryPersonId) {
        return d.readThrough(d.getStatus(), deliveryPersonId, tarif(d), d.getPickupMinTime(), d.getPickupMaxTime(),
                d.getDeliveryMinTime(), d.getDeliveryMaxTime(), d.getActualPickupTime(), d.getActualDeliveryTime());
    }

    public static Delivery tarif(Delivery d, Double amount) {
        return d.readThrough(d.getStatus(), d.getFreelancerId(),
                amount == null ? null : BigDecimal.valueOf(amount), d.getPickupMinTime(), d.getPickupMaxTime(),
                d.getDeliveryMinTime(), d.getDeliveryMaxTime(), d.getActualPickupTime(), d.getActualDeliveryTime());
    }

    /** The same core state with an agreed price (XAF), as the core reports it once the terms are recorded. */
    public static com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState priced(
            com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState s,
            double amount) {
        return new com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState(
                s.deliveryId(), s.status(), s.trackingCode(), s.deliveryPersonId(), s.actualPickupTime(),
                s.actualDeliveryTime(), BigDecimal.valueOf(amount), "XAF", s.pickupWindowStart(), s.pickupWindowEnd(),
                s.deliveryWindowStart(), s.deliveryWindowEnd(), s.announcementId(), s.parcelId(), s.parcelVolumeM3());
    }

    /**
     * A core lifecycle port that records the agreed terms (answers with a CREATED state), for services that
     * record them at selection; nothing else is stubbed.
     */
    public static com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort termsRecorder() {
        var port = org.mockito.Mockito.mock(
                com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.class);
        acceptTerms(port);
        return port;
    }

    /**
     * Makes {@code port} record the agreed terms, the core reporting none recorded yet (lenient: not every
     * test selects a response; a test may stub {@code findState} more precisely afterwards).
     */
    public static void acceptTerms(com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort port) {
        org.mockito.Mockito.lenient().when(port.findState(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(reactor.core.publisher.Mono.empty());
        org.mockito.Mockito.lenient().when(port.recordAgreedTerms(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> reactor.core.publisher.Mono.just(
                        new com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort
                                .CoreDeliveryState(inv.getArgument(1), "CREATED", null, null, null, null)));
    }

    private static BigDecimal tarif(Delivery d) {
        return d.getTarif() == null ? null : BigDecimal.valueOf(d.getTarif());
    }
}
