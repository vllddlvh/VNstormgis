package vn.weathergis.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.weathergis.alert.config.AlertRetryProperties;
import vn.weathergis.alert.domain.AlertEntity;
import vn.weathergis.alert.domain.AlertGenerationJobEntity;
import vn.weathergis.alert.domain.AlertJobStatus;
import vn.weathergis.alert.domain.AlertViolationKeyEntity;
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
import vn.weathergis.location.repository.LocationRepository;
import vn.weathergis.storm.repository.StormRepository;

@ExtendWith(MockitoExtension.class)
class AlertPersistenceServiceTest {

    private static final UUID STATION_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID CYCLE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant EVALUATED_AT = Instant.parse("2026-11-05T03:10:00Z");
    private static final Instant WEATHER_WINDOW = Instant.parse("2026-11-05T03:00:00Z");
    private static final Instant STORM_WINDOW = Instant.parse("2026-11-05T03:05:00Z");
    private static final Instant NOW = Instant.parse("2026-11-05T03:12:00Z");

    @Mock private AlertRepository alertRepository;
    @Mock private AlertGenerationJobRepository jobRepository;
    @Mock private AlertViolationKeyRepository keyRepository;
    @Mock private AlertThresholdRepository thresholdRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private StormRepository stormRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private ObjectMapper objectMapper;
    private AlertRetryProperties retryProperties;
    private AlertPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        retryProperties = new AlertRetryProperties();
        lenient()
                .when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        service =
                new AlertPersistenceServiceImpl(
                        alertRepository,
                        jobRepository,
                        keyRepository,
                        thresholdRepository,
                        locationRepository,
                        stormRepository,
                        objectMapper,
                        retryProperties,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        transactionManager);
    }

    @Test
    void intakeMergesMultipleViolationsSelectsMaxSeverityAndFormatsTriggerReason() {
        Violation windWarning =
                weatherViolation(10, "wind_speed", ">=", 15.0, "WARNING", 18.0, "m/s");
        Violation rainDanger = weatherViolation(4, "rain_1h", ">=", 50.0, "DANGER", 65.5, "mm");
        Violation stormCritical =
                stormViolation(8, "<=", 300.0, "CRITICAL", 180.5, "W4_STORM", "Bão Yagi");
        ThresholdViolatedEvent event =
                eventWithViolations(List.of(windWarning, rainDanger, stormCritical));

        doNothing().when(jobRepository).acquireStationLock(anyLong());
        when(keyRepository.findExistingKeys(anyCollection())).thenReturn(Set.of());
        UUID generatedJobId = UUID.randomUUID();
        when(jobRepository.saveAndFlush(any(AlertGenerationJobEntity.class)))
                .thenAnswer(
                        inv -> {
                            AlertGenerationJobEntity entity = inv.getArgument(0);
                            entity.setId(generatedJobId);
                            return entity;
                        });

        Optional<AlertGenerationJobView> result = service.intake(event);

        assertThat(result).isPresent();
        AlertGenerationJobView job = result.get();
        assertThat(job.jobId()).isEqualTo(generatedJobId);
        assertThat(job.status()).isEqualTo(AlertJobStatus.PENDING);
        assertThat(job.severityLevel()).isEqualTo("CRITICAL");
        assertThat(job.representativeThresholdId()).isEqualTo(8);
        assertThat(job.stormId()).isEqualTo("W4_STORM");
        assertThat(job.triggeredAt()).isEqualTo(EVALUATED_AT);
        assertThat(job.triggerReason())
                .isEqualTo(
                        "wind_speed đo được 18.0 m/s, đạt điều kiện >= 15.0 m/s. "
                                + "rain_1h đo được 65.5 mm, đạt điều kiện >= 50.0 mm. "
                                + "storm_dist đo được 180.5 km, đạt điều kiện <= 300.0 km.");
        assertThat(job.violationKeys())
                .containsExactlyInAnyOrder(
                        windWarning.violationKey(),
                        rainDanger.violationKey(),
                        stormCritical.violationKey());
        assertThat(job.factualSnapshot().locationId()).isEqualTo(STATION_ID);
        assertThat(job.factualSnapshot().locationCode()).isEqualTo("W4E_FIRST");
        assertThat(job.factualSnapshot().locationName()).isEqualTo("Trạm Đà Nẵng");
        assertThat(job.factualSnapshot().coordinates()).containsExactly(108.2, 16.0);
        assertThat(job.factualSnapshot().evaluatedAt()).isEqualTo(EVALUATED_AT);
        assertThat(job.factualSnapshot().representativeThreshold().thresholdId()).isEqualTo(8);
        assertThat(job.factualSnapshot().violations()).hasSize(3);
    }

    @Test
    void representativeTieBreakSelectsSmallestThresholdIdThenLexicographicalViolationKey() {
        Violation higherThresholdId =
                weatherViolation(12, "wind_gust", ">=", 25.0, "DANGER", 30.0, "m/s");
        Violation lowerThresholdIdLaterKey =
                new Violation(
                        5,
                        "rain_1h",
                        ">=",
                        50.0,
                        "DANGER",
                        60.0,
                        "mm",
                        WEATHER_WINDOW,
                        null,
                        null,
                        null,
                        WEATHER_WINDOW,
                        "v1:" + STATION_ID + ":5:2026-11-05T03:00:00Z:b");
        Violation lowerThresholdIdEarlierKey =
                new Violation(
                        5,
                        "rain_1h",
                        ">=",
                        50.0,
                        "DANGER",
                        60.0,
                        "mm",
                        WEATHER_WINDOW,
                        null,
                        null,
                        null,
                        WEATHER_WINDOW,
                        "v1:" + STATION_ID + ":5:2026-11-05T03:00:00Z:a");
        Violation infoViolation = weatherViolation(1, "rain_3h", ">=", 20.0, "INFO", 25.0, "mm");

        doNothing().when(jobRepository).acquireStationLock(anyLong());
        when(keyRepository.findExistingKeys(anyCollection())).thenReturn(Set.of());
        when(jobRepository.saveAndFlush(any(AlertGenerationJobEntity.class)))
                .thenAnswer(
                        inv -> {
                            AlertGenerationJobEntity entity = inv.getArgument(0);
                            entity.setId(UUID.randomUUID());
                            return entity;
                        });

        AlertGenerationJobView job =
                service.intake(
                                eventWithViolations(
                                        List.of(
                                                higherThresholdId,
                                                lowerThresholdIdLaterKey,
                                                lowerThresholdIdEarlierKey,
                                                infoViolation)))
                        .orElseThrow();

        assertThat(job.severityLevel()).isEqualTo("DANGER");
        assertThat(job.representativeThresholdId()).isEqualTo(5);
        assertThat(job.factualSnapshot().representativeThreshold().violationKey())
                .isEqualTo(lowerThresholdIdEarlierKey.violationKey());
    }

    @Test
    void partialOverlapCreatesJobForUnclaimedViolationOnlyWithoutInheritingClaimedSeverity() {
        Violation claimedCritical =
                stormViolation(2, "<=", 300.0, "CRITICAL", 120.0, "W4_STORM", "Bão số 1");
        Violation claimedDanger = weatherViolation(3, "rain_1h", ">=", 50.0, "DANGER", 60.0, "mm");
        Violation newInfo = weatherViolation(9, "wind_speed", ">=", 10.0, "INFO", 12.0, "m/s");

        doNothing().when(jobRepository).acquireStationLock(anyLong());
        when(keyRepository.findExistingKeys(anyCollection()))
                .thenReturn(Set.of(claimedCritical.violationKey(), claimedDanger.violationKey()));
        when(jobRepository.saveAndFlush(any(AlertGenerationJobEntity.class)))
                .thenAnswer(
                        inv -> {
                            AlertGenerationJobEntity entity = inv.getArgument(0);
                            entity.setId(UUID.randomUUID());
                            return entity;
                        });

        AlertGenerationJobView job =
                service.intake(
                                eventWithViolations(
                                        List.of(claimedCritical, claimedDanger, newInfo)))
                        .orElseThrow();

        assertThat(job.severityLevel()).isEqualTo("INFO");
        assertThat(job.representativeThresholdId()).isEqualTo(9);
        assertThat(job.stormId()).isNull();
        assertThat(job.violationKeys()).containsExactly(newInfo.violationKey());
        assertThat(job.triggerReason())
                .isEqualTo("wind_speed đo được 12.0 m/s, đạt điều kiện >= 10.0 m/s.");
        assertThat(job.factualSnapshot().violations()).hasSize(1);
    }

    @Test
    void intakeReturnsEmptyWhenAllViolationKeysAreAlreadyClaimed() {
        Violation rain = weatherViolation(1, "rain_1h", ">=", 50.0, "DANGER", 55.0, "mm");
        doNothing().when(jobRepository).acquireStationLock(anyLong());
        when(keyRepository.findExistingKeys(anyCollection()))
                .thenReturn(Set.of(rain.violationKey()));

        assertThat(service.intake(eventWithViolations(List.of(rain)))).isEmpty();
        verify(jobRepository, never()).saveAndFlush(any());
        verify(keyRepository, never()).saveAllAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothVerifiedAiAndFallbackContentUseSameGateAndPreserveFallbackFlag(boolean isFallback) {
        Violation rain = weatherViolation(1, "rain_1h", ">=", 50.0, "DANGER", 55.0, "mm");
        UUID jobId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        AtomicReference<AlertGenerationJobEntity> persistedJob = new AtomicReference<>();

        doNothing().when(jobRepository).acquireStationLock(anyLong());
        when(keyRepository.findExistingKeys(anyCollection())).thenReturn(Set.of());
        when(jobRepository.saveAndFlush(any(AlertGenerationJobEntity.class)))
                .thenAnswer(
                        inv -> {
                            AlertGenerationJobEntity entity = inv.getArgument(0);
                            if (entity.getId() == null) {
                                entity.setId(jobId);
                            }
                            persistedJob.set(entity);
                            return entity;
                        });

        AlertGenerationJobView pending =
                service.intake(eventWithViolations(List.of(rain))).orElseThrow();

        when(jobRepository.findByIdForUpdate(jobId))
                .thenAnswer(inv -> Optional.of(persistedJob.get()));
        when(keyRepository.findByJobIdOrderByViolationKeyAsc(jobId))
                .thenReturn(
                        List.of(
                                new AlertViolationKeyEntity(
                                        rain.violationKey(),
                                        jobId,
                                        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))));
        when(thresholdRepository.existsById(1)).thenReturn(true);
        when(locationRepository.existsById(STATION_ID)).thenReturn(true);
        AtomicReference<AlertEntity> savedAlert = new AtomicReference<>();
        when(alertRepository.saveAndFlush(any(AlertEntity.class)))
                .thenAnswer(
                        inv -> {
                            AlertEntity alert = inv.getArgument(0);
                            alert.setId(alertId);
                            savedAlert.set(alert);
                            return alert;
                        });
        when(keyRepository.linkKeysToAlert(jobId, alertId)).thenReturn(1);

        UUID token = UUID.randomUUID();
        when(jobRepository.hasLiveClaim(jobId, token)).thenReturn(true);
        var content =
                new vn.weathergis.alert.ai.AlertContentGuardrail(
                                new vn.weathergis.alert.ai.AlertPromptBuilder(objectMapper))
                        .fallback(pending);
        VerifiedAlertSubmission submission =
                new VerifiedAlertSubmission(
                        jobId,
                        content.title(),
                        content.aiExplanation(),
                        isFallback,
                        pending.violationKeys(),
                        pending.factualSnapshot(),
                        token);

        UUID publishedId = service.submitAndPublish(submission);

        assertThat(publishedId).isEqualTo(alertId);
        assertThat(savedAlert.get().getTitle()).isEqualTo(content.title());
        assertThat(savedAlert.get().getAiExplanation()).isEqualTo(content.aiExplanation());
        assertThat(savedAlert.get().getIsFallback()).isEqualTo(isFallback);
        assertThat(savedAlert.get().getTriggeredAt().toInstant()).isEqualTo(EVALUATED_AT);
        assertThat(savedAlert.get().getCreatedAt().toInstant()).isEqualTo(NOW);
        assertThat(savedAlert.get().getAffectedGeom().getCoordinate().x).isEqualTo(108.2);
        assertThat(savedAlert.get().getAffectedGeom().getCoordinate().y).isEqualTo(16.0);
        assertThat(savedAlert.get().getAffectedGeom().getSRID()).isEqualTo(4326);
        assertThat(persistedJob.get().getStatus()).isEqualTo(AlertJobStatus.COMPLETED);
        assertThat(persistedJob.get().getAlertId()).isEqualTo(alertId);
    }

    @Test
    void rejectsInvalidEventAndSubmissionPayloads() {
        Violation validViolation = weatherViolation(1, "rain_1h", ">=", 50.0, "DANGER", 55.0, "mm");
        ThresholdViolatedEvent validEvent = eventWithViolations(List.of(validViolation));

        assertThatThrownBy(() -> service.intake(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                service.intake(
                                        new ThresholdViolatedEvent(
                                                STATION_ID,
                                                "W4E_FIRST",
                                                "Trạm Đà Nẵng",
                                                181.0,
                                                16.0,
                                                CYCLE_ID,
                                                EVALUATED_AT,
                                                List.of(validViolation))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                service.intake(
                                        eventWithViolations(
                                                List.of(
                                                        weatherViolation(
                                                                1,
                                                                "rain_1h",
                                                                ">=",
                                                                50.0,
                                                                "DANGER",
                                                                Double.NaN,
                                                                "mm")))))
                .isInstanceOf(IllegalArgumentException.class);

        UUID jobId = UUID.randomUUID();
        doNothing().when(jobRepository).acquireStationLock(anyLong());
        when(keyRepository.findExistingKeys(anyCollection())).thenReturn(Set.of());
        AtomicReference<AlertGenerationJobEntity> persistedJob = new AtomicReference<>();
        when(jobRepository.saveAndFlush(any(AlertGenerationJobEntity.class)))
                .thenAnswer(
                        inv -> {
                            AlertGenerationJobEntity entity = inv.getArgument(0);
                            entity.setId(jobId);
                            persistedJob.set(entity);
                            return entity;
                        });
        AlertGenerationJobView pending = service.intake(validEvent).orElseThrow();

        assertThatThrownBy(
                        () ->
                                service.recordVerifiedContent(
                                        new VerifiedAlertSubmission(
                                                jobId,
                                                "   ",
                                                "Giải thích hợp lệ",
                                                false,
                                                pending.violationKeys(),
                                                pending.factualSnapshot())))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                        () ->
                                service.recordVerifiedContent(
                                        new VerifiedAlertSubmission(
                                                jobId,
                                                "x".repeat(256),
                                                "Giải thích hợp lệ",
                                                false,
                                                pending.violationKeys(),
                                                pending.factualSnapshot())))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                        () ->
                                service.recordVerifiedContent(
                                        new VerifiedAlertSubmission(
                                                jobId,
                                                "Tiêu đề hợp lệ",
                                                "   ",
                                                false,
                                                pending.violationKeys(),
                                                pending.factualSnapshot())))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                        () ->
                                service.recordVerifiedContent(
                                        new VerifiedAlertSubmission(
                                                jobId,
                                                "Tiêu đề hợp lệ",
                                                "Giải thích hợp lệ",
                                                null,
                                                pending.violationKeys(),
                                                pending.factualSnapshot())))
                .isInstanceOf(IllegalArgumentException.class);

        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(persistedJob.get()));
        when(keyRepository.findByJobIdOrderByViolationKeyAsc(jobId))
                .thenReturn(
                        List.of(
                                new AlertViolationKeyEntity(
                                        validViolation.violationKey(),
                                        jobId,
                                        OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))));

        assertThatThrownBy(
                        () ->
                                service.recordVerifiedContent(
                                        new VerifiedAlertSubmission(
                                                jobId,
                                                "Tiêu đề hợp lệ",
                                                "Giải thích hợp lệ",
                                                false,
                                                List.of("v1:other-key"),
                                                pending.factualSnapshot())))
                .isInstanceOf(AlertJobConflictException.class);
    }

    @Test
    void retryPropertiesComputeBoundedExponentialBackoff() {
        AlertRetryProperties props = new AlertRetryProperties();
        props.setBaseBackoffMs(5_000L);
        props.setMaxBackoffMs(60_000L);

        assertThat(props.computeBackoffMs(1)).isEqualTo(5_000L);
        assertThat(props.computeBackoffMs(2)).isEqualTo(10_000L);
        assertThat(props.computeBackoffMs(3)).isEqualTo(20_000L);
        assertThat(props.computeBackoffMs(4)).isEqualTo(40_000L);
        assertThat(props.computeBackoffMs(5)).isEqualTo(60_000L);
        assertThat(props.computeBackoffMs(15)).isEqualTo(60_000L);
    }

    private static ThresholdViolatedEvent eventWithViolations(List<Violation> violations) {
        return new ThresholdViolatedEvent(
                STATION_ID,
                "W4E_FIRST",
                "Trạm Đà Nẵng",
                108.2,
                16.0,
                CYCLE_ID,
                EVALUATED_AT,
                violations);
    }

    private static Violation weatherViolation(
            int thresholdId,
            String parameter,
            String operator,
            double thresholdValue,
            String severityLevel,
            double measuredValue,
            String unit) {
        String key = "v1:" + STATION_ID + ":" + thresholdId + ":" + WEATHER_WINDOW;
        return new Violation(
                thresholdId,
                parameter,
                operator,
                thresholdValue,
                severityLevel,
                measuredValue,
                unit,
                WEATHER_WINDOW,
                null,
                null,
                null,
                WEATHER_WINDOW,
                key);
    }

    private static Violation stormViolation(
            int thresholdId,
            String operator,
            double thresholdValue,
            String severityLevel,
            double distanceKm,
            String stormId,
            String stormName) {
        String key = "v1:" + STATION_ID + ":" + thresholdId + ":" + STORM_WINDOW + ":" + stormId;
        return new Violation(
                thresholdId,
                "storm_dist",
                operator,
                thresholdValue,
                severityLevel,
                distanceKm,
                "km",
                STORM_WINDOW,
                stormId,
                stormName,
                distanceKm,
                STORM_WINDOW,
                key);
    }
}
