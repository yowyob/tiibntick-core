package com.yowyob.tiibntick.core.billing.dsl.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslEvaluationException;
import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslParseException;
import com.yowyob.tiibntick.core.billing.dsl.domain.exception.DslRuleNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Global exception handler for the {@code tnt-billing-dsl} REST layer.
 * Translates domain exceptions to RFC 9457 Problem Detail responses.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.billing.dsl.infrastructure.adapter.in.web")
public class DslBillingExceptionHandler {

    @ExceptionHandler(DslRuleNotFoundException.class)
    public ProblemDetail handleNotFound(DslRuleNotFoundException ex) {
        log.warn("DSL rule not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create("https://tiibntick.io/problems/dsl-rule-not-found"));
        pd.setTitle("DSL Rule Not Found");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(DslParseException.class)
    public ProblemDetail handleParseError(DslParseException ex) {
        log.warn("DSL parse error: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        pd.setType(URI.create("https://tiibntick.io/problems/dsl-parse-error"));
        pd.setTitle("DSL Parse Error");
        pd.setDetail(ex.getMessage());
        pd.setProperty("position", ex.getPosition());
        pd.setProperty("token", ex.getToken());
        return pd;
    }

    @ExceptionHandler(DslEvaluationException.class)
    public ProblemDetail handleEvaluationError(DslEvaluationException ex) {
        log.error("DSL evaluation error: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setType(URI.create("https://tiibntick.io/problems/dsl-evaluation-error"));
        pd.setTitle("DSL Evaluation Error");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("https://tiibntick.io/problems/bad-request"));
        pd.setTitle("Bad Request");
        pd.setDetail(ex.getMessage());
        return pd;
    }
}
