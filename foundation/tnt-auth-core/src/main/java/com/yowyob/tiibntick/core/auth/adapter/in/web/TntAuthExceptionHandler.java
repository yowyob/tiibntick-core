package com.yowyob.tiibntick.core.auth.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.common.api.ErrorDetail;
import com.yowyob.tiibntick.core.auth.domain.exception.TntAuthException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

/**
 * Module-specific advisor for {@link TntAuthException} — maps each error code to an HTTP
 * status and TiiBnTick Core's standard {@link ApiResponse} envelope. Runs before
 * {@code TntGlobalExceptionHandler} (tnt-bootstrap, {@code @Order(Integer.MAX_VALUE)}),
 * per that handler's own "module-specific advisors take precedence" contract.
 *
 * @author MANFOUO Braun
 */
@RestControllerAdvice
@Order(10)
public class TntAuthExceptionHandler {

    @ExceptionHandler(TntAuthException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleTntAuthException(TntAuthException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "AUTH_UNAUTHORIZED", "AUTH_MISSING_CONTEXT", "AUTH_TOKEN_EXPIRED", "AUTH_TOKEN_INVALID" -> HttpStatus.UNAUTHORIZED;
            case "AUTH_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "AUTH_ACTOR_NOT_LINKED", "SSO_CONTEXT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "SSO_APP_NOT_CONFIGURED" -> HttpStatus.BAD_REQUEST;
            case "SSO_TOKEN_EXCHANGE_FAILED" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return Mono.just(ResponseEntity.status(status)
                .body(ApiResponse.error(ErrorDetail.of(ex.getCode(), ex.getMessage()), null)));
    }
}
