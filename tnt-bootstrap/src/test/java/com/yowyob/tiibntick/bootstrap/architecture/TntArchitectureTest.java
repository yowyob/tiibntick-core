package com.yowyob.tiibntick.bootstrap.architecture;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Kafka listener guardrails of the aggregate-events workstream — decision [G.8] of 2026-11-26, lot L0
 * ({@code docs/architecture/chantiers/cible-evenements-colis-livraison.md} §3.10 point 3).
 *
 * <p>A {@code @KafkaListener} (or {@code @KafkaHandler}) must wait for the end of its work
 * ({@code ReactiveListenerSupport}) so that a failure reaches the container, which retries the record and then
 * publishes it to {@code tnt.dlq}. Forbidden, in the listener method or in any method / lambda / method reference
 * of the same class it reaches:
 * <ul>
 *   <li>{@code .subscribe()} on a Reactor/Reactive Streams publisher (fire-and-forget: the offset is committed
 *       and a later failure is lost);</li>
 *   <li>swallowing the processing error: {@code onErrorResume(e -> Mono.empty())} (a lambda taking a
 *       {@code Throwable} that returns {@code Mono.empty()}/{@code Flux.empty()}) or {@code onErrorComplete()}.</li>
 * </ul>
 *
 * <p><b>Frozen</b> ({@link FreezingArchRule}): the violations existing when lot L0 introduced the rule are the
 * baseline in {@code tnt-bootstrap/archunit_store}; any new one fails the build, every fixed one leaves the
 * baseline for good. Lots L2–L8 fix them (inventory §9.2 of the spec); L8 unfreezes the rule (0 violation).
 */
class TntArchitectureTest {

    /** Lines after an {@code onErrorResume} call where a block lambda's {@code return Mono.empty();} may sit. */
    private static final int FALLBACK_LINES = 3;

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.yowyob.tiibntick", "com.yowyob.kernel");
    }

    @Test
    void kafkaListenersMustAwaitTheirWorkAndNeverSwallowErrors() {
        ArchRule rule = FreezingArchRule.freeze(classes().should(new ArchCondition<JavaClass>(
                "not have a Kafka listener that calls subscribe() or swallows its processing error "
                        + "(onErrorResume(e -> Mono.empty()), onErrorComplete()), directly or through a method "
                        + "or lambda of the same class") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                for (JavaMethod listener : clazz.getMethods()) {
                    if (!isListener(listener)) {
                        continue;
                    }
                    Set<JavaCodeUnit> reached = reachedWithinClass(listener);
                    reached.stream().flatMap(unit -> unit.getMethodCallsFromSelf().stream())
                            .filter(TntArchitectureTest::isPublisherSubscribe)
                            .findFirst()
                            .ifPresent(call -> events.add(SimpleConditionEvent.violated(listener,
                                    listener.getFullName() + " does not await its work: it calls "
                                            + call.getTarget().getOwner().getSimpleName() + ".subscribe() in "
                                            + call.getOrigin().getName() + " " + call.getSourceCodeLocation())));
                    swallowedError(reached).ifPresent(access -> events.add(SimpleConditionEvent.violated(listener,
                            listener.getFullName() + " swallows its processing error in "
                                    + access.getOrigin().getName() + " " + access.getSourceCodeLocation())));
                }
            }
        }).because("a listener failure must reach the container: retries, then tnt.dlq (decision G.8)"));
        rule.check(classes);
    }

    private static boolean isListener(JavaMethod method) {
        return method.isAnnotatedWith(KafkaListener.class) || method.isAnnotatedWith(KafkaHandler.class);
    }

    /** The listener plus every method, lambda and method reference of its own class it reaches (transitively). */
    private static Set<JavaCodeUnit> reachedWithinClass(JavaMethod listener) {
        JavaClass owner = listener.getOwner();
        Set<JavaCodeUnit> reached = new LinkedHashSet<>();
        Deque<JavaCodeUnit> todo = new ArrayDeque<>(List.of(listener));
        while (!todo.isEmpty()) {
            JavaCodeUnit unit = todo.pop();
            if (!reached.add(unit)) {
                continue;
            }
            Stream.concat(unit.getCallsFromSelf().stream().map(call -> call.getTarget().resolveMember()),
                            unit.getMethodReferencesFromSelf().stream().map(ref -> ref.getTarget().resolveMember()))
                    .flatMap(Optional::stream)
                    .filter(target -> target.getOwner().equals(owner))
                    .forEach(todo::push);
            // lambdas are invokedynamic (no call recorded): javac names them lambda$<enclosing method>$<n>
            String lambdaPrefix = "lambda$" + unit.getName() + "$";
            owner.getCodeUnits().stream()
                    .filter(candidate -> candidate.getName().startsWith(lambdaPrefix))
                    .forEach(todo::push);
        }
        return reached;
    }

    private static boolean isPublisherSubscribe(JavaMethodCall call) {
        return "subscribe".equals(call.getName()) && call.getTargetOwner().isAssignableTo(Publisher.class);
    }

    /**
     * {@code onErrorComplete()}, or {@code onErrorResume} whose fallback is {@code Mono/Flux.empty()}. ArchUnit
     * attributes the calls made inside a lambda to the enclosing method, so the fallback is recognised as an
     * {@code empty()} call on the line of the {@code onErrorResume} call or on the {@value #FALLBACK_LINES} lines
     * after it (a block lambda); a synthetic lambda method taking a Throwable is also checked, should a version
     * import it on its own.
     */
    private static Optional<JavaAccess<?>> swallowedError(Set<JavaCodeUnit> reached) {
        for (JavaCodeUnit unit : reached) {
            for (JavaMethodCall call : unit.getMethodCallsFromSelf()) {
                if (!call.getTargetOwner().isAssignableTo(Publisher.class)) {
                    continue;
                }
                if ("onErrorComplete".equals(call.getName())) {
                    return Optional.of(call);
                }
                if ("onErrorResume".equals(call.getName())) {
                    int line = call.getLineNumber();
                    Optional<JavaMethodCall> fallback = unit.getMethodCallsFromSelf().stream()
                            .filter(empty -> "empty".equals(empty.getName())
                                    && empty.getTargetOwner().isAssignableTo(Publisher.class)
                                    && empty.getLineNumber() >= line
                                    && empty.getLineNumber() <= line + FALLBACK_LINES)
                            .findFirst();
                    if (fallback.isPresent()) {
                        return Optional.of(call);
                    }
                }
            }
            boolean errorLambda = unit.getName().startsWith("lambda$")
                    && !unit.getRawParameterTypes().isEmpty()
                    && unit.getRawParameterTypes().get(unit.getRawParameterTypes().size() - 1)
                            .isAssignableTo(Throwable.class)
                    && unit.getRawReturnType().isAssignableTo(Publisher.class);
            if (errorLambda) {
                for (JavaMethodCall call : unit.getMethodCallsFromSelf()) {
                    if ("empty".equals(call.getName()) && call.getTargetOwner().isAssignableTo(Publisher.class)) {
                        return Optional.of(call);
                    }
                }
            }
        }
        return Optional.empty();
    }
}
