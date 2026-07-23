package com.yowyob.kernel.i18n.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SupportedLanguage#fromCode(String)}.
 *
 * <p>Regression coverage for Audit n°4 / P-2: callers across the codebase pass
 * bare ISO codes (e.g. {@code "fr"}) while the JSON locale packs are indexed by
 * {@code fr_CM}/{@code fr_FR}/... tags — this normalization is what bridges the two.
 *
 * @author MANFOUO Braun
 */
class SupportedLanguageTest {

    @Test
    void fromCode_shouldResolveExactTag_caseInsensitive() {
        assertThat(SupportedLanguage.fromCode("fr_CM")).isEqualTo(SupportedLanguage.FR_CM);
        assertThat(SupportedLanguage.fromCode("FR_CM")).isEqualTo(SupportedLanguage.FR_CM);
        assertThat(SupportedLanguage.fromCode("en_US")).isEqualTo(SupportedLanguage.EN_US);
    }

    @Test
    void fromCode_shouldResolveBareIsoCode_toFirstDeclaredMatchingVariant() {
        // "fr" alone must resolve to FR_CM (declared first among FR_* variants,
        // matching the African-first default), not fail the lookup outright.
        assertThat(SupportedLanguage.fromCode("fr")).isEqualTo(SupportedLanguage.FR_CM);
        assertThat(SupportedLanguage.fromCode("en")).isEqualTo(SupportedLanguage.EN_CM);
    }

    @Test
    void fromCode_shouldTolerateDashSeparatedBcp47Style() {
        assertThat(SupportedLanguage.fromCode("fr-FR")).isEqualTo(SupportedLanguage.FR_FR);
    }

    @Test
    void fromCode_shouldReturnNull_whenNullOrBlank() {
        assertThat(SupportedLanguage.fromCode(null)).isNull();
        assertThat(SupportedLanguage.fromCode("")).isNull();
        assertThat(SupportedLanguage.fromCode("   ")).isNull();
    }

    @Test
    void fromCode_shouldReturnNull_whenCodeMatchesNothing() {
        assertThat(SupportedLanguage.fromCode("de")).isNull();
        assertThat(SupportedLanguage.fromCode("zz_ZZ")).isNull();
    }
}
