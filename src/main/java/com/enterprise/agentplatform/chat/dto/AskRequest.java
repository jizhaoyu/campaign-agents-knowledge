package com.enterprise.agentplatform.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AskRequest(
        @NotNull(message = "knowledgeBaseId 不能为空") Long knowledgeBaseId,
        @NotBlank(message = "question 不能为空") String question
) {
}
