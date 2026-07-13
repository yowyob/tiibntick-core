package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.List;

/**
 * Value Object — SEO metadata attached to a MarketListing.
 * @author MANFOUO Braun
 */
public record SeoMetadata(
        String metaTitle,
        String metaDescription,
        List<String> keywords,
        String canonicalUrl,
        String structuredDataJson
) {
    public boolean isIndexable() {
        return metaTitle != null && !metaTitle.isBlank()
                && metaDescription != null && !metaDescription.isBlank();
    }
}
