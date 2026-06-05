package com.enterprise.agentplatform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FrontendSpaSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldServeSpaRoutesWithoutAuthentication() throws Exception {
        for (String route : new String[]{"/", "/dashboard", "/knowledge", "/chat", "/tickets", "/approvals", "/ai-config", "/users", "/sessions", "/audits"}) {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void shouldKeepApiRoutesProtected() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge-bases"))
                .andExpect(status().isUnauthorized());
    }
}
