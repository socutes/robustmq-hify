package com.hify.common.http;

import lombok.Getter;

@Getter
public class LlmApiException extends RuntimeException {

    public enum Type {
        TIMEOUT,
        AUTH_FAILED,
        RATE_LIMITED,
        UNKNOWN
    }

    private final Type type;
    private final int statusCode;

    public LlmApiException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
        this.statusCode = -1;
    }

    public LlmApiException(Type type, int statusCode, String message) {
        super(message);
        this.type = type;
        this.statusCode = statusCode;
    }
}
