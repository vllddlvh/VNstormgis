package vn.weathergis.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.weathergis.chat.config.ChatSessionProperties;
import vn.weathergis.chat.domain.ChatSessionEntity;
import vn.weathergis.chat.dto.CreateSessionResponse;
import vn.weathergis.chat.exception.ChatSessionNotFoundException;
import vn.weathergis.chat.exception.ChatSessionTokenMissingException;
import vn.weathergis.chat.repository.ChatSessionRepository;
import vn.weathergis.common.exception.ErrorCode;

/**
 * Session creation and uniform authentication (contract §2.1–2.2, D2/D4): token returned exactly
 * once, only the verifier persisted, and every failure branch (wrong token / unknown session /
 * expired / path mismatch) yields the identical 404 so sessions cannot be probed.
 */
@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("7c8e9f10-1234-5678-abcd-ef0123456789");

    @Mock private ChatSessionRepository sessionRepository;

    private ChatTokenService tokenService;
    private ChatSessionProperties properties;
    private ChatSessionService service;

    @BeforeEach
    void setUp() {
        tokenService = new ChatTokenService();
        properties = new ChatSessionProperties();
        service =
                new ChatSessionService(
                        sessionRepository,
                        tokenService,
                        properties,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ChatSessionEntity sessionUpdatedAgo(Duration age) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(SESSION_ID);
        session.setTokenHash(tokenService.hash("token-a"));
        session.setTitle("Hội thoại mới");
        session.setCreatedAt(OffsetDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC));
        session.setUpdatedAt(OffsetDateTime.ofInstant(NOW.minus(age), ZoneOffset.UTC));
        return session;
    }

    @Test
    void createSessionReturnsTokenOnceAndPersistsOnlyTheVerifier() {
        when(sessionRepository.save(any(ChatSessionEntity.class)))
                .thenAnswer(
                        invocation -> {
                            ChatSessionEntity entity = invocation.getArgument(0);
                            entity.setId(SESSION_ID);
                            return entity;
                        });

        CreateSessionResponse response = service.createSession();

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.sessionToken()).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(response.title()).isEqualTo("Hội thoại mới");
        assertThat(response.createdAt()).isEqualTo(NOW);

        ArgumentCaptor<ChatSessionEntity> captor = ArgumentCaptor.forClass(ChatSessionEntity.class);
        org.mockito.Mockito.verify(sessionRepository).save(captor.capture());
        ChatSessionEntity saved = captor.getValue();
        assertThat(saved.getTokenHash())
                .isEqualTo(tokenService.hash(response.sessionToken()))
                .hasSize(64);
        assertThat(saved.getUpdatedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void authenticateResolvesSessionFromTokenVerifierAndMatchingPath() {
        ChatSessionEntity session = sessionUpdatedAgo(Duration.ofHours(1));
        when(sessionRepository.findByTokenHash(tokenService.hash("token-a")))
                .thenReturn(Optional.of(session));

        assertThat(service.authenticate(SESSION_ID, "token-a")).isSameAs(session);
    }

    @Test
    void missingOrBlankTokenIs401() {
        for (String token : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> service.authenticate(SESSION_ID, token))
                    .isInstanceOf(ChatSessionTokenMissingException.class)
                    .satisfies(
                            ex ->
                                    assertThat(
                                                    ((ChatSessionTokenMissingException) ex)
                                                            .getErrorCode())
                                            .isEqualTo(ErrorCode.CHAT_SESSION_TOKEN_MISSING));
        }
    }

    @Test
    void wrongTokenUnknownSessionAndPathMismatchShareTheIdentical404() {
        when(sessionRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        Throwable unknownToken =
                org.assertj.core.api.Assertions.catchThrowable(
                        () -> service.authenticate(SESSION_ID, "wrong-token"));

        ChatSessionEntity session = sessionUpdatedAgo(Duration.ofHours(1));
        when(sessionRepository.findByTokenHash(tokenService.hash("token-a")))
                .thenReturn(Optional.of(session));
        Throwable pathMismatch =
                org.assertj.core.api.Assertions.catchThrowable(
                        () -> service.authenticate(UUID.randomUUID(), "token-a"));

        assertThat(unknownToken).isInstanceOf(ChatSessionNotFoundException.class);
        assertThat(pathMismatch).isInstanceOf(ChatSessionNotFoundException.class);
        assertThat(((ChatSessionNotFoundException) unknownToken).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND);
        assertThat(pathMismatch.getMessage()).isEqualTo(unknownToken.getMessage());
    }

    @Test
    void sessionInactiveForMoreThan30DaysIsExpiredAtAuth() {
        ChatSessionEntity session = sessionUpdatedAgo(Duration.ofDays(30).plusSeconds(1));
        when(sessionRepository.findByTokenHash(tokenService.hash("token-a")))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.authenticate(SESSION_ID, "token-a"))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    @Test
    void sessionInactiveForExactly30DaysIsStillValid() {
        ChatSessionEntity session = sessionUpdatedAgo(Duration.ofDays(30));
        when(sessionRepository.findByTokenHash(tokenService.hash("token-a")))
                .thenReturn(Optional.of(session));

        assertThat(service.authenticate(SESSION_ID, "token-a")).isSameAs(session);
    }

    @Test
    void sessionInactiveForJustUnder30DaysIsStillValid() {
        ChatSessionEntity session = sessionUpdatedAgo(Duration.ofDays(30).minusSeconds(1));
        when(sessionRepository.findByTokenHash(tokenService.hash("token-a")))
                .thenReturn(Optional.of(session));

        assertThat(service.authenticate(SESSION_ID, "token-a")).isSameAs(session);
    }

    @Test
    void retentionThresholdIsConfigurable() {
        properties.setRetention(Duration.ofDays(7));
        ChatSessionEntity session = sessionUpdatedAgo(Duration.ofDays(8));
        when(sessionRepository.findByTokenHash(tokenService.hash("token-a")))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.authenticate(SESSION_ID, "token-a"))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }
}
