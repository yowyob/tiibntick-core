package com.yowyob.tiibntick.core.agency.intake.domain;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public final class IntakeReferenceGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private IntakeReferenceGenerator() {}

    public static String generate() {
        String date = LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "REF-" + date + "-" + suffix;
    }
}
