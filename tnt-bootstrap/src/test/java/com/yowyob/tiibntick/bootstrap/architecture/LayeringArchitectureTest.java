package com.yowyob.tiibntick.bootstrap.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reactor-wide ArchUnit guardrails for the strict Maven layering rule documented in the root
 * {@code CLAUDE.md} ("Layering rule (strict)"), plus two structural hexagonal-architecture
 * conventions used across (almost) every module.
 *
 * <h3>Why this test lives in tnt-bootstrap</h3>
 * {@code tnt-bootstrap} is the only module that — transitively, via {@code @Import} of every
 * module's {@code @Configuration} class in {@code TntCoreConfig} — compiles against essentially
 * the entire L0-L6 module graph. It is therefore the only place in the reactor where a single
 * test's classpath can see every layer at once, which {@link #noModuleMayDependOnAStrictlyHigherLayer()}
 * needs in order to catch cross-module violations.
 *
 * <p>{@code coreBackend/*} product backends and {@code tnt-bootstrap} itself are intentionally
 * NOT part of the {@link #LAYER_INDEX} map below: per {@code CLAUDE.md} they orchestrate L0-L6
 * and are allowed to depend on any of it, so they sit outside the scope of the "no upward layer
 * dependency" check — only the L0-L6 module graph itself is checked for upward violations.
 *
 * <p>Regression coverage: this test is what keeps Audit n1 findings A1
 * ({@code yow-i18n-kernel} → {@code tnt-common-core}) and A2 ({@code tnt-actor-core} →
 * {@code tnt-incident-core}) fixed, and A6 (application services leaking web
 * {@code adapter.in.web} DTOs) from silently regressing.
 */
class LayeringArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.yowyob.tiibntick", "com.yowyob.kernel");
    }

    /**
     * Package prefix → layer index, mirroring CLAUDE.md's L0-L6 module graph exactly
     * (L7 / tnt-bootstrap has nothing above it, so it needs no entry here).
     */
    private static final Map<String, Integer> LAYER_INDEX = new LinkedHashMap<>();

    static {
        // L0 — foundation event/i18n kernel
        LAYER_INDEX.put("com.yowyob.kernel.event", 0);
        LAYER_INDEX.put("com.yowyob.kernel.i18n", 0);
        // L1 — foundation shared types / auth bridge / RBAC / platform gateway
        LAYER_INDEX.put("com.yowyob.tiibntick.common", 1);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.auth", 1);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.roles", 1);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.platformgateway", 1);
        // L2 — identity
        LAYER_INDEX.put("com.yowyob.tiibntick.core.actor", 2);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.organization", 2);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.tp", 2);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.administration", 2);
        // L3 — logistics
        LAYER_INDEX.put("com.yowyob.tiibntick.core.geo", 3);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.route", 3);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.delivery", 3);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.dispute", 3);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.incident", 3);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.realtime", 3);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.sync", 3);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.notify", 3);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.media", 3);
        // L4 — business
        LAYER_INDEX.put("com.yowyob.tiibntick.core.resource", 4);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.product", 4);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.inventory", 4);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.sales", 4);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.accounting", 4);
        LAYER_INDEX.put("com.yowyob.tiibntick.core.hrm", 4);
        // L5 — billing (tnt-billing-dsl/pricing/cost/invoice/wallet/report/templates all share
        // the com.yowyob.tiibntick.core.billing package root, one sub-package each)
        LAYER_INDEX.put("com.yowyob.tiibntick.core.billing", 5);
        // L6 — trust (cross-cutting; consumed by L2-L5, never depends back on any of them)
        LAYER_INDEX.put("com.yowyob.tiibntick.core.trust", 6);
    }

    /**
     * Resolves the layer index of a class from the longest matching package prefix in
     * {@link #LAYER_INDEX}, or {@code null} if the class belongs to none of them (Kernel
     * RT-comops classes, coreBackend/*, tnt-bootstrap itself, third-party libraries, ...).
     */
    private static Integer layerOf(JavaClass clazz) {
        String pkg = clazz.getPackageName();
        Integer best = null;
        int bestLength = -1;
        for (Map.Entry<String, Integer> entry : LAYER_INDEX.entrySet()) {
            String prefix = entry.getKey();
            if ((pkg.equals(prefix) || pkg.startsWith(prefix + ".")) && prefix.length() > bestLength) {
                best = entry.getValue();
                bestLength = prefix.length();
            }
        }
        return best;
    }

    @Test
    void noModuleMayDependOnAStrictlyHigherLayer() {
        // NOTE: classes().should(...), NOT noClasses().should(...) — noClasses() wraps the
        // condition in ArchUnit's NeverCondition, which *inverts* a hand-written
        // ArchCondition's semantics (a SimpleConditionEvent.violated(...) reported by our own
        // condition would be read as "did not match, therefore fine" and the rule would
        // never fail). classes().should(condition) uses our violated(...) events directly,
        // which is what a custom, positively-phrased-as-a-check condition like this one needs.
        ArchRule rule = classes().should(new ArchCondition<JavaClass>(
                "depend on a class belonging to a strictly higher-numbered layer (L0-L6)") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                Integer sourceLayer = layerOf(clazz);
                if (sourceLayer == null) {
                    return;
                }
                clazz.getDirectDependenciesFromSelf().forEach(dependency -> {
                    JavaClass target = dependency.getTargetClass();
                    Integer targetLayer = layerOf(target);
                    if (targetLayer != null && targetLayer > sourceLayer) {
                        // Plain concatenation on purpose (not String.format): formatting a "%d"
                        // conversion pulls in the JDK CLDR locale provider, which JaCoCo's
                        // coverage agent fails to instrument on JDK 21 (unrelated JaCoCo/JDK
                        // interaction bug) — avoid tripping it from inside an ArchCondition.
                        String message = clazz.getFullName() + " (L" + sourceLayer + ") depends on "
                                + target.getFullName() + " (L" + targetLayer + ") via ["
                                + dependency.getDescription() + "]";
                        events.add(SimpleConditionEvent.violated(clazz, message));
                    }
                });
            }
        });
        rule.check(classes);
    }

    /**
     * {@code adapter.in} (web controllers, Kafka/messaging consumers) must go through
     * application ports, never call {@code adapter.out} (persistence/messaging clients)
     * directly.
     *
     * <p>Wrapped in {@link FreezingArchRule}: a from-scratch reactor-wide sweep found this
     * pattern already pervasive across the existing codebase (pre-existing legacy debt, well
     * beyond a Chantier E "quick win" to fix in bulk). Freezing records today's violations as
     * the baseline in {@code tnt-bootstrap/archunit_store} — the build stays green, but any
     * *new* violation introduced from now on fails it, and any violation that gets fixed is
     * automatically dropped from the baseline (so it can never silently come back).
     */
    @Test
    void adapterInMustNotDependOnAdapterOut() {
        ArchRule rule = FreezingArchRule.freeze(noClasses()
                .that().resideInAPackage("..adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
                .because("inbound adapters (web controllers, Kafka/messaging consumers) must go "
                        + "through application ports, never call outbound adapters directly"));
        rule.check(classes);
    }

    /**
     * The application layer must not know about web-facing request/response DTOs (Audit n1 A6):
     * controllers map to/from web DTOs, application services expose domain/entity types only.
     *
     * <p>Frozen for the same reason as {@link #adapterInMustNotDependOnAdapterOut()} — the A6
     * finding was illustrated by two files in the audit, but the underlying pattern turned out
     * to be widespread. This test is the guardrail against *new* occurrences from now on; the
     * existing ones are tracked as frozen baseline debt, not silently ignored.
     */
    @Test
    void applicationMustNotDependOnAdapterInWeb() {
        ArchRule rule = FreezingArchRule.freeze(noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.in.web..")
                .because("the application layer must not know about web-facing request/response "
                        + "DTOs (Audit n1 A6) — controllers map to/from web DTOs, application "
                        + "services expose domain/entity types only"));
        rule.check(classes);
    }
}
