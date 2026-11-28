package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.command.RateActorCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IRateActorUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.EvaluationDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.EvaluationUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.SentimentAnalysisUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Evaluation;
import com.yowyob.tiibntick.core.tp.application.port.in.command.RateThirdPartyCommand;
import com.yowyob.tiibntick.core.tp.application.service.RatingService;
import com.yowyob.tiibntick.core.tp.domain.model.ThirdPartyRating;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Evaluations of Go-Freelancer, delegated to the core (lot L3, de-duplication).
 *
 * <p>Go keeps only the product rule: the score is predicted from the comment (sentiment analysis) when one is
 * given. The rating itself is a tp-core third-party rating ({@link RatingService#rate}: one per mission and rater),
 * and the average shown on a profile is the actor-core {@code ActorRating} ({@link IRateActorUseCase}).
 * Both calls go through {@code @RequirePermission} with the caller's token ({@code rating:create}).
 *
 * <p>The ids received may be profile ids (freelancer or client profile, relay point) or actor ids: they are
 * resolved to actor ids, the core ids.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationApplicationService implements EvaluationUseCase {

    static final String SUBJECT_CLIENT = "CLIENT";
    static final String SUBJECT_DELIVERY_PERSON = "DELIVERY_PERSON";
    static final String SUBJECT_RELAY_OPERATOR = "RELAY_OPERATOR";

    private final SentimentAnalysisUseCase sentimentAnalysisUseCase;
    private final RatingService ratingService;
    private final IRateActorUseCase rateActorUseCase;
    private final IFreelancerRepository freelancerProfiles;
    private final IClientProfileRepository clientProfiles;
    private final GofpRelayPointRepository gofpRelayPointRepository;
    private final TenantContextHolder tenantContextHolder;

    /** A party of an evaluation resolved to the core: its actor id and what it is. */
    record Party(UUID actorId, String subjectKind, ActorType actorType, UUID relayPointId) { }

    @Override
    public Mono<Evaluation> submitEvaluation(EvaluationDTO dto, TntSecurityContext ctx) {
        if (dto.getEvaluatedId() == null || dto.getDeliveryId() == null) {
            return Mono.error(new IllegalArgumentException("deliveryId and evaluatedId are required"));
        }
        if (ctx == null || ctx.userId() == null) {
            return Mono.error(new AccessDeniedException("An evaluation requires an authenticated caller"));
        }
        Mono<Integer> ratingMono;
        if (dto.getComment() != null && !dto.getComment().trim().isEmpty()) {
            ratingMono = sentimentAnalysisUseCase.predictRating(dto.getComment())
                    .defaultIfEmpty(dto.getRating() != null ? dto.getRating() : 5);
        } else {
            ratingMono = Mono.just(dto.getRating() != null ? dto.getRating() : 5);
        }

        return ratingMono.flatMap(finalRating -> {
            if (finalRating < 1 || finalRating > 5) {
                return Mono.error(new IllegalArgumentException("La note doit être comprise entre 1 et 5."));
            }
            return tenantContextHolder.currentTenantId().flatMap(tenantId -> Mono.zip(
                            resolve(tenantId, dto.getEvaluatedId(), evaluatedKind(dto.getType())),
                            evaluator(tenantId, dto, ctx))
                    .flatMap(parties -> {
                        Party evaluated = parties.getT1();
                        Party evaluator = parties.getT2();
                        return ratingService.rate(new RateThirdPartyCommand(tenantId, evaluated.actorId(),
                                        evaluator.actorId(), dto.getDeliveryId().toString(), finalRating,
                                        dto.getComment(), dto.getType(), evaluated.subjectKind()))
                                .flatMap(saved -> updateAverage(tenantId, evaluated, evaluator, saved)
                                        .thenReturn(toView(saved)));
                    }));
        })
                .doOnSuccess(e -> log.info("Evaluation saved in the core for evaluatedId={}, rating={}, type={}",
                        e.getEvaluatedId(), e.getRating(), e.getType()))
                .doOnError(e -> log.error("Failed to save evaluation for delivery {}: {}",
                        dto.getDeliveryId(), e.getMessage()));
    }

    @Override
    public Flux<Evaluation> getEvaluationsForPerson(UUID personId) {
        return tenantContextHolder.currentTenantId()
                .flatMapMany(tenantId -> resolve(tenantId, personId, null)
                        .flatMapMany(party -> ratingService.listRatings(tenantId, party.actorId())
                                .filter(r -> party.relayPointId() == null
                                        || SUBJECT_RELAY_OPERATOR.equals(r.getSubjectKind()))))
                .map(EvaluationApplicationService::toView);
    }

    /**
     * The evaluator (lot L3, C5): the actor of the token. A body {@code evaluatorId} is accepted only when it
     * designates the caller (its actor id or one of its profiles); any other id is refused (403), except for staff
     * (TNT_ADMIN, SUPPORT_AGENT), who may record an evaluation on behalf of the party it names.
     */
    Mono<Party> evaluator(UUID tenantId, EvaluationDTO dto, TntSecurityContext ctx) {
        UUID caller = ctx.userId();
        String kind = evaluatorKind(dto.getType());
        UUID requested = dto.getEvaluatorId();
        if (requested == null || requested.equals(caller)) {
            return resolve(tenantId, caller, kind)
                    // an actor without a Go-visible profile still rates as itself
                    .onErrorResume(IllegalArgumentException.class,
                            e -> Mono.just(new Party(caller, kind, null, null)));
        }
        if (DeliveryStatusApplicationService.isStaff(ctx)) {
            return resolve(tenantId, requested, kind);
        }
        return resolve(tenantId, requested, kind)
                .onErrorMap(IllegalArgumentException.class, e -> denied(requested))
                .flatMap(p -> caller.equals(p.actorId()) ? Mono.just(p) : Mono.error(denied(requested)));
    }

    private static AccessDeniedException denied(UUID requested) {
        return new AccessDeniedException("An evaluation can only be given in your own name (evaluatorId "
                + requested + " is not the caller)");
    }

    /**
     * Resolves a received id: freelancer profile, client profile, relay point (its owner's actor), then actor id.
     * An actor id may carry both a client and a freelancer profile (one account, two profiles): the side named by
     * the evaluation type ({@code preferredKind}) is taken first, the client profile otherwise.
     */
    Mono<Party> resolve(UUID tenantId, UUID id, String preferredKind) {
        Mono<Party> asClientActor = Mono.defer(() -> clientProfiles.findByActorId(tenantId, id)
                .map(p -> new Party(id, SUBJECT_CLIENT, ActorType.CLIENT, null)));
        Mono<Party> asFreelancerActor = Mono.defer(() -> freelancerProfiles.findByActorId(tenantId, id)
                .map(p -> new Party(id, SUBJECT_DELIVERY_PERSON, ActorType.FREELANCER, null)));
        Mono<Party> byActorId = SUBJECT_DELIVERY_PERSON.equals(preferredKind)
                ? asFreelancerActor.switchIfEmpty(asClientActor)
                : asClientActor.switchIfEmpty(asFreelancerActor);
        return freelancerProfiles.findById(tenantId, id)
                .map(p -> new Party(p.actorId(), SUBJECT_DELIVERY_PERSON, ActorType.FREELANCER, null))
                .switchIfEmpty(Mono.defer(() -> clientProfiles.findById(tenantId, id)
                        .map(p -> new Party(p.actorId(), SUBJECT_CLIENT, ActorType.CLIENT, null))))
                .switchIfEmpty(Mono.defer(() -> gofpRelayPointRepository.findByCoreRelayPointId(id)
                        .switchIfEmpty(Mono.defer(() -> gofpRelayPointRepository.findById(id)))
                        .flatMap(rp -> rp.getCoreFreelancerId() == null ? Mono.empty()
                                : freelancerProfiles.findById(tenantId, rp.getCoreFreelancerId())
                                .map(p -> new Party(p.actorId(), SUBJECT_RELAY_OPERATOR,
                                        ActorType.RELAY_OPERATOR, rp.getId())))))
                .switchIfEmpty(byActorId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown evaluation party: " + id)));
    }

    /** Kind of the rated party named by the type ({@code CLIENT_TO_DP}, {@code DP_TO_CLIENT}, ...), or null. */
    static String evaluatedKind(String type) {
        if (type == null) return null;
        String t = type.toUpperCase();
        if (t.endsWith("_TO_DP") || t.endsWith("DELIVERY_PERSON")) return SUBJECT_DELIVERY_PERSON;
        if (t.endsWith("_TO_CLIENT") || t.endsWith("_CLIENT")) return SUBJECT_CLIENT;
        return null;
    }

    /** Kind of the rater named by the type, or null. */
    static String evaluatorKind(String type) {
        if (type == null) return null;
        String t = type.toUpperCase();
        if (t.startsWith("DP_TO_") || t.startsWith("DELIVERY_PERSON")) return SUBJECT_DELIVERY_PERSON;
        if (t.startsWith("CLIENT")) return SUBJECT_CLIENT;
        return null;
    }

    /**
     * Profile average: actor-core {@code ActorRating} for a client or a freelancer. A relay point keeps its own
     * displayed average (relay point row, lot L4), recomputed from its owner's relay ratings in the core.
     */
    private Mono<Void> updateAverage(UUID tenantId, Party evaluated, Party evaluator, ThirdPartyRating saved) {
        if (evaluated.relayPointId() == null) {
            return rateActorUseCase.rateActor(new RateActorCommand(tenantId, evaluated.actorId(),
                    evaluated.actorType(), saved.getScore(), evaluator.actorId()));
        }
        return ratingService.listRatings(tenantId, evaluated.actorId())
                .filter(r -> SUBJECT_RELAY_OPERATOR.equals(r.getSubjectKind()))
                .map(ThirdPartyRating::getScore)
                .collectList()
                .filter(scores -> !scores.isEmpty())
                .flatMap(scores -> gofpRelayPointRepository.findById(evaluated.relayPointId()).flatMap(rp -> {
                    double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    rp.setRating(Math.round(avg * 10.0) / 10.0);
                    rp.setUpdatedAt(Instant.now());
                    return gofpRelayPointRepository.save(rp);
                }))
                .then();
    }

    static Evaluation toView(ThirdPartyRating r) {
        UUID deliveryId = null;
        try {
            deliveryId = r.getMissionId() != null ? UUID.fromString(r.getMissionId()) : null;
        } catch (IllegalArgumentException ignored) {
            // a mission reference that is not a delivery id stays null
        }
        return Evaluation.builder()
                .id(r.getId())
                .deliveryId(deliveryId)
                .evaluatorId(r.getRaterActorId())
                .evaluatedId(r.getRatedThirdPartyId())
                .rating((int) Math.round(r.getScore()))
                .score(r.getScore())
                .comment(r.getComment())
                .type(r.getRatingContext())
                .subjectKind(r.getSubjectKind())
                .createdAt(r.getCreatedAt() != null ? LocalDateTime.ofInstant(r.getCreatedAt(), ZoneOffset.UTC) : null)
                .build();
    }
}
