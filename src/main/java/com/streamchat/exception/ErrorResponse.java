package com.streamchat.exception;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response format shared by the REST and WebSocket transports.
 *
 * <p>REST responses populate {@code status}, {@code error}, {@code message},
 * {@code path}, optional field-level {@code errors} and {@code timestamp}.
 * WebSocket error frames additionally echo the originating
 * {@code idempotencyKey} so clients can correlate a failed send with the
 * optimistic message they rendered locally.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private int status;
    private String error;
    private String message;
    private String path;
    private String idempotencyKey;
    private String traceId;
    private Map<String, String> errors;
    private LocalDateTime timestamp;

    /**
     * Build a fully populated error response with the current timestamp.
     * Intended for transport-neutral call sites (notably WebSocket handlers)
     * that do not need field-level validation details.
     */
    public static ErrorResponse of(int status, String error, String message,
                                   String path, String idempotencyKey) {
        return of(status, error, message, path, idempotencyKey, null);
    }

    /**
     * Build a fully populated error response with the current timestamp and an
     * explicit trace id. When {@code traceId} is null the value is resolved
     * from the MDC (populated by Micrometer Tracing) so REST/WebSocket callers
     * do not need to thread the tracer through every call site.
     */
    public static ErrorResponse of(int status, String error, String message,
                                   String path, String idempotencyKey, String traceId) {
        if (traceId == null || traceId.isBlank()) {
            traceId = org.slf4j.MDC.get("traceId");
        }
        return ErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .idempotencyKey(idempotencyKey)
                .traceId(traceId)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
