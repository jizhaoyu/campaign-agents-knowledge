package com.enterprise.agentplatform.common.security;

import com.enterprise.agentplatform.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

public final class SecurityJsonWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecurityJsonWriter() {
    }

    public static void writeUnauthorized(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OBJECT_MAPPER.writeValue(response.getWriter(), ApiResponse.failure(code, message, null));
    }
}
