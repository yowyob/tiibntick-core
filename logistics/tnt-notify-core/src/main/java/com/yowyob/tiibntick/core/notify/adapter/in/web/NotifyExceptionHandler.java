package com.yowyob.tiibntick.core.notify.adapter.in.web;

import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelContextMissingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

/**
 * Notification-specific error mapping. Runs before the bootstrap fallback, which would turn the missing
 * Kernel context (an {@link IllegalStateException}) into a misleading {@code 409 State Conflict}.
 */
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.notify.adapter.in.web")
@Order(Integer.MAX_VALUE - 100)
public class NotifyExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(NotifyExceptionHandler.class);

    @ExceptionHandler(KernelContextMissingException.class)
    public Mono<ProblemDetail> handleKernelContextMissing(KernelContextMissingException ex) {
        log.warn("Notification engine not configured: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:notify:kernel-context-missing"));
        pd.setTitle("Notification Engine Not Configured");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }
}
