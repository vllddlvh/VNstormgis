package vn.weathergis.chat.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.weathergis.chat.domain.ChatSessionEntity;

/** Session lookup is always by token verifier — never by path id alone (contract §2.1). */
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {

    Optional<ChatSessionEntity> findByTokenHash(String tokenHash);

    /**
     * Refreshes the inactivity anchor inside the pair-write transaction (D4, contract §3.4); the V5
     * trigger {@code trg_chat_sessions_updated_at} stamps the value. Without this, an active
     * session would be deleted by the retention job.
     */
    @Modifying
    @Query(
            value = "UPDATE chat_sessions SET updated_at = CURRENT_TIMESTAMP WHERE id = :sessionId",
            nativeQuery = true)
    int touchUpdatedAt(@Param("sessionId") UUID sessionId);

    /** Counts messages that will cascade when expired sessions are deleted (for the INFO log). */
    @Query(
            value =
                    "SELECT count(*) FROM chat_messages WHERE session_id IN "
                            + "(SELECT id FROM chat_sessions WHERE updated_at < :cutoff)",
            nativeQuery = true)
    long countMessagesOfExpiredSessions(@Param("cutoff") OffsetDateTime cutoff);

    /** Retention delete (D4); messages cascade via the V5 FK. Idempotent. */
    @Modifying
    @Query(value = "DELETE FROM chat_sessions WHERE updated_at < :cutoff", nativeQuery = true)
    int deleteExpiredSessions(@Param("cutoff") OffsetDateTime cutoff);
}
