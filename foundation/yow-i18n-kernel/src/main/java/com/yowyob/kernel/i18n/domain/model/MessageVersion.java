package com.yowyob.kernel.i18n.domain.model;

import java.time.Instant;

/** One published revision of a message, kept so a bad edit can be compared and restored. */
public record MessageVersion(int version, String text, Instant publishedAt, String publishedBy) {
}
