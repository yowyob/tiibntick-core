package com.yowyob.tiibntick.common.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Standardized HTTP response envelope for all TiiBnTick REST endpoints.
 *
 * <p>Every API response — success or error — is wrapped in this envelope to provide
 * a consistent client contract across all platforms and services.
 *
 * <p>Success format:
 * <pre>{@code
 * {
 *   "status": "SUCCESS",
 *   "data": { ... },
 *   "error": null,
 *   "correlationId": "3fa85f64-...",
 *   "timestamp": "2026-05-20T10:00:00Z"
 * }
 * }</pre>
 *
 * <p>Error format:
 * <pre>{@code
 * {
 *   "status": "ERROR",
 *   "data": null,
 *   "error": { "code": "MISSION_NOT_FOUND", "message": "...", "violations": [] },
 *   "correlationId": "...",
 *   "timestamp": "..."
 * }
 * }</pre>
 *
 * Author: MANFOUO Braun
 *
 * @param <T> type of the success payload
 */
public final class ApiResponse<T> {

    /** Possible statuses returned in the envelope. */
    public enum Status { SUCCESS, ERROR, PARTIAL }

    private final Status status;
    private final T data;
    private final ErrorDetail error;
    private final String correlationId;
    private final Instant timestamp;

    private ApiResponse(Status status, T data, ErrorDetail error, String correlationId) {
        this.status        = Objects.requireNonNull(status, "status is required");
        this.data          = data;
        this.error         = error;
        this.correlationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        this.timestamp     = Instant.now();
    }

    // ── Static factories ──────────────────────────────────────────────────

    /**
     * Creates a successful response wrapping {@code data}.
     *
     * @param data          response payload — must not be null
     * @param correlationId request correlation ID for distributed tracing
     */
    public static <T> ApiResponse<T> success(T data, String correlationId) {
        return new ApiResponse<>(Status.SUCCESS, data, null, correlationId);
    }

    /**
     * Creates a successful response with an auto-generated correlation ID.
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(data, UUID.randomUUID().toString());
    }

    /**
     * Creates an error response from an {@link ErrorDetail}.
     */
    public static <T> ApiResponse<T> error(ErrorDetail error, String correlationId) {
        return new ApiResponse<>(Status.ERROR, null, error, correlationId);
    }

    /**
     * Creates an error response from an error code and message.
     */
    public static <T> ApiResponse<T> error(String errorCode, String message, String correlationId) {
        return error(ErrorDetail.of(errorCode, message), correlationId);
    }

    /**
     * Creates a partial-success response (e.g., bulk operations with mixed results).
     */
    public static <T> ApiResponse<T> partial(T data, ErrorDetail error, String correlationId) {
        return new ApiResponse<>(Status.PARTIAL, data, error, correlationId);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public Status getStatus()        { return status; }
    public T getData()               { return data; }
    public ErrorDetail getError()    { return error; }
    public String getCorrelationId() { return correlationId; }
    public Instant getTimestamp()    { return timestamp; }

    public boolean isSuccess()       { return status == Status.SUCCESS; }
    public boolean isError()         { return status == Status.ERROR; }

    @Override
    public String toString() {
        return "ApiResponse{status=" + status
                + ", correlationId='" + correlationId + "'"
                + (error != null ? ", error=" + error : "")
                + "}";
    }
}
