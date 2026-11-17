package vn.weathergis.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.weathergis.chat.config.ChatSessionProperties;
import vn.weathergis.chat.exception.ChatSessionNotFoundException;
import vn.weathergis.chat.repository.ChatSessionRepository;
import vn.weathergis.chat.service.ChatSessionCleanupScheduler;
import vn.weathergis.chat.service.ChatSessionService;
import vn.weathergis.chat.service.ChatTokenService;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

/**
 * Retention job verification (D4, R02/R04) on real PostGIS with a fixed UTC clock: sessions
 * inactive beyond 30 days are deleted with their messages (FK cascade), the exactly-30-day boundary
 * and active sessions survive, the job is idempotent, a deleted session's token resolves to 404,
 * and an activity touch refreshes the anchor so an active session is never reaped.
 */
@SpringBootTest
class ChatSessionCleanupIT extends AbstractPostGisIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");

    @Autowired private ChatSessionRepository sessionRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private final ChatTokenService tokenService = new ChatTokenService();
    private ChatSessionCleanupScheduler scheduler;
    private ChatSessionService sessionService;
    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ChatSessionProperties properties = new ChatSessionProperties();
        scheduler = new ChatSessionCleanupScheduler(sessionRepository, properties, clock);
        sessionService = new ChatSessionService(sessionRepository, tokenService, properties, clock);
        txTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanFixtures() {
        jdbc.update("DELETE FROM chat_sessions WHERE title LIKE 'W503%'");
    }

    private UUID insertSession(String token, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO chat_sessions (id, token_hash, title, created_at, updated_at) VALUES"
                        + " (?, ?, 'W503cleanup', ?, ?)",
                id,
                tokenService.hash(token),
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofDays(40)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
        return id;
    }

    private void insertMessage(UUID sessionId) {
        jdbc.update(
                "INSERT INTO chat_messages (id, session_id, sender_type, content, tokens_used,"
                        + " is_fallback, created_at) VALUES (?, ?, 'user', 'nội dung', 0, false, ?)",
                UUID.randomUUID(),
                sessionId,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofDays(35)), ZoneOffset.UTC));
    }

    private int cleanup() {
        return txTemplate.execute(status -> scheduler.runCleanup("test"));
    }

    private boolean sessionExists(UUID id) {
        return jdbc.queryForObject(
                        "SELECT count(*) FROM chat_sessions WHERE id = ?", Long.class, id)
                > 0;
    }

    private long messageCount(UUID sessionId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM chat_messages WHERE session_id = ?", Long.class, sessionId);
    }

    @Test
    void expiredSessionsCascadeMessagesWhileBoundaryAndActiveSurvive() {
        UUID expired = insertSession("expired", NOW.minus(Duration.ofDays(31)));
        UUID boundary = insertSession("boundary", NOW.minus(Duration.ofDays(30)));
        UUID active = insertSession("active", NOW.minus(Duration.ofDays(1)));
        insertMessage(expired);
        insertMessage(expired);
        insertMessage(boundary);
        insertMessage(active);

        int deleted = cleanup();

        assertThat(deleted).isEqualTo(1);
        assertThat(sessionExists(expired)).isFalse();
        assertThat(messageCount(expired)).isZero();
        assertThat(sessionExists(boundary)).isTrue();
        assertThat(messageCount(boundary)).isEqualTo(1);
        assertThat(sessionExists(active)).isTrue();
        assertThat(messageCount(active)).isEqualTo(1);
    }

    @Test
    void cleanupIsIdempotent() {
        insertSession("expired", NOW.minus(Duration.ofDays(40)));

        assertThat(cleanup()).isEqualTo(1);
        assertThat(cleanup()).isZero();
    }

    @Test
    void deletedSessionTokenResolvesTo404() {
        UUID expired = insertSession("expired-token", NOW.minus(Duration.ofDays(31)));

        cleanup();

        assertThatThrownBy(() -> sessionService.authenticate(expired, "expired-token"))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    @Test
    void activityTouchRefreshesAnchorSoSessionIsNotReaped() {
        UUID stale = insertSession("touched-token", NOW.minus(Duration.ofDays(31)));

        txTemplate.executeWithoutResult(status -> sessionRepository.touchUpdatedAt(stale));
        cleanup();

        assertThat(sessionExists(stale)).isTrue();
    }
}
