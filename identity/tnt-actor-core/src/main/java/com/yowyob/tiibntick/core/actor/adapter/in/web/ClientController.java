package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.ClientProfileResponse;
import com.yowyob.tiibntick.core.actor.adapter.in.web.dto.CreateClientRequest;
import com.yowyob.tiibntick.core.actor.application.command.CreateClientProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateClientProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindClientUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.domain.exception.ClientNotFoundException;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.yowyob.tiibntick.common.api.ApiResponse;
import reactor.core.publisher.Mono;

/**
 * REST controller for client profile operations.
 *
 * <p>{@code identity} on the response is resolved live from the Kernel via
 * {@link IResolveActorIdentityUseCase} — never stored on {@code ClientProfile}
 * itself, so name/phone/email never get duplicated locally.
 *
 * @author MANFOUO Braun
 */
@RestController
@Validated
@RequestMapping("/api/v1/clients")
@Tag(name = "Clients", description = "Client profile management")
public class ClientController {

    private com.yowyob.tiibntick.common.session.AuthenticatedSessionPublisher sessionPublisher;

    /**
     * Tells session listeners (e.g. wallet auto-provisioning) that the caller's profile now
     * exists; optional so hand-built controllers still work. Fire-and-forget, never fails the request.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSessionPublisher(com.yowyob.tiibntick.common.session.AuthenticatedSessionPublisher sessionPublisher) {
        this.sessionPublisher = sessionPublisher;
    }

    private Mono<Void> announceProfileCreated() {
        return sessionPublisher == null ? Mono.empty() : sessionPublisher.profileCreated();
    }

    private ProfileLanguageSupport profileLanguage;

    /** Stores the language picked at creation on the account; optional so hand-built controllers still work. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProfileLanguage(ProfileLanguageSupport profileLanguage) {
        this.profileLanguage = profileLanguage;
    }

    private reactor.core.publisher.Mono<Void> recordLanguage(java.util.UUID userId, String chosen, String acceptLanguage) {
        return profileLanguage == null ? reactor.core.publisher.Mono.empty()
                : profileLanguage.record(userId, chosen, acceptLanguage);
    }

    private final ICreateClientProfileUseCase createUseCase;
    private final IFindClientUseCase findUseCase;
    private final IResolveActorIdentityUseCase identityUseCase;

    public ClientController(ICreateClientProfileUseCase createUseCase,
                             IFindClientUseCase findUseCase,
                             IResolveActorIdentityUseCase identityUseCase) {
        this.createUseCase = createUseCase;
        this.findUseCase = findUseCase;
        this.identityUseCase = identityUseCase;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a client profile for the authenticated user")
    public Mono<ResponseEntity<ApiResponse<ClientProfileResponse>>> createClientProfile(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @Valid @RequestBody Mono<CreateClientRequest> requestMono,
            @Parameter(hidden = true) @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return requestMono.flatMap(req ->
                recordLanguage(currentUser.userId(), req.preferredLanguage(), acceptLanguage)
                        .then(Mono.defer(() -> createUseCase.createClientProfile(
                                new CreateClientProfileCommand(currentUser.tenantId(), currentUser.userId())))))
                .flatMap(profile -> identityUseCase.resolveCurrent()
                        .map(identity -> ClientProfileResponse.from(profile, identity))
                        .defaultIfEmpty(ClientProfileResponse.from(profile)))
                .flatMap(r -> announceProfileCreated().thenReturn(r))
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(r)));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the client profile of the authenticated client")
    public Mono<ResponseEntity<ApiResponse<ClientProfileResponse>>> getMyProfile(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return findUseCase.findMine(currentUser.tenantId(), currentUser.userId())
                .switchIfEmpty(Mono.error(new ClientNotFoundException(currentUser.tenantId(), currentUser.userId())))
                .flatMap(profile -> identityUseCase.resolveCurrent()
                        .map(identity -> ClientProfileResponse.from(profile, identity))
                        .defaultIfEmpty(ClientProfileResponse.from(profile)))
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }
}
