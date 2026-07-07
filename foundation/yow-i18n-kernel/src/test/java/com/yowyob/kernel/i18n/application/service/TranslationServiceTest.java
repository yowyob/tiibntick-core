package com.yowyob.kernel.i18n.application.service;

import com.yowyob.kernel.i18n.application.port.out.MessageTranslationPort;
import com.yowyob.kernel.i18n.domain.vo.LocalizedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TranslationService}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private MessageTranslationPort port;

    private TranslationService service;

    @BeforeEach
    void setUp() {
        service = new TranslationService(port);
    }

    @Test
    void translate_shouldReturnTranslatedMessage_whenKeyExists() {
        // given
        String key    = "notification.package.delivered";
        String language = "fr_CM";
        String content = "Le colis PKG-1 a été livré avec succès.";
        when(port.findByKeyAndLanguage(key, language))
                .thenReturn(Optional.of(new LocalizedMessage(key, language, content)));

        // when
        Optional<String> result = service.translate(key, language);

        // then
        assertThat(result).isPresent().contains(content);
    }

    @Test
    void translate_shouldReturnEmpty_whenKeyNotFound() {
        when(port.findByKeyAndLanguage("unknown.key", "fr_CM")).thenReturn(Optional.empty());

        Optional<String> result = service.translate("unknown.key", "fr_CM");

        assertThat(result).isEmpty();
    }

    @Test
    void translate_shouldInterpolateParameters_whenParametersProvided() {
        // given
        String key     = "notification.package.delivered";
        String language  = "en_CM";
        String template = "Package {{package_id}} has been delivered.";
        when(port.findByKeyAndLanguage(key, language))
                .thenReturn(Optional.of(new LocalizedMessage(key, language, template)));

        // when
        Optional<String> result = service.translate(key, language, Map.of("package_id", "PKG-99"));

        // then
        assertThat(result).isPresent().contains("Package PKG-99 has been delivered.");
    }

    @Test
    void translate_withEmptyParams_shouldReturnUninterpolatedMessage() {
        String key    = "billing.cost.estimate";
        String language = "fr_CM";
        String template = "Le coût estimé est de {{amount}} {{currency}}.";
        when(port.findByKeyAndLanguage(key, language))
                .thenReturn(Optional.of(new LocalizedMessage(key, language, template)));

        Optional<String> result = service.translate(key, language, Map.of());

        assertThat(result)
                .isPresent()
                .hasValueSatisfying(v -> assertThat(v).contains("{{amount}}"));
    }
}
