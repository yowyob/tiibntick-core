package com.yowyob.tiibntick.core.notify.infrastructure.adapter.i18n;

import com.yowyob.kernel.i18n.application.port.in.TranslateMessageUseCase;
import com.yowyob.kernel.i18n.config.I18nKernelProperties;
import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link I18nTranslationPortAdapter} — the single choke point
 * every notification send routes its i18n lookup through.
 *
 * <p>Regression coverage for Audit n°4 (P-1, P-2, P-21, P0 item1): a lookup
 * called with the bare language code {@code "fr"} against a template key
 * actually used in production must resolve correctly (not return empty just
 * because the pack is indexed by {@code fr_CM}/{@code fr_FR}), and a genuinely
 * missing key must never leak the raw "⚠ Missing translation: ..." text (or
 * the template key) to the end client.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class I18nTranslationPortAdapterTest {

    @Mock
    private TranslateMessageUseCase translateMessageUseCase;

    private SimpleMeterRegistry meterRegistry;
    private I18nTranslationPortAdapter adapter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        I18nKernelProperties properties = new I18nKernelProperties();
        properties.setDefaultLanguage(SupportedLanguage.FR_CM);
        adapter = new I18nTranslationPortAdapter(translateMessageUseCase, properties, meterRegistry);
    }

    @Test
    void translate_shouldNormalizeBareLanguageCode_forRealProductionKey() {
        // "fr" is what FreelancerOrgKafkaEventConsumer/MarketNotificationAdapter/etc.
        // used to pass; the pack is indexed "fr_CM" — this must resolve, not miss.
        String key = "notify.freelancer_org.verified";
        when(translateMessageUseCase.translate(key, "fr_CM", Map.of("orgId", "org-1", "kycLevel", "BASIC")))
                .thenReturn(Optional.of("🎉 Félicitations ! Votre organisation org-1 a été vérifiée."));

        NotificationModel model = new NotificationModel(key, "fr",
                Map.of("orgId", "org-1", "kycLevel", "BASIC"), NotificationPriority.HIGH);

        StepVerifier.create(adapter.translate(model))
                .expectNext("🎉 Félicitations ! Votre organisation org-1 a été vérifiée.")
                .verifyComplete();
    }

    @Test
    void translate_shouldFallBackToDefaultLocale_whenKeyMissingForRequestedLanguage() {
        String key = "notify.freelancer_org.verified";
        when(translateMessageUseCase.translate(key, "en_NG", Map.of())).thenReturn(Optional.empty());
        when(translateMessageUseCase.translate(key, "fr_CM", Map.of()))
                .thenReturn(Optional.of("Texte par défaut."));

        NotificationModel model = NotificationModel.of(key, "en_NG", Map.of());

        StepVerifier.create(adapter.translate(model))
                .expectNext("Texte par défaut.")
                .verifyComplete();
    }

    @Test
    void translate_shouldNeverLeakRawMissingTranslationText_whenKeyMissingEverywhere() {
        String key = "notify.some.genuinely.missing.key";
        when(translateMessageUseCase.translate(key, "fr_CM", Map.of())).thenReturn(Optional.empty());

        NotificationModel model = NotificationModel.of(key, "fr", Map.of());

        String result = adapter.translate(model).block();

        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("Missing translation");
        assertThat(result).doesNotContain(key);
        assertThat(meterRegistry.get("tnt.notify.i18n.missing_translation").counter().count()).isEqualTo(1.0);
    }

    @Test
    void translate_shouldResolveEnglishSafeFallback_whenTargetLanguageIsEnglish() {
        String key = "notify.some.genuinely.missing.key";
        when(translateMessageUseCase.translate(key, "en_CM", Map.of())).thenReturn(Optional.empty());

        NotificationModel model = NotificationModel.of(key, "en", Map.of());

        String result = adapter.translate(model).block();

        assertThat(result).isEqualTo("You have a new notification.");
    }

    @Test
    void translate_shouldUseDefaultLanguage_whenTargetLanguageUnrecognized() {
        String key = "notify.freelancer_org.verified";
        when(translateMessageUseCase.translate(key, "fr_CM", Map.of()))
                .thenReturn(Optional.of("Texte par défaut."));

        NotificationModel model = NotificationModel.of(key, "not-a-real-language", Map.of());

        StepVerifier.create(adapter.translate(model))
                .expectNext("Texte par défaut.")
                .verifyComplete();
    }
}
