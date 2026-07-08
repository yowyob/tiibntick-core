package com.yowyob.tiibntick.core.administration.onboarding.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.common.api.ErrorDetail;
import com.yowyob.tiibntick.core.administration.onboarding.domain.exception.AgencyOnboardingException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

/**
 * Module-specific advisor for {@link AgencyOnboardingException} — maps it to an HTTP
 * status and TiiBnTick Core's standard {@link ApiResponse} envelope. Runs before
 * {@code TntGlobalExceptionHandler} (tnt-bootstrap, {@code @Order(Integer.MAX_VALUE)}).
 *
 * @author MANFOUO Braun
 */
@RestControllerAdvice
@Order(10)
public class AgencyOnboardingExceptionHandler {

    @ExceptionHandler(AgencyOnboardingException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleAgencyOnboardingException(AgencyOnboardingException ex) {
        HttpStatus status = "ONBOARDING_OWNER_ROLE_NOT_FOUND".equals(ex.getCode())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_GATEWAY;
        return Mono.just(ResponseEntity.status(status)
                .body(ApiResponse.error(ErrorDetail.of(ex.getCode(), ex.getMessage()), null)));
    }
}
