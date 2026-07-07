package com.yowyob.tiibntick.core.actor.adapter.out.incident;

import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;
import com.yowyob.tiibntick.core.incident.port.outbound.IActorReputationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * tnt-actor-core implementation of {@link IActorReputationPort} (outbound port from tnt-incident-core).
 *
 * <p>This adapter is called by {@code tnt-incident-core} to apply reputation consequences
 * when incidents are closed or escalated to fraud disputes. It translates incident-domain
 * operations into actor-domain mutations:
 * <ul>
 *   <li>{@link #decreaseReputation} → deducts penalty points from {@code ActorRating.score}</li>
 *   <li>{@link #flagForFraud} → sets {@code KycStatus.FLAGGED} and suspends the actor</li>
 *   <li>{@link #getReputationScore} → reads the current rating score</li>
 *   <li>{@link #getIncidentHistoryCount} → reads the local incident counter</li>
 * </ul>
 *
 * <p>All operations check deliverer profiles first, then freelancer profiles.
 * If the actor is found in neither, the operation is a no-op (logged as warning).
 *
 * @author MANFOUO Braun
 * @see IActorReputationPort
 */
@Slf4j
@Component
public class ActorReputationPortAdapter implements IActorReputationPort {

    private final IDelivererRepository delivererRepository;
    private final IFreelancerRepository freelancerRepository;

    public ActorReputationPortAdapter(IDelivererRepository delivererRepository,
                                       IFreelancerRepository freelancerRepository) {
        this.delivererRepository = delivererRepository;
        this.freelancerRepository = freelancerRepository;
    }

    /**
     * Decreases the actor's reputation score by the given penalty points.
     *
     * <p>Penalty is applied directly to the current score (clamped to 0.0 minimum),
     * not as a new rating entry. This ensures fault-based penalties are distinct
     * from client star ratings in the weighted average history.
     *
     * @param actorId UUID of the actor whose reputation should be reduced
     * @param points  penalty points to deduct (must be positive)
     * @param reason  human-readable reason for the deduction (audit trail)
     * @return empty Mono on success
     */
    @Override
    public Mono<Void> decreaseReputation(UUID actorId, double points, String reason) {
        return decreaseDelivererReputation(actorId, points, reason)
                .switchIfEmpty(decreaseFreelancerReputation(actorId, points, reason))
                .doOnSuccess(ignored -> log.info(
                        "Reputation decreased for actor {} by {} points. Reason: {}",
                        actorId, points, reason))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("decreaseReputation: actor {} not found in any profile repository",
                                actorId)));
    }

    /**
     * Flags the actor for fraud following an incident investigation.
     *
     * <p>For permanent deliverers: calls {@link DelivererProfile#withFraudFlag(UUID)} which
     * sets {@code KycStatus.FLAGGED} and {@code ActorStatus.SUSPENDED}.
     *
     * <p>For freelancers: applies {@code KycStatus.FLAGGED} and {@code ActorStatus.SUSPENDED}
     * using the available domain mutations (withKycStatus + suspend).
     *
     * @param actorId    UUID of the actor suspected of fraud
     * @param incidentId UUID of the incident that triggered this flag
     * @param evidence   description of the fraud evidence (audit log only)
     * @return empty Mono on success
     */
    @Override
    public Mono<Void> flagForFraud(UUID actorId, UUID incidentId, String evidence) {
        return flagDelivererForFraud(actorId, incidentId)
                .switchIfEmpty(flagFreelancerForFraud(actorId, evidence))
                .doOnSuccess(ignored -> log.warn(
                        "Actor {} flagged for fraud — incidentId: {}, evidence snippet: {}",
                        actorId, incidentId,
                        evidence != null && evidence.length() > 80
                                ? evidence.substring(0, 80) + "..." : evidence))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("flagForFraud: actor {} not found in any profile repository",
                                actorId)));
    }

    private Mono<Void> decreaseDelivererReputation(UUID actorId, double points, String reason) {
        return delivererRepository.findFirstByActorId(actorId)
                .flatMap(profile -> delivererRepository.save(
                        profile.withRating(profile.rating().decreaseScore(points, reason))).then());
    }

    private Mono<Void> decreaseFreelancerReputation(UUID actorId, double points, String reason) {
        return freelancerRepository.findFirstByActorId(actorId)
                .flatMap(profile -> freelancerRepository.save(
                        profile.withRating(profile.rating().decreaseScore(points, reason))).then());
    }

    private Mono<Void> flagDelivererForFraud(UUID actorId, UUID incidentId) {
        return delivererRepository.findFirstByActorId(actorId)
                .flatMap(profile -> delivererRepository.save(profile.withFraudFlag(incidentId)).then());
    }

    private Mono<Void> flagFreelancerForFraud(UUID actorId, String evidence) {
        return freelancerRepository.findFirstByActorId(actorId)
                .flatMap(profile -> freelancerRepository.save(
                        profile.withKycStatus(KycStatus.FLAGGED).suspend("FRAUD: " + evidence)).then());
    }

    /**
     * Returns the current reputation score of the actor (0.0–5.0).
     *
     * @param actorId UUID of the actor
     * @return the current rating score, or {@code 0.0} if no profile found
     */
    @Override
    public Mono<Double> getReputationScore(UUID actorId) {
        return delivererRepository.findFirstByActorId(actorId)
                .map(p -> p.rating().score())
                .switchIfEmpty(
                        freelancerRepository.findFirstByActorId(actorId)
                                .map(p -> p.rating().score()))
                .defaultIfEmpty(0.0);
    }

    /**
     * Returns the number of incidents this actor was involved in as a driver.
     *
     * <p>The count is maintained locally in {@code tnt_actor} via the
     * {@code incident_history_count} column, incremented atomically by
     * {@code IncidentEventConsumer} on each {@code tnt.incident.closed} event.
     *
     * @param actorId UUID of the actor
     * @return the incident history count, or {@code 0} if no profile found
     */
    @Override
    public Mono<Integer> getIncidentHistoryCount(UUID actorId) {
        return delivererRepository.findFirstByActorId(actorId)
                .map(DelivererProfile::incidentHistoryCount)
                .switchIfEmpty(
                        freelancerRepository.findFirstByActorId(actorId)
                                .map(FreelancerProfile::incidentHistoryCount))
                .defaultIfEmpty(0);
    }
}
