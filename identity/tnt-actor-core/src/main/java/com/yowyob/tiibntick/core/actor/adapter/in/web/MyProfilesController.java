package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindMyProfilesUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.ProfileRef;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/** One call to know every TiiBnTick profile of the signed-in account, independently of token roles and space. */
@RestController
@RequestMapping("/api/v1/actors/me/profiles")
@Tag(name = "Actor profiles", description = "All TiiBnTick profiles held by the authenticated account")
public class MyProfilesController {

    private final IFindMyProfilesUseCase useCase;

    public MyProfilesController(IFindMyProfilesUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List my profiles (client, freelancer, deliverer, relay operator), whichever space created them")
    public Mono<ResponseEntity<ApiResponse<List<ProfileRef>>>> myProfiles(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return useCase.findAll(currentUser.tenantId(), currentUser.userId())
                .collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list)));
    }
}
