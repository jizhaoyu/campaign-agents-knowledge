package com.enterprise.agentplatform.ticket.service;

import com.enterprise.agentplatform.domain.entity.Ticket;
import com.enterprise.agentplatform.ticket.dto.SimilarTicketResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SimilarTicketMatcher {

    static final int SIMILAR_TICKET_LIMIT = 5;
    static final int SIMILAR_KEYWORD_LIMIT = 8;

    public List<String> tokenize(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsAlphabetic}\\p{IsIdeographic}\\p{IsDigit}]+", " ");
        return java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.isBlank())
                .distinct()
                .toList();
    }

    public List<SimilarTicketResponse> rank(List<Ticket> candidates, List<String> keywords) {
        return candidates.stream()
                .map(ticket -> {
                    List<String> matchedKeywords = matchedKeywords(ticket, keywords);
                    return new SimilarTicketResponse(
                            ticket.getId(),
                            ticket.getTitle(),
                            ticket.getPriority().name(),
                            ticket.getStatus().name(),
                            matchedKeywords.size(),
                            matchedKeywords,
                            matchSummary(ticket, matchedKeywords)
                    );
                })
                .filter(response -> response.score() > 0)
                .sorted(Comparator.comparingInt(SimilarTicketResponse::score)
                        .reversed()
                        .thenComparing(SimilarTicketResponse::ticketId, Comparator.reverseOrder()))
                .limit(SIMILAR_TICKET_LIMIT)
                .toList();
    }

    private List<String> matchedKeywords(Ticket ticket, List<String> keywords) {
        String candidate = (safeText(ticket.getTitle()) + " " + safeText(ticket.getDescription())).toLowerCase(Locale.ROOT);
        return keywords.stream()
                .filter(candidate::contains)
                .limit(SIMILAR_KEYWORD_LIMIT)
                .toList();
    }

    private String matchSummary(Ticket ticket, List<String> matchedKeywords) {
        if (matchedKeywords.isEmpty()) {
            return "未命中明显关键词";
        }
        String title = safeText(ticket.getTitle()).toLowerCase(Locale.ROOT);
        String description = safeText(ticket.getDescription()).toLowerCase(Locale.ROOT);
        List<String> fields = new java.util.ArrayList<>();
        if (matchedKeywords.stream().anyMatch(title::contains)) {
            fields.add("标题");
        }
        if (matchedKeywords.stream().anyMatch(description::contains)) {
            fields.add("描述");
        }
        return "命中关键词：" + String.join("、", matchedKeywords) + "；来源：" + String.join("、", fields);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
