package com.yowyob.kernel.i18n.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Loads the catalog when the application starts (bounded wait, so the first request is already served
 * from the database) and keeps it current by probing the catalog revision at a fixed interval.
 */
public class MessageCatalogRefresher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MessageCatalogRefresher.class);

    private final MessageCatalog catalog;
    private final Duration interval;
    private volatile Disposable task;

    public MessageCatalogRefresher(MessageCatalog catalog, Duration interval) {
        this.catalog = catalog;
        this.interval = interval;
    }

    @Override
    public void start() {
        try {
            catalog.reload().block(Duration.ofSeconds(15));
        } catch (RuntimeException e) {
            log.warn("i18n catalog initial load did not complete ({}); packaged messages are used until it does",
                    e.getMessage());
        }
        if (!interval.isZero() && !interval.isNegative()) {
            task = Flux.interval(interval, interval)
                    .concatMap(tick -> catalog.refreshIfChanged())
                    .onErrorContinue((e, o) -> log.warn("i18n catalog refresh failed: {}", e.getMessage()))
                    .subscribe();
        }
    }

    @Override
    public void stop() {
        Disposable t = task;
        if (t != null) {
            t.dispose();
        }
        task = null;
    }

    @Override
    public boolean isRunning() {
        return task != null;
    }
}
