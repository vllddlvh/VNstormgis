package vn.weathergis.automation.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import vn.weathergis.automation.domain.DataSyncLogEntity;
import vn.weathergis.automation.dto.WeatherSyncResult;
import vn.weathergis.automation.event.ThresholdViolatedEvent;
import vn.weathergis.automation.model.StationSnapshot;
import vn.weathergis.automation.model.ThresholdRule;
import vn.weathergis.automation.port.ThresholdRuleSource;
import vn.weathergis.automation.repository.DataSyncLogRepository;
import vn.weathergis.automation.repository.ThresholdEvaluationQueryRepository;

/** Runs after committed weather ingestion; the existing weather cron supplies its cadence. */
@Component
public class ThresholdEvaluationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ThresholdEvaluationScheduler.class);
    private static final String SOURCE = "AI_MONITOR";

    private final ThresholdRuleSource ruleSource;
    private final ThresholdEvaluationQueryRepository queryRepository;
    private final ThresholdEvaluator evaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final DataSyncLogRepository syncLogRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ThresholdEvaluationScheduler(
            ThresholdRuleSource ruleSource,
            ThresholdEvaluationQueryRepository queryRepository,
            ThresholdEvaluator evaluator,
            ApplicationEventPublisher eventPublisher,
            DataSyncLogRepository syncLogRepository) {
        this.ruleSource = ruleSource;
        this.queryRepository = queryRepository;
        this.evaluator = evaluator;
        this.eventPublisher = eventPublisher;
        this.syncLogRepository = syncLogRepository;
    }

    /** A partially failed sync still supplies valid committed records for evaluation. */
    public void evaluateAfterSync(WeatherSyncResult result) {
        if (result.recordsProcessed() <= 0) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info(
                    "threshold_evaluation source={} status=SKIPPED reason=already_running", SOURCE);
            return;
        }

        UUID cycleId = UUID.randomUUID();
        Instant evaluatedAt = Instant.now();
        DataSyncLogEntity audit = new DataSyncLogEntity();
        audit.setSource(SOURCE);
        audit.setStatus("RUNNING");
        audit.setStartedAt(OffsetDateTime.ofInstant(evaluatedAt, ZoneOffset.UTC));
        int stationsScanned = 0;
        int enabledThresholds = 0;
        int violations = 0;
        String status = "SUCCESS";
        String error = null;
        try {
            syncLogRepository.save(audit);
            // Read fresh rules and all spatial results before publishing any events.
            List<ThresholdRule> rules = List.copyOf(ruleSource.findEnabledThresholds());
            enabledThresholds = (int) rules.stream().filter(ThresholdRule::enabled).count();
            List<StationSnapshot> stations = queryRepository.findActiveStationSnapshots();
            for (StationSnapshot station : stations) {
                var event = evaluator.evaluate(station, rules, cycleId, evaluatedAt);
                stationsScanned++;
                if (event.isPresent()) {
                    ThresholdViolatedEvent violationEvent = event.get();
                    violations += violationEvent.violations().size();
                    eventPublisher.publishEvent(violationEvent);
                }
            }
        } catch (RuntimeException ex) {
            status = "FAILED";
            // Exception messages can contain SQL/source data; record only the failure category.
            error = ex.getClass().getSimpleName();
        } finally {
            try {
                audit.setStatus(status);
                audit.setRecordsProcessed(stationsScanned);
                audit.setErrorMessage(error);
                audit.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
                try {
                    syncLogRepository.save(audit);
                } catch (RuntimeException ex) {
                    status = "FAILED";
                    error = ex.getClass().getSimpleName();
                    log.error(
                            "threshold_evaluation_audit cycleId={} source={} status=FAILED error={}",
                            cycleId,
                            SOURCE,
                            error);
                }
                log.info(
                        "threshold_evaluation cycleId={} source={} status={} stations={} enabledThresholds={} violations={} durationMs={} error={}",
                        cycleId,
                        SOURCE,
                        status,
                        stationsScanned,
                        enabledThresholds,
                        violations,
                        Duration.between(evaluatedAt, audit.getCompletedAt()).toMillis(),
                        error);
            } finally {
                running.set(false);
            }
        }
    }
}
