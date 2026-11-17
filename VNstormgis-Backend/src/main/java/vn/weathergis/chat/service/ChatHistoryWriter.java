package vn.weathergis.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.weathergis.chat.context.ChatContextRequest;
import vn.weathergis.chat.domain.ChatMessageEntity;
import vn.weathergis.chat.domain.ChatSessionEntity;
import vn.weathergis.chat.port.ChatReplyResult;
import vn.weathergis.chat.repository.ChatMessageRepository;
import vn.weathergis.chat.repository.ChatSessionRepository;

/**
 * Atomic user+assistant pair write (D10, contract §8.2). Runs in ONE transaction: user message,
 * assistant message, then the session activity touch — a failure on the second save rolls back the
 * whole pair (R21). The reply port is invoked by the orchestrator BEFORE this transaction opens, so
 * no DB connection is held while waiting for AI. Client retries may duplicate pairs; accepted (no
 * dedup key in W5).
 */
@Component
public class ChatHistoryWriter {

    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ChatHistoryWriter(
            ChatMessageRepository messageRepository,
            ChatSessionRepository sessionRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ChatMessageEntity writePair(
            ChatSessionEntity session, ChatContextRequest validated, ChatReplyResult.Reply reply) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        ChatMessageEntity user = new ChatMessageEntity();
        user.setSessionId(session.getId());
        user.setSenderType("user");
        user.setContent(validated.message());
        user.setReferencedAlertId(validated.referencedAlertId());
        user.setSpatialFilter(serializeBbox(validated.bbox()));
        user.setTokensUsed(0);
        user.setFallback(false);
        user.setCreatedAt(now);
        messageRepository.save(user);

        ChatMessageEntity assistant = new ChatMessageEntity();
        assistant.setSessionId(session.getId());
        assistant.setSenderType("assistant");
        assistant.setContent(reply.content());
        assistant.setReferencedAlertId(null);
        assistant.setSpatialFilter(null);
        assistant.setTokensUsed(reply.tokensUsed());
        assistant.setFallback(reply.isFallback());
        assistant.setCreatedAt(now);
        messageRepository.save(assistant);

        sessionRepository.touchUpdatedAt(session.getId());
        return assistant;
    }

    private String serializeBbox(ChatContextRequest.Bbox bbox) {
        if (bbox == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(
                    Map.of(
                            "bbox",
                            new double[] {
                                bbox.minLng(), bbox.minLat(), bbox.maxLng(), bbox.maxLat()
                            }));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể serialize bbox đã validate", e);
        }
    }
}
