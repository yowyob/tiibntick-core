package com.yowyob.tiibntick.core.notify.i18n;

import com.yowyob.tiibntick.core.notify.domain.model.FreelancerOrgNotificationTemplates;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every notification scenario the code can send must exist in the database seed, in French AND English, so
 * a recipient never gets a generic fallback or another language's text. Reads the real seed files shipped by
 * yow-i18n-kernel.
 */
class NotificationScenarioCoverageTest {

    private static final Pattern ROW = Pattern.compile(
            "VALUES \\(gen_random_uuid\\(\\), '([a-z]+)', '([^']+)', '([a-z]+)', '([A-Z_]+)'");

    private static Set<String> seeded() throws IOException {
        Set<String> rows = new HashSet<>();
        for (String file : List.of("i18n-002-seed-existing-messages.sql", "i18n-003-seed-notification-scenarios.sql", "i18n-004-seed-profile-welcome.sql")) {
            try (InputStream in = NotificationScenarioCoverageTest.class.getClassLoader()
                    .getResourceAsStream("db/changelog/changes/" + file)) {
                assertThat(in).as(file).isNotNull();
                Matcher m = ROW.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                while (m.find()) {
                    rows.add(m.group(1) + "|" + m.group(2) + "|" + m.group(3) + "|" + m.group(4));
                }
            }
        }
        return rows;
    }

    private static List<String> constants(Class<?> holder) throws IllegalAccessException {
        List<String> keys = new ArrayList<>();
        for (Field f : holder.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class && f.getName().equals(f.getName().toUpperCase())) {
                keys.add((String) f.get(null));
            }
        }
        return keys;
    }

    private static void assertBothLanguages(Set<String> seeded, List<String> keys) {
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            for (String lang : List.of("fr", "en")) {
                if (!seeded.contains("notify|" + key + "|" + lang + "|DEFAULT")) {
                    missing.add(key + " [" + lang + "]");
                }
            }
        }
        assertThat(missing).as("scenarios without a seeded text").isEmpty();
    }

    @Test
    void freelancerOrgAndSubDelivererScenariosAreSeededInBothLanguages() throws Exception {
        assertBothLanguages(seeded(), constants(FreelancerOrgNotificationTemplates.class));
    }

    @Test
    void incidentScenariosAreSeededInBothLanguages() throws Exception {
        // type strings sent by tnt-incident-core services -> "incident.notification." + lower.dotted
        List<String> types = List.of("INCIDENT_CREATED", "INCIDENT_ESCALATED", "AGENCY_HANDLING_STARTED",
                "HANDOVER_CONFIRMED", "DRIVER_MISSION_ASSIGNED", "INCIDENT_DRIVER_PROPOSAL", "INCIDENT_AUTO_FAILED",
                "INTERAGENCY_COOP_REQUESTED", "INTERAGENCY_COOP_ACCEPTED", "INTERAGENCY_COOP_REJECTED",
                "INTERAGENCY_COOP_UPDATED");
        assertBothLanguages(seeded(), types.stream()
                .map(t -> "incident.notification." + t.toLowerCase().replace("_", ".")).toList());
    }

    @Test
    void marketAndAccountScenariosAreSeededInBothLanguages() throws Exception {
        assertBothLanguages(seeded(), List.of("CLIENT_QUOTE_RECEIVED", "PROVIDER_NEW_QUOTE_REQUEST", "ORDER_CONFIRMED",
                "ORDER_PAID", "ORDER_COMPLETED", "REVIEW_PUBLISHED", "LISTING_APPROVED", "LISTING_REJECTED",
                "notify.agency.access_credentials", "account.welcome", "account.otp.sent"));
    }

    @Test
    void profileWelcomeIsSeededOnScreenAndByEmailInBothLanguages() throws Exception {
        Set<String> seeded = seeded();
        List<String> missing = new ArrayList<>();
        for (String kind : List.of("client", "deliverer", "freelancer", "relay_operator")) {
            for (String lang : List.of("fr", "en")) {
                for (String row : List.of("notify.profile.welcome." + kind + "|" + lang + "|DEFAULT",
                        "notify.profile.created." + kind + "|" + lang + "|EMAIL_SUBJECT",
                        "notify.profile.created." + kind + "|" + lang + "|EMAIL_BODY")) {
                    if (!seeded.contains("notify|" + row)) {
                        missing.add(row);
                    }
                }
            }
        }
        assertThat(missing).isEmpty();
    }

    @Test
    void everyFlavourExistsInBothLanguagesOrInNeither() throws Exception {
        Set<String> seeded = seeded();
        List<String> lopsided = new ArrayList<>();
        for (String row : seeded) {
            String[] p = row.split("\\|");
            String other = p[0] + "|" + p[1] + "|" + ("fr".equals(p[2]) ? "en" : "fr") + "|" + p[3];
            if (!seeded.contains(other)) {
                lopsided.add(row);
            }
        }
        assertThat(lopsided).as("seed rows that exist in only one language").isEmpty();
    }
}
