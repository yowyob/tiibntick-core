package com.yowyob.tiibntick.core.i18n.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.i18n.application.port.in.ManageUserLanguageUseCase;
import com.yowyob.tiibntick.core.i18n.domain.model.UserLanguagePreference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

/** The language of the signed-in account — one for all its profiles and every TiiBnTick product. */
@RestController
@RequestMapping("/api/v1/me/language")
@Tag(name = "i18n", description = "Language preference of the signed-in account")
public class MyLanguageController {

    public record LanguageView(String language, String source, Instant updatedAt) {
        static LanguageView of(UserLanguagePreference p) {
            return new LanguageView(p.language(), p.source(), p.updatedAt());
        }
    }

    /** @param ifUnset when true the value is only a detection (browser) and never overrides a choice */
    public record SetLanguageRequest(String language, Boolean ifUnset) {}

    private final ManageUserLanguageUseCase useCase;

    public MyLanguageController(ManageUserLanguageUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @Operation(summary = "My language; 404 when none was ever stored (the client then proposes its own default)")
    public Mono<ResponseEntity<ApiResponse<LanguageView>>> get(@CurrentUser TntSecurityContext user) {
        return useCase.get(user.userId())
                .map(p -> ResponseEntity.ok(ApiResponse.success(LanguageView.of(p))))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("LANGUAGE_NOT_SET", "No language stored for this account", null)));
    }

    @PutMapping
    @Operation(summary = "Choose my language (or, with ifUnset=true, record the detected one if I have none)")
    public Mono<ApiResponse<LanguageView>> set(@RequestBody SetLanguageRequest request,
                                               @CurrentUser TntSecurityContext user) {
        Mono<UserLanguagePreference> saved = Boolean.TRUE.equals(request.ifUnset())
                ? useCase.detect(user.userId(), request.language())
                : useCase.choose(user.userId(), request.language());
        return saved.map(p -> ApiResponse.success(LanguageView.of(p)));
    }
}
