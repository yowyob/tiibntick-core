package com.yowyob.tiibntick.common.kafka;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for {@link TntTopics}, the single frozen Kafka topic referential introduced
 * by Audit n°5 · P-03/P-12.
 *
 * <p>The whole point of centralizing every topic name in one class is defeated if the class
 * itself accidentally assigns the same literal to two different constants (a copy-paste typo
 * that would silently re-introduce the exact kind of producer/consumer mismatch this
 * referential exists to prevent) — this test fails loudly if that ever happens.
 *
 * @author MANFOUO Braun
 */
class TntTopicsTest {

    @Test
    void cannotBeInstantiated() throws Exception {
        var constructor = TntTopics.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
        } catch (InvocationTargetException e) {
            throw new AssertionError("Private constructor should not throw", e.getCause());
        }
    }

    @Test
    void everyTopicConstantIsNonBlankLowercaseAndUnique() throws IllegalAccessException {
        Map<String, String> valueToFieldName = new HashMap<>();
        List<String> duplicates = new ArrayList<>();

        for (Field field : TntTopics.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            field.setAccessible(true);
            String value = (String) field.get(null);

            assertThat(value)
                    .as("constant %s must not be blank", field.getName())
                    .isNotBlank();
            assertThat(value)
                    .as("constant %s (%s) must be lowercase — Kafka topic names are case-sensitive"
                            + " and the whole point of this referential is one canonical spelling",
                            field.getName(), value)
                    .isEqualTo(value.toLowerCase());
            assertThat(value)
                    .as("constant %s (%s) must start with a recognised prefix (tnt./yow./gofp.)",
                            field.getName(), value)
                    .matches("^(tnt\\.|yow\\.|gofp\\.).+");

            String previousOwner = valueToFieldName.putIfAbsent(value, field.getName());
            if (previousOwner != null) {
                duplicates.add(value + " assigned to both " + previousOwner + " and " + field.getName());
            }
        }

        assertThat(duplicates)
                .as("Two constants must never share the same topic literal — that reintroduces "
                        + "exactly the kind of naming ambiguity this referential exists to prevent")
                .isEmpty();
        assertThat(valueToFieldName).hasSizeGreaterThan(50);
    }
}
