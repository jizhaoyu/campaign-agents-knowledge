package com.enterprise.agentplatform.chat.controller;

import com.enterprise.agentplatform.chat.dto.AskRequest;
import com.enterprise.agentplatform.chat.dto.AskResponse;
import com.enterprise.agentplatform.chat.dto.ChatHistoryItemResponse;
import com.enterprise.agentplatform.chat.service.ChatService;
import com.enterprise.agentplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        return ApiResponse.success(chatService.ask(request));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<List<ChatHistoryItemResponse>> history(
            @RequestParam(value = "limit", defaultValue = "10") @Min(1) @Max(50) int limit
    ) {
        return ApiResponse.success(chatService.history(limit));
    }
}
