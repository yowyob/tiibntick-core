package com.yowyob.kernel.i18n.domain.model;

import java.util.Set;

/**
 * Channel flavours of one message. {@link #DEFAULT} is the plain text every consumer can use; a
 * channel variant (a 160-character SMS, a push title, an e-mail subject and body) is optional and
 * resolution falls back to {@link #DEFAULT} in the same language when it is missing.
 */
public final class MessageVariant {

    public static final String DEFAULT = "DEFAULT";
    public static final String IN_APP = "IN_APP";
    public static final String PUSH = "PUSH";
    public static final String SMS = "SMS";
    public static final String EMAIL_SUBJECT = "EMAIL_SUBJECT";
    public static final String EMAIL_BODY = "EMAIL_BODY";

    private static final Set<String> ALL = Set.of(DEFAULT, IN_APP, PUSH, SMS, EMAIL_SUBJECT, EMAIL_BODY);

    private MessageVariant() {
    }

    public static boolean isValid(String variant) {
        return variant != null && ALL.contains(variant);
    }

    /** {@code null}/blank → {@link #DEFAULT}; otherwise the upper-cased value. */
    public static String normalize(String variant) {
        return variant == null || variant.isBlank() ? DEFAULT : variant.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
