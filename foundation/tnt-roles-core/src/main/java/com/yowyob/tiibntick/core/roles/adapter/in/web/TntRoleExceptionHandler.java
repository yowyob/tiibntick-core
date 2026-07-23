package com.yowyob.tiibntick.core.roles.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.common.api.ErrorDetail;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

/**
 * Module-specific advisor for {@link TntRoleException} — maps each RBAC error code to an
 * HTTP status and TiiBnTick Core's standard {@link ApiResponse} envelope, mirroring
 * {@code TntAuthExceptionHandler}'s pattern in {@code tnt-auth-core}.
 *
 * <h3>Why this exists (Audit n°7 · #6)</h3>
 * <p>Before this handler, {@code TntPermissionAspect} (this module) threw
 * {@link TntRoleException#forbidden(String, String)} whenever a
 * {@code @RequirePermission}-guarded method denied access — but nothing in the repo mapped
 * {@code TntRoleException} to an HTTP status. The exception fell through to WebFlux's
 * default error handler and surfaced as a <b>500</b>, not the expected <b>403</b>, for
 * every module relying on {@code @RequirePermission} (invoice, wallet, and any future
 * caller). The mutation was still blocked (the guarded method body never ran), but the
 * response code was wrong — this handler fixes that without touching the aspect itself.
 *
 * <p>Runs before {@code TntGlobalExceptionHandler} (tnt-bootstrap,
 * {@code @Order(Integer.MAX_VALUE)}), per that handler's "module-specific advisors take
 * precedence" contract.
 *
 * @author MANFOUO Braun
 */
@RestControllerAdvice
@Order(10)
public class TntRoleExceptionHandler {

    @ExceptionHandler(TntRoleException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleTntRoleException(TntRoleException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "ROLE_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "ROLE_MISSING_CONTEXT" -> HttpStatus.UNAUTHORIZED;
            case "ROLE_UNKNOWN", "ROLE_NOT_PROVISIONED", "ROLE_NOT_FOUND", "ROLE_ASSIGNMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ROLE_ALREADY_EXISTS", "ROLE_CODE_RESERVED", "ROLE_SYSTEM_NOT_EDITABLE" -> HttpStatus.CONFLICT;
            case "ROLE_MISSING_SCOPE_ID", "ROLE_MISSING_TENANT_ID" -> HttpStatus.BAD_REQUEST;
            case "ROLE_NOT_SEEDED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return Mono.just(ResponseEntity.status(status)
                .body(ApiResponse.error(ErrorDetail.of(ex.getCode(), ex.getMessage()), null)));
    }
}
