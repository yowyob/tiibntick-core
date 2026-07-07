package com.yowyob.kernel.i18n.adapter.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.i18n.application.port.out.MessageTranslationPort;
import com.yowyob.kernel.i18n.domain.vo.LocalizedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter that loads localized messages from JSON files on the classpath.
 * Files must be placed at /i18n/messages_{locale}.json within the jar.
 * <p>
 * Supported locale tags: fr_CM, en_CM, pidgin_CM, en_NG, fr_FR, en_US.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public class JsonLocalePackAdapter implements MessageTranslationPort {

    private static final Logger log = LoggerFactory.getLogger(JsonLocalePackAdapter.class);

    private static final String I18N_RESOURCE_PATH = "/i18n/messages_%s.json";

    /** In-memory cache: locale tag -> (messageKey -> translatedValue). */
    private final Map<String, Map<String, String>> inMemoryTranslations = new HashMap<>();

    /**
     * Constructs the adapter and eagerly loads all specified locale packs.
     *
     * @param objectMapper Jackson mapper for JSON deserialization
     * @param languages      array of locale tags to load (e.g., "fr_CM", "en_CM")
     */
    public JsonLocalePackAdapter(ObjectMapper objectMapper, String... languages) {
        for (String language : languages) {
            loadLanguagePack(objectMapper, language);
        }
        log.info("i18n packs loaded for locales: {}", (Object) languages);
    }

    private void loadLanguagePack(ObjectMapper objectMapper, String language) {
        String fileName = String.format(I18N_RESOURCE_PATH, language);
        try (InputStream is = getClass().getResourceAsStream(fileName)) {
            if (is != null) {
                Map<String, String> dictionary = objectMapper.readValue(is, new TypeReference<>() {});
                inMemoryTranslations.put(language, Collections.unmodifiableMap(dictionary));
                log.debug("Loaded {} keys for locale '{}'", dictionary.size(), language);
            } else {
                log.warn("i18n file not found on classpath: {}", fileName);
            }
        } catch (Exception e) {
            log.error("Failed to load i18n pack for locale '{}': {}", language, e.getMessage(), e);
        }
    }

    @Override
    public Optional<LocalizedMessage> findByKeyAndLanguage(String key, String language) {
        Map<String, String> dictionary = inMemoryTranslations.get(language);
        if (dictionary != null && dictionary.containsKey(key)) {
            return Optional.of(new LocalizedMessage(key, language, dictionary.get(key)));
        }
        // Attempt fallback: if lang is "fr_CM" and not found, try "fr_FR"
        return Optional.empty();
    }

    /**
     * Returns the number of loaded locale packs (useful for health checks).
     */
    public int numberOfLoadedLocales() {
        return inMemoryTranslations.size();
    }
}
