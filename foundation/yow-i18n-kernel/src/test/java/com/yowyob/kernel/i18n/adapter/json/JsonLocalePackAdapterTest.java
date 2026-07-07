package com.yowyob.kernel.i18n.adapter.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.i18n.domain.vo.LocalizedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-style unit tests for {@link JsonLocalePackAdapter}.
 * Loads actual JSON files from the test classpath.
 *
 * @author MANFOUO Braun
 */
class JsonLocalePackAdapterTest {

    private JsonLocalePackAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        adapter = new JsonLocalePackAdapter(mapper, "fr_CM", "en_CM", "pidgin_CM");
    }

    @Test
    void findByKeyAndLanguage_shouldFindFrenchMessage() {
        Optional<LocalizedMessage> result = adapter.findByKeyAndLanguage(
                "notification.package.delivered", "fr_CM");

        assertThat(result).isPresent();
        assertThat(result.get().content()).contains("colis");
    }

    @Test
    void findByKeyAndLanguage_shouldFindEnglishMessage() {
        Optional<LocalizedMessage> result = adapter.findByKeyAndLanguage(
                "notification.package.delivered", "en_CM");

        assertThat(result).isPresent();
        assertThat(result.get().content()).containsIgnoringCase("package");
    }

    @Test
    void findByKeyAndLanguage_shouldFindPidginMessage() {
        Optional<LocalizedMessage> result = adapter.findByKeyAndLanguage(
                "notification.package.delivered", "pidgin_CM");

        assertThat(result).isPresent();
        assertThat(result.get().content()).containsIgnoringCase("cargo");
    }

    @Test
    void findByKeyAndLanguage_shouldReturnEmpty_whenKeyNotFound() {
        Optional<LocalizedMessage> result = adapter.findByKeyAndLanguage("does.not.exist", "fr_CM");
        assertThat(result).isEmpty();
    }

    @Test
    void findByKeyAndLanguage_shouldReturnEmpty_whenLocaleNotLoaded() {
        Optional<LocalizedMessage> result = adapter.findByKeyAndLanguage(
                "notification.package.delivered", "es_ES");
        assertThat(result).isEmpty();
    }

    @Test
    void numberOfLoadedLocales_shouldBeThree() {
        assertThat(adapter.numberOfLoadedLocales()).isEqualTo(3);
    }
}
