package com.yowyob.tiibntick.core.i18n.application;

import com.yowyob.tiibntick.core.i18n.application.port.out.UserLanguagePreferenceRepository;
import com.yowyob.tiibntick.core.i18n.application.service.UserLanguageService;
import com.yowyob.tiibntick.core.i18n.domain.model.UserLanguagePreference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserLanguageServiceTest {

    private final UserLanguagePreferenceRepository repo = mock(UserLanguagePreferenceRepository.class);
    private final UserLanguageService service = new UserLanguageService(repo, Duration.ofMinutes(1));
    private final UUID user = UUID.randomUUID();

    private static UserLanguagePreference pref(UUID u, String lang, String source) {
        return new UserLanguagePreference(u, lang, source, Instant.now());
    }

    @Test
    void theAccountLanguageWinsOverTheCallersHint() {
        when(repo.findByUserId(user)).thenReturn(Mono.just(pref(user, "en", "USER")));
        StepVerifier.create(service.languageOf(user.toString(), "fr")).expectNext("en").verifyComplete();
    }

    @Test
    void anUnknownRecipientFallsBackToTheHintThenTheDefault() {
        when(repo.findByUserId(any())).thenReturn(Mono.empty());
        StepVerifier.create(service.languageOf(user.toString(), "en_CM")).expectNext("en").verifyComplete();
        StepVerifier.create(service.languageOf(UUID.randomUUID().toString(), null)).expectNext("fr").verifyComplete();
        StepVerifier.create(service.languageOf(UUID.randomUUID().toString(), "de")).expectNext("fr").verifyComplete();
    }

    @Test
    void aRecipientThatIsNotAUuidNeverHitsTheDatabase() {
        StepVerifier.create(service.languageOf("not-a-uuid", "en")).expectNext("en").verifyComplete();
        StepVerifier.create(service.languageOf(null, null)).expectNext("fr").verifyComplete();
        verify(repo, never()).findByUserId(any());
    }

    @Test
    void aDatabaseFailureDegradesToTheHintInsteadOfBlockingTheMessage() {
        when(repo.findByUserId(user)).thenReturn(Mono.error(new IllegalStateException("db down")));
        StepVerifier.create(service.languageOf(user.toString(), "en")).expectNext("en").verifyComplete();
    }

    @Test
    void lookupsAreCachedIncludingTheAbsenceOfAPreference() {
        AtomicInteger calls = new AtomicInteger();
        when(repo.findByUserId(user)).thenReturn(Mono.defer(() -> {
            calls.incrementAndGet();
            return Mono.empty();
        }));
        service.languageOf(user.toString(), null).block();
        service.languageOf(user.toString(), null).block();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void choosingStoresAPublicCodeAndRefreshesTheCache() {
        when(repo.upsert(eq(user), eq("en"), eq("USER"))).thenReturn(Mono.just(pref(user, "en", "USER")));
        StepVerifier.create(service.choose(user, "en-US")).assertNext(p -> assertThat(p.language()).isEqualTo("en"))
                .verifyComplete();
        // served from cache: the repository is not asked again
        StepVerifier.create(service.languageOf(user.toString(), "fr")).expectNext("en").verifyComplete();
        verify(repo, never()).findByUserId(any());
    }

    @Test
    void detectionNeverReplacesAnExplicitChoice() {
        when(repo.insertIfAbsent(eq(user), eq("fr"), eq("DETECTED"))).thenReturn(Mono.just(pref(user, "en", "USER")));
        StepVerifier.create(service.detect(user, "fr")).assertNext(p -> assertThat(p.language()).isEqualTo("en"))
                .verifyComplete();
        verify(repo, never()).upsert(any(), any(), any());
    }

    @Test
    void unsupportedLanguagesAreRejected() {
        StepVerifier.create(service.choose(user, "de")).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(service.choose(user, " ")).expectError(IllegalArgumentException.class).verify();
        verify(repo, never()).upsert(any(), any(), any());
    }
}
