package com.enterprise.agentplatform.domain.repository;

import com.enterprise.agentplatform.domain.entity.AuthTokenSession;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenSessionRepository extends JpaRepository<AuthTokenSession, Long> {

    Optional<AuthTokenSession> findByTokenHash(String tokenHash);

    Optional<AuthTokenSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthTokenSession> findLockedByRefreshTokenHash(String refreshTokenHash);

    List<AuthTokenSession> findByUsernameOrderByIdDesc(String username);

    List<AuthTokenSession> findTop200ByOrderByIdDesc();

    @Query("""
            select count(session)
            from AuthTokenSession session
            where session.revokedAt is null
              and (
                    (session.refreshExpiresAt is not null and session.refreshExpiresAt > :now)
                    or (session.refreshExpiresAt is null and session.expiresAt > :now)
                  )
            """)
    long countActiveSessions(@Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthTokenSession session
            set session.revokedAt = :now
            where session.tokenHash = :tokenHash
              and session.revokedAt is null
            """)
    int revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthTokenSession session
            set session.revokedAt = :now
            where session.id = :sessionId
              and session.revokedAt is null
            """)
    int revokeById(@Param("sessionId") Long sessionId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthTokenSession session
            set session.revokedAt = :now
            where session.userId = :userId
              and session.revokedAt is null
            """)
    int revokeByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthTokenSession session
            set session.revokedAt = :now
            where (
                    (session.refreshExpiresAt is not null and session.refreshExpiresAt < :now)
                    or (session.refreshExpiresAt is null and session.expiresAt < :now)
                  )
              and session.revokedAt is null
            """)
    int revokeExpiredSessions(@Param("now") LocalDateTime now);
}
