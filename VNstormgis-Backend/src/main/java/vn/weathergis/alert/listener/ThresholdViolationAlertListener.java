package vn.weathergis.alert.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.weathergis.alert.service.AlertPersistenceService;
import vn.weathergis.automation.event.ThresholdViolatedEvent;

/**
 * Synchronous in-process consumer in the {@code alert} module for {@link ThresholdViolatedEvent}
 * emitted by {@code automation}.
 *
 * <p>Persists durable inbox jobs and violation keys within the intake transaction. Any persistence
 * failure propagates to {@code ThresholdEvaluationScheduler}, marking the {@code AI_MONITOR} audit
 * as {@code FAILED} without affecting committed weather ingestion.
 */
@Component
public class ThresholdViolationAlertListener {

    private final AlertPersistenceService persistenceService;

    public ThresholdViolationAlertListener(AlertPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @EventListener
    public void onThresholdViolated(ThresholdViolatedEvent event) {
        persistenceService.intake(event);
    }
}
