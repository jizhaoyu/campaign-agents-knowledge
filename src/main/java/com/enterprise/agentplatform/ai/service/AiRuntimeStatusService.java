package com.enterprise.agentplatform.ai.service;

import com.enterprise.agentplatform.ai.dto.AiRuntimeComponentStatus;
import com.enterprise.agentplatform.ai.dto.AiRuntimeStatusResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class AiRuntimeStatusService {

    private final Environment environment;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public AiRuntimeStatusService(
            Environment environment,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider
    ) {
        this.environment = environment;
        this.chatModelProvider = chatModelProvider;
        this.embeddingModelProvider = embeddingModelProvider;
    }

    public AiRuntimeStatusResponse describe() {
        AiRuntimeComponentStatus chat = chatStatus();
        AiRuntimeComponentStatus embedding = embeddingStatus();
        List<String> warnings = warningsFor(chat, embedding);
        return new AiRuntimeStatusResponse(
                Arrays.asList(environment.getActiveProfiles()),
                chat,
                embedding,
                readinessLevel(chat, embedding),
                warnings,
                LocalDateTime.now()
        );
    }

    private AiRuntimeComponentStatus chatStatus() {
        return new AiRuntimeComponentStatus(
                bool("app.ai.chat.enabled", false),
                chatModelProvider.getIfAvailable() != null,
                hasText(prop("spring.ai.openai.api-key")),
                valueOrNone("spring.ai.model.chat"),
                prop("spring.ai.openai.base-url"),
                prop("spring.ai.openai.chat.completions-path"),
                prop("spring.ai.openai.chat.options.model")
        );
    }

    private AiRuntimeComponentStatus embeddingStatus() {
        return new AiRuntimeComponentStatus(
                bool("app.ai.embedding.enabled", false),
                embeddingModelProvider.getIfAvailable() != null,
                hasText(prop("spring.ai.openai.api-key")),
                valueOrNone("spring.ai.model.embedding"),
                prop("spring.ai.openai.base-url"),
                prop("spring.ai.openai.embedding.embeddings-path"),
                prop("spring.ai.openai.embedding.options.model")
        );
    }

    private List<String> warningsFor(AiRuntimeComponentStatus chat, AiRuntimeComponentStatus embedding) {
        List<String> warnings = new ArrayList<>();
        addComponentWarnings(warnings, "Chat", chat);
        addComponentWarnings(warnings, "Embedding", embedding);
        if (warnings.isEmpty()) {
            warnings.add("AI runtime configuration is ready for enabled components.");
        }
        return warnings;
    }

    private void addComponentWarnings(List<String> warnings, String label, AiRuntimeComponentStatus status) {
        if (!status.enabled()) {
            warnings.add(label + " is disabled by application configuration.");
            return;
        }
        if (!status.credentialConfigured()) {
            warnings.add(label + " credential is not configured.");
        }
        if (!status.modelAvailable()) {
            warnings.add(label + " Spring AI model bean is not available.");
        }
        if (!hasText(status.provider()) || "none".equalsIgnoreCase(status.provider())) {
            warnings.add(label + " provider is set to none.");
        }
    }

    private String readinessLevel(AiRuntimeComponentStatus chat, AiRuntimeComponentStatus embedding) {
        if (!chat.enabled() && !embedding.enabled()) {
            return "DISABLED";
        }
        boolean chatReady = componentReady(chat);
        boolean embeddingReady = !embedding.enabled() || componentReady(embedding);
        return chatReady && embeddingReady ? "READY" : "PARTIAL";
    }

    private boolean componentReady(AiRuntimeComponentStatus status) {
        return status.enabled()
                && status.modelAvailable()
                && status.credentialConfigured()
                && hasText(status.provider())
                && !"none".equalsIgnoreCase(status.provider());
    }

    private String valueOrNone(String key) {
        String value = prop(key);
        return hasText(value) ? value : "none";
    }

    private String prop(String key) {
        String value = environment.getProperty(key);
        return hasText(value) ? value.trim() : null;
    }

    private boolean bool(String key, boolean defaultValue) {
        return environment.getProperty(key, Boolean.class, defaultValue);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
