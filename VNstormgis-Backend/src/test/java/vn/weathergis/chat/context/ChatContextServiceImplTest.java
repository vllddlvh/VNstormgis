package vn.weathergis.chat.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.weathergis.alert.domain.AlertEntity;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.repository.AlertRepository;
import vn.weathergis.chat.config.ChatContextProperties;
import vn.weathergis.chat.exception.ChatContextException;
import vn.weathergis.chat.intent.ChatIntent;
import vn.weathergis.chat.intent.IntentClassification;
import vn.weathergis.chat.intent.IntentClassifier;
import vn.weathergis.chat.intent.RuleBasedIntentClassifier;
import vn.weathergis.chat.repository.ChatContextQueryRepository;
import vn.weathergis.chat.repository.StationObservationRow;
import vn.weathergis.chat.repository.StormSummaryRow;

/** Service-level outcomes, ranking, stale boundary and logging with a fixed clock (W5-02 §4–5). */
@ExtendWith(MockitoExtension.class)
class ChatContextServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final UUID STATION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ALERT_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final String QUESTION = "Khu vực nào đang có mưa to nhất?";

    @Mock private IntentClassifier classifier;
    @Mock private ChatContextQueryRepository queryRepository;
    @Mock private AlertRepository alertRepository;

    private ObjectMapper objectMapper;
    private FactualContextSerializer serializer;
    private ChatContextProperties properties;
    private ChatContextServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        serializer = new FactualContextSerializer(objectMapper);
        properties = new ChatContextProperties();
        properties.setStaleThreshold(Duration.ofMinutes(60));
        ChatContextShaper shaper = new ChatContextShaper(serializer, properties);
        service =
                new ChatContextServiceImpl(
                        classifier,
                        queryRepository,
                        alertRepository,
                        objectMapper,
                        serializer,
                        shaper,
                        properties,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private IntentClassification classification(
            ChatIntent intent, UUID stationId, String stationCode, UUID alertId) {
        return new IntentClassification(
                intent,
                "TEST_RULE",
                RuleBasedIntentClassifier.RULE_VERSION,
                stationId,
                stationCode,
                null,
                alertId);
    }

    private StationObservationRow row(
            String code, Double rain1h, Double windSpeed, Instant recordedAt) {
        return new StationObservationRow(
                UUID.randomUUID(),
                code,
                "Trạm " + code,
                108.2,
                16.0,
                recordedAt != null,
                recordedAt,
                25.0,
                70.0,
                1000.0,
                windSpeed,
                12.0,
                90.0,
                rain1h,
                30.0,
                "Rain",
                "mưa vừa");
    }

    private ChatContextRequest rainRequest() {
        return new ChatContextRequest(QUESTION, null, null);
    }

    private AlertFactualSnapshot snapshot(int violationCount) {
        List<ViolationSnapshot> violations = new ArrayList<>();
        for (int i = 0; i < violationCount; i++) {
            violations.add(
                    new ViolationSnapshot(
                            1,
                            "rain_1h",
                            ">=",
                            50.0,
                            i % 2 == 0 ? "DANGER" : "WARNING",
                            65.5 + i,
                            "mm",
                            NOW.minusSeconds(600),
                            NOW.minusSeconds(300),
                            "FIXTURE_KEY_" + i,
                            i % 3 == 0 ? "TC1000987" : null,
                            i % 3 == 0 ? "YAGI" : null,
                            i % 3 == 0 ? 120.0 : null));
        }
        return new AlertFactualSnapshot(
                STATION_ID,
                "DNG",
                "Đà Nẵng",
                List.of(108.2, 16.0),
                UUID.randomUUID(),
                NOW.minusSeconds(300),
                new RepresentativeThresholdSnapshot(
                        1, "rain_1h", ">=", 50.0, "DANGER", "mm", "FIXTURE_KEY_0"),
                violations);
    }

    private AlertEntity alert(String status, String snapshotJson, Instant resolvedAt) {
        AlertEntity entity = new AlertEntity();
        entity.setId(ALERT_ID);
        entity.setStatus(status);
        entity.setSeverityLevel("DANGER");
        entity.setTriggeredAt(NOW.minusSeconds(300).atOffset(ZoneOffset.UTC));
        entity.setResolvedAt(resolvedAt == null ? null : resolvedAt.atOffset(ZoneOffset.UTC));
        entity.setFactualSnapshot(snapshotJson);
        return entity;
    }

    @Test
    void rainTopRanksByRain1hDescThenCodeAscWithUnitsAndStaleFlags() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.RAIN_TOP, null, null, null));
        when(queryRepository.findLatestPerActiveStation(null))
                .thenReturn(
                        List.of(
                                row("AAA", 50.0, 5.0, NOW.minusSeconds(600)),
                                row("ZZZ", 80.0, 5.0, NOW.minusSeconds(5400)),
                                row("BBB", 80.0, 5.0, NOW.minusSeconds(600)),
                                row("CCC", null, 5.0, NOW.minusSeconds(600))));

        ChatContextResult result = service.retrieve(rainRequest());

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        assertThat(result.context().stations())
                .extracting(FactualContext.StationObservation::code)
                .containsExactly("BBB", "ZZZ", "AAA");
        FactualContext.StationObservation top = result.context().stations().get(0);
        assertThat(top.rain1h()).isEqualTo(80.0);
        assertThat(top.coordinates()).containsExactly(108.2, 16.0);
        assertThat(top.recordedAt()).isEqualTo(NOW.minusSeconds(600));
        assertThat(top.stale()).isFalse();
        assertThat(top.units()).containsEntry("rain_1h", "mm").containsEntry("wind_speed", "m/s");
        assertThat(result.context().stations().get(1).stale()).isTrue();
        assertThat(result.context().referencedEntities().stations())
                .containsExactly("AAA", "BBB", "ZZZ");
    }

    @Test
    void rainTopKeepsOnlyFiveStations() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.RAIN_TOP, null, null, null));
        List<StationObservationRow> rows = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            rows.add(row("S" + i, 10.0 + i, 5.0, NOW.minusSeconds(600)));
        }
        when(queryRepository.findLatestPerActiveStation(null)).thenReturn(rows);

        ChatContextResult result = service.retrieve(rainRequest());

        assertThat(result.context().stations()).hasSize(5);
        assertThat(result.context().stations().get(0).code()).isEqualTo("S7");
        assertThat(result.context().meta().omittedStationCount()).isZero();
    }

    @Test
    void windTopRanksByWindSpeed() {
        String question = "Trạm nào đang có gió mạnh nhất?";
        when(classifier.classify(question, null))
                .thenReturn(classification(ChatIntent.WIND_TOP, null, null, null));
        when(queryRepository.findLatestPerActiveStation(null))
                .thenReturn(
                        List.of(
                                row("AAA", 90.0, 5.0, NOW.minusSeconds(600)),
                                row("BBB", 10.0, 25.0, NOW.minusSeconds(600))));

        ChatContextResult result = service.retrieve(new ChatContextRequest(question, null, null));

        assertThat(result.context().stations())
                .extracting(FactualContext.StationObservation::code)
                .containsExactly("BBB", "AAA");
        assertThat(result.context().stations().get(0).windSpeed()).isEqualTo(25.0);
    }

    @Test
    void nonFiniteMetricIsExcludedFromRanking() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.RAIN_TOP, null, null, null));
        when(queryRepository.findLatestPerActiveStation(null))
                .thenReturn(
                        List.of(
                                row("AAA", Double.NaN, 5.0, NOW.minusSeconds(600)),
                                row("BBB", 10.0, 5.0, NOW.minusSeconds(600))));

        ChatContextResult result = service.retrieve(rainRequest());

        assertThat(result.context().stations())
                .extracting(FactualContext.StationObservation::code)
                .containsExactly("BBB");
    }

    @Test
    void rainTopWithoutAnyRecordIsHonestNoData() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.RAIN_TOP, null, null, null));
        when(queryRepository.findLatestPerActiveStation(null)).thenReturn(List.of());

        ChatContextResult result = service.retrieve(rainRequest());

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.NO_DATA);
        assertThat(result.noDataReason()).isEqualTo(NoDataReason.MISSING_RECORDS);
        assertThat(result.context().stations()).isEmpty();
        assertThat(result.context().referencedEntities().stations()).isEmpty();
    }

    @Test
    void emptyBboxResultIsNoDataNotEmptyError() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.RAIN_TOP, null, null, null));
        ChatContextRequest.Bbox bbox = new ChatContextRequest.Bbox(0.0, 0.0, 1.0, 1.0);
        when(queryRepository.findLatestPerActiveStation(bbox)).thenReturn(List.of());

        ChatContextResult result = service.retrieve(new ChatContextRequest(QUESTION, null, bbox));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.NO_DATA);
        assertThat(result.noDataReason()).isEqualTo(NoDataReason.EMPTY_BBOX);
    }

    @Test
    void stationCurrentReturnsLatestObservationWithNullsPreserved() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.STATION_CURRENT, STATION_ID, "DNG", null));
        StationObservationRow row =
                new StationObservationRow(
                        STATION_ID,
                        "DNG",
                        "Đà Nẵng",
                        108.2,
                        16.0,
                        true,
                        NOW.minusSeconds(600),
                        25.0,
                        70.0,
                        1000.0,
                        5.0,
                        null,
                        90.0,
                        0.0,
                        0.0,
                        "Clear",
                        "trời quang");
        when(queryRepository.findLatestForStation(STATION_ID)).thenReturn(row);

        ChatContextResult result = service.retrieve(rainRequest());

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        FactualContext.StationObservation station = result.context().stations().get(0);
        assertThat(station.code()).isEqualTo("DNG");
        assertThat(station.windGust()).isNull();
        assertThat(station.rain1h()).isZero();
        assertThat(result.context().referencedEntities().stations()).containsExactly("DNG");
    }

    @Test
    void unresolvedStationIsNoDataWithoutSubstitution() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.STATION_CURRENT, null, null, null));

        ChatContextResult result = service.retrieve(rainRequest());

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.NO_DATA);
        assertThat(result.noDataReason()).isEqualTo(NoDataReason.UNKNOWN_STATION);
        verifyNoInteractions(queryRepository);
    }

    @Test
    void stationWithoutRecordsIsNoData() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.STATION_CURRENT, STATION_ID, "DNG", null));
        when(queryRepository.findLatestForStation(STATION_ID))
                .thenReturn(row("DNG", null, null, null));

        ChatContextResult result = service.retrieve(rainRequest());

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.NO_DATA);
        assertThat(result.noDataReason()).isEqualTo(NoDataReason.MISSING_RECORDS);
    }

    @Test
    void stormActiveMapsGdacsFieldsCenterOrderAndDbUpdateLabel() {
        String question = "Tình hình bão hiện nay ra sao?";
        when(classifier.classify(question, null))
                .thenReturn(classification(ChatIntent.STORM_ACTIVE, null, null, null));
        StormSummaryRow row =
                new StormSummaryRow(
                        "TC1000987",
                        "YAGI",
                        "Red",
                        45.0,
                        960.0,
                        127.4,
                        31.2,
                        NOW.minusSeconds(600),
                        true,
                        NOW.plusSeconds(3600),
                        "forecast",
                        126.0,
                        30.0,
                        40.0,
                        null,
                        955.0,
                        "HU");
        when(queryRepository.findActiveStormSummaries(NOW, 5)).thenReturn(List.of(row));

        ChatContextResult result = service.retrieve(new ChatContextRequest(question, null, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        FactualContext.StormSummary storm = result.context().storms().get(0);
        assertThat(storm.stormId()).isEqualTo("TC1000987");
        assertThat(storm.source()).isEqualTo("GDACS");
        assertThat(storm.center()).containsExactly(127.4, 31.2);
        assertThat(storm.maxWindSpeed()).isEqualTo(45.0);
        assertThat(storm.minPressure()).isEqualTo(960.0);
        assertThat(storm.updatedAt()).isEqualTo(NOW.minusSeconds(600));
        assertThat(storm.updatedAtKind()).isEqualTo("db_update");
        assertThat(storm.stale()).isFalse();
        assertThat(storm.units())
                .containsEntry("max_wind_speed", "m/s")
                .containsEntry("min_pressure", "hPa");
        assertThat(storm.nearestForecast().forecastTime()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(storm.nearestForecast().trackType()).isEqualTo("forecast");
        assertThat(storm.nearestForecast().coordinates()).containsExactly(126.0, 30.0);
        assertThat(storm.nearestForecast().windGust()).isNull();
        assertThat(result.context().referencedEntities().stormIds()).containsExactly("TC1000987");
    }

    @Test
    void stormStaleFlagUsesUpdatedAtAgainstThreshold() {
        String question = "Tình hình bão hiện nay ra sao?";
        when(classifier.classify(question, null))
                .thenReturn(classification(ChatIntent.STORM_ACTIVE, null, null, null));
        StormSummaryRow row =
                new StormSummaryRow(
                        "TC1000987",
                        "YAGI",
                        "Red",
                        45.0,
                        null,
                        127.4,
                        31.2,
                        NOW.minusSeconds(5400),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        when(queryRepository.findActiveStormSummaries(NOW, 5)).thenReturn(List.of(row));

        ChatContextResult result = service.retrieve(new ChatContextRequest(question, null, null));

        assertThat(result.context().storms().get(0).stale()).isTrue();
        assertThat(result.context().storms().get(0).minPressure()).isNull();
        assertThat(result.context().storms().get(0).nearestForecast()).isNull();
    }

    @Test
    void noActiveStormIsHonestNoData() {
        String question = "Tình hình bão hiện nay ra sao?";
        when(classifier.classify(question, null))
                .thenReturn(classification(ChatIntent.STORM_ACTIVE, null, null, null));
        when(queryRepository.findActiveStormSummaries(NOW, 5)).thenReturn(List.of());

        ChatContextResult result = service.retrieve(new ChatContextRequest(question, null, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.NO_DATA);
        assertThat(result.noDataReason()).isEqualTo(NoDataReason.NO_ACTIVE_STORM);
    }

    @Test
    void alertExplainUsesImmutableSnapshotAsSoleBasis() throws Exception {
        AlertFactualSnapshot snapshot = snapshot(2);
        when(classifier.classify(QUESTION, ALERT_ID))
                .thenReturn(classification(ChatIntent.ALERT_EXPLAIN, null, null, ALERT_ID));
        when(alertRepository.findById(ALERT_ID))
                .thenReturn(
                        Optional.of(
                                alert("active", objectMapper.writeValueAsString(snapshot), null)));

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(QUESTION, ALERT_ID, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        FactualContext.AlertContext alert = result.context().alert();
        assertThat(alert.snapshotPresent()).isTrue();
        assertThat(alert.factualSnapshot()).isEqualTo(snapshot);
        assertThat(alert.status()).isEqualTo("active");
        assertThat(alert.triggeredAt()).isEqualTo(NOW.minusSeconds(300));
        assertThat(alert.violationOrder()).hasSize(2);
        assertThat(alert.violationOrder().get(0).severityLevel()).isEqualTo("DANGER");
        assertThat(alert.violationOrder().get(0).exceedance()).isEqualTo(15.5);
        assertThat(result.context().referencedEntities().alertIds()).containsExactly(ALERT_ID);
        assertThat(result.context().referencedEntities().stations()).containsExactly("DNG");
        assertThat(result.context().referencedEntities().stormIds()).containsExactly("TC1000987");
    }

    @Test
    void missingAlertIsAlertNotFoundOutcome() {
        when(classifier.classify(QUESTION, ALERT_ID))
                .thenReturn(classification(ChatIntent.ALERT_EXPLAIN, null, null, ALERT_ID));
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.empty());

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(QUESTION, ALERT_ID, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.ALERT_NOT_FOUND);
        assertThat(result.context().alert()).isNull();
        assertThat(result.context().referencedEntities().alertIds()).isEmpty();
    }

    @Test
    void legacyAlertWithoutSnapshotIsNoSnapshotNotReconstruction() {
        when(classifier.classify(QUESTION, ALERT_ID))
                .thenReturn(classification(ChatIntent.ALERT_EXPLAIN, null, null, ALERT_ID));
        when(alertRepository.findById(ALERT_ID))
                .thenReturn(Optional.of(alert("active", null, null)));

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(QUESTION, ALERT_ID, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.NO_SNAPSHOT);
        assertThat(result.context().alert().snapshotPresent()).isFalse();
        assertThat(result.context().alert().factualSnapshot()).isNull();
        assertThat(result.context().referencedEntities().alertIds()).containsExactly(ALERT_ID);
        assertThat(result.context().referencedEntities().stations()).isEmpty();
    }

    @Test
    void resolvedAlertCarriesStatusAndResolvedAtWithoutTtlInference() {
        when(classifier.classify(QUESTION, ALERT_ID))
                .thenReturn(classification(ChatIntent.ALERT_EXPLAIN, null, null, ALERT_ID));
        when(alertRepository.findById(ALERT_ID))
                .thenReturn(Optional.of(alert("resolved", null, NOW.minusSeconds(60))));

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(QUESTION, ALERT_ID, null));

        assertThat(result.context().alert().status()).isEqualTo("resolved");
        assertThat(result.context().alert().resolvedAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void oversizeSnapshotIsFlaggedNeverTruncated() throws Exception {
        List<ViolationSnapshot> many = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            many.add(
                    new ViolationSnapshot(
                            1,
                            "rain_1h",
                            ">=",
                            50.0,
                            "CRITICAL",
                            65.5,
                            "mm",
                            NOW.minusSeconds(600),
                            NOW.minusSeconds(300),
                            "FIXTURE_KEY_" + i + "x".repeat(200),
                            null,
                            null,
                            null));
        }
        AlertFactualSnapshot snapshot =
                new AlertFactualSnapshot(
                        STATION_ID,
                        "DNG",
                        "Đà Nẵng",
                        List.of(108.2, 16.0),
                        UUID.randomUUID(),
                        NOW.minusSeconds(300),
                        new RepresentativeThresholdSnapshot(
                                1, "rain_1h", ">=", 50.0, "CRITICAL", "mm", "FIXTURE_KEY_0"),
                        many);
        when(classifier.classify(QUESTION, ALERT_ID))
                .thenReturn(classification(ChatIntent.ALERT_EXPLAIN, null, null, ALERT_ID));
        when(alertRepository.findById(ALERT_ID))
                .thenReturn(
                        Optional.of(
                                alert("active", objectMapper.writeValueAsString(snapshot), null)));

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(QUESTION, ALERT_ID, null));

        FactualContext.AlertContext alert = result.context().alert();
        assertThat(alert.snapshotOversize()).isTrue();
        assertThat(alert.snapshotSizeBytes()).isGreaterThan(8192);
        assertThat(alert.factualSnapshot().violations()).hasSize(75);
        assertThat(alert.violationOrder()).hasSize(75);
        assertThat(result.context().meta().contextOversize()).isTrue();
    }

    @Test
    void outOfScopeSkipsAllRetrieval() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.OUT_OF_SCOPE, null, null, null));

        ChatContextResult result = service.retrieve(rainRequest());

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.OUT_OF_SCOPE);
        assertThat(result.context().stations()).isEmpty();
        assertThat(result.context().storms()).isEmpty();
        assertThat(result.context().alert()).isNull();
        verifyNoInteractions(queryRepository, alertRepository);
    }

    @Test
    void databaseFailurePropagatesAndIsNotConvertedToNoData() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.RAIN_TOP, null, null, null));
        when(queryRepository.findLatestPerActiveStation(null))
                .thenThrow(new DataAccessResourceFailureException("fixture connection failure"));

        assertThatThrownBy(() -> service.retrieve(rainRequest()))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void corruptSnapshotRaisesInternalChatExceptionNotNoData() {
        when(classifier.classify(QUESTION, ALERT_ID))
                .thenReturn(classification(ChatIntent.ALERT_EXPLAIN, null, null, ALERT_ID));
        when(alertRepository.findById(ALERT_ID))
                .thenReturn(Optional.of(alert("active", "{corrupt", null)));

        assertThatThrownBy(() -> service.retrieve(new ChatContextRequest(QUESTION, ALERT_ID, null)))
                .isInstanceOf(ChatContextException.class);
    }

    @Test
    void staleBoundaryIsExclusiveAtSixtyMinutesWithFixedClock() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.RAIN_TOP, null, null, null));
        when(queryRepository.findLatestPerActiveStation(null))
                .thenReturn(
                        List.of(
                                row(
                                        "BELOW",
                                        30.0,
                                        5.0,
                                        NOW.minus(Duration.ofMinutes(60)).plusSeconds(1)),
                                row("AT", 20.0, 5.0, NOW.minus(Duration.ofMinutes(60))),
                                row(
                                        "ABOVE",
                                        10.0,
                                        5.0,
                                        NOW.minus(Duration.ofMinutes(60)).minusSeconds(1))));

        ChatContextResult result = service.retrieve(rainRequest());

        assertThat(result.context().stations())
                .filteredOn(s -> s.code().equals("BELOW"))
                .singleElement()
                .satisfies(s -> assertThat(s.stale()).isFalse());
        assertThat(result.context().stations())
                .filteredOn(s -> s.code().equals("AT"))
                .singleElement()
                .satisfies(s -> assertThat(s.stale()).isFalse());
        assertThat(result.context().stations())
                .filteredOn(s -> s.code().equals("ABOVE"))
                .singleElement()
                .satisfies(s -> assertThat(s.stale()).isTrue());
    }

    @Test
    void staleThresholdIsConfigurable() {
        properties.setStaleThreshold(Duration.ofMinutes(15));
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.RAIN_TOP, null, null, null));
        when(queryRepository.findLatestPerActiveStation(null))
                .thenReturn(List.of(row("AAA", 30.0, 5.0, NOW.minusSeconds(20 * 60))));

        ChatContextResult result = service.retrieve(rainRequest());

        assertThat(result.context().stations().get(0).stale()).isTrue();
        assertThat(result.context().meta().staleThresholdMinutes()).isEqualTo(15);
    }

    @Test
    void sizeBytesMatchesCanonicalPayloadAndIntentIsLoggedWithoutQuestion() {
        when(classifier.classify(QUESTION, null))
                .thenReturn(classification(ChatIntent.RAIN_TOP, null, null, null));
        when(queryRepository.findLatestPerActiveStation(null))
                .thenReturn(List.of(row("AAA", 30.0, 5.0, NOW.minusSeconds(600))));
        Logger logger = (Logger) LoggerFactory.getLogger(ChatContextServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ChatContextResult result = service.retrieve(rainRequest());

            assertThat(result.context().sizeBytes())
                    .isEqualTo(serializer.payloadBytes(result.context()));
            assertThat(result.context().schemaVersion()).isEqualTo(FactualContext.SCHEMA_VERSION);
            assertThat(result.context().generatedAt()).isEqualTo(NOW);
            List<ILoggingEvent> info =
                    appender.list.stream().filter(e -> e.getLevel() == Level.INFO).toList();
            assertThat(info).isNotEmpty();
            assertThat(info)
                    .allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain(QUESTION));
            assertThat(info)
                    .anySatisfy(
                            e ->
                                    assertThat(e.getFormattedMessage())
                                            .contains("rain_top")
                                            .contains("CONTEXT_READY"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
