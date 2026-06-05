package com.enterprise.agentplatform.common.api;

import com.enterprise.agentplatform.common.support.TraceIdHolder;

public record ApiResponse<T>(String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "success", data, TraceIdHolder.currentTraceId());
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data) {
        return new ApiResponse<>(code, message, data, TraceIdHolder.currentTraceId());
    }
}
