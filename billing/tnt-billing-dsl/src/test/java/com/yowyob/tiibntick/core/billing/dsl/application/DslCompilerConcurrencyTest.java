package com.yowyob.tiibntick.core.billing.dsl.application;

import com.yowyob.tiibntick.core.billing.dsl.application.service.DslCompilerService;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.evaluator.DslEvaluator;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer.DslLexer;
import com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.parser.DslParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot C-56 — the compiler is a shared singleton and the parser keeps its cursor in fields: priced in
 * parallel (one candidate per thread), conditions used to be corrupted and silently evaluated as false,
 * so a surcharge vanished at random. Many threads compiling at once must all get the right AST.
 */
@DisplayName("C-56 — DSL compilation is safe under concurrency")
class DslCompilerConcurrencyTest {

    @Test
    void concurrentCompilationNeverCorruptsAParse() throws Exception {
        DslCompilerService compiler = new DslCompilerService(new DslLexer(), new DslParser());
        DslEvaluator evaluator = new DslEvaluator();
        PricingContext ctx = PricingContext.builder().weightKg(1.0).distanceKm(0.0)
                .packageTypes(List.of(PackageType.FRAGILE, PackageType.PERISHABLE)).build();
        List<String> expressions = List.of("packageTypes CONTAINS FRAGILE", "packageTypes CONTAINS PERISHABLE",
                "weight <= 5 AND packageType == 'FRAGILE'");

        int threads = 16;
        int perThread = 300;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        AtomicInteger trueResults = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    String expr = expressions.get(i % expressions.size());
                    try {
                        if (evaluator.evaluate(compiler.compileCondition(expr), ctx)) trueResults.incrementAndGet();
                        else failures.add("false: " + expr);
                    } catch (RuntimeException e) {
                        failures.add(expr + " → " + e.getMessage());
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).as("aucune compilation corrompue").isEmpty();
        assertThat(trueResults.get()).isEqualTo(threads * perThread);
    }
}
