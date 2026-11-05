package vn.weathergis.alert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.weathergis.alert.config.AlertRetryProperties;

/**
 * Runs startup recovery and periodic retry for {@code READY} alert jobs whose verified content is
 * already committed in PostgreSQL.
 */
@Component
public class AlertPublicationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertPublicationRetryScheduler.class);

    private final AlertPersistenceService persistenceService;
    private final AlertRetryProperties retryProperties;

    public AlertPublicationRetryScheduler(
            AlertPersistenceService persistenceService, AlertRetryProperties retryProperties) {
        this.persistenceService = persistenceService;
        this.retryProperties = retryProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (!retryProperties.isEnabled()) {
            return;
        }
        runRetryCycle("startup");
    }

    @Scheduled(
            fixedDelayString = "${weathergis.alert.retry.fixed-delay-ms:30000}",
            initialDelayString = "${weathergis.alert.retry.initial-delay-ms:5000}")
    public void scheduledRetry() {
        if (!retryProperties.isEnabled()) {
            return;
        }
        runRetryCycle("scheduled");
    }

    public int runRetryCycle(String trigger) {
        try {
            int published = persistenceService.retryDueReadyJobs();
            if (published > 0) {
                log.info(
                        "alert_publication_retry trigger={} status=SUCCESS published={}",
                        trigger,
                        published);
            }
            return published;
        } catch (RuntimeException ex) {
            log.error(
                    "alert_publication_retry trigger={} status=FAILED error={}",
                    trigger,
                    ex.getClass().getSimpleName());
            return 0;
        }
    }
}
