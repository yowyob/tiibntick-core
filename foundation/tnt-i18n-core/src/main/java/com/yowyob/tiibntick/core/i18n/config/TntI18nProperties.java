package com.yowyob.tiibntick.core.i18n.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "tnt.i18n")
public class TntI18nProperties {

    /**
     * Namespaces a browser/mobile client may download without authentication. Anything else (notably
     * {@code notify}, {@code error}, {@code doc}) stays server-side.
     */
    private List<String> publicNamespaces = List.of("common", "go", "link", "agency", "point", "market", "ref");

    /** How long an account's language is cached in memory (another instance may change it). */
    private Duration preferenceCacheTtl = Duration.ofSeconds(60);

    /** Browser cache lifetime of a bundle; the ETag makes revalidation cheap after it. */
    private Duration bundleMaxAge = Duration.ofMinutes(5);

    public List<String> getPublicNamespaces() { return publicNamespaces; }
    public void setPublicNamespaces(List<String> publicNamespaces) { this.publicNamespaces = publicNamespaces; }
    public Duration getPreferenceCacheTtl() { return preferenceCacheTtl; }
    public void setPreferenceCacheTtl(Duration preferenceCacheTtl) { this.preferenceCacheTtl = preferenceCacheTtl; }
    public Duration getBundleMaxAge() { return bundleMaxAge; }
    public void setBundleMaxAge(Duration bundleMaxAge) { this.bundleMaxAge = bundleMaxAge; }
}
