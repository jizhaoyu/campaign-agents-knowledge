package com.enterprise.agentplatform.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitTicketRequest(
        @NotNull(message = "conversationId 不能为空") Long conversationId,
        @NotBlank(message = "title 不能为空") String title,
        @NotBlank(message = "description 不能为空") String description,
        @NotBlank(message = "priority 不能为空") String priority,
        Long assigneeId
) {
}
