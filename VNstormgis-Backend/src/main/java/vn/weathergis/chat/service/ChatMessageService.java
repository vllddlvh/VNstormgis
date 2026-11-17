package vn.weathergis.chat.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.weathergis.chat.context.ChatContextOutcome;
import vn.weathergis.chat.context.ChatContextRequest;
import vn.weathergis.chat.context.ChatContextResult;
import vn.weathergis.chat.context.ChatContextService;
import vn.weathergis.chat.domain.ChatMessageEntity;
import vn.weathergis.chat.domain.ChatSessionEntity;
import vn.weathergis.chat.dto.ChatMessageView;
import vn.weathergis.chat.dto.SendMessageRequest;
import vn.weathergis.chat.dto.SendMessageResponse;
import vn.weathergis.chat.exception.ChatAiUnavailableException;
import vn.weathergis.chat.exception.ChatAlertNotFoundException;
import vn.weathergis.chat.exception.ChatException;
import vn.weathergis.chat.exception.ChatValidationException;
import vn.weathergis.chat.intent.IntentClassification;
import vn.weathergis.chat.intent.IntentClassifier;
import vn.weathergis.chat.port.ChatRateLimitKey;
import vn.weathergis.chat.port.ChatRateLimiter;
import vn.weathergis.chat.port.ChatReplyGenerator;
import vn.weathergis.chat.port.ChatReplyResult;
import vn.weathergis.chat.repository.ChatMessageRepository;
import vn.weathergis.common.exception.ErrorCode;

/**
 * Question orchestration in the contract §2.3 order: validation → session auth → rate-limit seam →
 * intent/context (W5-02) → reply port (W5-04, outside any transaction) → atomic pair write →
 * response. Requests rejected with 400/401/404/429/503 never write history (D4). INFO logs and the
 * {@code weathergis.chat.requests} metric carry ids/outcomes only — never question, reply or token.
 */
@Service
public class ChatMessageService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageService.class);

    private final ChatMessageValidator validator;
    private final ChatSessionService sessionService;
    private final ChatRateLimiter rateLimiter;
    private final IntentClassifier classifier;
    private final ChatContextService contextService;
    private final ChatReplyGenerator replyGenerator;
    private final ChatHistoryWriter historyWriter;
    private final ChatMessageRepository messageRepository;
    private final MeterRegistry meterRegistry;

    public ChatMessageService(
            ChatMessageValidator validator,
            ChatSessionService sessionService,
            ChatRateLimiter rateLimiter,
            IntentClassifier classifier,
            ChatContextService contextService,
            ChatReplyGenerator replyGenerator,
            ChatHistoryWriter historyWriter,
            MeterRegistry meterRegistry,
            ChatMessageRepository messageRepository) {
        this.validator = validator;
        this.sessionService = sessionService;
        this.rateLimiter = rateLimiter;
        this.classifier = classifier;
        this.contextService = contextService;
        this.replyGenerator = replyGenerator;
        this.historyWriter = historyWriter;
        this.meterRegistry = meterRegistry;
        this.messageRepository = messageRepository;
    }

    public SendMessageResponse sendMessage(
            UUID sessionId,
            String sessionToken,
            SendMessageRequest raw,
            String remoteAddress,
            String forwardedFor) {
        long startNanos = System.nanoTime();
        String intentName = "unknown";
        String outcomeTag = "success";
        int tokensUsed = 0;
        int messagesWritten = 0;
        try {
            ChatContextRequest validated = validator.validate(raw);
            ChatSessionEntity session = sessionService.authenticate(sessionId, sessionToken);
            rateLimiter.acquire(
                    new ChatRateLimitKey(session.getTokenHash(), remoteAddress, forwardedFor));

            IntentClassification classification =
                    classifier.classify(validated.message(), validated.referencedAlertId());
            intentName = classification.intent().wireName();
            ChatContextResult context = contextService.retrieve(validated, classification);
            if (context.outcome() == ChatContextOutcome.ALERT_NOT_FOUND) {
                throw new ChatAlertNotFoundException();
            }

            ChatReplyResult reply =
                    replyGenerator.generate(classification, context, validated.message());
            if (reply instanceof ChatReplyResult.Unavailable) {
                throw new ChatAiUnavailableException();
            }
            ChatReplyResult.Reply verified = (ChatReplyResult.Reply) reply;
            tokensUsed = verified.tokensUsed();

            ChatMessageEntity assistant = historyWriter.writePair(session, validated, verified);
            messagesWritten = 2;
            return new SendMessageResponse(
                    assistant.getId(),
                    verified.content(),
                    verified.isFallback(),
                    context.context().referencedEntities(),
                    assistant.getCreatedAt().toInstant());
        } catch (ChatException ex) {
            outcomeTag = ex.getErrorCode().getCode();
            throw ex;
        } catch (RuntimeException ex) {
            outcomeTag = ErrorCode.INTERNAL_SERVER_ERROR.getCode();
            throw ex;
        } finally {
            meterRegistry.counter("weathergis.chat.requests", "outcome", outcomeTag).increment();
            log.info(
                    "chat request sessionId={} intent={} durationMs={} tokens={} messagesWritten={} outcome={}",
                    sessionId,
                    intentName,
                    Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                    tokensUsed,
                    messagesWritten,
                    outcomeTag);
        }
    }

    /**
     * Stable forward-cursor history (contract §2.4, R12). {@code limit} is validated before auth;
     * an {@code after} id that does not belong to the resolved session yields an empty page (200),
     * never revealing whether the message exists in another session.
     */
    public List<ChatMessageView> getHistory(
            UUID sessionId, String sessionToken, int limit, UUID after) {
        if (limit < 1 || limit > 200) {
            throw new ChatValidationException(
                    ErrorCode.CHAT_VALIDATION, "limit", "limit phải là số nguyên từ 1 đến 200");
        }
        ChatSessionEntity session = sessionService.authenticate(sessionId, sessionToken);
        Pageable page = PageRequest.of(0, limit);
        List<ChatMessageEntity> rows;
        if (after == null) {
            rows = messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(session.getId(), page);
        } else {
            ChatMessageEntity cursor =
                    messageRepository.findByIdAndSessionId(after, session.getId()).orElse(null);
            if (cursor == null) {
                return List.of();
            }
            rows =
                    messageRepository.findPageAfterCursor(
                            session.getId(), cursor.getCreatedAt(), cursor.getId(), page);
        }
        return rows.stream().map(ChatMessageService::toView).toList();
    }

    private static ChatMessageView toView(ChatMessageEntity entity) {
        return new ChatMessageView(
                entity.getId(),
                entity.getSenderType(),
                entity.getContent(),
                entity.getReferencedAlertId(),
                entity.isFallback(),
                entity.getCreatedAt().toInstant());
    }
}
