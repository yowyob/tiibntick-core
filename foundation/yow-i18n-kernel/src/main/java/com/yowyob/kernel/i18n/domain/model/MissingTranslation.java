package com.yowyob.kernel.i18n.domain.model;

/** A key that exists in {@code presentIn} but has no live text in {@code missingLanguage}. */
public record MissingTranslation(String namespace, String key, String variant, String missingLanguage) {
}
