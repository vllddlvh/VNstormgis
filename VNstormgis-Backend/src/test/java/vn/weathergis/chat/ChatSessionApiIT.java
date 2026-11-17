package vn.weathergis.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.weathergis.chat.config.ChatSessionProperties;
import vn.weathergis.chat.context.ChatContextService;
import vn.weathergis.chat.domain.ChatMessageEntity;
import vn.weathergis.chat.dto.ChatMessageView;
import vn.weathergis.chat.dto.CreateSessionResponse;
import vn.weathergis.chat.dto.SendMessageRequest;
import vn.weathergis.chat.dto.SendMessageResponse;
import vn.weathergis.chat.exception.ChatAiUnavailableException;
import vn.weathergis.chat.exception.ChatAlertNotFoundException;
import vn.weathergis.chat.exception.ChatSessionNotFoundException;
import vn.weathergis.chat.intent.IntentClassifier;
import vn.weathergis.chat.port.ChatRateLimiter;
import vn.weathergis.chat.port.ChatReplyGenerator;
import vn.weathergis.chat.port.ChatReplyResult;
import vn.weathergis.chat.port.NoOpChatRateLimiter;
import vn.weathergis.chat.repository.ChatMessageRepository;
import vn.weathergis.chat.repository.ChatSessionRepository;
import vn.weathergis.chat.service.ChatHistoryWriter;
import vn.weathergis.chat.service.ChatMessageService;
import vn.weathergis.chat.service.ChatMessageValidator;
import vn.weathergis.chat.service.ChatSessionService;
import vn.weathergis.chat.service.ChatTokenService;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

/**
 * Real PostGIS verification of the W5-03 session/history/persistence layer (contract §8, R01/R02/
 * R10/R12/R21/R22): isolated W503 fixtures, fixed UTC clock, stubbed reply port for the 200 path
 * (clearly NOT real AI — W5-04/07). Non-transactional so the atomic-write fault injection proves a
 * genuine independent rollback; fixtures cleaned by title prefix after each test.
 */
