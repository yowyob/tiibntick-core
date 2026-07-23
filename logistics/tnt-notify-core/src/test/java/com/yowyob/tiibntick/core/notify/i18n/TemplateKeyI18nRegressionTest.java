package com.yowyob.tiibntick.core.notify.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-regression test (Audit n4, P0 item2 — covers P-1/P-3): scans the codebase for i18n
 * notification {@code templateKey} literals and verifies each one exists in every one of the 5
 * {@code yow-i18n-kernel} language packs ({@code messages_fr_CM.json}, {@code messages_en_CM.json},
 * {@code messages_pidgin_CM.json}, {@code messages_en_NG.json}, {@code messages_fr_FR.json}).
 *
 * <h3>Why this is a "known-missing baseline" test, not a hard "everything must pass" test</h3>
 * As of this test's introduction, a from-scratch scan already finds a real, pre-existing gap:
 * 12 {@code notify.*}/{@code notification.*} dotted keys (FreelancerOrg notifications, Chantier F
 * P-1) plus 8 upper-snake-case market keys (Chantier F P-21) referenced in production code but
 * absent from the packs — exactly the bug Audit n4 documents. Fixing all of that is Chantier F's
 * job, not this Chantier E "add the safety net" item. So, like {@code LayeringArchitectureTest}'s
 * use of ArchUnit's {@code FreezingArchRule}, this test freezes today's known-missing keys in
 * {@code known-missing-template-keys.txt} (checked into git): the suite stays green today, but
 * introducing ANY new template key that isn't backed by a translation in all 5 packs fails the
 * build immediately. When Chantier F adds a missing key's translations, delete its line from the
 * baseline file — if you forget, this test will simply keep passing (baseline is a superset check,
 * not exact-match) until you tidy it up.
 *
 * <h3>Scanner scope &amp; limitations (documented, not silently hidden)</h3>
 * <ul>
 *   <li>Only scans {@code src/main/java} (not tests, not {@code target/}) across the whole reactor.</li>
 *   <li>Recognizes literal dotted keys whose first segment is a namespace actually used by the
 *       packs ({@code account, billing, blockchain, error, notification, status}) or by the
 *       existing {@code notify.*} convention used by {@code FreelancerOrgNotificationTemplates}.</li>
 *   <li>Also recognizes the upper-snake-case literal keys in {@code MarketNotificationAdapter}
 *       (a different, non-pack-matching convention — itself the Audit n4 P-21 finding).</li>
 *   <li>Does NOT resolve dynamically-built keys, e.g. {@code IncidentNotificationPortAdapter}'s
 *       {@code "incident.notification." + type.toLowerCase()...}: a static text scanner cannot
 *       enumerate every possible enum value that feeds a string concatenation. That specific gap
 *       is separately called out in Audit n4 and is not covered by this scanner.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
class TemplateKeyI18nRegressionTest {

    /** Locale tags of the 5 packs shipped by yow-i18n-kernel (see JsonLocalePackAdapter). */
    private static final String[] PACK_LOCALES = {"fr_CM", "en_CM", "pidgin_CM", "en_NG", "fr_FR"};

    /** First-segment namespaces recognized as real template-key literals (not Java packages, etc). */
    private static final Set<String> KEY_NAMESPACES = Set.of(
            "account", "billing", "blockchain", "error", "notification", "status", "notify");

    private static final Pattern DOTTED_KEY_PATTERN =
            Pattern.compile("\"([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*){1,})\"");

    /**
     * MarketNotificationAdapter uses upper-snake-case keys — a convention that never matches the
     * packs' dotted-lowercase format (Audit n4 P-21). Scanned from this one explicitly-named file
     * rather than reactor-wide, since a blind "any uppercase literal" regex would be far too noisy.
     */
    private static final Path MARKET_NOTIFICATION_ADAPTER_RELATIVE_PATH = Path.of(
            "coreBackend/tnt-market-back-core/src/main/java/com/yowyob/tiibntick/core/marketback/"
                    + "adapter/out/messaging/MarketNotificationAdapter.java");
    private static final Pattern UPPER_SNAKE_KEY_PATTERN = Pattern.compile("\"([A-Z][A-Z0-9_]*)\"");

