package com.yowyob.tiibntick.core.billing.wallet.adapter.in.web;

import com.yowyob.tiibntick.core.billing.wallet.domain.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    @ExceptionHandler(PaymentIntentNotFoundException.class)
    public Mono<ProblemDetail> handlePaymentIntentNotFound(PaymentIntentNotFoundException ex) {
        log.warn("Payment intent not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:billing:payment-intent-not-found"));
        pd.setTitle("Payment Intent Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public Mono<ProblemDetail> handleInsufficientBalance(InsufficientBalanceException ex) {
        log.warn("Insufficient balance: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(422), ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:billing:insufficient-balance"));
        pd.setTitle("Insufficient Balance");
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

    @ExceptionHandler(DuplicatePaymentException.class)
    public Mono<ProblemDetail> handleDuplicatePayment(DuplicatePaymentException ex) {
        log.warn("Duplicate payment: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:billing:duplicate-payment"));
        pd.setTitle("Duplicate Payment");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }
}
