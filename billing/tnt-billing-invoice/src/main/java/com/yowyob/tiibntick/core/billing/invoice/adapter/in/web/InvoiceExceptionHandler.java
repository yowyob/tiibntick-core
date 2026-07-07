package com.yowyob.tiibntick.core.billing.invoice.adapter.in.web;

import com.yowyob.tiibntick.core.billing.invoice.domain.exception.InvoiceNotFoundException;
import com.yowyob.tiibntick.core.billing.invoice.domain.exception.InvoiceStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

@Slf4j
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.billing.invoice.adapter.in.web")
public class InvoiceExceptionHandler {

    @ExceptionHandler(InvoiceNotFoundException.class)
    public Mono<ProblemDetail> handleNotFound(InvoiceNotFoundException ex) {
        log.warn("Invoice not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:billing:invoice-not-found"));
        pd.setTitle("Invoice Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(InvoiceStateException.class)
    public Mono<ProblemDetail> handleStateError(InvoiceStateException ex) {
        log.warn("Invoice state error: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:billing:invoice-state-error"));
        pd.setTitle("Invalid Invoice State Transition");
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("invoiceId", ex.getInvoiceId());
        pd.setProperty("attemptedAction", ex.getAttemptedAction());
        return Mono.just(pd);
    }
}
