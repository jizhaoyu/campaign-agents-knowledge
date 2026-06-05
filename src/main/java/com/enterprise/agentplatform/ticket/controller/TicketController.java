package com.enterprise.agentplatform.ticket.controller;

import com.enterprise.agentplatform.common.api.ApiResponse;
import com.enterprise.agentplatform.ticket.dto.GenerateTicketDraftRequest;
import com.enterprise.agentplatform.ticket.dto.SimilarTicketResponse;
import com.enterprise.agentplatform.ticket.dto.SubmitTicketRequest;
import com.enterprise.agentplatform.ticket.dto.SubmitTicketResponse;
import com.enterprise.agentplatform.ticket.dto.TicketDraftResponse;
import com.enterprise.agentplatform.ticket.service.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/draft")
    @PreAuthorize("hasAuthority('ticket:draft')")
    public ApiResponse<TicketDraftResponse> generateDraft(@Valid @RequestBody GenerateTicketDraftRequest request) {
        return ApiResponse.success(ticketService.generateDraft(request.conversationId()));
    }

    @GetMapping("/similar")
    @PreAuthorize("hasAuthority('ticket:similar:read')")
    public ApiResponse<List<SimilarTicketResponse>> similar(@RequestParam("conversationId") @NotNull Long conversationId) {
        return ApiResponse.success(ticketService.searchSimilar(conversationId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ticket:submit')")
    public ApiResponse<SubmitTicketResponse> submit(@Valid @RequestBody SubmitTicketRequest request) {
        return ApiResponse.success(ticketService.submit(request));
    }
}
