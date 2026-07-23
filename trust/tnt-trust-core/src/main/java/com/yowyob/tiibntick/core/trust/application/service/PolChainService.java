package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.PolVerificationRecord;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordPolVerificationUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.GetPolVerificationsUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.PolVerificationRepository;

/**
 * Application Service — {@code PolChainService}.
 *
 * <p>Anchors Proof-of-Location (PoL) verifications on Hyperledger Fabric.
 * A PoL is cryptographic evidence that a deliverer was physically present
 * at a specific GPS location at a specific time, computed on the mobile device
 * using GPS + network data and verified server-side before anchoring.
 *
 * <p>Implements {@link RecordPolVerificationUseCase} and {@link GetPolVerificationsUseCase}.
 *
 * @author MANFOUO Braun
 * @version 1.1
 */
@Service
public class PolChainService implements RecordPolVerificationUseCase, GetPolVerificationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(PolChainService.class);

    private final LogisticEventPublisherService publisherService;
    private final PolVerificationRepository polVerificationRepository;
    private final MeterRegistry meterRegistry;

    public PolChainService(
            final LogisticEventPublisherService publisherService,
            final PolVerificationRepository polVerificationRepository,
            final MeterRegistry meterRegistry) {
        this.publisherService = publisherService;
        this.polVerificationRepository = polVerificationRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Persists a {@link PolVerificationRecord} locally first — so it is
     * immediately visible to reads — then builds a
     * {@code PROOF_OF_LOCATION_VERIFIED} logistic event and
     * publishes it to Kafka for Fabric anchoring.
     */
    @Override
    @Transactional
    public Mono<String> record(
            final String actorId,
            final double gpsLat,
            final double gpsLng,
            final String polHash,
            final String tenantId) {

        log.info("Recording PoL for actorId={}, lat={}, lng={}", actorId, gpsLat, gpsLng);

        final PolVerificationRecord verification = PolVerificationRecord.verify(
                actorId, tenantId, gpsLat, gpsLng, polHash);
        final LogisticTrustEvent event = LogisticTrustEvent.forPolVerification(verification);

        return polVerificationRepository.save(verification)
                .then(publisherService.publish(event))
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.pol.recorded",
                            "tenant", tenantId).increment();
                    log.info("PoL event published — correlationId={}", event.getCorrelationId());
                })
                .thenReturn(event.getCorrelationId());
    }

    /** {@inheritDoc} */
    @Override
    public Flux<PolVerificationRecord> getByActorId(final String actorId, final String tenantId) {
        return polVerificationRepository.findByActorId(actorId, tenantId);
    }
}
