package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web;

import com.yowyob.tiibntick.core.billing.wallet.domain.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

@Slf4j
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.billing.wallet.adapter.in.web")
public class WalletExceptionHandler {

    @ExceptionHandler(WalletNotFoundException.class)
    public Mono<ProblemDetail> handleWalletNotFound(WalletNotFoundException ex) {
        log.warn("Wallet not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:billing:wallet-not-found"));
        pd.setTitle("Wallet Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }



    @ExceptionHandler(WalletFrozenException.class)
    public Mono<ProblemDetail> handleWalletFrozen(WalletFrozenException ex) {
        log.warn("Wallet frozen: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(422), ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:billing:wallet-frozen"));
        pd.setTitle("Wallet Frozen");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }


    /**
     * Relays a Kernel HTTP error (wrong confirmation code, insufficient funds, unknown wallet…)
     * with its original 4xx status so clients can react; any Kernel 5xx becomes a 502.
     */
    @ExceptionHandler(WebClientResponseException.class)
    public Mono<ProblemDetail> handleKernelError(WebClientResponseException ex) {
        HttpStatus status = ex.getStatusCode().is4xxClientError()
                ? HttpStatus.valueOf(ex.getStatusCode().value())
                : HttpStatus.BAD_GATEWAY;
        log.warn("Kernel wallet call failed: {} {}", ex.getStatusCode(), ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status,
                status == HttpStatus.BAD_GATEWAY ? "Kernel payment service error" : kernelMessage(ex));
        pd.setType(URI.create("urn:tiibntick:billing:kernel-wallet-error"));
        pd.setTitle("Kernel Wallet Error");
        pd.setProperty("kernelStatus", ex.getStatusCode().value());
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    private static String kernelMessage(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body == null || body.isBlank() ? ex.getStatusText() : body;
    }
}
