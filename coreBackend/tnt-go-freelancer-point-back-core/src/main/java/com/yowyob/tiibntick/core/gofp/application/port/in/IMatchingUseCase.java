package com.yowyob.tiibntick.core.gofp.application.port.in;

import com.yowyob.tiibntick.core.gofp.domain.model.MatchingRequest;
import com.yowyob.tiibntick.core.gofp.domain.model.MatchingResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IMatchingUseCase {

    /**
     * Lance le matching pour une annonce publiée.
     * Applique TOPSIS+AHP et notifie les candidats retenus.
     */
    Flux<MatchingResult> matchAnnouncement(MatchingRequest request);

    /**
     * Retourne les candidats classés pour une annonce sans les notifier.
     * Utile pour afficher la liste avant confirmation.
     */
    Flux<MatchingResult> rankCandidates(MatchingRequest request);

    /** Notifie les candidats retenus (FCM / SMS) après classement. */
    Mono<Void> notifyCandidates(UUID announcementId, Flux<MatchingResult> rankedCandidates);
}
