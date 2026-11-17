package vn.weathergis.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.weathergis.chat.context.ChatContextOutcome;
import vn.weathergis.chat.context.ChatContextRequest;
import vn.weathergis.chat.context.ChatContextResult;
import vn.weathergis.chat.context.ChatContextService;
import vn.weathergis.chat.context.FactualContext;
import vn.weathergis.chat.domain.ChatMessageEntity;
import vn.weathergis.chat.domain.ChatSessionEntity;
import vn.weathergis.chat.dto.SendMessageRequest;
import vn.weathergis.chat.dto.SendMessageResponse;
import vn.weathergis.chat.exception.ChatAiUnavailableException;
import vn.weathergis.chat.exception.ChatAlertNotFoundException;
import vn.weathergis.chat.exception.ChatException;
import vn.weathergis.chat.exception.ChatSessionNotFoundException;
import vn.weathergis.chat.exception.ChatValidationException;
import vn.weathergis.chat.intent.ChatIntent;
import vn.weathergis.chat.intent.IntentClassification;
import vn.weathergis.chat.intent.IntentClassifier;
import vn.weathergis.chat.port.ChatRateLimitKey;
import vn.weathergis.chat.port.ChatRateLimiter;
import vn.weathergis.chat.port.ChatReplyGenerator;
import vn.weathergis.chat.port.ChatReplyResult;
import vn.weathergis.common.exception.ErrorCode;

