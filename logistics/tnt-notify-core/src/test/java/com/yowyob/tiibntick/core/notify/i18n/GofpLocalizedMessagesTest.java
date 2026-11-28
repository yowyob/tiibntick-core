package com.yowyob.tiibntick.core.notify.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.i18n.adapter.json.JsonLocalePackAdapter;
import com.yowyob.kernel.i18n.application.service.TranslationService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every Go-Freelancer message written once per language ({@code notify.gofp.<scenario>.*}) must render in
 * French and in English with its parameters, leave no {{placeholder}} behind, use the same parameters in
 * both languages, and be seeded identically in the database catalog (changeset i18n-005) and in the packs.
 */
class GofpLocalizedMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    private static final Pattern NEW_KEY = Pattern.compile(
            "^notify\\.gofp\\.(delivery|relay|relay_status|chat|route|announcement|evaluation)\\..+|^notify\\.gofp\\.password_reset$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void everyNewMessageRendersInBothLanguagesWithItsParametersAndNoLeftover() throws IOException {
        Map<String, String> fr = pack("fr_CM");
        Map<String, String> en = pack("en_CM");
        Set<String> keys = new TreeSet<>();
        fr.keySet().stream().filter(k -> NEW_KEY.matcher(k).matches()).forEach(keys::add);
        assertThat(keys).as("the new messages are present").hasSizeGreaterThanOrEqualTo(27);

        TranslationService service = new TranslationService(new JsonLocalePackAdapter(MAPPER, "fr_CM", "en_CM"));
        for (String key : keys) {
            assertThat(en).as("English text of %s", key).containsKey(key);
            Set<String> frParams = placeholders(fr.get(key));
            assertThat(placeholders(en.get(key))).as("same parameters in both languages for %s", key)
                    .isEqualTo(frParams);

            Map<String, Object> params = new HashMap<>();
            frParams.forEach(p -> params.put(p, "<" + p + ">"));
            String french = service.translate(key, "fr_CM", params).orElseThrow();
            String english = service.translate(key, "en_CM", params).orElseThrow();

            assertThat(french).as("French rendering of %s", key).doesNotContain("{{");
            assertThat(english).as("English rendering of %s", key).doesNotContain("{{");
            frParams.forEach(p -> {
                assertThat(french).as("%s uses %s in French", key, p).contains("<" + p + ">");
                assertThat(english).as("%s uses %s in English", key, p).contains("<" + p + ">");
            });
            assertThat(french).as("%s is really translated", key).isNotEqualTo(english);
        }
    }

    @Test
    void theCatalogSeedAndThePacksCarryTheSameMessages() throws IOException {
        String sql;
        try (InputStream in = getClass().getResourceAsStream(
                "/db/changelog/changes/i18n-005-seed-gofp-localized-notifications.sql")) {
            assertThat(in).as("changeset i18n-005 is on the classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher row = Pattern.compile(
                "VALUES \\(gen_random_uuid\\(\\), 'notify', '([^']+)', '(fr|en)', 'DEFAULT', \\$i18n\\$(.*?)\\$i18n\\$",
                Pattern.DOTALL).matcher(sql);
        Map<String, String> seeded = new TreeMap<>();
        while (row.find()) {
            seeded.put(row.group(1) + "|" + row.group(2), row.group(3));
        }
        // Lot L8 (spec §2.3): changeset i18n-006 renames notify.gofp.delivery.at_relay_point -> .at_relay_hub
        // (and the two *.mission_assigned keys); the catalog is the result of both changesets.
        String renames;
        try (InputStream in = getClass().getResourceAsStream(
                "/db/changelog/changes/i18n-006-rename-delivery-vocabulary-keys.sql")) {
            assertThat(in).as("changeset i18n-006 is on the classpath").isNotNull();
            renames = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        seeded.keySet().removeIf(keyLang -> keyLang.startsWith("notify.gofp.delivery.at_relay_point|"));
        Matcher renamed = Pattern.compile(
                "VALUES \\(gen_random_uuid\\(\\), 'notify', '([^']+)', '(fr|en)', 'DEFAULT', \\$i18n\\$(.*?)\\$i18n\\$",
                Pattern.DOTALL).matcher(renames);
        while (renamed.find()) {
            seeded.put(renamed.group(1) + "|" + renamed.group(2), renamed.group(3));
        }
        assertThat(seeded).containsKeys("notify.gofp.delivery.at_relay_hub|fr", "notify.gofp.delivery.at_relay_hub|en",
                "notify.freelancer_org.delivery_assigned|fr", "notify.sub_deliverer.delivery_assigned|en");
        Map<String, String> fr = pack("fr_CM");
        Map<String, String> en = pack("en_CM");
        assertThat(seeded).isNotEmpty();
        seeded.forEach((keyLang, text) -> {
            String[] parts = keyLang.split("\\|");
            Map<String, String> pack = "fr".equals(parts[1]) ? fr : en;
            assertThat(pack.get(parts[0])).as("pack text of %s", keyLang).isEqualTo(text);
        });
        // the account OTP text is gone: OTP belongs to the Kernel
        assertThat(fr).doesNotContainKey("account.otp.sent");
        assertThat(sql).contains("DELETE FROM i18n.message").contains("account.otp.sent");
    }

    @Test
    void theRelayRevocationReasonIsSeededLikeThePacks() throws IOException {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/changelog/changes/gdd-l4-i18n.sql")) {
            assertThat(in).as("changeset gdd-l4-419 is on the classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher row = Pattern.compile(
                "VALUES \\(gen_random_uuid\\(\\), 'notify', '([^']+)', '(fr|en)', 'DEFAULT', \\$i18n\\$(.*?)\\$i18n\\$",
                Pattern.DOTALL).matcher(sql);
        Map<String, String> seeded = new TreeMap<>();
        while (row.find()) {
            seeded.put(row.group(1) + "|" + row.group(2), row.group(3));
        }
        String key = "notify.gofp.relay_status.revoked_reason";
        assertThat(seeded).containsOnlyKeys(key + "|fr", key + "|en");
        for (String locale : List.of("fr_CM", "fr_FR")) {
            assertThat(pack(locale).get(key)).as(locale).isEqualTo(seeded.get(key + "|fr"));
        }
        for (String locale : List.of("en_CM", "en_NG", "pidgin_CM")) {
            assertThat(pack(locale).get(key)).as(locale).isEqualTo(seeded.get(key + "|en"));
        }
        TranslationService service = new TranslationService(new JsonLocalePackAdapter(MAPPER, "fr_CM", "en_CM"));
        assertThat(service.translate(key, "fr_CM", Map.of("reason", "Fraude")).orElseThrow())
                .endsWith("révoqué définitivement : Fraude");
        assertThat(service.translate(key, "en_CM", Map.of("reason", "Fraud")).orElseThrow())
                .endsWith("permanently revoked: Fraud");
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    private static Map<String, String> pack(String locale) throws IOException {
        try (InputStream in = TranslationService.class.getResourceAsStream("/i18n/messages_" + locale + ".json")) {
            assertThat(in).as("pack %s", locale).isNotNull();
            return MAPPER.readValue(in, new TypeReference<TreeMap<String, String>>() { });
        }
    }
}
