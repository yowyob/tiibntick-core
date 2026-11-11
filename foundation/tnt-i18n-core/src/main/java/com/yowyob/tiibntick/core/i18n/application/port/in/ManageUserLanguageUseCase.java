package com.yowyob.tiibntick.core.i18n.application.port.in;

import com.yowyob.tiibntick.core.i18n.domain.model.UserLanguagePreference;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ManageUserLanguageUseCase {

    /** The stored preference, empty when the account never chose or was never assigned one. */
    Mono<UserLanguagePreference> get(UUID userId);

    /** Explicit choice by the user: replaces whatever is stored. */
    Mono<UserLanguagePreference> choose(UUID userId, String language);

    /** First-login detection: stored only when nothing is stored yet; an explicit choice is never touched. */
    Mono<UserLanguagePreference> detect(UUID userId, String language);
}
