package vn.weathergis.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.weathergis.alert.config.AlertRetryProperties;
import vn.weathergis.alert.domain.AlertJobStatus;
import vn.weathergis.alert.dto.AlertDetailResponse;
import vn.weathergis.alert.dto.AlertGenerationJobView;
import vn.weathergis.alert.dto.VerifiedAlertSubmission;
import vn.weathergis.alert.exception.AlertJobConflictException;
import vn.weathergis.alert.repository.AlertGenerationJobRepository;
import vn.weathergis.alert.repository.AlertRepository;
import vn.weathergis.alert.repository.AlertThresholdRepository;
import vn.weathergis.alert.repository.AlertViolationKeyRepository;
import vn.weathergis.alert.service.impl.AlertPersistenceServiceImpl;
import vn.weathergis.automation.event.ThresholdViolatedEvent;
import vn.weathergis.automation.event.ThresholdViolatedEvent.Violation;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.location.repository.LocationRepository;
import vn.weathergis.storm.repository.StormRepository;

@SpringBootTest
class AlertPublicationIT extends AbstractPostGisIntegrationTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-11-05T05:00:00Z");
    private static final Instant WINDOW_AT = Instant.parse("2026-11-05T04:45:00Z");

    @Autowired private AlertRepository alertRepository;
    @Autowired private AlertGenerationJobRepository jobRepository;
    @Autowired private AlertViolationKeyRepository keyRepository;
    @Autowired private AlertThresholdRepository thresholdRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private StormRepository stormRepository;
    @Autowired private AlertService alertService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AlertPersistenceService persistenceService;

    private final List<UUID> stationIds = new ArrayList<>();
    private final List<Integer> thresholdIds = new ArrayList<>();

    @Test
    void committedReadyJobIsPublishedOnRecoveryWithoutAnotherModelCall() {
        UUID station = createStation("W405_READY", "Trạm Ready", 108.2, 16);
        int rule = createThreshold("rain_1h", ">=", 50, "DANGER");
        var pending =
                persistenceService
                        .intake(
                                new ThresholdViolatedEvent(
                                        station,
                                        "W405_READY",
                                        "Trạm Ready",
                                        108.2,
                                        16,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(
                                                violation(
                                                        station, rule, "rain_1h", ">=", 50,
                                                        "DANGER", 60, "mm"))))
                        .orElseThrow();
        persistenceService.recordVerifiedContent(
                vn.weathergis.alert.AlertTestSubmission.claim(pending, true, jdbc, objectMapper));
        var client = mock(vn.weathergis.alert.ai.GeminiClient.class);
        var worker =
                new AlertGenerationWorker(
                        jdbc,
                        transactionManager,
                        persistenceService,
                        client,
                        new vn.weathergis.alert.ai.AlertContentGuardrail(
                                new vn.weathergis.alert.ai.AlertPromptBuilder(objectMapper)),
                        new vn.weathergis.alert.config.AlertWorkerProperties());
        worker.runBatch();
        assertThat(persistenceService.retryDueReadyJobs()).isEqualTo(1);
        assertThat(persistenceService.findJobById(pending.jobId()).orElseThrow().status())
                .isEqualTo(AlertJobStatus.COMPLETED);
        verifyNoInteractions(client);
    }

    @Test
    void databaseClaimFailureDoesNotCallModelAndNextScanRecovers() {
        UUID station = createStation("W405_CLAIM", "Trạm Claim", 108.2, 16);
        int rule = createThreshold("rain_1h", ">=", 50, "DANGER");
        var pending =
                persistenceService
                        .intake(
                                new ThresholdViolatedEvent(
                                        station,
                                        "W405_CLAIM",
                                        "Trạm Claim",
                                        108.2,
                                        16,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(
                                                violation(
                                                        station, rule, "rain_1h", ">=", 50,
                                                        "DANGER", 60, "mm"))))
                        .orElseThrow();
        jdbc.execute(
                """
                create or replace function w405_fail_claim() returns trigger as $$
                begin
                    if NEW.claim_token is not null then raise exception 'claim failure'; end if;
                    return NEW;
                end; $$ language plpgsql;
                create trigger w405_fail_claim before update on alert_generation_jobs
                for each row execute function w405_fail_claim();
                """);
        var client = mock(vn.weathergis.alert.ai.GeminiClient.class);
        when(client.generate(any()))
                .thenAnswer(
                        inv ->
                                new vn.weathergis.alert.ai.GeminiGenerationResult(
                                        inv.getArgument(0),
                                        null,
                                        vn.weathergis.alert.ai.GeminiGenerationResult.Outcome
                                                .DISABLED,
                                        null,
                                        "test",
                                        Duration.ZERO,
                                        null));
        var worker =
                new AlertGenerationWorker(
                        jdbc,
                        transactionManager,
                        persistenceService,
                        client,
                        new vn.weathergis.alert.ai.AlertContentGuardrail(
                                new vn.weathergis.alert.ai.AlertPromptBuilder(objectMapper)),
                        new vn.weathergis.alert.config.AlertWorkerProperties());
        worker.runBatch();
        assertThat(persistenceService.findJobById(pending.jobId()).orElseThrow().status())
                .isEqualTo(AlertJobStatus.PENDING);
        verify(client, never()).generate(any());
        jdbc.execute("drop trigger w405_fail_claim on alert_generation_jobs");
        jdbc.execute("drop function w405_fail_claim()");
        worker.runBatch();
        assertThat(persistenceService.findJobById(pending.jobId()).orElseThrow().status())
                .isEqualTo(AlertJobStatus.COMPLETED);
    }

    @Test
    void nullMeasurementIsNotConvertedToZeroOrPublished() {
        UUID station = createStation("W405_NULL", "Trạm Null", 108.2, 16);
        int rule = createThreshold("rain_1h", ">=", 50, "DANGER");
        var pending =
                persistenceService
                        .intake(
                                new ThresholdViolatedEvent(
                                        station,
                                        "W405_NULL",
                                        "Trạm Null",
                                        108.2,
                                        16,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(
                                                violation(
                                                        station, rule, "rain_1h", ">=", 50,
                                                        "DANGER", 60, "mm"))))
                        .orElseThrow();
        jdbc.update(
                "update alert_generation_jobs set factual_snapshot = jsonb_set(factual_snapshot, '{violations,0,measured_value}', 'null'::jsonb) where id = ?",
                pending.jobId());
        var client = mock(vn.weathergis.alert.ai.GeminiClient.class);
        var worker =
                new AlertGenerationWorker(
                        jdbc,
                        transactionManager,
                        persistenceService,
                        client,
                        new vn.weathergis.alert.ai.AlertContentGuardrail(
                                new vn.weathergis.alert.ai.AlertPromptBuilder(objectMapper)),
                        new vn.weathergis.alert.config.AlertWorkerProperties());
        worker.runBatch();
        assertThat(
                        jdbc.queryForObject(
                                "select last_error from alert_generation_jobs where id = ?",
                                String.class,
                                pending.jobId()))
                .isEqualTo("INVALID_FACTUAL_DATA");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where location_id = ?",
                                Integer.class,
                                station))
                .isZero();
        verifyNoInteractions(client);
    }

    @Test
    void databaseFailureBeforeReadyIsQuarantinedAndRecovers() {
        UUID station = createStation("W405_DB", "Trạm DB", 108.2, 16);
        int rule = createThreshold("rain_1h", ">=", 50, "DANGER");
        var pending =
                persistenceService
                        .intake(
                                new ThresholdViolatedEvent(
                                        station,
                                        "W405_DB",
                                        "Trạm DB",
                                        108.2,
                                        16,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(
                                                violation(
                                                        station, rule, "rain_1h", ">=", 50,
                                                        "DANGER", 60, "mm"))))
                        .orElseThrow();
        jdbc.execute(
                """
                create or replace function w405_fail_ready() returns trigger as $$
                begin
                    if NEW.status = 'READY' then raise exception 'test failure'; end if;
                    return NEW;
                end; $$ language plpgsql;
                create trigger w405_fail_ready before update on alert_generation_jobs
                for each row execute function w405_fail_ready();
                """);
        var client = mock(vn.weathergis.alert.ai.GeminiClient.class);
        when(client.generate(any()))
                .thenAnswer(
                        inv ->
                                new vn.weathergis.alert.ai.GeminiGenerationResult(
                                        inv.getArgument(0),
                                        null,
                                        vn.weathergis.alert.ai.GeminiGenerationResult.Outcome
                                                .DISABLED,
                                        null,
                                        "test",
                                        Duration.ZERO,
                                        null));
        var worker =
                new AlertGenerationWorker(
                        jdbc,
                        transactionManager,
                        persistenceService,
                        client,
                        new vn.weathergis.alert.ai.AlertContentGuardrail(
                                new vn.weathergis.alert.ai.AlertPromptBuilder(objectMapper)),
                        new vn.weathergis.alert.config.AlertWorkerProperties());
        worker.runBatch();
        assertThat(persistenceService.findJobById(pending.jobId()).orElseThrow().status())
                .isEqualTo(AlertJobStatus.PENDING);
        assertThat(
                        jdbc.queryForObject(
                                "select quarantine_until > clock_timestamp() from alert_generation_jobs where id = ?",
                                Boolean.class,
                                pending.jobId()))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where location_id = ?",
                                Integer.class,
                                station))
                .isZero();
        jdbc.execute("drop trigger w405_fail_ready on alert_generation_jobs");
        jdbc.execute("drop function w405_fail_ready()");
        jdbc.update(
                "update alert_generation_jobs set quarantine_until = null where id = ?",
                pending.jobId());
        worker.runBatch();
        assertThat(persistenceService.findJobById(pending.jobId()).orElseThrow().status())
                .isEqualTo(AlertJobStatus.COMPLETED);
    }

    @Test
    void workerPublishesAcceptedDraftWithFalseFallbackFlag() {
        UUID station = createStation("W405_AI", "Trạm AI", 108.2, 16);
        int rule = createThreshold("wind_speed", ">=", 15, "WARNING");
        var pending =
                persistenceService
                        .intake(
                                new ThresholdViolatedEvent(
                                        station,
                                        "W405_AI",
                                        "Trạm AI",
                                        108.2,
                                        16,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(
                                                violation(
                                                        station,
                                                        rule,
                                                        "wind_speed",
                                                        ">=",
                                                        15,
                                                        "WARNING",
                                                        20,
                                                        "m/s"))))
                        .orElseThrow();
        var guard =
                new vn.weathergis.alert.ai.AlertContentGuardrail(
                        new vn.weathergis.alert.ai.AlertPromptBuilder(objectMapper));
        var client = mock(vn.weathergis.alert.ai.GeminiClient.class);
        when(client.generate(any()))
                .thenAnswer(
                        inv ->
                                new vn.weathergis.alert.ai.GeminiGenerationResult(
                                        inv.getArgument(0),
                                        guard.fallback(inv.getArgument(0)),
                                        vn.weathergis.alert.ai.GeminiGenerationResult.Outcome.DRAFT,
                                        null,
                                        "test",
                                        Duration.ZERO,
                                        null));
        var worker =
                new AlertGenerationWorker(
                        jdbc,
                        transactionManager,
                        persistenceService,
                        client,
                        guard,
                        new vn.weathergis.alert.config.AlertWorkerProperties());
        worker.runBatch();
        var completed = persistenceService.findJobById(pending.jobId()).orElseThrow();
        assertThat(completed.status()).isEqualTo(AlertJobStatus.COMPLETED);
        assertThat(alertService.getAlertById(completed.alertId()).isFallback()).isFalse();
        verify(client, times(1)).generate(any());
    }

    @Test
    void workerHonorsBatchLimitAcrossScans() {
        UUID one = createStation("W405_BATCH1", "Trạm Một", 108.2, 16);
        UUID two = createStation("W405_BATCH2", "Trạm Hai", 108.2, 16);
        int rule = createThreshold("rain_1h", ">=", 50, "DANGER");
        for (UUID station : List.of(one, two)) {
            persistenceService.intake(
                    new ThresholdViolatedEvent(
                            station,
                            "W405_BATCH",
                            "Trạm",
                            108.2,
                            16,
                            UUID.randomUUID(),
                            EVALUATED_AT,
                            List.of(
                                    violation(
                                            station, rule, "rain_1h", ">=", 50, "DANGER", 60,
                                            "mm"))));
        }
        var client = mock(vn.weathergis.alert.ai.GeminiClient.class);
        when(client.generate(any()))
                .thenAnswer(
                        inv ->
                                new vn.weathergis.alert.ai.GeminiGenerationResult(
                                        inv.getArgument(0),
                                        null,
                                        vn.weathergis.alert.ai.GeminiGenerationResult.Outcome
                                                .DISABLED,
                                        null,
                                        "test",
                                        Duration.ZERO,
                                        null));
        var properties = new vn.weathergis.alert.config.AlertWorkerProperties();
        properties.setBatchSize(1);
        properties.setConcurrency(2);
        var worker =
                new AlertGenerationWorker(
                        jdbc,
                        transactionManager,
                        persistenceService,
                        client,
                        new vn.weathergis.alert.ai.AlertContentGuardrail(
                                new vn.weathergis.alert.ai.AlertPromptBuilder(objectMapper)),
                        properties);
        worker.runBatch();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where location_id in (?, ?)",
                                Integer.class,
                                one,
                                two))
                .isEqualTo(1);
        worker.runBatch();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where location_id in (?, ?)",
                                Integer.class,
                                one,
                                two))
                .isEqualTo(2);
        verify(client, times(2)).generate(any());
    }

    @Test
    void invalidFactsAreQuarantinedAndDoNotStarveTheNextJob() {
        UUID badStation = createStation("W405_BAD", "Trạm\nkhông hợp lệ", 108.2, 16);
        UUID goodStation = createStation("W405_GOOD", "Trạm Tốt", 108.2, 16);
        int rule = createThreshold("rain_1h", ">=", 50, "DANGER");
        var bad =
                persistenceService
                        .intake(
                                new ThresholdViolatedEvent(
                                        badStation,
                                        "W405_BAD",
                                        "Trạm\nkhông hợp lệ",
                                        108.2,
                                        16,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(
                                                violation(
                                                        badStation,
                                                        rule,
                                                        "rain_1h",
                                                        ">=",
                                                        50,
                                                        "DANGER",
                                                        60,
                                                        "mm"))))
                        .orElseThrow();
        var good =
                persistenceService
                        .intake(
                                new ThresholdViolatedEvent(
                                        goodStation,
                                        "W405_GOOD",
                                        "Trạm Tốt",
                                        108.2,
                                        16,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(
                                                violation(
                                                        goodStation,
                                                        rule,
                                                        "rain_1h",
                                                        ">=",
                                                        50,
                                                        "DANGER",
                                                        60,
                                                        "mm"))))
                        .orElseThrow();
        jdbc.update(
                "update alert_generation_jobs set created_at = clock_timestamp() - interval '1 minute' where id = ?",
                bad.jobId());
        var client = mock(vn.weathergis.alert.ai.GeminiClient.class);
        when(client.generate(any()))
                .thenAnswer(
                        inv ->
                                new vn.weathergis.alert.ai.GeminiGenerationResult(
                                        inv.getArgument(0),
                                        null,
                                        vn.weathergis.alert.ai.GeminiGenerationResult.Outcome
                                                .TIMEOUT,
                                        null,
                                        "test",
                                        Duration.ZERO,
                                        null));
        var worker =
                new AlertGenerationWorker(
                        jdbc,
                        transactionManager,
                        persistenceService,
                        client,
                        new vn.weathergis.alert.ai.AlertContentGuardrail(
                                new vn.weathergis.alert.ai.AlertPromptBuilder(objectMapper)),
                        new vn.weathergis.alert.config.AlertWorkerProperties());
        worker.runBatch();
        assertThat(persistenceService.findJobById(bad.jobId()).orElseThrow().status())
                .isEqualTo(AlertJobStatus.PENDING);
        assertThat(
                        jdbc.queryForObject(
                                "select quarantine_until > clock_timestamp() from alert_generation_jobs where id = ?",
                                Boolean.class,
                                bad.jobId()))
                .isTrue();
        var completed = persistenceService.findJobById(good.jobId()).orElseThrow();
        assertThat(completed.status()).isEqualTo(AlertJobStatus.COMPLETED);
        assertThat(alertService.getAlertById(completed.alertId()).isFallback()).isTrue();
        verify(client, times(1)).generate(any());
    }

    @Test
    void expiredClaimIsFencedAfterConcurrentClaimAndReclaim() throws Exception {
        UUID station = createStation("W405_LEASE", "Trạm Lease", 108.2, 16.0);
        int rule = createThreshold("rain_1h", ">=", 50, "DANGER");
        var pending =
                persistenceService
                        .intake(
                                new ThresholdViolatedEvent(
                                        station,
                                        "W405_LEASE",
                                        "Trạm Lease",
                                        108.2,
                                        16,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(
                                                violation(
                                                        station, rule, "rain_1h", ">=", 50,
                                                        "DANGER", 60, "mm"))))
                        .orElseThrow();
        var client = mock(vn.weathergis.alert.ai.GeminiClient.class);
        when(client.generate(any()))
                .thenAnswer(
                        inv ->
                                new vn.weathergis.alert.ai.GeminiGenerationResult(
                                        inv.getArgument(0),
                                        null,
                                        vn.weathergis.alert.ai.GeminiGenerationResult.Outcome
                                                .DISABLED,
                                        null,
                                        "test",
                                        Duration.ZERO,
                                        null));
        var guard =
                new vn.weathergis.alert.ai.AlertContentGuardrail(
                        new vn.weathergis.alert.ai.AlertPromptBuilder(objectMapper));
        var worker =
                new AlertGenerationWorker(
                        jdbc,
                        transactionManager,
                        persistenceService,
                        client,
                        guard,
                        new vn.weathergis.alert.config.AlertWorkerProperties());
        AlertGenerationWorker.Claim old;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var latch = new CountDownLatch(1);
            var a =
                    pool.submit(
                            () -> {
                                latch.await();
                                return worker.claim();
                            });
            var b =
                    pool.submit(
                            () -> {
                                latch.await();
                                return worker.claim();
                            });
            latch.countDown();
            var results =
                    java.util.stream.Stream.of(a.get(), b.get())
                            .filter(java.util.Objects::nonNull)
                            .toList();
            assertThat(results).hasSize(1);
            old = results.getFirst();
        }
        assertThat(old.jobId()).isEqualTo(pending.jobId());
        jdbc.update(
                "update alert_generation_jobs set lease_until = clock_timestamp() - interval '1 second' where id = ?",
                pending.jobId());
        var replacement = worker.claim();
        assertThat(replacement.token()).isNotEqualTo(old.token());
        var content = guard.fallback(pending);
        assertThatThrownBy(
                        () ->
                                persistenceService.recordVerifiedContent(
                                        new VerifiedAlertSubmission(
                                                pending.jobId(),
                                                content.title(),
                                                content.aiExplanation(),
                                                true,
                                                pending.violationKeys(),
                                                pending.factualSnapshot(),
                                                old.token())))
                .isInstanceOf(AlertJobConflictException.class);
        worker.process(replacement);
        var completed = persistenceService.findJobById(pending.jobId()).orElseThrow();
        assertThat(completed.status()).isEqualTo(AlertJobStatus.COMPLETED);
        assertThat(alertService.getAlertById(completed.alertId()).isFallback()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where location_id = ?",
                                Integer.class,
                                station))
                .isEqualTo(1);
        verify(client, times(1)).generate(any());
        worker.process(old);
        verifyNoMoreInteractions(client);
    }

    @AfterEach
    void cleanFixtures() {
        jdbc.execute("drop trigger if exists w405_fail_claim on alert_generation_jobs");
        jdbc.execute("drop function if exists w405_fail_claim()");
        jdbc.execute("drop trigger if exists w405_fail_ready on alert_generation_jobs");
        jdbc.execute("drop function if exists w405_fail_ready()");
        jdbc.execute("drop trigger if exists trg_fail_key_link_w403 on alert_violation_keys");
        jdbc.execute("drop function if exists fail_key_link_w403()");
        for (UUID stationId : stationIds) {
            jdbc.update(
                    """
                    delete from alert_violation_keys
                    where job_id in (select id from alert_generation_jobs where location_id = ?)
                    """,
                    stationId);
            jdbc.update(
                    "update alert_generation_jobs set alert_id = null where location_id = ?",
                    stationId);
            jdbc.update("delete from alerts where location_id = ?", stationId);
            jdbc.update("delete from alert_generation_jobs where location_id = ?", stationId);
            jdbc.update("delete from locations where id = ?", stationId);
        }
        thresholdIds.forEach(id -> jdbc.update("delete from alert_thresholds where id = ?", id));
    }

    @Test
    void failureAfterAlertInsertBeforeKeyLinkRollsBackAlertAndSchedulesBoundedRetryThenRecovers() {
        AtomicReference<Instant> currentInstant =
                new AtomicReference<>(Instant.parse("2026-11-05T05:01:00Z"));
        AlertRetryProperties retryProps = new AlertRetryProperties();
        retryProps.setEnabled(true);
        retryProps.setBaseBackoffMs(5_000L);
        retryProps.setMaxBackoffMs(60_000L);
        AlertPersistenceService service = serviceWithClock(currentInstant, retryProps);
        AlertPublicationRetryScheduler retryScheduler =
                new AlertPublicationRetryScheduler(service, retryProps);

        UUID stationId = createStation("W403_PUB_FAIL", "Trạm Retry", 108.25, 16.05);
        int rainRule = createThreshold("rain_1h", ">=", 50.0, "DANGER");
        int windRule = createThreshold("wind_speed", ">=", 15.0, "WARNING");

        Violation v1 = violation(stationId, rainRule, "rain_1h", ">=", 50.0, "DANGER", 68.0, "mm");
        Violation v2 =
                violation(stationId, windRule, "wind_speed", ">=", 15.0, "WARNING", 19.5, "m/s");

        AlertGenerationJobView pending =
                service.intake(
                                new ThresholdViolatedEvent(
                                        stationId,
                                        "W403_PUB_FAIL",
                                        "Trạm Retry",
                                        108.25,
                                        16.05,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(v1, v2)))
                        .orElseThrow();

        // Inject DB fault AFTER alert insert, right when alert_violation_keys.alert_id is updated
        jdbc.execute(
                """
                create or replace function fail_key_link_w403() returns trigger as $$
                begin
                    if NEW.job_id = '"""
                        + pending.jobId()
                        + """
                    '::uuid and NEW.alert_id is not null then
                        raise exception 'Simulated failure after alert insert before key linking';
                    end if;
                    return NEW;
                end;
                $$ language plpgsql;
                create trigger trg_fail_key_link_w403
                before update on alert_violation_keys
                for each row execute function fail_key_link_w403();
                """);

        VerifiedAlertSubmission fallbackSubmission =
                vn.weathergis.alert.AlertTestSubmission.claim(pending, true, jdbc, objectMapper);

        assertThatThrownBy(() -> service.submitAndPublish(fallbackSubmission))
                .isInstanceOf(RuntimeException.class);

        // Alert insert rolled back completely; keys still have alert_id = NULL; job is committed in
        // READY
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where location_id = ?",
                                Integer.class,
                                stationId))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                """
                                select count(*) from alert_violation_keys
                                where job_id = ? and alert_id is not null
                                """,
                                Integer.class,
                                pending.jobId()))
                .isZero();

        AlertGenerationJobView failedReady = service.findJobById(pending.jobId()).orElseThrow();
        assertThat(failedReady.status()).isEqualTo(AlertJobStatus.READY);
        assertThat(failedReady.verifiedTitle()).isEqualTo(fallbackSubmission.title());
        assertThat(failedReady.verifiedAiExplanation())
                .isEqualTo(fallbackSubmission.aiExplanation());
        assertThat(failedReady.isFallback()).isTrue();
        assertThat(failedReady.attemptCount()).isEqualTo(1);
        assertThat(failedReady.lastError()).isNotBlank();
        assertThat(failedReady.nextAttemptAt())
                .isEqualTo(currentInstant.get().plus(Duration.ofSeconds(5)));
        assertThat(failedReady.alertId()).isNull();

        // Retry before nextAttemptAt does not publish yet
        assertThat(retryScheduler.runRetryCycle("scheduled")).isZero();

        // Remove fault and advance clock to nextAttemptAt; startup/scheduled recovery publishes
        // without AI call
        jdbc.execute("drop trigger if exists trg_fail_key_link_w403 on alert_violation_keys");
        jdbc.execute("drop function if exists fail_key_link_w403()");
        currentInstant.set(currentInstant.get().plus(Duration.ofSeconds(5)));

        retryScheduler.recoverOnStartup();

        AlertGenerationJobView completed = service.findJobById(pending.jobId()).orElseThrow();
        assertThat(completed.status()).isEqualTo(AlertJobStatus.COMPLETED);
        assertThat(completed.alertId()).isNotNull();
        assertThat(completed.isFallback()).isTrue();
        assertThat(completed.triggeredAt()).isEqualTo(EVALUATED_AT);

        AlertDetailResponse publishedAlert = alertService.getAlertById(completed.alertId());
        assertThat(publishedAlert.id()).isEqualTo(completed.alertId());
        assertThat(publishedAlert.title()).isEqualTo(fallbackSubmission.title());
        assertThat(publishedAlert.aiExplanation()).isEqualTo(fallbackSubmission.aiExplanation());
        assertThat(publishedAlert.isFallback()).isTrue();
        assertThat(publishedAlert.triggeredAt()).isEqualTo(EVALUATED_AT);
        assertThat(publishedAlert.createdAt()).isEqualTo(currentInstant.get());
        assertThat(
                        jdbc.queryForObject(
                                """
                                select count(*) from alert_violation_keys
                                where job_id = ? and alert_id = ?
                                """,
                                Integer.class,
                                pending.jobId(),
                                completed.alertId()))
                .isEqualTo(2);
    }

    @Test
    void concurrentRetryWorkersAndDuplicateOrConflictingCallbacksPreserveSingleAlertGuarantee()
            throws Exception {
        AtomicReference<Instant> currentInstant =
                new AtomicReference<>(Instant.parse("2026-11-05T05:15:00Z"));
        AlertRetryProperties retryProps = new AlertRetryProperties();
        retryProps.setEnabled(true);
        AlertPersistenceService service = serviceWithClock(currentInstant, retryProps);

        UUID stationId = createStation("W403_WORKERS", "Trạm Đa Worker", 109.1, 12.25);
        int rule = createThreshold("rain_1h", ">=", 50.0, "DANGER");
        Violation v = violation(stationId, rule, "rain_1h", ">=", 50.0, "DANGER", 80.0, "mm");

        AlertGenerationJobView pending =
                service.intake(
                                new ThresholdViolatedEvent(
                                        stationId,
                                        "W403_WORKERS",
                                        "Trạm Đa Worker",
                                        109.1,
                                        12.25,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(v)))
                        .orElseThrow();

        VerifiedAlertSubmission verifiedSubmission =
                vn.weathergis.alert.AlertTestSubmission.claim(pending, false, jdbc, objectMapper);

        // Step 1: Transition PENDING -> READY
        AlertGenerationJobView readyJob = service.recordVerifiedContent(verifiedSubmission);
        assertThat(readyJob.status()).isEqualTo(AlertJobStatus.READY);

        // Identical callback while READY is a no-op
        AlertGenerationJobView repeatedReady = service.recordVerifiedContent(verifiedSubmission);
        assertThat(repeatedReady.status()).isEqualTo(AlertJobStatus.READY);

        // Conflicting callback while READY (different isFallback or title) is rejected
        assertThatThrownBy(
                        () ->
                                service.recordVerifiedContent(
                                        new VerifiedAlertSubmission(
                                                pending.jobId(),
                                                verifiedSubmission.title(),
                                                verifiedSubmission.aiExplanation(),
                                                true,
                                                pending.violationKeys(),
                                                pending.factualSnapshot())))
                .isInstanceOf(AlertJobConflictException.class);

        // 4 concurrent workers race to publish due READY jobs
        CountDownLatch workersReady = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 4; i++) {
                AlertPersistenceService worker = serviceWithClock(currentInstant, retryProps);
                results.add(
                        pool.submit(
                                () -> {
                                    workersReady.countDown();
                                    start.await();
                                    return worker.retryDueReadyJobs();
                                }));
            }
            assertThat(workersReady.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int totalPublished = 0;
            for (Future<Integer> future : results) {
                totalPublished += future.get(10, TimeUnit.SECONDS);
            }
            assertThat(totalPublished).isEqualTo(1);
        }

        AlertGenerationJobView completed = service.findJobById(pending.jobId()).orElseThrow();
        assertThat(completed.status()).isEqualTo(AlertJobStatus.COMPLETED);
        UUID alertId = completed.alertId();
        assertThat(alertId).isNotNull();

        // Repeated publishReadyJob and submitAndPublish with identical content return existing
        // alertId
        assertThat(service.publishReadyJob(pending.jobId())).isEqualTo(alertId);
        assertThat(service.submitAndPublish(verifiedSubmission)).isEqualTo(alertId);

        // Conflicting callback after COMPLETED is rejected and never alters the published alert
        assertThatThrownBy(
                        () ->
                                service.submitAndPublish(
                                        new VerifiedAlertSubmission(
                                                pending.jobId(),
                                                "Tiêu đề bị sửa đổi sau khi hoàn tất",
                                                verifiedSubmission.aiExplanation(),
                                                false,
                                                pending.violationKeys(),
                                                pending.factualSnapshot())))
                .isInstanceOf(AlertJobConflictException.class);

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where location_id = ?",
                                Integer.class,
                                stationId))
                .isEqualTo(1);
        assertThat(alertService.getAlertById(alertId).title())
                .isEqualTo(verifiedSubmission.title());
        assertThat(alertService.getAlertById(alertId).isFallback()).isFalse();
    }

    private AlertPersistenceService serviceWithClock(
            AtomicReference<Instant> currentInstant, AlertRetryProperties retryProps) {
        Clock dynamicClock =
                new Clock() {
                    @Override
                    public ZoneOffset getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(java.time.ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return currentInstant.get();
                    }
                };
        return new AlertPersistenceServiceImpl(
                alertRepository,
                jobRepository,
                keyRepository,
                thresholdRepository,
                locationRepository,
                stormRepository,
                objectMapper,
                retryProps,
                dynamicClock,
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
                        values ('W4-03 pub fixture', ?, ?, ?, ?, 'Integration test fixture', true)
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
}
