package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Cas d'usage du besoin de livraison.
 *
 * <p><strong>{@code callerId}.</strong> Les opérations qui exposent ou modifient le besoin
 * d'un utilisateur reçoivent l'identité de l'appelant, résolue par l'adaptateur web depuis
 * {@code TntSecurityContext#userId()} via {@code @CurrentUser}. Elle est {@code null} quand
 * le contexte est absent (profil local {@code tnt.auth.allow-anonymous-context=true}) ; avec
 * la garde active ({@code tnt.gofp.ownership-guard.enabled=true}), un {@code callerId == null}
 * lève {@code AccessDeniedException} — cf. {@code DeliveryNeedApplicationService#resolveOwner}.
 *
 * <p>L'identité circule en paramètre plutôt qu'être lue depuis un contexte réactif au fond du
 * service : le port reste explicite sur ce dont il a besoin, et testable sans pile Spring Security.
 */
public interface DeliveryNeedUseCase {
    Mono<DeliveryNeedResponseDTO> createDeliveryNeed(DeliveryNeedRequestDTO request, UUID callerId);
    Mono<DeliveryNeedResponseDTO> getDeliveryNeed(UUID id, UUID callerId);
    Flux<DeliveryNeedResponseDTO> getDeliveryNeedsByUserId(UUID userId, UUID callerId);
    Mono<Void> deleteDeliveryNeed(UUID id, UUID callerId);
    Flux<FreelancerCandidateDTO> getCandidatesWithPricing(UUID deliveryNeedId, UUID callerId);
    Mono<DeliveryNeedResponseDTO> assignFreelancer(UUID deliveryNeedId, UUID freelancerId, UUID callerId);
}
