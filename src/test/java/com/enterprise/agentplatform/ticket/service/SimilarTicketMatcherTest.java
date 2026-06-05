package com.enterprise.agentplatform.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.agentplatform.domain.entity.Ticket;
import com.enterprise.agentplatform.domain.enums.TicketPriority;
import com.enterprise.agentplatform.domain.enums.TicketStatus;
import org.junit.jupiter.api.Test;

class SimilarTicketMatcherTest {

    private final SimilarTicketMatcher matcher = new SimilarTicketMatcher();

    @Test
    void shouldTokenizeDistinctBusinessKeywords() {
        assertThat(matcher.tokenize("VPN 无法连接，VPN error 502"))
                .containsExactly("vpn", "无法连接", "error", "502");
    }

    @Test
    void shouldRankCandidatesByMatchedKeywordCountThenNewestTicketId() {
        Ticket olderStrongMatch = ticket(11L, "VPN error", "无法连接 error", TicketPriority.HIGH);
        Ticket newerStrongMatch = ticket(12L, "VPN error", "无法连接 error", TicketPriority.MEDIUM);
        Ticket weakMatch = ticket(99L, "VPN", "打印机耗材", TicketPriority.LOW);

        assertThat(matcher.rank(
                        java.util.List.of(weakMatch, olderStrongMatch, newerStrongMatch),
                        java.util.List.of("vpn", "error", "无法连接")
                ))
                .extracting("ticketId")
                .containsExactly(12L, 11L, 99L);
    }

    @Test
    void shouldExplainMatchedFieldsAndIgnoreZeroScoreCandidates() {
        Ticket matched = ticket(21L, "VPN 故障", "客户端无法连接", TicketPriority.HIGH);
        Ticket unmatched = ticket(22L, "打印机", "耗材提醒", TicketPriority.LOW);

        assertThat(matcher.rank(java.util.List.of(unmatched, matched), java.util.List.of("vpn", "无法连接")))
                .hasSize(1)
                .first()
                .satisfies(response -> {
                    assertThat(response.ticketId()).isEqualTo(21L);
                    assertThat(response.score()).isEqualTo(2);
                    assertThat(response.matchedKeywords()).containsExactly("vpn", "无法连接");
                    assertThat(response.matchSummary()).contains("命中关键词：vpn、无法连接", "来源：标题、描述");
                });
    }

    private Ticket ticket(Long id, String title, String description, TicketPriority priority) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setPriority(priority);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setConversationId(1L);
        ticket.setCreatedBy(1L);
        return ticket;
    }
}
