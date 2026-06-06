package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@ActiveProfiles("ai-openai")
class OpenAiCompatibleProfileTest {

    @Autowired
    private Environment environment;

    @Test
    void shouldUseOpenAiCompatibleDefaults() {
        assertThat(environment.getProperty("spring.ai.openai.base-url"))
                .isEqualTo("https://mmw-codex.zenscaleai.com/v1");
        assertThat(environment.getProperty("spring.ai.openai.chat.completions-path"))
                .isEqualTo("/chat/completions");
        assertThat(environment.getProperty("spring.ai.openai.chat.options.model"))
                .isEqualTo("gpt-5.5");
        assertThat(environment.getProperty("spring.ai.model.embedding"))
                .isEqualTo("none");
        assertThat(environment.getProperty("app.ai.embedding.enabled", Boolean.class))
                .isFalse();
    }
}
