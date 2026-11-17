package vn.weathergis.chat.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.weathergis.chat.domain.ChatMessageEntity;

/**
 * Message persistence for the atomic pair (D10) and stable keyset history (contract §2.4, R12).
 * Order is always {@code created_at ASC, id ASC}; the V5 index {@code (session_id, created_at)}
 * serves it — no offset pagination, no new index.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    Optional<ChatMessageEntity> findByIdAndSessionId(UUID id, UUID sessionId);

    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAscIdAsc(
            UUID sessionId, Pageable pageable);

    @Query(
            "select m from ChatMessageEntity m where m.sessionId = :sessionId "
                    + "and (m.createdAt > :cursorCreatedAt "
                    + "or (m.createdAt = :cursorCreatedAt and m.id > :cursorId)) "
                    + "order by m.createdAt asc, m.id asc")
    List<ChatMessageEntity> findPageAfterCursor(
            @Param("sessionId") UUID sessionId,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
