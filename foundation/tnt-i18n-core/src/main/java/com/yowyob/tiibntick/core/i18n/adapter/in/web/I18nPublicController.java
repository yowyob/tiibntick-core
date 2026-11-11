package com.yowyob.tiibntick.core.i18n.adapter.in.web;

import com.yowyob.kernel.i18n.application.service.MessageCatalog;
import com.yowyob.kernel.i18n.domain.vo.LanguageTags;
import com.yowyob.tiibntick.core.i18n.config.TntI18nProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Map;

/**
 * What every TiiBnTick frontend downloads to display its screens: the list of languages and the texts of
 * the requested namespaces. Public (no token) because the login page itself must be translated, and
 * limited to the namespaces allowed by {@code tnt.i18n.public-namespaces}.
 */
@RestController
@RequestMapping("/api/v1/i18n")
@Tag(name = "i18n", description = "Languages and UI message bundles shared by every TiiBnTick platform")
public class I18nPublicController {

    public record LanguageInfo(String code, String nativeName) {}

    public record LanguagesResponse(String defaultLanguage, List<LanguageInfo> languages) {}

    public record BundleResponse(String language, String revision, Map<String, Map<String, String>> namespaces) {}

    private final MessageCatalog catalog;
    private final TntI18nProperties properties;

    public I18nPublicController(MessageCatalog catalog, TntI18nProperties properties) {
        this.catalog = catalog;
        this.properties = properties;
    }

    @GetMapping("/languages")
    @Operation(summary = "Languages the platform speaks")
    public LanguagesResponse languages() {
        return new LanguagesResponse(LanguageTags.DEFAULT, List.of(
                new LanguageInfo(LanguageTags.FRENCH, "Français"),
                new LanguageInfo(LanguageTags.ENGLISH, "English")));
    }

    /**
     * @param ns comma-separated namespaces; defaults to every public one. A namespace that is not public
     *           is rejected (400) rather than silently dropped, so a typo is visible to the developer.
     */
    @GetMapping("/bundles/{language}")
    @Operation(summary = "UI texts of a language, with ETag revalidation")
    public ResponseEntity<BundleResponse> bundle(@PathVariable String language,
                                                 @RequestParam(name = "ns", required = false) List<String> ns,
                                                 ServerWebExchange exchange) {
        String code = LanguageTags.toPublicCode(language);
        if (code == null) {
            throw new IllegalArgumentException("unsupported language '" + language + "' (expected "
                    + LanguageTags.supported() + ")");
        }
        List<String> wanted = ns == null || ns.isEmpty() ? properties.getPublicNamespaces() : ns;
        for (String n : wanted) {
            if (!properties.getPublicNamespaces().contains(n)) {
                throw new IllegalArgumentException("namespace '" + n + "' is not public");
            }
        }
        String etag = "\"" + Integer.toHexString((catalog.revision() + '|' + code + '|' + wanted).hashCode()) + "\"";
        CacheControl cache = CacheControl.maxAge(properties.getBundleMaxAge()).cachePublic().mustRevalidate();
        if (exchange.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(cache)
                .body(new BundleResponse(code, catalog.revision(), catalog.bundle(code, wanted)));
    }
}
