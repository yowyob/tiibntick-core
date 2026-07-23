package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.linkback.domain.exception.NearbyRateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

/**
 * Exception handler for tnt-link-back-core's inbound web adapters.
 *
 * <p>Currently only maps {@link NearbyRateLimitExceededException} (Phase 0 stop-gap,
 * Chantier G — see docs/audits/remediation/phase-0-critical.md) to HTTP 429.
 * Pre-existing domain exceptions in this module (e.g. {@code NetworkNodeDomainException},
 * {@code NetworkAlertDomainException}, {@code DaoZoneDomainException}) are intentionally
 * left untouched here — mapping them is out of scope for this stop-gap and falls
 * through to the generic reactive error handling already in place.
 *
 * @author Dilane PAFE
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.linkback.adapter.in.web")
public class LinkBackExceptionHandler {

    @ExceptionHandler(NearbyRateLimitExceededException.class)
    public Mono<ProblemDetail> handleRateLimitExceeded(NearbyRateLimitExceededException ex) {
        log.warn("Nearby rate limit exceeded: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:link:nearby-rate-limit-exceeded"));
        pd.setTitle("Too Many Requests");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }
}
