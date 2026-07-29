package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.exception.DuplicateResourceException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.InvalidCredentialsException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ResourceNotFoundException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ValidationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Global exception handler for the application.
 * Handles common exceptions and returns appropriate error responses.
 *
 * @author Kenmeugne Michèle
 * @date 18/12/2025
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

        /**
         * Handles ResourceNotFoundException.
         * Returns 404 Not Found.
         *
         * @param ex       exception
         * @param exchange web exchange
         * @return error response
         * @author Kengfack Lagrange
         * @date 18/12/2025
         */
        @ExceptionHandler(ResourceNotFoundException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleResourceNotFoundException(
                        ResourceNotFoundException ex,
                        ServerWebExchange exchange) {

                ErrorResponse error = new ErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.NOT_FOUND.value(),
                                "Not Found",
                                ex.getMessage(),
                                exchange.getRequest().getPath().value());

                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
        }

        /**
         * Handles DuplicateResourceException.
         * Returns 409 Conflict.
         *
         * @param ex       exception
         * @param exchange web exchange
         * @return error response
         * @author Kengfack Lagrange
         * @date 18/12/2025
         */
        @ExceptionHandler(DuplicateResourceException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleDuplicateResourceException(
                        DuplicateResourceException ex,
                        ServerWebExchange exchange) {

                ErrorResponse error = new ErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.CONFLICT.value(),
                                "Conflict",
                                ex.getMessage(),
                                exchange.getRequest().getPath().value());

                return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(error));
        }

        /**
         * Handles duplicate key exceptions from database.
         * Returns 409 Conflict.
         *
         * @param ex       exception
         * @param exchange web exchange
         * @return error response
         * @author Kengfack Lagrange
         * @date 18/12/2025
         */
        @ExceptionHandler(DuplicateKeyException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleDuplicateKeyException(
                        DuplicateKeyException ex,
                        ServerWebExchange exchange) {

                String message = "A resource with this information already exists";

                if (ex.getMessage().contains("persons_email_key")) {
                        message = "Email already exists";
                } else if (ex.getMessage().contains("persons_national_id_key")) {
                        message = "National ID already exists";
                }

                ErrorResponse error = new ErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.CONFLICT.value(),
                                "Conflict",
                                message,
                                exchange.getRequest().getPath().value());

                return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(error));
        }

        /**
         * Handles validation errors.
         * Returns 400 Bad Request.
         *
         * @param ex       exception
         * @param exchange web exchange
         * @return error response
         * @author Kengfack Lagrange
         * @date 18/12/2025
         */
        @ExceptionHandler(WebExchangeBindException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleValidationException(
                        WebExchangeBindException ex,
                        ServerWebExchange exchange) {

                String message = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("Validation error");

                ErrorResponse error = new ErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Bad Request",
                                message,
                                exchange.getRequest().getPath().value());

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
        }

        /**
         * Handles custom ValidationException.
         * Returns 400 Bad Request.
         *
         * @param ex       exception
         * @param exchange web exchange
         * @return error response
         */
        @ExceptionHandler(ValidationException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleCustomValidationException(
                        ValidationException ex,
                        ServerWebExchange exchange) {

                ErrorResponse error = new ErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                "Bad Request",
                                ex.getMessage(),
                                exchange.getRequest().getPath().value());

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
        }

        /**
         * Handles InvalidCredentialsException.
         * Returns 401 Unauthorized.
         *
         * @param ex       exception
         * @param exchange web exchange
         * @return error response
         */
        @ExceptionHandler(InvalidCredentialsException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleInvalidCredentialsException(
                        InvalidCredentialsException ex,
                        ServerWebExchange exchange) {

                ErrorResponse error = new ErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.UNAUTHORIZED.value(),
                                "Unauthorized",
                                ex.getMessage(),
                                exchange.getRequest().getPath().value());

                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error));
        }

        /**
         * Handles all other exceptions.
         * Returns 500 Internal Server Error.
         *
         * @param ex       exception
         * @param exchange web exchange
         * @return error response
         * @author Kengfack Lagrange
         * @date 18/12/2025
         */
        @ExceptionHandler(Exception.class)
        public Mono<ResponseEntity<ErrorResponse>> handleGenericException(
                        Exception ex,
                        ServerWebExchange exchange) {

                ErrorResponse error = new ErrorResponse(
                                LocalDateTime.now(),
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "Internal Server Error",
                                "An unexpected error occurred: " + ex.getMessage(),
                                exchange.getRequest().getPath().value());

                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
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