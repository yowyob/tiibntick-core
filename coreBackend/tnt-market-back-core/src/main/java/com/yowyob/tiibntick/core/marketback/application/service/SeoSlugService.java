package com.yowyob.tiibntick.core.marketback.application.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Generates SEO-friendly URL slugs from display names.
 *
 * <p>Ported verbatim from {@code tiibntick-market-backend}'s
 * {@code application.service.SeoSlugService}.</p>
 *
 * @author MANFOUO Braun
 */
@Service
public class SeoSlugService {

    private static final Pattern NON_ALPHANUM = Pattern.compile("[^a-z0-9]+");

    /**
     * Generates a unique slug from a display name.
     * Example: "Mon Agence Rapide!" to "mon-agence-rapide-3a1b"
     */
    public String generate(String displayName) {
        String normalized = Normalizer.normalize(displayName.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = NON_ALPHANUM.matcher(normalized).replaceAll("-")
                .replaceAll("^-+|-+$", "");
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        return slug + "-" + suffix;
    }
}
