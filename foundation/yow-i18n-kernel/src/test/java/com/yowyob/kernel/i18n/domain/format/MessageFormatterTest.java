package com.yowyob.kernel.i18n.domain.format;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageFormatterTest {

    private static String f(String tpl, String lang, Map<String, ?> p) {
        // JDK CLDR groups with narrow no-break spaces: compare on plain spaces.
        String out = MessageFormatter.format(tpl, lang, p);
        return out == null ? null : out.replace('\u202f', ' ').replace('\u00a0', ' ');
    }

    @Test
    void replacesPlainParametersAndKeepsMissingOnesVisible() {
        assertThat(f("Colis {{id}} livré par {{driver}}", "fr", Map.of("id", "PKG-42", "driver", "Jo")))
                .isEqualTo("Colis PKG-42 livré par Jo");
        assertThat(f("Colis {{id}} / {{other}}", "fr", Map.of("id", "1"))).isEqualTo("Colis 1 / {{other}}");
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("id", null);
        assertThat(f("[{{id}}]", "fr", withNull)).isEqualTo("[]");
        assertThat(MessageFormatter.hasUnresolved(f("{{x}}", "fr", Map.of()))).isTrue();
    }

    @Test
    void pluralFollowsTheLanguageRules() {
        String tpl = "{{n:plural|one=# colis|other=# colis}} en route";
        assertThat(f(tpl, "fr", Map.of("n", 0))).isEqualTo("0 colis en route");
        assertThat(f(tpl, "fr", Map.of("n", 1))).isEqualTo("1 colis en route");
        String en = "{{n:plural|zero=no parcel|one=# parcel|other=# parcels}}";
        assertThat(f(en, "en", Map.of("n", 0))).isEqualTo("no parcel");
        assertThat(f(en, "en", Map.of("n", 1))).isEqualTo("1 parcel");
        assertThat(f(en, "en", Map.of("n", 3))).isEqualTo("3 parcels");
        assertThat(f("{{n:plural|one=# parcel|other=# parcels}}", "en", Map.of("n", 1500)))
                .isEqualTo("1,500 parcels");
    }

    @Test
    void moneyPutsTheCurrencyOnTheRightSideOfTheNumber() {
        assertThat(f("{{a:money}}", "fr", Map.of("a", 1500))).isEqualTo("1 500 XAF");
        assertThat(f("{{a:money}}", "en", Map.of("a", 1500))).isEqualTo("XAF 1,500");
        assertThat(f("{{a:money|NGN}}", "en", Map.of("a", new BigDecimal("12.5")))).isEqualTo("NGN 12.50");
        assertThat(f("{{a:money}}", "fr", Map.of("a", 20, "currency", "xof"))).isEqualTo("20 XOF");
    }

    @Test
    void distanceSwitchesToMetresBelowOneKilometre() {
        assertThat(f("{{d:distance}}", "fr", Map.of("d", 0.85))).isEqualTo("850 m");
        assertThat(f("{{d:distance}}", "fr", Map.of("d", 4.25))).isEqualTo("4,3 km");
        assertThat(f("{{d:distance}}", "en", Map.of("d", 4.25))).isEqualTo("4.3 km");
    }

    @Test
    void datesAreLocalisedInTheDoualaZoneByDefault() {
        Instant noonUtc = Instant.parse("2030-03-05T12:00:00Z");
        assertThat(f("{{t:time}}", "fr", Map.of("t", noonUtc))).isEqualTo("13:00");
        assertThat(f("{{t:time}}", "en", Map.of("t", noonUtc))).contains("1:00");
        assertThat(f("{{t:date}}", "fr", Map.of("t", noonUtc))).contains("5").contains("2030");
        assertThat(f("{{t:date}}", "en", Map.of("t", "2030-03-05T12:00:00Z"))).contains("Mar").contains("2030");
    }

    @Test
    void badInputNeverThrowsAndIsEmittedAsWritten() {
        assertThat(f("{{n:number}}", "fr", Map.of("n", "abc"))).isEqualTo("{{n:number}}");
        assertThat(f("{{t:datetime}}", "fr", Map.of("t", "not a date"))).isEqualTo("{{t:datetime}}");
        assertThat(f("unterminated {{x", "fr", Map.of("x", 1))).isEqualTo("unterminated {{x");
        assertThat(f(null, "fr", Map.of())).isNull();
        assertThat(f("{{x:unknown}}", "fr", Map.of("x", 7))).isEqualTo("7");
    }
}
