package com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds;

import com.yowyob.tiibntick.common.exception.TntConflictException;
import com.yowyob.tiibntick.common.exception.TntException;
import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP mapping of the threshold errors: 422 invalid / not an agency, 409 stale version, 404 not found. Body:
 * {@code {status, code, detail, violations}} (plain JSON, independent of the ProblemDetail serialization setup).
 */
@RestControllerAdvice(assignableTypes = {
        PlatformDeliveryThresholdsController.class, OrganizationDeliveryThresholdsController.class})
@Order(5)
public class DeliveryThresholdsExceptionHandler {

    @ExceptionHandler(TntValidationException.class)
    public ResponseEntity<Map<String, Object>> invalid(TntValidationException ex) {
        return body(422, ex, ex.getViolations());
    }

    @ExceptionHandler(TntConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(TntConflictException ex) {
        return body(409, ex, List.of());
    }

    @ExceptionHandler(TntNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(TntNotFoundException ex) {
        return body(404, ex, List.of());
    }

    private static ResponseEntity<Map<String, Object>> body(int status, TntException ex, List<?> violations) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("code", ex.getErrorCode());
        body.put("detail", ex.getMessage());
        body.put("violations", violations);
        return ResponseEntity.status(HttpStatusCode.valueOf(status)).body(body);
    }
}
