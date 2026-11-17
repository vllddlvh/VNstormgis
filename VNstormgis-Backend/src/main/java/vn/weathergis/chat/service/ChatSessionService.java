package vn.weathergis.chat.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.weathergis.chat.config.ChatSessionProperties;
import vn.weathergis.chat.domain.ChatSessionEntity;
import vn.weathergis.chat.dto.CreateSessionResponse;
import vn.weathergis.chat.exception.ChatSessionNotFoundException;
import vn.weathergis.chat.exception.ChatSessionTokenMissingException;
import vn.weathergis.chat.repository.ChatSessionRepository;

/**
 * Anonymous session lifecycle (contract §2.1–2.2, §3.1; D2/D4). The server issues a 256-bit token,
 * returns it once, and stores only its SHA-256 verifier. Authentication resolves the session from
 * the token hash and requires the path id to match; expiry is enforced here at auth time (not only
 * by the cleanup job) against {@code updated_at + retention}.
 */
@Service
public class ChatSessionService {

    private static final String DEFAULT_TITLE = "Hội thoại mới";

    private final ChatSessionRepository sessionRepository;
    private final ChatTokenService tokenService;
    private final ChatSessionProperties properties;
    private final Clock clock;

    public ChatSessionService(
            ChatSessionRepository sessionRepository,
            ChatTokenService tokenService,
            ChatSessionProperties properties,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.tokenService = tokenService;
        this.properties = properties;
        this.clock = clock;
    }

    public CreateSessionResponse createSession() {
        String token = tokenService.generateToken();
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ChatSessionEntity session = new ChatSessionEntity();
        session.setTokenHash(tokenService.hash(token));
        session.setTitle(DEFAULT_TITLE);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        ChatSessionEntity saved = sessionRepository.save(session);
        return new CreateSessionResponse(
                saved.getId(), token, saved.getTitle(), saved.getCreatedAt().toInstant());
    }

    public ChatSessionEntity authenticate(UUID sessionId, String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new ChatSessionTokenMissingException();
        }
        ChatSessionEntity session =
                sessionRepository.findByTokenHash(tokenService.hash(sessionToken)).orElse(null);
        if (session == null || !session.getId().equals(sessionId)) {
            throw new ChatSessionNotFoundException();
        }
        Instant expiry = session.getUpdatedAt().toInstant().plus(properties.getRetention());
        if (expiry.isBefore(clock.instant())) {
            throw new ChatSessionNotFoundException();
        }
        return session;
    }
}
