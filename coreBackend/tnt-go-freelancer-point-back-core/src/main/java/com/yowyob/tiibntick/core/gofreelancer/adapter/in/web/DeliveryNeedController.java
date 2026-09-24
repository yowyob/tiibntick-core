package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryNeedUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AssignFreelancerRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/delivery-needs")
@RequiredArgsConstructor
public class DeliveryNeedController {

    private final DeliveryNeedUseCase deliveryNeedUseCase;

    /**
     * Identité de l'appelant, ou {@code null} si le contexte est absent.
     *
     * <p>On retient {@code userId()} et non {@code actorId()} : {@code actorId} pointe vers
     * {@code DelivererProfile}/{@code FreelancerProfile} de {@code tnt-actor-core}, un espace
     * d'identité sans rapport avec {@code delivery_needs.user_id}. Ce dernier vit dans l'espace
     * {@code gofp_users.core_user_id} — c'est ce que fait déjà
     * {@code DeliveryNeedApplicationService#ensureLegacyUser}.
     *
     * <p>Pourquoi {@code required = false} plutôt que {@code @CurrentUser} seul : la chaîne de
     * sécurité ({@code TntSecurityConfig @Order(20)}) refuse déjà les requêtes non authentifiées
     * sur {@code /api/**} avant que le contrôleur soit atteint. {@code required = false} n'est
     * donc pas une porte dérobée : un {@code null} passé ici est refusé par la garde d'ownership
     * du service ({@code resolveOwner} lève {@code AccessDeniedException} depuis le lot 25.1).
     * Le seul intérêt de {@code required = false} est de laisser le service porter l'échec
     * explicitement plutôt que de déléguer un 401 opaque au résolveur Spring.
     */
    private static UUID callerId(TntSecurityContext ctx) {
        return ctx != null ? ctx.userId() : null;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DeliveryNeedResponseDTO> createDeliveryNeed(
            @RequestBody DeliveryNeedRequestDTO request,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return deliveryNeedUseCase.createDeliveryNeed(request, callerId(ctx));
    }

    @GetMapping("/{id}")
    public Mono<DeliveryNeedResponseDTO> getDeliveryNeed(
            @PathVariable UUID id,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return deliveryNeedUseCase.getDeliveryNeed(id, callerId(ctx));
    }

    @GetMapping("/user/{userId}")
    public Flux<DeliveryNeedResponseDTO> getDeliveryNeedsByUser(
            @PathVariable UUID userId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return deliveryNeedUseCase.getDeliveryNeedsByUserId(userId, callerId(ctx));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteDeliveryNeed(
            @PathVariable UUID id,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return deliveryNeedUseCase.deleteDeliveryNeed(id, callerId(ctx));
    }

    @GetMapping("/{id}/candidates")
    public Flux<FreelancerCandidateDTO> getCandidatesWithPricing(
            @PathVariable UUID id,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return deliveryNeedUseCase.getCandidatesWithPricing(id, callerId(ctx));
    }

    @PostMapping("/{id}/assign")
    public Mono<ResponseEntity<DeliveryNeedResponseDTO>> assignFreelancer(
            @PathVariable UUID id,
            @RequestBody AssignFreelancerRequestDTO request,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return deliveryNeedUseCase.assignFreelancer(id, request.getFreelancerId(), callerId(ctx))
                .map(ResponseEntity::ok);
    }
}