    @Test
    void everyReferencedTemplateKeyExistsInEveryLanguagePack() throws IOException {
        Path repoRoot = findRepoRoot();

        Set<String> referencedKeys = new TreeSet<>();
        referencedKeys.addAll(scanDottedKeys(repoRoot));
        referencedKeys.addAll(scanMarketNotificationAdapterKeys(repoRoot));

        assertThat(referencedKeys)
                .as("sanity check: the scanner itself found nothing — it's probably broken")
                .isNotEmpty();

        Map<String, Map<String, String>> packs = loadLanguagePacks();

        Set<String> missingSomewhere = new TreeSet<>();
        for (String key : referencedKeys) {
            for (Map.Entry<String, Map<String, String>> pack : packs.entrySet()) {
                if (!pack.getValue().containsKey(key)) {
                    missingSomewhere.add(key);
                }
            }
        }

        Set<String> knownMissingBaseline = loadKnownMissingBaseline();
        Set<String> newRegressions = new TreeSet<>(missingSomewhere);
        newRegressions.removeAll(knownMissingBaseline);

        assertThat(newRegressions)
                .as("New templateKey(s) referenced in production code without a translation in "
                        + "every language pack (fr_CM/en_CM/pidgin_CM/en_NG/fr_FR). Either add the "
                        + "missing translations to all 5 packs, or — if this is pre-existing "
                        + "Chantier F debt you're deliberately not fixing right now — add the key "
                        + "to src/test/resources/i18n/known-missing-template-keys.txt")
                .isEmpty();
    }

    // ── Scanning ─────────────────────────────────────────────────────────────────────────────

    private static Set<String> scanDottedKeys(Path repoRoot) throws IOException {
        Set<String> keys = new TreeSet<>();
        forEachMainJavaFile(repoRoot, content -> {
            Matcher matcher = DOTTED_KEY_PATTERN.matcher(content);
            while (matcher.find()) {
                String key = matcher.group(1);
                String namespace = key.substring(0, key.indexOf('.'));
                if (KEY_NAMESPACES.contains(namespace)) {
                    keys.add(key);
                }
            }
        });
        return keys;
    }

    private static Set<String> scanMarketNotificationAdapterKeys(Path repoRoot) throws IOException {
        Path file = repoRoot.resolve(MARKET_NOTIFICATION_ADAPTER_RELATIVE_PATH);
        if (!Files.exists(file)) {
            // File moved/renamed — fail loudly rather than silently scanning nothing.
            throw new IllegalStateException(
                    "MarketNotificationAdapter.java not found at expected path: " + file
                            + " — update TemplateKeyI18nRegressionTest.MARKET_NOTIFICATION_ADAPTER_RELATIVE_PATH");
        }
        String content = Files.readString(file);
        Set<String> keys = new TreeSet<>();
        Matcher matcher = UPPER_SNAKE_KEY_PATTERN.matcher(content);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static void forEachMainJavaFile(Path repoRoot, java.util.function.Consumer<String> consumer)
            throws IOException {
        try (Stream<Path> paths = Files.walk(repoRoot)) {
            paths.filter(TemplateKeyI18nRegressionTest::isMainJavaSourceFile)
                    .forEach(path -> {
                        try {
                            consumer.accept(Files.readString(path));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    private static boolean isMainJavaSourceFile(Path path) {
        if (!Files.isRegularFile(path) || !path.toString().endsWith(".java")) {
            return false;
        }
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/src/main/java/")
                && !normalized.contains("/target/")
                && !normalized.contains("/.git/");
    }

    // ── Repo-root resolution ─────────────────────────────────────────────────────────────────

    /**
     * Walks up from the current working directory (the module's basedir when Maven/surefire runs
     * this test) looking for the root {@code pom.xml}. Every child module's pom also mentions
     * {@code tiibntick-core-parent} (in its own {@code <parent>} block), so that alone isn't a
     * reliable marker — instead look for the {@code <module>foundation/yow-event-kernel</module>}
     * declaration, which only exists in the true reactor root.
     */
    private static Path findRepoRoot() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 10 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("pom.xml");
            if (Files.isRegularFile(candidate)
                    && Files.readString(candidate).contains("<module>foundation/yow-event-kernel</module>")) {
                return dir;
            }
        }
        throw new IllegalStateException(
                "Could not locate the reactor root pom.xml by walking up from "
                        + Path.of("").toAbsolutePath());
    }

    // ── Language packs ───────────────────────────────────────────────────────────────────────

    private static Map<String, Map<String, String>> loadLanguagePacks() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Map<String, String>> packs = new TreeMap<>();
        for (String locale : PACK_LOCALES) {
            String resource = "/i18n/messages_" + locale + ".json";
            try (InputStream is = TemplateKeyI18nRegressionTest.class.getResourceAsStream(resource)) {
                assertThat(is)
                        .as("Language pack missing from classpath: %s (expected on yow-i18n-kernel)",
                                resource)
                        .isNotNull();
                Map<String, String> dictionary = mapper.readValue(is, new TypeReference<>() {});
                packs.put(locale, dictionary);
            }
        }
        return packs;
    }

    // ── Known-missing baseline ───────────────────────────────────────────────────────────────

    private static Set<String> loadKnownMissingBaseline() throws IOException {
        try (InputStream is = TemplateKeyI18nRegressionTest.class
                .getResourceAsStream("/i18n/known-missing-template-keys.txt")) {
            if (is == null) {
                return Set.of();
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
