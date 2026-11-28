package com.yowyob.tiibntick.core.gofreelancer;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L1 acceptance guards (architecture-cible 4.1): the gofp delivery row cannot write the delivery's own
 * facts, no gofp source reads or writes the legacy {@code packets} table with FROM / INTO / UPDATE, and the
 * private {@code DeliveryUrgency} copy is gone.
 */
class GofpLot1GuardTest {

    private static final Path MAIN = Path.of("src/main/java/com/yowyob/tiibntick/core/gofreelancer");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    @DisplayName("the gofp Delivery has no setter for status, courier, price, windows or real instants")
    void noCoreFactSetter() {
        List<String> forbidden = List.of("setStatus", "setFreelancerId", "setTarif", "setPickupMinTime",
                "setPickupMaxTime", "setDeliveryMinTime", "setDeliveryMaxTime", "setActualPickupTime",
                "setActualDeliveryTime");
        List<String> setters = Arrays.stream(Delivery.class.getMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName).toList();
        assertThat(setters).doesNotContainAnyElementsOf(forbidden);
    }

    @Test
    @DisplayName("no FROM / INTO / UPDATE packets in the gofp sources")
    void noPacketsTableAccess() throws IOException {
        Pattern sql = Pattern.compile("(?i)\\b(FROM|INTO|UPDATE)\\s+packets\\b");
        List<Path> offenders = sources().stream().filter(p -> {
            try {
                return sql.matcher(Files.readString(p)).find();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }).toList();
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("no private DeliveryUrgency enum in gofp: the core's is used")
    void noPrivateUrgency() throws IOException {
        assertThat(sources()).noneMatch(p -> p.getFileName().toString().equals("DeliveryUrgency.java"));
    }
}
