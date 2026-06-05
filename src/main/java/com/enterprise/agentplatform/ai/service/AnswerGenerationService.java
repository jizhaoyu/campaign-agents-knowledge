package com.enterprise.agentplatform.ai.service;

import com.enterprise.agentplatform.chat.dto.CitationResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AnswerGenerationService {

    private final Optional<ChatClient> chatClient;
    private final boolean enabled;

    public AnswerGenerationService(
            ObjectProvider<ChatModel> chatModelProvider,
            @Value("${app.ai.chat.enabled:false}") boolean enabled
    ) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        this.chatClient = chatModel == null ? Optional.empty() : Optional.of(ChatClient.create(chatModel));
        this.enabled = enabled;
    }

    public String generateAnswer(String question, List<CitationResponse> citations) {
        if (citations.isEmpty()) {
            return "未找到足够证据支持回答，请补充更具体的制度名、报错信息或场景描述。";
        }
        if (enabled && chatClient.isPresent()) {
            try {
                return chatClient.get()
                        .prompt()
                        .system("""
                                你是企业内部知识库助手。只能基于用户提供的引用片段回答。
                                如果引用不足以支撑结论，明确说明证据不足。
                                回答要面向工单处理场景，给出简洁可执行建议。
                                """)
                        .user(buildUserPrompt(question, citations))
                        .call()
                        .content();
            } catch (RuntimeException ex) {
                return fallbackAnswer(citations);
            }
        }
        return fallbackAnswer(citations);
    }

    private String buildUserPrompt(String question, List<CitationResponse> citations) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题：").append(question).append(System.lineSeparator());
        builder.append("引用片段：").append(System.lineSeparator());
        for (int i = 0; i < citations.size(); i++) {
            CitationResponse citation = citations.get(i);
            builder.append(i + 1)
                    .append(". 文档：")
                    .append(citation.documentName())
                    .append("，片段：")
                    .append(citation.snippet())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String fallbackAnswer(List<CitationResponse> citations) {
        StringBuilder builder = new StringBuilder("根据知识库检索结果，建议优先检查以下内容：");
        for (CitationResponse citation : citations) {
            builder.append(System.lineSeparator()).append("- ").append(citation.snippet());
        }
        builder.append(System.lineSeparator()).append("如果仍未解决，建议继续补充具体报错或生成工单草稿。");
        return builder.toString();
    }
}
