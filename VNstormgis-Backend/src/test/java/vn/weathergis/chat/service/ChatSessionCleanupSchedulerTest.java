package vn.weathergis.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.weathergis.chat.config.ChatSessionProperties;
import vn.weathergis.chat.repository.ChatSessionRepository;

/**
 * Daily retention job (D4, contract §3.4): deletes sessions inactive beyond the configurable
 * retention measured from updated_at, messages cascade via FK. The cutoff is now - retention; a
 * session exactly at the cutoff survives (matches auth-time expiry). Disabled flag stops the
 * scheduled entry point.
 */
@ExtendWith(MockitoExtension.class)
class ChatSessionCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");

    @Mock private ChatSessionRepository sessionRepository;

    private ChatSessionProperties properties;
    private ChatSessionCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new ChatSessionProperties();
        scheduler =
                new ChatSessionCleanupScheduler(
                        sessionRepository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void cleanupDeletesSessionsOlderThanNowMinusRetention() {
        when(sessionRepository.countMessagesOfExpiredSessions(any())).thenReturn(5L);
        when(sessionRepository.deleteExpiredSessions(any())).thenReturn(2);

        int deleted = scheduler.runCleanup("test");

        assertThat(deleted).isEqualTo(2);
        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(sessionRepository).countMessagesOfExpiredSessions(cutoff.capture());
        verify(sessionRepository).deleteExpiredSessions(cutoff.capture());
        assertThat(cutoff.getAllValues().get(0))
                .isEqualTo(
                        OffsetDateTime.ofInstant(NOW.minus(Duration.ofDays(30)), ZoneOffset.UTC));
        assertThat(cutoff.getAllValues().get(1)).isEqualTo(cutoff.getAllValues().get(0));
    }

    @Test
    void retentionThresholdIsConfigurable() {
        properties.setRetention(Duration.ofDays(7));
        when(sessionRepository.deleteExpiredSessions(any())).thenReturn(0);

        scheduler.runCleanup("test");

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(sessionRepository).deleteExpiredSessions(cutoff.capture());
        assertThat(cutoff.getValue())
                .isEqualTo(OffsetDateTime.ofInstant(NOW.minus(Duration.ofDays(7)), ZoneOffset.UTC));
    }

    @Test
    void scheduledEntryPointIsSkippedWhenDisabled() {
        properties.getCleanup().setEnabled(false);

        scheduler.scheduledCleanup();

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void scheduledEntryPointRunsWhenEnabled() {
        when(sessionRepository.deleteExpiredSessions(any())).thenReturn(0);

        scheduler.scheduledCleanup();

        verify(sessionRepository).deleteExpiredSessions(any());
    }
}
