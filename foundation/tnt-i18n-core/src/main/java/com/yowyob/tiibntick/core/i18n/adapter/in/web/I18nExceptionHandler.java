package com.yowyob.tiibntick.core.i18n.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the catalog's validation errors into 400s, scoped to this module's controllers only. */
@RestControllerAdvice(assignableTypes = {I18nPublicController.class, MyLanguageController.class,
        I18nAdminController.class})
public class I18nExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("I18N_INVALID_REQUEST", e.getMessage(), null));
    }
}
