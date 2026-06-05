package com.enterprise.agentplatform.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TokenSessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenSessionCleanupJob.class);

    private final SimpleTokenStore tokenStore;

    public TokenSessionCleanupJob(SimpleTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Scheduled(
            fixedDelayString = "${app.security.expired-token-cleanup-delay-ms:600000}",
            initialDelayString = "${app.security.expired-token-cleanup-initial-delay-ms:60000}"
    )
    public void revokeExpiredTokenSessions() {
        int revokedCount = tokenStore.revokeExpiredSessions();
        if (revokedCount > 0) {
            log.info("Revoked {} expired token sessions", revokedCount);
        }
    }
}
