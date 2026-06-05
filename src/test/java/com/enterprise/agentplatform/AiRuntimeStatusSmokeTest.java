package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=secret-runtime-key",
        "spring.ai.openai.base-url=https://relay.example.com/v1",
        "spring.ai.openai.chat.completions-path=/chat/completions",
        "spring.ai.openai.chat.options.model=gpt-runtime-test",
        "app.ai.chat.enabled=true",
        "spring.ai.model.embedding=none",
        "app.ai.embedding.enabled=false"
})
@AutoConfigureMockMvc
class AiRuntimeStatusSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldExposeRuntimeStatusWithoutSecretsForDashboardReadersOnly() throws Exception {
        String adminToken = login("admin", "admin123");
        String userToken = login("user", "user123");

        MvcResult result = mockMvc.perform(get("/api/v1/ai/runtime")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chat.enabled").value(true))
                .andExpect(jsonPath("$.data.chat.credentialConfigured").value(true))
                .andExpect(jsonPath("$.data.chat.provider").value("openai"))
                .andExpect(jsonPath("$.data.chat.baseUrl").value("https://relay.example.com/v1"))
                .andExpect(jsonPath("$.data.chat.path").value("/chat/completions"))
                .andExpect(jsonPath("$.data.chat.model").value("gpt-runtime-test"))
                .andExpect(jsonPath("$.data.embedding.enabled").value(false))
                .andExpect(jsonPath("$.data.readinessLevel").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("secret-runtime-key")
                .doesNotContain("api-key")
                .doesNotContain("apiKey");

        mockMvc.perform(get("/api/v1/ai/runtime")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }
}
