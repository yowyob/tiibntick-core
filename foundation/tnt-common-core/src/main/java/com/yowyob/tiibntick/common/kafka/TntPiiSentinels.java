package com.yowyob.tiibntick.common.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sentinel values for the personal-data leak tests required by spec §3.4: every lot fills a snapshot source
 * with {@code "PII-<field>"} values (and the exact coordinates below), serialises the event it publishes and
 * fails if {@link #leaks(String)} finds anything.
 */
public final class TntPiiSentinels {

    /** Prefix of every text sentinel. */
    public static final String PREFIX = "PII-";
    /** Exact latitude/longitude (7 decimals): must never appear unrounded in a published record. */
    public static final double EXACT_LATITUDE = 3.8666781;
    public static final double EXACT_LONGITUDE = 11.5166742;

    private static final Pattern SENTINEL = Pattern.compile(Pattern.quote(PREFIX) + "[A-Za-z0-9_.\\-]*");

    private TntPiiSentinels() {
    }

    /** @return the sentinel of {@code field}, e.g. {@code "PII-recipient.phone"} */
    public static String of(final String field) {
        return PREFIX + field;
    }

    /**
     * @param publishedJson a serialised record
     * @return every sentinel and exact coordinate found in it (empty when nothing leaks)
     */
    public static List<String> leaks(final String publishedJson) {
        List<String> found = new ArrayList<>();
        if (publishedJson == null) {
            return found;
        }
        Matcher matcher = SENTINEL.matcher(publishedJson);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        for (double exact : new double[] {EXACT_LATITUDE, EXACT_LONGITUDE}) {
            String text = Double.toString(exact);
            if (publishedJson.contains(text.substring(0, text.indexOf('.') + 4))) {
                found.add(text);
            }
        }
        return found;
    }
}
