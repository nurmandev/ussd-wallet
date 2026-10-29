package com.nurudeen.ussd_wallet.ussd.session;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UssdSessionRepository extends JpaRepository<UssdSession, String> {

    @Modifying
    @Query(value = """
            INSERT INTO ussd_sessions (
                session_id, phone_number, service_code, network_code, current_menu,
                current_step, session_data, session_status, started_at,
                last_activity_at, expires_at, created_at, updated_at, version
            ) VALUES (
                :sessionId, :phoneNumber, :serviceCode, :networkCode, 'main',
                'START', '{}'::jsonb, 'ACTIVE', :now, :now, :expiresAt, :now, :now, 0
            ) ON CONFLICT (session_id) DO NOTHING
            """, nativeQuery = true)
    int createIfAbsent(
            @Param("sessionId") String sessionId,
            @Param("phoneNumber") String phoneNumber,
            @Param("serviceCode") String serviceCode,
            @Param("networkCode") String networkCode,
            @Param("now") Instant now,
            @Param("expiresAt") Instant expiresAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from UssdSession session where session.sessionId = :sessionId")
    Optional<UssdSession> findBySessionIdForUpdate(@Param("sessionId") String sessionId);

    @Query(value = """
            SELECT * FROM ussd_sessions
            WHERE session_status IN ('ACTIVE', 'WAITING_FOR_INPUT', 'PROCESSING')
              AND expires_at <= :now
            FOR UPDATE
            """, nativeQuery = true)
    List<UssdSession> findOpenExpired(@Param("now") Instant now);

    @Modifying
    @Query("delete from UssdSession session where session.completedAt < :retentionCutoff")
    int deleteCompletedBefore(@Param("retentionCutoff") Instant retentionCutoff);
}
