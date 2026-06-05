package com.enterprise.agentplatform.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST),
    DOCUMENT_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    INDEX_BUILD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    RAG_NO_EVIDENCE(HttpStatus.OK),
    TOOL_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    APPROVAL_REQUIRED(HttpStatus.ACCEPTED),
    APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND),
    APPROVAL_ALREADY_DECIDED(HttpStatus.CONFLICT),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
