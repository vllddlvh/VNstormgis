package vn.weathergis.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.weathergis.alert.config.AlertRetryProperties;
import vn.weathergis.alert.dto.AlertGenerationJobView;
import vn.weathergis.alert.dto.VerifiedAlertSubmission;
import vn.weathergis.alert.repository.AlertGenerationJobRepository;
import vn.weathergis.alert.repository.AlertRepository;
import vn.weathergis.alert.repository.AlertThresholdRepository;
import vn.weathergis.alert.repository.AlertViolationKeyRepository;
import vn.weathergis.alert.service.impl.AlertPersistenceServiceImpl;
import vn.weathergis.automation.dto.WeatherSyncResult;
import vn.weathergis.automation.event.ThresholdViolatedEvent;
import vn.weathergis.automation.event.ThresholdViolatedEvent.Violation;
import vn.weathergis.automation.service.ThresholdEvaluationScheduler;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.location.repository.LocationRepository;
import vn.weathergis.storm.repository.StormRepository;

@SpringBootTest
class AlertEventIntakeIT extends AbstractPostGisIntegrationTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-11-05T04:00:00Z");
    private static final Instant WINDOW_AT = Instant.parse("2026-11-05T03:45:00Z");

    @Autowired private AlertPersistenceService persistenceService;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private ThresholdEvaluationScheduler evaluationScheduler;
    @Autowired private AlertRepository alertRepository;
    @Autowired private AlertGenerationJobRepository jobRepository;
    @Autowired private AlertViolationKeyRepository keyRepository;
    @Autowired private AlertThresholdRepository thresholdRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StormRepository stormRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AlertRetryProperties retryProperties;
    @Autowired private Clock clock;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    private final List<UUID> stationIds = new ArrayList<>();
    private final List<Integer> thresholdIds = new ArrayList<>();

    @AfterEach
    void cleanFixtures() {
        for (UUID stationId : stationIds) {
            jdbc.update(
                    """
                    delete from alert_violation_keys
                    where job_id in (select id from alert_generation_jobs where location_id = ?)
                    """,
                    stationId);
            jdbc.update(
                    """
                    update alert_generation_jobs set alert_id = null where location_id = ?
                    """,
                    stationId);
            jdbc.update("delete from alerts where location_id = ?", stationId);
            jdbc.update("delete from alert_generation_jobs where location_id = ?", stationId);
            jdbc.update("delete from weather_records where location_id = ?", stationId);
            jdbc.update("delete from locations where id = ?", stationId);
        }
        thresholdIds.forEach(id -> jdbc.update("delete from alert_thresholds where id = ?", id));
    }

    @Test
    void
            replayAndFreshServiceInstanceAcrossRestartPreserveDeduplicationEvenAfterResolvedOrDismissed() {
        UUID stationId = createStation("W403_REPLAY", "Trạm Replay", 108.2, 16.0);
        int rainRule = createThreshold("rain_1h", ">=", 50.0, "DANGER");
        int windRule = createThreshold("wind_speed", ">=", 15.0, "WARNING");

        Violation vA = violation(stationId, rainRule, "rain_1h", ">=", 50.0, "DANGER", 60.0, "mm");
        Violation vB =
                violation(stationId, windRule, "wind_speed", ">=", 15.0, "WARNING", 18.0, "m/s");

        ThresholdViolatedEvent firstEvent =
                event(stationId, "W403_REPLAY", "Trạm Replay", List.of(vA, vB));
        eventPublisher.publishEvent(firstEvent);

        List<AlertGenerationJobView> pendingJobs =
                persistenceService.findPendingJobs().stream()
                        .filter(j -> j.locationId().equals(stationId))
                        .toList();
        assertThat(pendingJobs).hasSize(1);
        AlertGenerationJobView initialJob = pendingJobs.getFirst();
        assertThat(initialJob.violationKeys())
                .containsExactlyInAnyOrder(vA.violationKey(), vB.violationKey());

        // Replay with a new evaluationCycleId and evaluatedAt while job is PENDING
        ThresholdViolatedEvent replayedWhilePending =
                new ThresholdViolatedEvent(
                        stationId,
                        "W403_REPLAY",
                        "Trạm Replay",
                        108.2,
                        16.0,
                        UUID.randomUUID(),
                        EVALUATED_AT.plusSeconds(300),
                        List.of(vA, vB));
        assertThat(persistenceService.intake(replayedWhilePending)).isEmpty();

        // Simulate process restart by instantiating a brand-new service instance backed only by DB
        AlertPersistenceService restartedService = newServiceInstance();
        assertThat(restartedService.intake(replayedWhilePending)).isEmpty();

        // Publish the alert and transition status to resolved, then dismissed; keys remain claimed
        UUID alertId =
                restartedService.submitAndPublish(
                        new VerifiedAlertSubmission(
                                initialJob.jobId(),
                                "Cảnh báo mưa to và gió mạnh tại Trạm Replay",
                                "Lượng mưa đạt 60.0 mm và gió đạt 18.0 m/s tại Trạm Replay.",
                                false,
                                initialJob.violationKeys(),
                                initialJob.factualSnapshot()));
        jdbc.update("update alerts set status = 'resolved' where id = ?", alertId);
        assertThat(restartedService.intake(replayedWhilePending)).isEmpty();

        jdbc.update("update alerts set status = 'dismissed' where id = ?", alertId);
        assertThat(restartedService.intake(replayedWhilePending)).isEmpty();

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_generation_jobs where location_id = ?",
                                Integer.class,
                                stationId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where location_id = ?",
                                Integer.class,
                                stationId))
                .isEqualTo(1);
    }

    @Test
    void partialOverlapClaimsOnlyNewKeyAndExcludesPreviousViolationsFromReasonAndSeverity() {
        UUID stationId = createStation("W403_OVERLAP", "Trạm Overlap", 107.5, 16.5);
        int criticalRule = createThreshold("rain_1h", ">=", 100.0, "CRITICAL");
        int dangerRule = createThreshold("wind_gust", ">=", 25.0, "DANGER");
        int infoRule = createThreshold("wind_speed", ">=", 10.0, "INFO");

        Violation vA =
                violation(stationId, criticalRule, "rain_1h", ">=", 100.0, "CRITICAL", 120.0, "mm");
        Violation vB =
                violation(stationId, dangerRule, "wind_gust", ">=", 25.0, "DANGER", 28.0, "m/s");
        Violation vC =
                violation(stationId, infoRule, "wind_speed", ">=", 10.0, "INFO", 12.0, "m/s");

        AlertGenerationJobView jobAB =
                persistenceService
                        .intake(event(stationId, "W403_OVERLAP", "Trạm Overlap", List.of(vA, vB)))
                        .orElseThrow();
        assertThat(jobAB.severityLevel()).isEqualTo("CRITICAL");
        assertThat(jobAB.violationKeys())
                .containsExactlyInAnyOrder(vA.violationKey(), vB.violationKey());

        AlertGenerationJobView jobC =
                persistenceService
                        .intake(
                                event(
                                        stationId,
                                        "W403_OVERLAP",
                                        "Trạm Overlap",
                                        List.of(vA, vB, vC)))
                        .orElseThrow();
        assertThat(jobC.jobId()).isNotEqualTo(jobAB.jobId());
        assertThat(jobC.violationKeys()).containsExactly(vC.violationKey());
        assertThat(jobC.severityLevel()).isEqualTo("INFO");
        assertThat(jobC.representativeThresholdId()).isEqualTo(infoRule);
        assertThat(jobC.triggerReason())
                .isEqualTo("wind_speed đo được 12.0 m/s, đạt điều kiện >= 10.0 m/s.")
                .doesNotContain("rain_1h")
                .doesNotContain("wind_gust");
        assertThat(jobC.factualSnapshot().violations())
                .hasSize(1)
                .allSatisfy(v -> assertThat(v.violationKey()).isEqualTo(vC.violationKey()));
    }

    @Test
    void concurrentMultiInstanceIntakeSerializesByStationWithoutDuplicates() throws Exception {
        UUID stationId = createStation("W403_CONC", "Trạm Đồng Thời", 106.7, 10.8);
        int ruleA = createThreshold("rain_1h", ">=", 50.0, "DANGER");
        int ruleB = createThreshold("wind_speed", ">=", 15.0, "WARNING");

        Violation vA = violation(stationId, ruleA, "rain_1h", ">=", 50.0, "DANGER", 62.0, "mm");
        Violation vB =
                violation(stationId, ruleB, "wind_speed", ">=", 15.0, "WARNING", 19.0, "m/s");
        ThresholdViolatedEvent incoming =
                event(stationId, "W403_CONC", "Trạm Đồng Thời", List.of(vA, vB));

        CountDownLatch ready = new CountDownLatch(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<AlertGenerationJobView>>> futures = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(6)) {
            for (int i = 0; i < 6; i++) {
                AlertPersistenceService instance = newServiceInstance();
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    return instance.intake(incoming);
                                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<AlertGenerationJobView> created = new ArrayList<>();
            for (Future<Optional<AlertGenerationJobView>> future : futures) {
                future.get(10, TimeUnit.SECONDS).ifPresent(created::add);
            }
            assertThat(created).hasSize(1);
        }

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_generation_jobs where location_id = ?",
                                Integer.class,
                                stationId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                """
                                select count(*) from alert_violation_keys
                                where violation_key in (?, ?)
                                """,
                                Integer.class,
                                vA.violationKey(),
                                vB.violationKey()))
                .isEqualTo(2);
    }

    @Test
    void intakeFailureRollsBackJobAndKeysAndMarksAiMonitorFailedWhilePreservingWeatherRecords() {
        UUID stationId = createStation("W403_ROLLBACK", "Trạm Rollback", 105.8, 21.0);
        int validRule = createThreshold("rain_1h", ">=", 50.0, "DANGER");
        int SecondRule = createThreshold("wind_speed", ">=", 15.0, "WARNING");

        Violation validV =
                violation(stationId, validRule, "rain_1h", ">=", 50.0, "DANGER", 75.0, "mm");
        // Overly long key (>255 chars) triggers validation/DB rollback before commit
        Violation invalidV =
                new Violation(
                        SecondRule,
                        "wind_speed",
                        ">=",
                        15.0,
                        "WARNING",
                        20.0,
                        "m/s",
                        WINDOW_AT,
                        null,
                        null,
                        null,
                        WINDOW_AT,
                        "v1:" + "x".repeat(260));

        assertThatThrownBy(
                        () ->
                                persistenceService.intake(
                                        event(
                                                stationId,
                                                "W403_ROLLBACK",
                                                "Trạm Rollback",
                                                List.of(validV, invalidV))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_generation_jobs where location_id = ?",
                                Integer.class,
                                stationId))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_violation_keys where violation_key = ?",
                                Integer.class,
                                validV.violationKey()))
                .isZero();

        // Also test DB constraint rollback if a key is inserted concurrently before
        // saveAllAndFlush:
        // Verify scheduler AI_MONITOR audit records FAILED when listener throws while keeping
        // weather
        OffsetDateTime observedAt = OffsetDateTime.parse("2026-11-05T03:45:00Z");
        jdbc.update(
                """
                insert into weather_records
                    (location_id, recorded_at, temperature, feels_like, humidity, pressure,
                     wind_speed, wind_direction, wind_gust, rain_1h, rain_3h,
                     weather_condition, weather_description)
                values (?, ?, 25, 25, 85, 1002, 20.0, 90, 25.0, 75.0, 100.0, 'Rain', 'Fixture')
                """,
                stationId,
                observedAt);

        // Temporarily drop NOT NULL or inject a trigger on alert_generation_jobs for this station
        // to simulate a DB failure during listener intake in ThresholdEvaluationScheduler
        jdbc.execute(
                """
                create or replace function fail_w403_rollback_station() returns trigger as $$
                begin
                    if NEW.location_id = '"""
                        + stationId
                        + """
                    '::uuid then
                        raise exception 'Simulated DB failure on alert_generation_jobs';
                    end if;
                    return NEW;
                end;
                $$ language plpgsql;
                create trigger trg_fail_w403_rollback
                before insert on alert_generation_jobs
                for each row execute function fail_w403_rollback_station();
                """);
        try {
            evaluationScheduler.evaluateAfterSync(
                    new WeatherSyncResult(
                            "OPENWEATHERMAP", "SUCCESS", 1, 1, observedAt, observedAt, null));
        } finally {
            jdbc.execute("drop trigger if exists trg_fail_w403_rollback on alert_generation_jobs");
            jdbc.execute("drop function if exists fail_w403_rollback_station()");
        }

        // Committed weather record remains intact, no partial job/key exists, AI_MONITOR is FAILED
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from weather_records where location_id = ?",
                                Integer.class,
                                stationId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_generation_jobs where location_id = ?",
                                Integer.class,
                                stationId))
                .isZero();
        var latestAudit =
                jdbc.queryForMap(
                        """
                        select status, error_message
                        from data_sync_logs
                        where source = 'AI_MONITOR'
                        order by id desc
                        limit 1
                        """);
        assertThat(latestAudit.get("status")).isEqualTo("FAILED");
        assertThat(latestAudit.get("error_message")).isNotNull();
    }

    private AlertPersistenceService newServiceInstance() {
        return new AlertPersistenceServiceImpl(
                alertRepository,
                jobRepository,
                keyRepository,
                thresholdRepository,
                locationRepository,
                stormRepository,
                objectMapper,
                retryProperties,
                clock,
                transactionManager);
    }

    private UUID createStation(String code, String name, double lng, double lat) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into locations (id, code, name, region, geom, is_active)
                values (?, ?, ?, 'Nam Trung Bộ', ST_SetSRID(ST_MakePoint(?, ?), 4326), true)
                """,
                id,
                code,
                name,
                lng,
                lat);
        stationIds.add(id);
        return id;
    }

    private int createThreshold(String parameter, String operator, double value, String severity) {
        Integer id =
                jdbc.queryForObject(
                        """
                        insert into alert_thresholds
                            (name, parameter, operator, threshold_value, severity_level, description, is_enabled)
                        values ('W4-03 fixture', ?, ?, ?, ?, 'Integration test fixture', true)
                        returning id
                        """,
                        Integer.class,
                        parameter,
                        operator,
                        value,
                        severity);
        thresholdIds.add(id);
        return id;
    }

    private static Violation violation(
            UUID stationId,
            int thresholdId,
            String parameter,
            String operator,
            double thresholdValue,
            String severity,
            double measuredValue,
            String unit) {
        String key = "v1:" + stationId + ":" + thresholdId + ":" + WINDOW_AT;
        return new Violation(
                thresholdId,
                parameter,
                operator,
                thresholdValue,
                severity,
                measuredValue,
                unit,
                WINDOW_AT,
                null,
                null,
                null,
                WINDOW_AT,
                key);
    }

    private static ThresholdViolatedEvent event(
            UUID stationId, String code, String name, List<Violation> violations) {
        return new ThresholdViolatedEvent(
                stationId, code, name, 108.2, 16.0, UUID.randomUUID(), EVALUATED_AT, violations);
    }
}
