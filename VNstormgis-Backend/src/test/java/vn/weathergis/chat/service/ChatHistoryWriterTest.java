package vn.weathergis.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.weathergis.chat.context.ChatContextRequest;
import vn.weathergis.chat.domain.ChatMessageEntity;
import vn.weathergis.chat.domain.ChatSessionEntity;
import vn.weathergis.chat.port.ChatReplyResult;
import vn.weathergis.chat.repository.ChatMessageRepository;
import vn.weathergis.chat.repository.ChatSessionRepository;

/**
 * Atomic pair content (D10, contract §2.3/§8.2): user message carries the stripped question,
 * validated alert id and bbox JSONB; assistant carries the port reply, is_fallback and tokens_used;
 * the session activity anchor is touched in the same transaction.
 */
@ExtendWith(MockitoExtension.class)
class ChatHistoryWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("7c8e9f10-1234-5678-abcd-ef0123456789");
    private static final UUID ALERT_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatSessionRepository sessionRepository;

    private ChatHistoryWriter writer;
    private ChatSessionEntity session;

    @BeforeEach
    void setUp() {
        writer =
                new ChatHistoryWriter(
                        messageRepository,
                        sessionRepository,
                        new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC));
        session = new ChatSessionEntity();
        session.setId(SESSION_ID);
        when(messageRepository.save(any(ChatMessageEntity.class)))
                .thenAnswer(
                        invocation -> {
                            ChatMessageEntity entity = invocation.getArgument(0);
                            if (entity.getId() == null) {
                                entity.setId(UUID.randomUUID());
                            }
                            return entity;
                        });
    }

    @Test
    void writePairPersistsUserThenAssistantThenTouchesSession() {
        ChatContextRequest validated =
                new ChatContextRequest(
                        "Câu hỏi đã strip",
                        ALERT_ID,
                        new ChatContextRequest.Bbox(107.5, 15.5, 109.0, 16.8));
        ChatReplyResult.Reply reply = new ChatReplyResult.Reply("Trả lời tự động", true, 7);

        ChatMessageEntity returned = writer.writePair(session, validated, reply);

        ArgumentCaptor<ChatMessageEntity> captor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(messageRepository, times(2)).save(captor.capture());
        ChatMessageEntity user = captor.getAllValues().get(0);
        ChatMessageEntity assistant = captor.getAllValues().get(1);

        assertThat(user.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(user.getSenderType()).isEqualTo("user");
        assertThat(user.getContent()).isEqualTo("Câu hỏi đã strip");
        assertThat(user.getReferencedAlertId()).isEqualTo(ALERT_ID);
        assertThat(user.getSpatialFilter()).isEqualTo("{\"bbox\":[107.5,15.5,109.0,16.8]}");
        assertThat(user.getTokensUsed()).isZero();
        assertThat(user.isFallback()).isFalse();
        assertThat(user.getCreatedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));

        assertThat(assistant.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(assistant.getSenderType()).isEqualTo("assistant");
        assertThat(assistant.getContent()).isEqualTo("Trả lời tự động");
        assertThat(assistant.isFallback()).isTrue();
        assertThat(assistant.getTokensUsed()).isEqualTo(7);
        assertThat(assistant.getReferencedAlertId()).isNull();
        assertThat(assistant.getSpatialFilter()).isNull();
        assertThat(assistant.getCreatedAt())
                .isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(returned).isSameAs(assistant);

        InOrder order = inOrder(messageRepository, sessionRepository);
        order.verify(messageRepository).save(user);
        order.verify(messageRepository).save(assistant);
        order.verify(sessionRepository).touchUpdatedAt(SESSION_ID);
    }

    @Test
    void nullBboxAndNullAlertIdStoreNullColumns() {
        ChatContextRequest validated = new ChatContextRequest("Câu hỏi", null, null);
        ChatReplyResult.Reply reply = new ChatReplyResult.Reply("ok", false, 0);

        writer.writePair(session, validated, reply);

        ArgumentCaptor<ChatMessageEntity> captor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(messageRepository, times(2)).save(captor.capture());
        ChatMessageEntity user = captor.getAllValues().get(0);
        assertThat(user.getSpatialFilter()).isNull();
        assertThat(user.getReferencedAlertId()).isNull();
    }
}
