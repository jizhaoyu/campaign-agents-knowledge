package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.agentplatform.common.api.ApiResponse;
import com.enterprise.agentplatform.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @Test
    void shouldHideInternalExceptionDetailsFromUnhandledErrorResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnhandledException(
                new IllegalStateException("internal-datasource-detail")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message())
                .isEqualTo("系统内部错误，请稍后重试")
                .doesNotContain("internal-datasource-detail");
    }
}