/**
 * Orchestration contract §2.3/§3.2 (D4/D9/D10): validation → session auth → rate-limit seam →
 * intent/context → reply port (outside any transaction) → atomic pair write. Rejected requests
 * never touch history; an Unavailable reply outcome is 503 with no invented content.
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("7c8e9f10-1234-5678-abcd-ef0123456789");
    private static final UUID ASSISTANT_ID =
            UUID.fromString("9e0f1a22-3456-789a-cdef-0123456789ab");
    private static final UUID ALERT_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final String TOKEN_HASH = "a".repeat(64);

    @Mock private ChatMessageValidator validator;
    @Mock private ChatSessionService sessionService;
    @Mock private ChatRateLimiter rateLimiter;
    @Mock private IntentClassifier classifier;
    @Mock private ChatContextService contextService;
    @Mock private ChatReplyGenerator replyGenerator;
    @Mock private ChatHistoryWriter historyWriter;
    @Mock private vn.weathergis.chat.repository.ChatMessageRepository messageRepository;

    private SimpleMeterRegistry meterRegistry;
    private ChatMessageService service;

    private ChatSessionEntity session;
    private SendMessageRequest raw;
    private ChatContextRequest validated;
    private IntentClassification classification;
    private ChatContextResult readyContext;
    private ChatMessageEntity assistant;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service =
                new ChatMessageService(
                        validator,
                        sessionService,
                        rateLimiter,
                        classifier,
                        contextService,
                        replyGenerator,
                        historyWriter,
                        meterRegistry,
                        messageRepository);

        session = new ChatSessionEntity();
        session.setId(SESSION_ID);
        session.setTokenHash(TOKEN_HASH);
        session.setUpdatedAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));

        raw = new SendMessageRequest("  Câu hỏi mẫu  ", null, null);
        validated = new ChatContextRequest("Câu hỏi mẫu", null, null);
        classification =
                new IntentClassification(
                        ChatIntent.RAIN_TOP, "R3", "chat-intent-v1", null, null, null, null);
        readyContext =
                new ChatContextResult(
                        ChatContextOutcome.CONTEXT_READY,
                        null,
                        new FactualContext(
                                FactualContext.SCHEMA_VERSION,
                                ChatIntent.RAIN_TOP,
                                NOW,
                                ChatContextOutcome.CONTEXT_READY,
                                null,
                                List.of(),
                                List.of(),
                                null,
                                new FactualContext.ReferencedEntities(
                                        List.of("DNG"), List.of(), List.of(ALERT_ID)),
                                new FactualContext.ContextMeta(60, 0, 0, false),
                                100));
        assistant = new ChatMessageEntity();
        assistant.setId(ASSISTANT_ID);
        assistant.setCreatedAt(OffsetDateTime.ofInstant(NOW.plusSeconds(5), ZoneOffset.UTC));
    }

    private void stubHappyPath(ChatReplyResult reply) {
        when(validator.validate(raw)).thenReturn(validated);
        when(sessionService.authenticate(SESSION_ID, "token-a")).thenReturn(session);
        when(classifier.classify("Câu hỏi mẫu", null)).thenReturn(classification);
        when(contextService.retrieve(validated, classification)).thenReturn(readyContext);
        when(replyGenerator.generate(classification, readyContext, "Câu hỏi mẫu"))
                .thenReturn(reply);
        when(historyWriter.writePair(session, validated, (ChatReplyResult.Reply) reply))
                .thenReturn(assistant);
    }

    @Test
    void happyPathFollowsContractOrderAndReturnsTheStoredAssistantRecord() {
        ChatReplyResult.Reply reply = new ChatReplyResult.Reply("Trả lời đã kiểm chứng", false, 42);
        stubHappyPath(reply);

        SendMessageResponse response =
                service.sendMessage(SESSION_ID, "token-a", raw, "203.0.113.7", "203.0.113.7");

        assertThat(response.messageId()).isEqualTo(ASSISTANT_ID);
        assertThat(response.reply()).isEqualTo("Trả lời đã kiểm chứng");
        assertThat(response.isFallback()).isFalse();
        assertThat(response.referencedEntities())
                .isEqualTo(readyContext.context().referencedEntities());
        assertThat(response.createdAt()).isEqualTo(NOW.plusSeconds(5));

        InOrder order =
                inOrder(
                        validator,
                        sessionService,
                        rateLimiter,
                        classifier,
                        contextService,
                        replyGenerator,
                        historyWriter);
        order.verify(validator).validate(raw);
        order.verify(sessionService).authenticate(SESSION_ID, "token-a");
        order.verify(rateLimiter)
                .acquire(new ChatRateLimitKey(TOKEN_HASH, "203.0.113.7", "203.0.113.7"));
        order.verify(classifier).classify("Câu hỏi mẫu", null);
        order.verify(contextService).retrieve(validated, classification);
        order.verify(replyGenerator).generate(classification, readyContext, "Câu hỏi mẫu");
        order.verify(historyWriter).writePair(session, validated, reply);
    }

    @Test
    void fallbackReplyIsPassedThroughWithIsFallbackAndTokens() {
        ChatReplyResult.Reply fallback = new ChatReplyResult.Reply("Trả lời tự động", true, 0);
        stubHappyPath(fallback);

        SendMessageResponse response =
                service.sendMessage(SESSION_ID, "token-a", raw, "203.0.113.7", null);

        assertThat(response.isFallback()).isTrue();
        assertThat(response.reply()).isEqualTo("Trả lời tự động");
        verify(historyWriter).writePair(session, validated, fallback);
    }

    @Test
    void unavailableReplyIs503AndNothingIsWritten() {
        when(validator.validate(raw)).thenReturn(validated);
        when(sessionService.authenticate(SESSION_ID, "token-a")).thenReturn(session);
        when(classifier.classify("Câu hỏi mẫu", null)).thenReturn(classification);
        when(contextService.retrieve(validated, classification)).thenReturn(readyContext);
        when(replyGenerator.generate(classification, readyContext, "Câu hỏi mẫu"))
                .thenReturn(new ChatReplyResult.Unavailable());

        assertThatThrownBy(
                        () -> service.sendMessage(SESSION_ID, "token-a", raw, "203.0.113.7", null))
                .isInstanceOf(ChatAiUnavailableException.class)
                .satisfies(
                        ex ->
                                assertThat(((ChatAiUnavailableException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.CHAT_AI_UNAVAILABLE));
        verifyNoInteractions(historyWriter);
    }

    @Test
    void alertNotFoundOutcomeMapsTo404BeforeReplyPortAndWrite() {
        when(validator.validate(raw)).thenReturn(validated);
        when(sessionService.authenticate(SESSION_ID, "token-a")).thenReturn(session);
        when(classifier.classify("Câu hỏi mẫu", null)).thenReturn(classification);
        when(contextService.retrieve(validated, classification))
                .thenReturn(
                        new ChatContextResult(
                                ChatContextOutcome.ALERT_NOT_FOUND, null, readyContext.context()));

        assertThatThrownBy(
                        () -> service.sendMessage(SESSION_ID, "token-a", raw, "203.0.113.7", null))
                .isInstanceOf(ChatAlertNotFoundException.class)
                .satisfies(
                        ex ->
                                assertThat(((ChatAlertNotFoundException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.CHAT_ALERT_NOT_FOUND));
        verifyNoInteractions(replyGenerator, historyWriter);
    }

    @Test
    void validationFailureNeverReachesAuthLimiterContextOrWrite() {
        when(validator.validate(raw))
                .thenThrow(
                        new ChatValidationException(ErrorCode.CHAT_VALIDATION, "message", "rỗng"));

        assertThatThrownBy(
                        () -> service.sendMessage(SESSION_ID, "token-a", raw, "203.0.113.7", null))
                .isInstanceOf(ChatValidationException.class);
        verifyNoInteractions(
                sessionService,
                rateLimiter,
                classifier,
                contextService,
                replyGenerator,
                historyWriter);
    }

    @Test
    void sessionFailureNeverReachesLimiterContextOrWrite() {
        when(validator.validate(raw)).thenReturn(validated);
        when(sessionService.authenticate(SESSION_ID, "token-a"))
                .thenThrow(new ChatSessionNotFoundException());

        assertThatThrownBy(
                        () -> service.sendMessage(SESSION_ID, "token-a", raw, "203.0.113.7", null))
                .isInstanceOf(ChatSessionNotFoundException.class);
        verifyNoInteractions(
                rateLimiter, classifier, contextService, replyGenerator, historyWriter);
    }

    @Test
    void rateLimitRejectionBlocksContextReplyAndWrite() {
        when(validator.validate(raw)).thenReturn(validated);
        when(sessionService.authenticate(SESSION_ID, "token-a")).thenReturn(session);
        org.mockito.Mockito.doThrow(
                        new ChatException(ErrorCode.CHAT_RATE_LIMITED, "rate-limited", "chờ"))
                .when(rateLimiter)
                .acquire(any(ChatRateLimitKey.class));

        assertThatThrownBy(
                        () -> service.sendMessage(SESSION_ID, "token-a", raw, "203.0.113.7", null))
                .isInstanceOf(ChatException.class);
        verifyNoInteractions(classifier, contextService, replyGenerator, historyWriter);
    }

    @Test
    void databaseFailurePropagatesAndIsNotConvertedToNoData() {
        when(validator.validate(raw)).thenReturn(validated);
        when(sessionService.authenticate(SESSION_ID, "token-a")).thenReturn(session);
        when(classifier.classify("Câu hỏi mẫu", null)).thenReturn(classification);
        when(contextService.retrieve(validated, classification))
                .thenThrow(new DataAccessResourceFailureException("DB down"));

        assertThatThrownBy(
                        () -> service.sendMessage(SESSION_ID, "token-a", raw, "203.0.113.7", null))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(replyGenerator, historyWriter);
    }

    @Test
    void requestMetricIsTaggedWithOutcome() {
        stubHappyPath(new ChatReplyResult.Reply("ok", false, 1));
        service.sendMessage(SESSION_ID, "token-a", raw, "203.0.113.7", null);
        assertThat(
                        meterRegistry
                                .get("weathergis.chat.requests")
                                .tag("outcome", "success")
                                .counter()
                                .count())
                .isEqualTo(1.0);

        when(validator.validate(raw))
                .thenThrow(
                        new ChatValidationException(ErrorCode.CHAT_VALIDATION, "message", "rỗng"));
        assertThatThrownBy(
                        () -> service.sendMessage(SESSION_ID, "token-a", raw, "203.0.113.7", null))
                .isInstanceOf(ChatValidationException.class);
        assertThat(
                        meterRegistry
                                .get("weathergis.chat.requests")
                                .tag("outcome", "CHT_400_VALIDATION")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    void infoLogsCarrySessionAndOutcomeButNeverContentOrToken() {
        Logger logger = (Logger) LoggerFactory.getLogger(ChatMessageService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String secretQuestion = "BÍ_MẬT_CÂU_HỎI";
            String secretReply = "BÍ_MẬT_TRẢ_LỜI";
            String secretToken = "BÍ_MẬT_TOKEN";
            SendMessageRequest secretRaw = new SendMessageRequest(secretQuestion, null, null);
            ChatContextRequest secretValidated = new ChatContextRequest(secretQuestion, null, null);
            when(validator.validate(secretRaw)).thenReturn(secretValidated);
            when(sessionService.authenticate(SESSION_ID, secretToken)).thenReturn(session);
            when(classifier.classify(secretQuestion, null)).thenReturn(classification);
            when(contextService.retrieve(secretValidated, classification)).thenReturn(readyContext);
            ChatReplyResult.Reply reply = new ChatReplyResult.Reply(secretReply, false, 3);
            when(replyGenerator.generate(classification, readyContext, secretQuestion))
                    .thenReturn(reply);
            when(historyWriter.writePair(session, secretValidated, reply)).thenReturn(assistant);

            service.sendMessage(SESSION_ID, secretToken, secretRaw, "203.0.113.7", null);

            assertThat(appender.list).isNotEmpty();
            for (ILoggingEvent event : appender.list) {
                if (event.getLevel() == Level.INFO) {
                    String message = event.getFormattedMessage();
                    assertThat(message).doesNotContain(secretQuestion, secretReply, secretToken);
                    assertThat(message).contains(SESSION_ID.toString());
                }
            }
        } finally {
            logger.detachAppender(appender);
        }
    }
}
