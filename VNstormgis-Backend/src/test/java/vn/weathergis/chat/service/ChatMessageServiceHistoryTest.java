package vn.weathergis.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import vn.weathergis.chat.context.ChatContextService;
import vn.weathergis.chat.domain.ChatMessageEntity;
import vn.weathergis.chat.domain.ChatSessionEntity;
import vn.weathergis.chat.dto.ChatMessageView;
import vn.weathergis.chat.exception.ChatSessionNotFoundException;
import vn.weathergis.chat.exception.ChatValidationException;
import vn.weathergis.chat.intent.IntentClassifier;
import vn.weathergis.chat.port.ChatRateLimiter;
import vn.weathergis.chat.port.ChatReplyGenerator;
import vn.weathergis.chat.repository.ChatMessageRepository;
import vn.weathergis.common.exception.ErrorCode;

/**
 * History contract §2.4 (R12): stable created_at/id order, forward cursor {@code after}, limit
 * 1–200 validated before auth, empty session → empty array, cursor outside the session → empty page
 * (never reveals other sessions' messages), and no token/tokens_used in the payload.
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceHistoryTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("7c8e9f10-1234-5678-abcd-ef0123456789");
    private static final UUID MSG_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MSG_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ALERT_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    @Mock private ChatMessageValidator validator;
    @Mock private ChatSessionService sessionService;
    @Mock private ChatRateLimiter rateLimiter;
    @Mock private IntentClassifier classifier;
    @Mock private ChatContextService contextService;
    @Mock private ChatReplyGenerator replyGenerator;
    @Mock private ChatHistoryWriter historyWriter;
    @Mock private ChatMessageRepository messageRepository;

    private ChatMessageService service;
    private ChatSessionEntity session;

    @BeforeEach
    void setUp() {
        service =
                new ChatMessageService(
                        validator,
                        sessionService,
                        rateLimiter,
                        classifier,
                        contextService,
                        replyGenerator,
                        historyWriter,
                        new SimpleMeterRegistry(),
                        messageRepository);
        session = new ChatSessionEntity();
        session.setId(SESSION_ID);
        session.setTokenHash("a".repeat(64));
    }

    private ChatMessageEntity message(UUID id, String sender, Instant createdAt, UUID alertId) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(id);
        entity.setSessionId(SESSION_ID);
        entity.setSenderType(sender);
        entity.setContent("nội dung " + id);
        entity.setReferencedAlertId(alertId);
        entity.setTokensUsed(99);
        entity.setFallback("assistant".equals(sender));
        entity.setCreatedAt(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        return entity;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 201, 1000})
    void invalidLimitIsRejectedBeforeAuth(int limit) {
        assertThatThrownBy(() -> service.getHistory(SESSION_ID, "token-a", limit, null))
                .isInstanceOf(ChatValidationException.class)
                .satisfies(
                        ex -> {
                            ChatValidationException v = (ChatValidationException) ex;
                            assertThat(v.getErrorCode()).isEqualTo(ErrorCode.CHAT_VALIDATION);
                            assertThat(v.getField()).isEqualTo("limit");
                        });
        verifyNoInteractions(sessionService, messageRepository);
    }

    @Test
    void emptySessionReturnsEmptyList() {
        when(sessionService.authenticate(SESSION_ID, "token-a")).thenReturn(session);
        when(messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(
                        eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.getHistory(SESSION_ID, "token-a", 50, null)).isEmpty();
    }

    @Test
    void firstPageMapsFieldsWithoutTokenOrTokensUsed() {
        when(sessionService.authenticate(SESSION_ID, "token-a")).thenReturn(session);
        when(messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(
                        eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(
                        List.of(
                                message(MSG_1, "user", NOW, ALERT_ID),
                                message(MSG_2, "assistant", NOW, null)));

        List<ChatMessageView> views = service.getHistory(SESSION_ID, "token-a", 50, null);

        assertThat(views).hasSize(2);
        ChatMessageView user = views.get(0);
        assertThat(user.id()).isEqualTo(MSG_1);
        assertThat(user.senderType()).isEqualTo("user");
        assertThat(user.content()).isEqualTo("nội dung " + MSG_1);
        assertThat(user.referencedAlertId()).isEqualTo(ALERT_ID);
        assertThat(user.isFallback()).isFalse();
        assertThat(user.createdAt()).isEqualTo(NOW);
        ChatMessageView assistant = views.get(1);
        assertThat(assistant.isFallback()).isTrue();
        assertThat(assistant.referencedAlertId()).isNull();
        assertThat(ChatMessageView.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("tokens_used", "session_token", "tokensUsed", "sessionToken");
    }

    @Test
    void limitBoundaries1And200AreAccepted() {
        when(sessionService.authenticate(SESSION_ID, "token-a")).thenReturn(session);
        when(messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(
                        eq(SESSION_ID), any(Pageable.class)))
                .thenReturn(List.of());

        service.getHistory(SESSION_ID, "token-a", 1, null);
        service.getHistory(SESSION_ID, "token-a", 200, null);

        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository, org.mockito.Mockito.times(2))
                .findBySessionIdOrderByCreatedAtAscIdAsc(eq(SESSION_ID), captor.capture());
        assertThat(captor.getAllValues().get(0).getPageSize()).isEqualTo(1);
        assertThat(captor.getAllValues().get(1).getPageSize()).isEqualTo(200);
    }

    @Test
    void cursorInsideSessionUsesKeysetQueryWithCursorTimestampAndId() {
        ChatMessageEntity cursor = message(MSG_1, "assistant", NOW, null);
        when(sessionService.authenticate(SESSION_ID, "token-a")).thenReturn(session);
        when(messageRepository.findByIdAndSessionId(MSG_1, SESSION_ID))
                .thenReturn(Optional.of(cursor));
        when(messageRepository.findPageAfterCursor(
                        eq(SESSION_ID),
                        eq(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)),
                        eq(MSG_1),
                        any(Pageable.class)))
                .thenReturn(List.of(message(MSG_2, "user", NOW.plusSeconds(1), null)));

        List<ChatMessageView> views = service.getHistory(SESSION_ID, "token-a", 50, MSG_1);

        assertThat(views).extracting(ChatMessageView::id).containsExactly(MSG_2);
    }

    @Test
    void cursorOutsideSessionReturnsEmptyPageWithoutKeysetQuery() {
        when(sessionService.authenticate(SESSION_ID, "token-a")).thenReturn(session);
        when(messageRepository.findByIdAndSessionId(MSG_2, SESSION_ID))
                .thenReturn(Optional.empty());

        assertThat(service.getHistory(SESSION_ID, "token-a", 50, MSG_2)).isEmpty();
        verify(messageRepository, org.mockito.Mockito.never())
                .findPageAfterCursor(any(), any(), any(), any());
    }

    @Test
    void authFailurePropagatesBeforeAnyMessageQuery() {
        when(sessionService.authenticate(SESSION_ID, "token-a"))
                .thenThrow(new ChatSessionNotFoundException());

        assertThatThrownBy(() -> service.getHistory(SESSION_ID, "token-a", 50, null))
                .isInstanceOf(ChatSessionNotFoundException.class);
        verifyNoInteractions(messageRepository);
    }
}
