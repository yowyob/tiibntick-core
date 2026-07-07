package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.KycSubmitRequest;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.KycValidateRequest;
import com.yowyob.tiibntick.core.actor.application.command.SubmitKycCommand;
import com.yowyob.tiibntick.core.actor.application.command.ValidateKycCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ISubmitKycUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IValidateKycUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for KYC (Know Your Customer) verification operations.
 *
 * <p> — Migrated from {@code ReactiveRequestContextHolder} (Kernel pattern) to
 * {@code @CurrentUser TntUserIdentity} (tnt-auth-core pattern).
 *
 * <p>KYC validation by a KYC officer can also set {@link com.yowyob.tiibntick.core.actor.domain.model.KycStatus#FLAGGED}
 * when reviewing a fraud flag previously set by tnt-incident-core via
 * {@code IActorReputationPort.flagForFraud()}.
 *
 * @author MANFOUO Braun
 */
@RestController
@Validated
@RequestMapping("/api/v1/kyc")
@Tag(name = "KYC", description = "Know Your Customer document submission and validation")
public class ActorKycController {

    private final ISubmitKycUseCase submitKycUseCase;
    private final IValidateKycUseCase validateKycUseCase;

    public ActorKycController(ISubmitKycUseCase submitKycUseCase,
                          IValidateKycUseCase validateKycUseCase) {
        this.submitKycUseCase = submitKycUseCase;
        this.validateKycUseCase = validateKycUseCase;
    }

    /**
     * Submits KYC documents for the authenticated actor.
     * Transitions the actor's {@code KycStatus} from {@code PENDING} or {@code REJECTED}
     * to {@code UNDER_REVIEW}.
     *
     * @param currentUser the authenticated user (injected by tnt-auth-core)
     * @param actorType   the type of actor submitting KYC
     * @param requestMono the KYC submission payload
     * @return 202 Accepted — document review is asynchronous
     */
    @PostMapping("/submit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit KYC documents for the authenticated actor")
    public Mono<ResponseEntity<Void>> submitKyc(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestParam ActorType actorType,
            @Valid @RequestBody Mono<KycSubmitRequest> requestMono) {
        return requestMono.flatMap(req ->
                submitKycUseCase.submitKyc(new SubmitKycCommand(
                        currentUser.tenantId(),
                        currentUser.userId(),
                        actorType,
                        req.documentUrl(),
                        req.documentType())))
                .thenReturn(ResponseEntity.accepted().build());
    }

    /**
     * Validates (approves or rejects) KYC documents for an actor.
     * Accessible only by KYC officers and platform admins.
     *
     * <p>This endpoint can also be used to clear a {@code FLAGGED} status by setting
     * {@code newKycStatus = VERIFIED}, which re-activates the actor after a fraud investigation.
     *
     * @param currentUser the authenticated KYC officer
     * @param actorId     the UUID of the actor whose KYC is being validated
     * @param actorType   the type of actor
     * @param requestMono the validation decision payload
     * @return 204 No Content on success
     */
    @PutMapping("/{actorId}/validate")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TNT_ADMIN')")
    @Operation(summary = "Approve or reject KYC documents for an actor (KYC officer only)")
    public Mono<ResponseEntity<Void>> validateKyc(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID actorId,
            @RequestParam ActorType actorType,
            @Valid @RequestBody Mono<KycValidateRequest> requestMono) {
        return requestMono.flatMap(req ->
                validateKycUseCase.validateKyc(new ValidateKycCommand(
                        currentUser.tenantId(),
                        actorId,
                        actorType,
                        req.newKycStatus(),
                        currentUser.userId().toString(),
                        req.rejectionReason())))
                .thenReturn(ResponseEntity.noContent().build());
    }
}
