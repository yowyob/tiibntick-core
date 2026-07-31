package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.billing.wallet.domain.exception.InsufficientBalanceException;
import com.yowyob.tiibntick.core.actor.domain.exception.DelivererNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.exception.AnnouncementNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.DuplicateResourceException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.InvalidCredentialsException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.InvalidTokenException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ResourceNotFoundException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ValidationException;
import org.springframework.core.codec.DecodingException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Global exception handler for the Go Freelancer Point module.
 * Maps domain / security / decoding failures to the correct HTTP status
 * instead of collapsing everything into 500.
 *
 * @author MANFOUO BRAUN
 */
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.gofreelancer")
public class GlobalExceptionHandler {

    @ExceptionHandler({
            ResourceNotFoundException.class,
            AnnouncementNotFoundException.class,
            DeliveryNotFoundException.class,
            DelivererNotFoundException.class
    })
    public Mono<ResponseEntity<ErrorResponse>> handleResourceNotFoundException(
            RuntimeException ex, ServerWebExchange exchange) {
        return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), exchange);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidToken(
            InvalidTokenException ex, ServerWebExchange exchange) {
        return error(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), exchange);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWebClient(
            WebClientResponseException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return error(status, status.getReasonPhrase(), ex.getMessage(), exchange);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDuplicateResourceException(
            DuplicateResourceException ex, ServerWebExchange exchange) {
        return error(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), exchange);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDuplicateKeyException(
            DuplicateKeyException ex, ServerWebExchange exchange) {
        String message = "A resource with this information already exists";
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("persons_email_key")) {
                message = "Email already exists";
            } else if (ex.getMessage().contains("persons_national_id_key")) {
                message = "National ID already exists";
            }
        }
        return error(HttpStatus.CONFLICT, "Conflict", message, exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(
            WebExchangeBindException ex, ServerWebExchange exchange) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Validation error");
        return error(HttpStatus.BAD_REQUEST, "Bad Request", message, exchange);
    }

    @ExceptionHandler(ValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleCustomValidationException(
            ValidationException ex, ServerWebExchange exchange) {
        return error(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), exchange);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidCredentialsException(
            InvalidCredentialsException ex, ServerWebExchange exchange) {
        return error(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), exchange);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccessDenied(
            AccessDeniedException ex, ServerWebExchange exchange) {
        return error(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), exchange);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInsufficientBalance(
            InsufficientBalanceException ex, ServerWebExchange exchange) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient Balance", ex.getMessage(), exchange);
    }

    @ExceptionHandler({ServerWebInputException.class, DecodingException.class})
    public Mono<ResponseEntity<ErrorResponse>> handleBadInput(
            Exception ex, ServerWebExchange exchange) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Invalid request body";
        Throwable cause = ex.getCause();
        if (cause != null && cause.getMessage() != null) {
            message = cause.getMessage();
        }
        return error(HttpStatus.BAD_REQUEST, "Bad Request", message, exchange);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResponseStatus(
            ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return error(status, status.getReasonPhrase(), reason, exchange);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(
            IllegalArgumentException ex, ServerWebExchange exchange) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Invalid argument";
        HttpStatus status = msg.toLowerCase().contains("not found")
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return error(status, status.getReasonPhrase(), msg, exchange);
    }

    @ExceptionHandler(DeliveryDomainException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDeliveryDomain(
            DeliveryDomainException ex, ServerWebExchange exchange) {
        return error(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), exchange);
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalState(
            IllegalStateException ex, ServerWebExchange exchange) {
        return error(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), exchange);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUnsupported(
            UnsupportedOperationException ex, ServerWebExchange exchange) {
        return error(HttpStatus.NOT_IMPLEMENTED, "Not Implemented", ex.getMessage(), exchange);
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(
            Exception ex, ServerWebExchange exchange) {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred: " + ex.getMessage(),
                exchange);
    }

    private static Mono<ResponseEntity<ErrorResponse>> error(
            HttpStatus status, String error, String message, ServerWebExchange exchange) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                error,
                message,
                exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.status(status).body(body));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String path;
    }
}
