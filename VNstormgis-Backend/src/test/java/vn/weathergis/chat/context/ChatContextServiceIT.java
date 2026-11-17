package vn.weathergis.chat.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.repository.AlertRepository;
import vn.weathergis.chat.config.ChatContextProperties;
import vn.weathergis.chat.intent.ChatIntent;
import vn.weathergis.chat.intent.IntentClassifier;
import vn.weathergis.chat.repository.ChatContextQueryRepository;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

/**
 * Real PostGIS verification of the W5-02 retrieval service (§7): isolated FIXTURE rows, seed
 * stations/storms neutralized per test, everything rolled back. The service is wired with the real
 * classifier/repositories and a fixed UTC clock so stale flags and rankings are reproducible.
 */
@SpringBootTest
@Transactional
class ChatContextServiceIT extends AbstractPostGisIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final String RAIN_QUESTION = "Khu vực nào đang có mưa to nhất?";
    private static final String WIND_QUESTION = "Trạm nào đang có gió mạnh nhất?";
    private static final String STORM_QUESTION = "Tình hình bão hiện nay ra sao?";

    @Autowired private IntentClassifier classifier;
    @Autowired private ChatContextQueryRepository queryRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FactualContextSerializer serializer;
    @Autowired private ChatContextShaper shaper;
    @Autowired private ChatContextProperties properties;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private ChatContextServiceImpl service;
    private UUID stationA;
    private UUID stationB;
    private UUID stationC;
    private UUID stationOff;
    private UUID stationEmpty;

    @BeforeEach
    void createIsolatedFixtures() {
        jdbc.update("update locations set is_active = false");
        jdbc.update("update storms set status = 'dissipated'");
        stationA = insertStation("W502A", "Trạm W502A", 108.0, 16.0, true);
        stationB = insertStation("W502B", "Sơn Trà W502B", 109.0, 16.0, true);
        stationC = insertStation("W502C", "Trạm W502C", 110.0, 16.0, true);
        stationOff = insertStation("W502OFF", "Trạm W502OFF", 108.0, 16.0, false);
        stationEmpty = insertStation("W502EMPTY", "Trạm W502EMPTY", 107.0, 15.0, true);
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

    @Test
    void rainTopRanksLatestPerActiveStationAgainstDbValues() {
        insertWeather(stationA, NOW.minus(Duration.ofHours(2)), 500.0, 600.0, 10.0, 15.0);
        insertWeather(stationA, NOW.minusSeconds(600), 55.0, 80.0, 16.0, 25.0);
        insertWeather(stationB, NOW.minusSeconds(300), 80.0, 120.0, 5.0, 8.0);
        insertWeather(stationC, NOW.minus(Duration.ofMinutes(90)), 70.0, 90.0, 5.0, 8.0);
        insertWeather(stationOff, NOW.minusSeconds(300), 999.0, 999.0, 50.0, 60.0);

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(RAIN_QUESTION, null, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        assertThat(result.context().intent()).isEqualTo(ChatIntent.RAIN_TOP);
        assertThat(result.context().stations())
                .extracting(FactualContext.StationObservation::code)
                .containsExactly("W502B", "W502C", "W502A");
        FactualContext.StationObservation top = result.context().stations().get(0);
        assertThat(top.rain1h()).isEqualTo(80.0);
        assertThat(top.rain3h()).isEqualTo(120.0);
        assertThat(top.recordedAt()).isEqualTo(NOW.minusSeconds(300));
        assertThat(top.coordinates()).containsExactly(109.0, 16.0);
        assertThat(top.units()).containsEntry("rain_1h", "mm").containsEntry("wind_speed", "m/s");
        FactualContext.StationObservation last = result.context().stations().get(2);
        assertThat(last.rain1h()).isEqualTo(55.0);
        assertThat(last.recordedAt()).isEqualTo(NOW.minusSeconds(600));
        assertThat(last.stale()).isFalse();
        assertThat(result.context().stations().get(1).stale()).isTrue();
        assertThat(result.context().referencedEntities().stations())
                .containsExactly("W502A", "W502B", "W502C");
        assertThat(result.context().sizeBytes()).isPositive().isLessThanOrEqualTo(8192);
        assertThat(result.context().meta().contextOversize()).isFalse();
    }

    @Test
    void windTopRanksByWindSpeedNotRain() {
        insertWeather(stationA, NOW.minusSeconds(600), 10.0, 20.0, 25.0, 30.0);
        insertWeather(stationB, NOW.minusSeconds(600), 90.0, 120.0, 5.0, 8.0);

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(WIND_QUESTION, null, null));

        assertThat(result.context().intent()).isEqualTo(ChatIntent.WIND_TOP);
        assertThat(result.context().stations())
                .extracting(FactualContext.StationObservation::code)
                .containsExactly("W502A", "W502B");
        assertThat(result.context().stations().get(0).windSpeed()).isEqualTo(25.0);
        assertThat(result.context().stations().get(0).windGust()).isEqualTo(30.0);
    }

    @Test
    void nonFiniteRainIsExcludedFromRanking() {
        insertWeather(stationA, NOW.minusSeconds(600), Double.NaN, 20.0, 5.0, 8.0);
        insertWeather(stationB, NOW.minusSeconds(600), 10.0, 20.0, 5.0, 8.0);

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(RAIN_QUESTION, null, null));

        assertThat(result.context().stations())
                .extracting(FactualContext.StationObservation::code)
                .containsExactly("W502B");
    }

    @Test
    void stationCurrentByCodeReturnsLatestRecordWithMeasuredZero() {
        insertWeather(stationA, NOW.minus(Duration.ofHours(2)), 500.0, 600.0, 40.0, 50.0);
        insertWeather(stationA, NOW.minusSeconds(600), 0.0, 0.0, 5.0, 8.0);

        ChatContextResult result =
                service.retrieve(
                        new ChatContextRequest("Thời tiết trạm W502A hiện tại?", null, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        assertThat(result.context().intent()).isEqualTo(ChatIntent.STATION_CURRENT);
        assertThat(result.context().stations()).hasSize(1);
        FactualContext.StationObservation station = result.context().stations().get(0);
        assertThat(station.code()).isEqualTo("W502A");
        assertThat(station.rain1h()).isZero();
        assertThat(station.windSpeed()).isEqualTo(5.0);
        assertThat(station.recordedAt()).isEqualTo(NOW.minusSeconds(600));
        assertThat(station.coordinates()).containsExactly(108.0, 16.0);
        assertThat(result.context().referencedEntities().stations()).containsExactly("W502A");
    }

    @Test
    void stationCurrentByNameResolvesCorrectStation() {
        insertWeather(stationB, NOW.minusSeconds(600), 12.0, 20.0, 5.0, 8.0);

        ChatContextResult result =
                service.retrieve(
                        new ChatContextRequest(
                                "Thời tiết trạm Sơn Trà W502B hiện tại?", null, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        assertThat(result.context().stations()).hasSize(1);
        assertThat(result.context().stations().get(0).code()).isEqualTo("W502B");
        assertThat(result.context().stations().get(0).rain1h()).isEqualTo(12.0);
    }

    @Test
    void unknownStationMentionIsNoDataWithoutSubstitution() {
        insertWeather(stationA, NOW.minusSeconds(600), 55.0, 80.0, 16.0, 25.0);

        ChatContextResult result =
                service.retrieve(
                        new ChatContextRequest("Thời tiết trạm W502X hiện tại?", null, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.NO_DATA);
        assertThat(result.noDataReason()).isEqualTo(NoDataReason.UNKNOWN_STATION);
        assertThat(result.context().stations()).isEmpty();
        assertThat(result.context().referencedEntities().stations()).isEmpty();
    }

    @Test
    void stationWithoutRecordsIsNoDataMissingRecords() {
        ChatContextResult result =
                service.retrieve(
                        new ChatContextRequest("Thời tiết trạm W502EMPTY hiện tại?", null, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.NO_DATA);
        assertThat(result.noDataReason()).isEqualTo(NoDataReason.MISSING_RECORDS);
        assertThat(result.context().stations()).isEmpty();
    }

    @Test
    void bboxAppliesLngLatOrderAndExcludesOutsideStations() {
        insertWeather(stationA, NOW.minusSeconds(600), 55.0, 80.0, 16.0, 25.0);
        insertWeather(stationB, NOW.minusSeconds(600), 80.0, 120.0, 5.0, 8.0);
        insertWeather(stationC, NOW.minusSeconds(600), 70.0, 90.0, 5.0, 8.0);
        ChatContextRequest.Bbox aroundA = new ChatContextRequest.Bbox(107.5, 15.5, 108.5, 16.5);

        ChatContextResult inside =
                service.retrieve(new ChatContextRequest(RAIN_QUESTION, null, aroundA));

        assertThat(inside.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        assertThat(inside.context().stations())
                .extracting(FactualContext.StationObservation::code)
                .containsExactly("W502A");

        ChatContextRequest.Bbox swapped = new ChatContextRequest.Bbox(15.5, 107.5, 16.5, 108.5);
        ChatContextResult antiSwap =
                service.retrieve(new ChatContextRequest(RAIN_QUESTION, null, swapped));

        assertThat(antiSwap.outcome()).isEqualTo(ChatContextOutcome.NO_DATA);
        assertThat(antiSwap.noDataReason()).isEqualTo(NoDataReason.EMPTY_BBOX);
    }

    @Test
    void bboxOutsideDataRegionIsHonestNoData() {
        insertWeather(stationA, NOW.minusSeconds(600), 55.0, 80.0, 16.0, 25.0);
        ChatContextRequest.Bbox farAway = new ChatContextRequest.Bbox(0.0, 0.0, 1.0, 1.0);

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(RAIN_QUESTION, null, farAway));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.NO_DATA);
        assertThat(result.noDataReason()).isEqualTo(NoDataReason.EMPTY_BBOX);
        assertThat(result.context().stations()).isEmpty();
    }

    @Test
    void stormActiveExcludesDissipatedOrdersByUpdatedAtAndLimitsFive() {
        insertStorm("W502_TIE_B", "active", 127.0, 31.0, NOW.minusSeconds(300), 40.0, 970.0);
        insertStorm("W502_TIE_A", "active", 127.0, 31.0, NOW.minusSeconds(300), 40.0, 970.0);
        insertStorm("W502_C", "active", 128.0, 32.0, NOW.minusSeconds(600), 35.0, 980.0);
        insertStorm("W502_D", "active", 128.0, 32.0, NOW.minusSeconds(1200), 35.0, 980.0);
        insertStorm("W502_E", "active", 128.0, 32.0, NOW.minusSeconds(1800), 35.0, 980.0);
        insertStorm("W502_F", "active", 128.0, 32.0, NOW.minusSeconds(2400), 35.0, 980.0);
        insertStorm("W502_DEAD", "dissipated", 126.0, 30.0, NOW.minusSeconds(60), 50.0, 950.0);

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(STORM_QUESTION, null, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        assertThat(result.context().intent()).isEqualTo(ChatIntent.STORM_ACTIVE);
        assertThat(result.context().storms())
                .extracting(FactualContext.StormSummary::stormId)
                .containsExactly("W502_TIE_A", "W502_TIE_B", "W502_C", "W502_D", "W502_E");
        assertThat(result.context().referencedEntities().stormIds())
                .containsExactly("W502_C", "W502_D", "W502_E", "W502_TIE_A", "W502_TIE_B");
    }

    @Test
    void stormNearestForecastIgnoresHistoricalAndPicksClosestForecastTime() {
        insertStorm("W502_TC1", "active", 127.4, 31.2, NOW.minusSeconds(600), 45.0, 960.0);
        insertTrack(
                "W502_TC1", "historical", NOW.plusSeconds(1800), 127.0, 31.0, 44.0, 50.0, 962.0);
        insertTrack(
                "W502_TC1",
                "forecast",
                NOW.plus(Duration.ofHours(6)),
                125.0,
                29.0,
                38.0,
                45.0,
                975.0);
        insertTrack(
                "W502_TC1",
                "forecast",
                NOW.plus(Duration.ofHours(1)),
                126.0,
                30.0,
                40.0,
                48.0,
                970.0);
        insertTrack(
                "W502_TC1",
                "forecast",
                NOW.minus(Duration.ofHours(3)),
                128.0,
                32.0,
                46.0,
                55.0,
                958.0);
        insertStorm("W502_TC2", "active", 120.0, 20.0, NOW.minusSeconds(1200), 30.0, 990.0);

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(STORM_QUESTION, null, null));

        assertThat(result.context().storms()).hasSize(2);
        FactualContext.StormSummary first = result.context().storms().get(0);
        assertThat(first.stormId()).isEqualTo("W502_TC1");
        assertThat(first.source()).isEqualTo("GDACS");
        assertThat(first.center()).containsExactly(127.4, 31.2);
        assertThat(first.maxWindSpeed()).isEqualTo(45.0);
        assertThat(first.minPressure()).isEqualTo(960.0);
        assertThat(first.updatedAt()).isEqualTo(NOW.minusSeconds(600));
        assertThat(first.updatedAtKind()).isEqualTo("db_update");
        assertThat(first.stale()).isFalse();
        assertThat(first.units())
                .containsEntry("max_wind_speed", "m/s")
                .containsEntry("min_pressure", "hPa");
        assertThat(first.nearestForecast().forecastTime()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(first.nearestForecast().trackType()).isEqualTo("forecast");
        assertThat(first.nearestForecast().coordinates()).containsExactly(126.0, 30.0);
        assertThat(first.nearestForecast().windSpeed()).isEqualTo(40.0);
        assertThat(result.context().storms().get(1).nearestForecast()).isNull();
    }

    @Test
    void noActiveStormIsHonestNoData() {
        insertStorm("W502_DEAD", "dissipated", 126.0, 30.0, NOW.minusSeconds(60), 50.0, 950.0);

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(STORM_QUESTION, null, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.NO_DATA);
        assertThat(result.noDataReason()).isEqualTo(NoDataReason.NO_ACTIVE_STORM);
        assertThat(result.context().storms()).isEmpty();
    }

    @Test
    void alertExplainUsesImmutableSnapshotNotCurrentThresholdsOrObservations() throws Exception {
        Integer thresholdId = insertThreshold(50.0);
        UUID alertId = UUID.randomUUID();
        AlertFactualSnapshot snapshot = snapshot(alertId, 2);
        insertAlert(
                alertId,
                "active",
                objectMapper.writeValueAsString(snapshot),
                NOW.minusSeconds(300),
                null,
                thresholdId);
        insertWeather(stationA, NOW.minusSeconds(600), 65.5, 90.0, 16.0, 25.0);

        ChatContextResult first =
                service.retrieve(new ChatContextRequest(RAIN_QUESTION, alertId, null));

        assertThat(first.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        assertThat(first.context().intent()).isEqualTo(ChatIntent.ALERT_EXPLAIN);
        FactualContext.AlertContext alert = first.context().alert();
        assertThat(alert.alertId()).isEqualTo(alertId);
        assertThat(alert.status()).isEqualTo("active");
        assertThat(alert.severityLevel()).isEqualTo("DANGER");
        assertThat(alert.triggeredAt()).isEqualTo(NOW.minusSeconds(300));
        assertThat(alert.snapshotPresent()).isTrue();
        assertThat(alert.factualSnapshot()).isEqualTo(snapshot);
        assertThat(alert.factualSnapshot().locationCode()).isEqualTo("W502A");
        assertThat(alert.factualSnapshot().coordinates()).containsExactly(108.0, 16.0);
        assertThat(alert.violationOrder()).hasSize(2);
        assertThat(alert.violationOrder().get(0).severityLevel()).isEqualTo("DANGER");
        assertThat(alert.violationOrder().get(0).exceedance()).isEqualTo(15.5);
        assertThat(alert.snapshotOversize()).isFalse();
        assertThat(first.context().referencedEntities().alertIds()).containsExactly(alertId);
        assertThat(first.context().referencedEntities().stations()).containsExactly("W502A");
        assertThat(first.context().referencedEntities().stormIds()).containsExactly("W502_TC1");

        jdbc.update("update alert_thresholds set threshold_value = 999 where id = ?", thresholdId);
        insertWeather(stationA, NOW.minusSeconds(60), 999.0, 999.0, 50.0, 60.0);
        entityManager.clear();

        ChatContextResult second =
                service.retrieve(new ChatContextRequest(RAIN_QUESTION, alertId, null));

        assertThat(second.context().alert().factualSnapshot()).isEqualTo(snapshot);
        assertThat(second.context().alert().violationOrder().get(0).exceedance()).isEqualTo(15.5);
    }

    @Test
    void legacyAlertWithoutSnapshotIsNoSnapshot() {
        UUID alertId = UUID.randomUUID();
        insertAlert(alertId, "active", null, NOW.minusSeconds(300), null, null);

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(RAIN_QUESTION, alertId, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.NO_SNAPSHOT);
        assertThat(result.context().alert().snapshotPresent()).isFalse();
        assertThat(result.context().alert().factualSnapshot()).isNull();
        assertThat(result.context().alert().violationOrder()).isEmpty();
        assertThat(result.context().referencedEntities().alertIds()).containsExactly(alertId);
    }

    @Test
    void missingAlertIsAlertNotFound() {
        ChatContextResult result =
                service.retrieve(new ChatContextRequest(RAIN_QUESTION, UUID.randomUUID(), null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.ALERT_NOT_FOUND);
        assertThat(result.context().alert()).isNull();
        assertThat(result.context().referencedEntities().alertIds()).isEmpty();
    }

    @Test
    void resolvedAlertCarriesStatusAndResolvedAtWithoutTtlInference() throws Exception {
        UUID alertId = UUID.randomUUID();
        insertAlert(
                alertId,
                "resolved",
                objectMapper.writeValueAsString(snapshot(alertId, 1)),
                NOW.minusSeconds(3600),
                NOW.minusSeconds(60),
                null);

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(RAIN_QUESTION, alertId, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        assertThat(result.context().alert().status()).isEqualTo("resolved");
        assertThat(result.context().alert().resolvedAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void oversize75RuleSnapshotIsFlaggedNotTruncated() throws Exception {
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
                        stationA,
                        "W502A",
                        "Trạm W502A",
                        List.of(108.0, 16.0),
                        UUID.randomUUID(),
                        NOW.minusSeconds(300),
                        new RepresentativeThresholdSnapshot(
                                1, "rain_1h", ">=", 50.0, "CRITICAL", "mm", "FIXTURE_KEY_0"),
                        many);
        UUID alertId = UUID.randomUUID();
        insertAlert(
                alertId,
                "active",
                objectMapper.writeValueAsString(snapshot),
                NOW.minusSeconds(300),
                null,
                null);

        ChatContextResult result =
                service.retrieve(new ChatContextRequest(RAIN_QUESTION, alertId, null));

        FactualContext.AlertContext alert = result.context().alert();
        assertThat(alert.snapshotOversize()).isTrue();
        assertThat(alert.snapshotSizeBytes()).isGreaterThan(8192);
        assertThat(alert.factualSnapshot().violations()).hasSize(75);
        assertThat(alert.violationOrder()).hasSize(75);
        assertThat(result.context().meta().contextOversize()).isTrue();
    }

    private AlertFactualSnapshot snapshot(UUID alertId, int violationCount) {
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
                            i == 0 ? "W502_TC1" : null,
                            i == 0 ? "Storm W502_TC1" : null,
                            i == 0 ? 120.0 : null));
        }
        return new AlertFactualSnapshot(
                stationA,
                "W502A",
                "Trạm W502A",
                List.of(108.0, 16.0),
                UUID.randomUUID(),
                NOW.minusSeconds(300),
                new RepresentativeThresholdSnapshot(
                        1, "rain_1h", ">=", 50.0, "DANGER", "mm", "FIXTURE_KEY_0"),
                violations);
    }

    private UUID insertStation(
            String code, String name, double longitude, double latitude, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into locations (id, code, name, region, geom, is_active)
                values (?, ?, ?, 'W5-02 FIXTURE', ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                """,
                id,
                code,
                name,
                longitude,
                latitude,
                active);
        return id;
    }

    private void insertWeather(
            UUID station,
            Instant recordedAt,
            double rain1h,
            double rain3h,
            double windSpeed,
            double windGust) {
        jdbc.update(
                """
                insert into weather_records
                    (location_id, recorded_at, temperature, feels_like, humidity, pressure,
                     wind_speed, wind_direction, wind_gust, rain_1h, rain_3h,
                     weather_condition, weather_description)
                values (?, ?, 25, 25, 70, 1000, ?, 90, ?, ?, ?, 'Rain', 'FIXTURE mưa')
                """,
                station,
                OffsetDateTime.ofInstant(recordedAt, ZoneOffset.UTC),
                windSpeed,
                windGust,
                rain1h,
                rain3h);
    }

    private void insertStorm(
            String id,
            String status,
            double longitude,
            double latitude,
            Instant updatedAt,
            double maxWindSpeed,
            double minPressure) {
        jdbc.update(
                """
                insert into storms
                    (id, name, year, category, max_wind_speed, min_pressure, status,
                     start_date, geom_current, updated_at)
                values (?, ?, 2026, 'Tropical Storm', ?, ?, ?,
                        '2026-11-16T00:00:00Z', ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                """,
                id,
                "Storm " + id,
                maxWindSpeed,
                minPressure,
                status,
                longitude,
                latitude,
                OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
    }

    private void insertTrack(
            String stormId,
            String trackType,
            Instant forecastTime,
            double longitude,
            double latitude,
            double windSpeed,
            double windGust,
            double pressure) {
        jdbc.update(
                """
                insert into storm_tracks
                    (storm_id, track_type, forecast_time, geom, wind_speed, wind_gust, pressure, category)
                values (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?, 'Tropical Storm')
                """,
                stormId,
                trackType,
                OffsetDateTime.ofInstant(forecastTime, ZoneOffset.UTC),
                longitude,
                latitude,
                windSpeed,
                windGust,
                pressure);
    }

    private Integer insertThreshold(double value) {
        return jdbc.queryForObject(
                """
                insert into alert_thresholds
                    (name, parameter, operator, threshold_value, severity_level, description, is_enabled)
                values ('W5-02 FIXTURE', 'rain_1h', '>=', ?, 'DANGER', 'FIXTURE', true)
                returning id
                """,
                Integer.class,
                value);
    }

    private void insertAlert(
            UUID id,
            String status,
            String snapshotJson,
            Instant triggeredAt,
            Instant resolvedAt,
            Integer thresholdId) {
        jdbc.update(
                """
                insert into alerts
                    (id, threshold_id, location_id, title, severity_level, trigger_reason,
                     ai_explanation, status, triggered_at, resolved_at, factual_snapshot)
                values (?, ?, ?, 'FIXTURE W5-02 alert', 'DANGER', 'FIXTURE rain violation',
                        'FIXTURE explanation', ?, ?, ?, ?::jsonb)
                """,
                id,
                thresholdId,
                stationA,
                status,
                OffsetDateTime.ofInstant(triggeredAt, ZoneOffset.UTC),
                resolvedAt == null ? null : OffsetDateTime.ofInstant(resolvedAt, ZoneOffset.UTC),
                snapshotJson);
    }
}
