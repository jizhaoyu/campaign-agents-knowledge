package com.enterprise.agentplatform.common.support;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public final class TraceIdHolder {

    public static final String TRACE_ID_KEY = "traceId";
    private static final ThreadLocal<String> CURRENT_TRACE_ID = new ThreadLocal<>();

    private TraceIdHolder() {
    }

    public static String currentTraceId() {
        String currentTraceId = CURRENT_TRACE_ID.get();
        if (currentTraceId != null && !currentTraceId.isBlank()) {
            return currentTraceId;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "N/A";
        }
        Object traceId = attributes.getAttribute(TRACE_ID_KEY, RequestAttributes.SCOPE_REQUEST);
        return traceId == null ? "N/A" : traceId.toString();
    }

    public static void bind(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            CURRENT_TRACE_ID.set(traceId);
        }
    }

    public static void clear() {
        CURRENT_TRACE_ID.remove();
    }
}
