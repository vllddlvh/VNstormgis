package vn.weathergis.chat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.weathergis.chat.config.ChatSessionProperties;
import vn.weathergis.chat.repository.ChatSessionRepository;

/**
 * Daily retention cleanup (D4, contract §3.4). Deletes sessions with {@code updated_at < now -
 * retention}; messages cascade via the V5 FK. Idempotent — re-running deletes nothing extra. Logs
 * counts and duration only, never tokens or content. Backups may retain data until the next backup
 * cycle; immediate erasure from backups is not promised. Disabled in the IT suite.
 */
@Component
public class ChatSessionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionCleanupScheduler.class);

    private final ChatSessionRepository sessionRepository;
    private final ChatSessionProperties properties;
    private final Clock clock;

    public ChatSessionCleanupScheduler(
            ChatSessionRepository sessionRepository,
            ChatSessionProperties properties,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${weathergis.chat.session.cleanup.cron:0 0 3 * * *}")
    public void scheduledCleanup() {
        if (!properties.getCleanup().isEnabled()) {
            return;
        }
        runCleanup("scheduled");
    }

    @org.springframework.transaction.annotation.Transactional
    public int runCleanup(String trigger) {
        long startNanos = System.nanoTime();
        OffsetDateTime cutoff =
                OffsetDateTime.ofInstant(
                        clock.instant().minus(properties.getRetention()), ZoneOffset.UTC);
        long messages = sessionRepository.countMessagesOfExpiredSessions(cutoff);
        int sessions = sessionRepository.deleteExpiredSessions(cutoff);
        log.info(
                "chat session cleanup trigger={} sessions={} messages={} durationMs={}",
                trigger,
                sessions,
                messages,
                Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
        return sessions;
    }
}