@SpringBootTest
class ChatSessionApiIT extends AbstractPostGisIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final String QUESTION = "Khu vực nào đang có mưa to nhất?";

    @Autowired private ChatSessionRepository sessionRepository;
    @SpyBean private ChatMessageRepository messageRepository;
    @Autowired private ChatHistoryWriter historyWriter;
    @Autowired private IntentClassifier classifier;
    @Autowired private ChatContextService contextService;
    @Autowired private ChatMessageValidator validator;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    private final ChatTokenService tokenService = new ChatTokenService();
    private final AtomicReference<ChatReplyResult> portOutcome =
            new AtomicReference<>(new ChatReplyResult.Reply("Trả lời W503", false, 3));
    private final AtomicReference<vn.weathergis.chat.context.ChatContextResult> lastContext =
            new AtomicReference<>();

    private ChatSessionService sessionService;
    private ChatMessageService messageService;
    private ChatSessionProperties properties;
    private long messageSeq;

    @BeforeEach
    void wireServicesWithFixedClockAndStubPort() {
        properties = new ChatSessionProperties();
        messageSeq = 0;
        portOutcome.set(new ChatReplyResult.Reply("Trả lời W503", false, 3));
        lastContext.set(null);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        sessionService = new ChatSessionService(sessionRepository, tokenService, properties, clock);
        ChatReplyGenerator stubPort =
                (classification, context, question) -> {
                    lastContext.set(context);
                    return portOutcome.get();
                };
        ChatRateLimiter noOp = new NoOpChatRateLimiter();
        messageService =
                new ChatMessageService(
                        validator,
                        sessionService,
                        noOp,
                        classifier,
                        contextService,
                        stubPort,
                        historyWriter,
                        new SimpleMeterRegistry(),
                        messageRepository);
    }

    @AfterEach
    void cleanFixtures() {
        jdbc.update("DELETE FROM chat_sessions WHERE title LIKE 'W503%'");
        jdbc.update("DELETE FROM alerts WHERE title LIKE 'W503%'");
        org.mockito.Mockito.reset(messageRepository);
    }

    private CreateSessionResponse newSession() {
        CreateSessionResponse response = sessionService.createSession();
        jdbc.update("UPDATE chat_sessions SET title = 'W503' WHERE id = ?", response.sessionId());
        return response;
    }

    private UUID insertSession(String token, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO chat_sessions (id, token_hash, title, created_at, updated_at) VALUES"
                        + " (?, ?, 'W503manual', ?, ?)",
                id,
                tokenService.hash(token),
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofDays(40)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
        return id;
    }

    private UUID insertMessage(UUID sessionId, String sender, Instant createdAt, UUID alertId) {
        // ascending deterministic ids so insertion order matches the created_at,id ASC tie-break
        UUID id = new UUID(0L, ++messageSeq);
        jdbc.update(
                "INSERT INTO chat_messages (id, session_id, sender_type, content, referenced_alert_id,"
                        + " tokens_used, is_fallback, created_at) VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
                id,
                sessionId,
                sender,
                "nội dung " + sender,
                alertId,
                "assistant".equals(sender),
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        return id;
    }

    private UUID insertAlert() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO alerts (id, title, severity_level, trigger_reason, ai_explanation, status)"
                        + " VALUES (?, 'W503alert', 'DANGER', 'rain_1h >= 50', 'giải thích', 'active')",
                id);
        return id;
    }

    private SendMessageRequest question(String alertId) {
        return new SendMessageRequest(QUESTION, alertId, null);
    }

    @Test
    void crossSessionTokenIsUniform404AndNeverLeaksOtherSessionData() {
        CreateSessionResponse a = newSession();
        CreateSessionResponse b = newSession();

        assertThatThrownBy(() -> sessionService.authenticate(b.sessionId(), a.sessionToken()))
                .isInstanceOf(ChatSessionNotFoundException.class);
        assertThatThrownBy(
                        () -> messageService.getHistory(b.sessionId(), a.sessionToken(), 50, null))
                .isInstanceOf(ChatSessionNotFoundException.class);
        assertThat(sessionService.authenticate(a.sessionId(), a.sessionToken()).getId())
                .isEqualTo(a.sessionId());
    }

    @Test
    void expiredSessionIs404AtAuthBeforeAnyCleanupJob() {
        UUID expired = insertSession("expired-token", NOW.minus(Duration.ofDays(31)));
        UUID boundary = insertSession("boundary-token", NOW.minus(Duration.ofDays(30)));

        assertThatThrownBy(() -> sessionService.authenticate(expired, "expired-token"))
                .isInstanceOf(ChatSessionNotFoundException.class);
        assertThat(sessionService.authenticate(boundary, "boundary-token").getId())
                .isEqualTo(boundary);
    }

    @Test
    void acceptedQuestionWritesAtomicPairAndTouchesSessionActivity() {
        CreateSessionResponse session = newSession();
        OffsetDateTime before =
                jdbc.queryForObject(
                        "SELECT updated_at FROM chat_sessions WHERE id = ?",
                        OffsetDateTime.class,
                        session.sessionId());

        SendMessageResponse response =
                messageService.sendMessage(
                        session.sessionId(),
                        session.sessionToken(),
                        question(null),
                        "203.0.113.7",
                        null);

        assertThat(response.reply()).isEqualTo("Trả lời W503");
        assertThat(response.messageId()).isNotNull();
        List<ChatMessageEntity> rows =
                messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(
                        session.sessionId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(rows).hasSize(2);
        // equal created_at → order tie-breaks by random UUID id, so assert by sender, not position
        ChatMessageEntity user =
                rows.stream()
                        .filter(m -> m.getSenderType().equals("user"))
                        .findFirst()
                        .orElseThrow();
        ChatMessageEntity assistant =
                rows.stream()
                        .filter(m -> m.getSenderType().equals("assistant"))
                        .findFirst()
                        .orElseThrow();
        assertThat(user.getContent()).isEqualTo(QUESTION);
        assertThat(user.getTokensUsed()).isZero();
        assertThat(assistant.getId()).isEqualTo(response.messageId());
        assertThat(assistant.getContent()).isEqualTo("Trả lời W503");
        assertThat(assistant.getTokensUsed()).isEqualTo(3);
        OffsetDateTime after =
                jdbc.queryForObject(
                        "SELECT updated_at FROM chat_sessions WHERE id = ?",
                        OffsetDateTime.class,
                        session.sessionId());
        assertThat(after).isAfterOrEqualTo(before);
    }

    @Test
    void unavailableReplyWritesNoHistory() {
        // Narrow §3.3 branch: port cannot build even a fallback (W5-04 keeps the mapping).
        CreateSessionResponse session = newSession();
        portOutcome.set(new ChatReplyResult.Unavailable());

        assertThatThrownBy(
                        () ->
                                messageService.sendMessage(
                                        session.sessionId(),
                                        session.sessionToken(),
                                        question(null),
                                        "203.0.113.7",
                                        null))
                .isInstanceOf(ChatAiUnavailableException.class);
        assertThat(
                        messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(
                                session.sessionId(),
                                org.springframework.data.domain.PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    void secondSaveFailureRollsBackTheWholePair() {
        CreateSessionResponse session = newSession();
        java.util.concurrent.atomic.AtomicInteger saveCalls =
                new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doThrow(
                        new org.springframework.dao.DataAccessResourceFailureException(
                                "fault injection"))
                .when(messageRepository)
                .save(
                        org.mockito.ArgumentMatchers.argThat(
                                entity -> saveCalls.incrementAndGet() >= 2));

        assertThatThrownBy(
                        () ->
                                messageService.sendMessage(
                                        session.sessionId(),
                                        session.sessionToken(),
                                        question(null),
                                        "203.0.113.7",
                                        null))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM chat_messages WHERE session_id = ?",
                                Long.class,
                                session.sessionId()))
                .isZero();
    }

    @Test
    void historyIsStableWithEqualTimestampsAndCursorHasNoDupOrGap() {
        UUID session = insertSession("history-token", NOW.minus(Duration.ofHours(1)));
        UUID m1 = insertMessage(session, "user", NOW, null);
        UUID m2 = insertMessage(session, "assistant", NOW, null);
        UUID m3 = insertMessage(session, "user", NOW.plusSeconds(1), null);

        List<ChatMessageView> page1 = messageService.getHistory(session, "history-token", 2, null);
        assertThat(page1).extracting(ChatMessageView::id).containsExactly(m1, m2);
        List<ChatMessageView> page2 =
                messageService.getHistory(session, "history-token", 2, page1.get(1).id());
        assertThat(page2).extracting(ChatMessageView::id).containsExactly(m3);
        List<ChatMessageView> page3 =
                messageService.getHistory(session, "history-token", 2, page2.get(0).id());
        assertThat(page3).isEmpty();
    }

    @Test
    void cursorFromAnotherSessionYieldsEmptyPage() {
        UUID session = insertSession("history-token", NOW.minus(Duration.ofHours(1)));
        UUID other = insertSession("other-token", NOW.minus(Duration.ofHours(1)));
        UUID foreignMessage = insertMessage(other, "user", NOW, null);
        insertMessage(session, "user", NOW, null);

        assertThat(messageService.getHistory(session, "history-token", 50, foreignMessage))
                .isEmpty();
    }

    @Test
    void historyNeverExposesTokenOrTokensUsed() throws Exception {
        UUID session = insertSession("history-token", NOW.minus(Duration.ofHours(1)));
        insertMessage(session, "assistant", NOW, null);

        List<ChatMessageView> views = messageService.getHistory(session, "history-token", 50, null);
        String json = objectMapper.writeValueAsString(views);
        assertThat(json).doesNotContain("tokens_used", "session_token", "token_hash");
        assertThat(json).contains("is_fallback");
    }

    @Test
    void referencedAlertIdBecomesNullAfterAlertDeletion() {
        UUID session = insertSession("history-token", NOW.minus(Duration.ofHours(1)));
        UUID alert = insertAlert();
        insertMessage(session, "user", NOW, alert);

        jdbc.update("DELETE FROM alerts WHERE id = ?", alert);

        List<ChatMessageView> views = messageService.getHistory(session, "history-token", 50, null);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).referencedAlertId()).isNull();
    }

    @Test
    void nonExistentAlertThroughRealContextIs404() {
        CreateSessionResponse session = newSession();
        UUID missingAlert = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                messageService.sendMessage(
                                        session.sessionId(),
                                        session.sessionToken(),
                                        question(missingAlert.toString()),
                                        "203.0.113.7",
                                        null))
                .isInstanceOf(ChatAlertNotFoundException.class);
        assertThat(
                        messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(
                                session.sessionId(),
                                org.springframework.data.domain.PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    void validBboxOutsideDataRegionYieldsHonestNoDataWithoutError() throws Exception {
        CreateSessionResponse session = newSession();
        SendMessageRequest request =
                new SendMessageRequest(
                        QUESTION,
                        null,
                        objectMapper.readTree("{\"bbox\":[170.0,80.0,175.0,85.0]}"));

        SendMessageResponse response =
                messageService.sendMessage(
                        session.sessionId(), session.sessionToken(), request, "203.0.113.7", null);

        assertThat(response).isNotNull();
        assertThat(lastContext.get().outcome())
                .isEqualTo(vn.weathergis.chat.context.ChatContextOutcome.NO_DATA);
        assertThat(lastContext.get().noDataReason())
                .isEqualTo(vn.weathergis.chat.context.NoDataReason.EMPTY_BBOX);
    }
}
