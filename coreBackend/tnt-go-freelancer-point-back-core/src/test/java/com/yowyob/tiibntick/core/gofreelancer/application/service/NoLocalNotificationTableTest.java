package com.yowyob.tiibntick.core.gofreelancer.application.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guard (lot L4): the module keeps no notification table of its own. Notifications are sent and kept by
 * tnt-notify-core; the legacy tables {@code notifications}, {@code notification_annonces} and
 * {@code notification_livraisons} are not mapped, read or written by any class (the tables themselves are dropped by
 * the closing changeset).
 */
class NoLocalNotificationTableTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Pattern LEGACY_TABLE_MAPPING =
            Pattern.compile("@Table\\(\\s*\"(notifications|notification_annonces|notification_livraisons)\"");
    private static final Pattern LEGACY_SQL =
            Pattern.compile("(?i)(from|into|update|join)\\s+(notifications|notification_annonces|notification_livraisons)\\b");

    @Test
    void noClassMapsOrQueriesALegacyNotificationTable() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(MAIN)) {
            offenders = files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> {
                        try {
                            String src = Files.readString(f);
                            return LEGACY_TABLE_MAPPING.matcher(src).find() || LEGACY_SQL.matcher(src).find();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(Path::toString).toList();
        }
        assertThat(offenders).isEmpty();
    }
}
