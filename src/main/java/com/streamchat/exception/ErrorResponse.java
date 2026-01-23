package com.streamchat.exception;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response format.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private int status;
    private String message;
    private Map<String, String> errors;
    private LocalDateTime timestamp;
}