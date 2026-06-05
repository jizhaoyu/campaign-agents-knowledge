package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeBaseSearchSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSearchKnowledgeBasesByNameOrDescription() throws Exception {
        String adminToken = login("admin", "admin123");
        String suffix = String.valueOf(System.nanoTime());
        String supportName = "Search Support KB " + suffix;
        String policyName = "Policy Archive " + suffix;
        createKnowledgeBase(adminToken, supportName, "VPN and device support playbooks");
        createKnowledgeBase(adminToken, policyName, "Human resources policy collection");

        JsonNode nameMatches = searchKnowledgeBases(adminToken, "support");
        assertThat(names(nameMatches)).contains(supportName);
        assertThat(names(nameMatches)).doesNotContain(policyName);

        JsonNode descriptionMatches = searchKnowledgeBases(adminToken, "human resources");
        assertThat(names(descriptionMatches)).contains(policyName);
        assertThat(names(descriptionMatches)).doesNotContain(supportName);

        JsonNode noMatches = searchKnowledgeBases(adminToken, "no-match-" + suffix);
        assertThat(noMatches).isEmpty();
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

    private void createKnowledgeBase(String adminToken, String name, String description) throws Exception {
        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "%s"
                                }
                                """.formatted(name, description)))
                .andExpect(status().isOk());
    }

    private JsonNode searchKnowledgeBases(String adminToken, String keyword) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/knowledge-bases")
                        .param("keyword", keyword)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private Iterable<String> names(JsonNode knowledgeBases) {
        return StreamSupport.stream(knowledgeBases.spliterator(), false)
                .map(knowledgeBase -> knowledgeBase.path("name").asText())
                .toList();
    }
}
