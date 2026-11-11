package com.yowyob.kernel.i18n.domain.vo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageTagsTest {

    @Test
    void foldsAnyTagToAPublicCode() {
        assertThat(LanguageTags.toPublicCode("fr")).isEqualTo("fr");
        assertThat(LanguageTags.toPublicCode("FR_cm")).isEqualTo("fr");
        assertThat(LanguageTags.toPublicCode("en-US")).isEqualTo("en");
        assertThat(LanguageTags.toPublicCode("pidgin_CM")).isEqualTo("en");
        assertThat(LanguageTags.toPublicCode("de")).isNull();
        assertThat(LanguageTags.toPublicCode(" ")).isNull();
        assertThat(LanguageTags.toPublicCodeOrDefault("de")).isEqualTo("fr");
    }

    @Test
    void fallbackChainGoesFromSpecificToDefault() {
        assertThat(LanguageTags.fallbackChain("en_CM")).containsExactly("en_CM", "en", "fr");
        assertThat(LanguageTags.fallbackChain("en")).containsExactly("en", "fr");
        assertThat(LanguageTags.fallbackChain("fr")).containsExactly("fr");
        assertThat(LanguageTags.fallbackChain("xx")).containsExactly("xx", "fr");
        assertThat(LanguageTags.fallbackChain(null)).containsExactly("fr");
    }

    @Test
    void acceptLanguageHonoursQualityWeights() {
        assertThat(LanguageTags.fromAcceptLanguage("en-US,en;q=0.9,fr;q=0.8")).isEqualTo("en");
        assertThat(LanguageTags.fromAcceptLanguage("de;q=1, fr;q=0.4, en;q=0.6")).isEqualTo("en");
        assertThat(LanguageTags.fromAcceptLanguage("de,es")).isNull();
        assertThat(LanguageTags.fromAcceptLanguage(null)).isNull();
    }

    @Test
    void localeFollowsTheRegionWhenGiven() {
        assertThat(LanguageTags.toLocale("fr_CM")).isEqualTo(Locale.forLanguageTag("fr-CM"));
        assertThat(LanguageTags.toLocale("pidgin_CM")).isEqualTo(Locale.ENGLISH);
        assertThat(LanguageTags.toLocale(null)).isEqualTo(Locale.FRENCH);
        assertThat(LanguageTags.supported()).isEqualTo(List.of("fr", "en"));
    }
}
