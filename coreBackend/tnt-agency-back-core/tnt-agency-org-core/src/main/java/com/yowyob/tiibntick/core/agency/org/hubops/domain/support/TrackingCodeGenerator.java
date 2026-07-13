package com.yowyob.tiibntick.core.agency.org.hubops.domain.support;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/** Generates human-readable parcel tracking codes ({@code TRK-YYYYMMDD-XXXX}). */
public final class TrackingCodeGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private TrackingCodeGenerator() {}

    public static String generate() {
        String date = LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "TRK-" + date + "-" + suffix;
    }
}
