package vn.weathergis.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.weathergis.automation.domain.DataSyncLogEntity;
import vn.weathergis.automation.dto.WeatherSyncResult;
import vn.weathergis.automation.event.ThresholdViolatedEvent;
import vn.weathergis.automation.model.StationSnapshot;
import vn.weathergis.automation.model.ThresholdRule;
import vn.weathergis.automation.port.ThresholdRuleSource;
import vn.weathergis.automation.repository.DataSyncLogRepository;
import vn.weathergis.automation.repository.ThresholdEvaluationQueryRepository;

@ExtendWith(MockitoExtension.class)
class ThresholdEvaluationSchedulerTest {

    private static final ThresholdRule RAIN =
            new ThresholdRule(1, "rain_1h", ">=", 50.0, "WARNING", true);

    @Mock private ThresholdRuleSource ruleSource;
    @Mock private ThresholdEvaluationQueryRepository queryRepository;
    @Mock private ThresholdEvaluator evaluator;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private DataSyncLogRepository auditRepository;

    private ThresholdEvaluationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler =
                new ThresholdEvaluationScheduler(
                        ruleSource, queryRepository, evaluator, publisher, auditRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "FAILED"})
    void shouldSkipCyclesWithoutCommittedWeather(String syncStatus) {
        scheduler.evaluateAfterSync(result(syncStatus, 0));

        verifyNoInteractions(ruleSource, queryRepository, evaluator, publisher, auditRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "FAILED"})
    void shouldScanEveryStationAndAuditSafeCycleWithoutChangingSync(String syncStatus) {
        List<AuditSnapshot> audits = captureAudits();
        StationSnapshot first = station("HAN");
        StationSnapshot second = station("DAD");
        when(ruleSource.findEnabledThresholds()).thenReturn(List.of(RAIN));
        when(queryRepository.findActiveStationSnapshots()).thenReturn(List.of(first, second));
        when(evaluator.evaluate(any(), anyList(), any(UUID.class), any(Instant.class)))
                .thenReturn(Optional.empty());
        WeatherSyncResult syncResult = result(syncStatus, 1);

        scheduler.evaluateAfterSync(syncResult);

        assertThat(syncResult.status()).isEqualTo(syncStatus);
        assertThat(audits)
                .extracting(AuditSnapshot::source)
                .containsExactly("AI_MONITOR", "AI_MONITOR");
        assertThat(audits).extracting(AuditSnapshot::status).containsExactly("RUNNING", "SUCCESS");
        assertThat(audits).extracting(AuditSnapshot::records).containsExactly(0, 2);
        assertThat(audits.get(0).completedAt()).isNull();
        assertThat(audits.get(1).completedAt()).isAfterOrEqualTo(audits.get(1).startedAt());
        assertThat(audits.get(1).error()).isNull();
        verify(publisher, times(0)).publishEvent(any(Object.class));
    }

    @Test
    void shouldPublishEvaluatorEventOncePerStation() {
        List<AuditSnapshot> audits = captureAudits();
        ThresholdViolatedEvent event = mock(ThresholdViolatedEvent.class);
        when(event.violations()).thenReturn(List.of());
        when(ruleSource.findEnabledThresholds()).thenReturn(List.of(RAIN));
        when(queryRepository.findActiveStationSnapshots()).thenReturn(List.of(station("HAN")));
        when(evaluator.evaluate(any(), anyList(), any(UUID.class), any(Instant.class)))
                .thenReturn(Optional.of(event));

        scheduler.evaluateAfterSync(result("SUCCESS", 1));

        verify(publisher).publishEvent(event);
        assertThat(audits.get(1).status()).isEqualTo("SUCCESS");
        assertThat(audits.get(1).records()).isEqualTo(1);
    }

    @Test
    void shouldReadEnabledRulesAgainOnEveryScan() {
        captureAudits();
        when(ruleSource.findEnabledThresholds()).thenReturn(List.of(RAIN)).thenReturn(List.of());
        when(queryRepository.findActiveStationSnapshots()).thenReturn(List.of(station("HAN")));
        List<List<ThresholdRule>> evaluatedRules = new ArrayList<>();
        List<UUID> cycleIds = new ArrayList<>();
        when(evaluator.evaluate(any(), anyList(), any(UUID.class), any(Instant.class)))
                .thenAnswer(
                        invocation -> {
                            evaluatedRules.add(List.copyOf(invocation.getArgument(1)));
                            cycleIds.add(invocation.getArgument(2));
                            return Optional.empty();
                        });

        scheduler.evaluateAfterSync(result("SUCCESS", 1));
        scheduler.evaluateAfterSync(result("SUCCESS", 1));

        assertThat(evaluatedRules).containsExactly(List.of(RAIN), List.of());
        assertThat(cycleIds).doesNotHaveDuplicates();
        verify(ruleSource, times(2)).findEnabledThresholds();
        verify(queryRepository, times(2)).findActiveStationSnapshots();
    }

    @Test
    void shouldAuditSpatialFailureWithoutPublishingOrLeakingQueryData() {
        List<AuditSnapshot> audits = captureAudits();
        when(ruleSource.findEnabledThresholds()).thenReturn(List.of(RAIN));
        when(queryRepository.findActiveStationSnapshots())
                .thenThrow(new DataAccessResourceFailureException("private source payload"));

        assertThatCode(() -> scheduler.evaluateAfterSync(result("SUCCESS", 1)))
                .doesNotThrowAnyException();

        assertThat(audits).extracting(AuditSnapshot::status).containsExactly("RUNNING", "FAILED");
        assertThat(audits.get(1).records()).isZero();
        assertThat(audits.get(1).error()).isEqualTo("DataAccessResourceFailureException");
        verifyNoInteractions(evaluator, publisher);
    }

    @Test
    void shouldAuditListenerFailureAndReleaseGuardForNextScan() {
        List<AuditSnapshot> audits = captureAudits();
        ThresholdViolatedEvent event = mock(ThresholdViolatedEvent.class);
        when(event.violations()).thenReturn(List.of());
        when(ruleSource.findEnabledThresholds()).thenReturn(List.of(RAIN));
        when(queryRepository.findActiveStationSnapshots()).thenReturn(List.of(station("HAN")));
        when(evaluator.evaluate(any(), anyList(), any(UUID.class), any(Instant.class)))
                .thenReturn(Optional.of(event));
        doThrow(new IllegalStateException("listener failed"))
                .doNothing()
                .when(publisher)
                .publishEvent(event);

        scheduler.evaluateAfterSync(result("FAILED", 1));
        scheduler.evaluateAfterSync(result("SUCCESS", 1));

        assertThat(audits)
                .extracting(AuditSnapshot::status)
                .containsExactly("RUNNING", "FAILED", "RUNNING", "SUCCESS");
        assertThat(audits.get(1).records()).isEqualTo(1);
        assertThat(audits.get(1).error()).isEqualTo("IllegalStateException");
        verify(publisher, times(2)).publishEvent(event);
    }

    @Test
    void shouldAuditRuleReadFailure() {
        List<AuditSnapshot> audits = captureAudits();
        when(ruleSource.findEnabledThresholds())
                .thenThrow(new IllegalStateException("repository unavailable"));

        scheduler.evaluateAfterSync(result("SUCCESS", 1));

        assertThat(audits).extracting(AuditSnapshot::status).containsExactly("RUNNING", "FAILED");
        assertThat(audits.get(1).records()).isZero();
        verifyNoInteractions(queryRepository, evaluator, publisher);
    }

    @Test
    void shouldSkipOverlappingEvaluationAndReleaseGuard() throws Exception {
        List<AuditSnapshot> audits = captureAudits();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        when(ruleSource.findEnabledThresholds()).thenReturn(List.of());
        when(queryRepository.findActiveStationSnapshots())
                .thenAnswer(
                        invocation -> {
                            entered.countDown();
                            if (!release.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("fixture timed out");
                            }
                            return List.of();
                        });
        Thread first =
                new Thread(
                        () -> {
                            try {
                                scheduler.evaluateAfterSync(result("SUCCESS", 1));
                            } catch (Throwable ex) {
                                threadFailure.set(ex);
                            }
                        });
        first.start();
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            scheduler.evaluateAfterSync(result("SUCCESS", 1));
            verify(auditRepository, times(1)).save(any());
            verify(queryRepository, times(1)).findActiveStationSnapshots();
        } finally {
            release.countDown();
            first.join(5000);
        }
        assertThat(first.isAlive()).isFalse();
        assertThat(threadFailure.get()).isNull();
        scheduler.evaluateAfterSync(result("SUCCESS", 1));

        assertThat(audits)
                .extracting(AuditSnapshot::status)
                .containsExactly("RUNNING", "SUCCESS", "RUNNING", "SUCCESS");
        verify(queryRepository, times(2)).findActiveStationSnapshots();
    }

    @Test
    void shouldContainAuditStartFailureAndPermitNextScan() {
        when(auditRepository.save(any()))
                .thenThrow(new DataAccessResourceFailureException("down"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleSource.findEnabledThresholds()).thenReturn(List.of());
        when(queryRepository.findActiveStationSnapshots()).thenReturn(List.of());

        assertThatCode(() -> scheduler.evaluateAfterSync(result("SUCCESS", 1)))
                .doesNotThrowAnyException();
        verifyNoInteractions(ruleSource, queryRepository, evaluator, publisher);
        scheduler.evaluateAfterSync(result("SUCCESS", 1));

        verify(queryRepository).findActiveStationSnapshots();
        verify(auditRepository, times(4)).save(any());
    }

    @Test
    void shouldContainTerminalAuditFailureAndPermitNextScan() {
        when(auditRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new DataAccessResourceFailureException("down"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleSource.findEnabledThresholds()).thenReturn(List.of());
        when(queryRepository.findActiveStationSnapshots()).thenReturn(List.of());

        assertThatCode(() -> scheduler.evaluateAfterSync(result("SUCCESS", 1)))
                .doesNotThrowAnyException();
        scheduler.evaluateAfterSync(result("SUCCESS", 1));

        verify(queryRepository, times(2)).findActiveStationSnapshots();
        verify(auditRepository, times(4)).save(any());
    }

    private List<AuditSnapshot> captureAudits() {
        List<AuditSnapshot> snapshots = new ArrayList<>();
        when(auditRepository.save(any()))
                .thenAnswer(
                        invocation -> {
                            DataSyncLogEntity entity = invocation.getArgument(0);
                            snapshots.add(
                                    new AuditSnapshot(
                                            entity.getSource(),
                                            entity.getStatus(),
                                            entity.getRecordsProcessed(),
                                            entity.getStartedAt(),
                                            entity.getCompletedAt(),
                                            entity.getErrorMessage()));
                            return entity;
                        });
        return snapshots;
    }

    private StationSnapshot station(String code) {
        return new StationSnapshot(
                UUID.randomUUID(), code, "Station " + code, 105.0, 21.0, null, null);
    }

    private WeatherSyncResult result(String status, int processed) {
        OffsetDateTime now = OffsetDateTime.now();
        return new WeatherSyncResult(
                "OPENWEATHERMAP",
                status,
                processed,
                2,
                now,
                now,
                "FAILED".equals(status) ? "partial ingestion" : null);
    }

    private record AuditSnapshot(
            String source,
            String status,
            int records,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            String error) {}
}
