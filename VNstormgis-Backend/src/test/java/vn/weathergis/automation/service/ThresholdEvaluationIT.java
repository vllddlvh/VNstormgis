package vn.weathergis.automation.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;
import vn.weathergis.alert.dto.AlertThresholdRequest;
import vn.weathergis.alert.service.AlertThresholdService;
import vn.weathergis.automation.dto.WeatherSyncResult;
import vn.weathergis.automation.event.ThresholdViolatedEvent;
import vn.weathergis.automation.event.ThresholdViolatedEvent.Violation;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

/** End-to-end evaluator wiring against real PostGIS, with rollback-isolated source fixtures. */
@SpringBootTest
@RecordApplicationEvents
@Transactional
class ThresholdEvaluationIT extends AbstractPostGisIntegrationTest {

    private static final OffsetDateTime OBSERVED_AT =
            OffsetDateTime.parse("2026-11-05T10:00:00+07:00");
    private static final OffsetDateTime STORM_UPDATED_AT =
            OffsetDateTime.parse("2026-11-05T10:05:00+07:00");

    @Autowired private ThresholdEvaluationScheduler scheduler;
    @Autowired private AlertThresholdService thresholdService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEvents events;
    @Autowired private EntityManager entityManager;

    private UUID firstStation;
    private UUID secondStation;
    private long initialAlerts;
    private long initialMonitorLogs;

    @BeforeEach
    void isolateSources() {
        jdbc.update("update locations set is_active = false where is_active = true");
        jdbc.update("update storms set status = 'dissipated' where status = 'active'");
        jdbc.update("update alert_thresholds set is_enabled = false where is_enabled = true");
        firstStation = station("W4E_FIRST", "Trạm Đà Nẵng", 108.2, 16.0, true);
        secondStation = station("W4E_SECOND", "Trạm phía đông", 109.2, 16.0, true);
        initialAlerts = count("select count(*) from alerts");
        initialMonitorLogs =
                count("select count(*) from data_sync_logs where source = 'AI_MONITOR'");
    }

    @Test
    void publishesOneFactualPayloadPerStationAndStableUtcKeysForTheSameSnapshot() {
        weather(firstStation, OBSERVED_AT.minusHours(1), 999.0, 999.0);
        weather(firstStation, OBSERVED_AT, 2.0, 2.0);
        weather(firstStation, OBSERVED_AT, 55.0, 18.0);
        UUID inactiveStation = station("W4E_INACTIVE", "Trạm tắt", 108.2, 16.0, false);
        weather(inactiveStation, OBSERVED_AT, 999.0, 999.0);
        Integer rainThreshold = threshold("rain_1h", ">=", 50.0, "DANGER", true);
        threshold("wind_speed", ">=", 15.0, "WARNING", true);
        threshold("storm_dist", "<=", 0.0, "CRITICAL", true);
        threshold("wind_gust", ">", 1.0, "CRITICAL", false);
        storm("W4E_NEAREST", "Bão kiểm thử", "active", 108.2, 16.0);
        storm("W4E_FAR", "Bão xa", "active", 115.0, 20.0);
        storm("W4E_DISSIPATED", "Bão đã tan", "dissipated", 109.2, 16.0);

        Instant beforeEvaluation = Instant.now();
        evaluate();

        assertThat(violatedEvents()).hasSize(1);
        ThresholdViolatedEvent event = violatedEvents().getFirst();
        assertThat(event.locationId()).isEqualTo(firstStation);
        assertThat(event.locationCode()).isEqualTo("W4E_FIRST");
        assertThat(event.locationName()).isEqualTo("Trạm Đà Nẵng");
        assertThat(event.longitude()).isEqualTo(108.2);
        assertThat(event.latitude()).isEqualTo(16.0);
        assertThat(event.evaluationCycleId()).isNotNull();
        assertThat(event.evaluatedAt()).isBetween(beforeEvaluation, Instant.now());
        assertThat(event.violations())
                .extracting(Violation::parameter)
                .containsExactly("rain_1h", "wind_speed", "storm_dist");
        Violation rain = event.violations().getFirst();
        assertThat(rain.thresholdId()).isEqualTo(rainThreshold);
        assertThat(rain.operator()).isEqualTo(">=");
        assertThat(rain.thresholdValue()).isEqualTo(50.0);
        assertThat(rain.severityLevel()).isEqualTo("DANGER");
        assertThat(rain.measuredValue()).isEqualTo(55.0);
        assertThat(rain.unit()).isEqualTo("mm");
        assertThat(rain.sourceTimestamp()).isEqualTo(OBSERVED_AT.toInstant());
        assertThat(rain.evaluationWindow()).isEqualTo(Instant.parse("2026-11-05T03:00:00Z"));
        assertThat(rain.stormId()).isNull();
        assertThat(rain.stormName()).isNull();
        assertThat(rain.distanceKm()).isNull();
        Violation wind = event.violations().get(1);
        assertThat(wind.measuredValue()).isEqualTo(18.0);
        assertThat(wind.unit()).isEqualTo("m/s");
        Violation storm = event.violations().get(2);
        assertThat(storm.stormId()).isEqualTo("W4E_NEAREST");
        assertThat(storm.stormName()).isEqualTo("Bão kiểm thử");
        assertThat(storm.distanceKm()).isZero();
        assertThat(storm.measuredValue()).isZero();
        assertThat(storm.unit()).isEqualTo("km");
        assertThat(storm.sourceTimestamp()).isEqualTo(STORM_UPDATED_AT.toInstant());
        assertThat(storm.evaluationWindow()).isEqualTo(Instant.parse("2026-11-05T03:05:00Z"));
        assertThat(event.violations()).extracting(Violation::violationKey).doesNotHaveDuplicates();
        assertSuccessfulAudit(1);

        events.clear();
        evaluate();

        ThresholdViolatedEvent repeated = violatedEvents().getFirst();
        assertThat(repeated.evaluationCycleId()).isNotEqualTo(event.evaluationCycleId());
        assertThat(repeated.violations())
                .extracting(Violation::violationKey)
                .containsExactlyElementsOf(
                        event.violations().stream().map(Violation::violationKey).toList());
        assertThat(count("select count(*) from alerts")).isEqualTo(initialAlerts);
        assertSuccessfulAudit(2);
    }

    @Test
    void thresholdEditsAndTogglesTakeEffectOnTheNextScan() {
        weather(firstStation, OBSERVED_AT, 55.0, 0.0);
        Integer rule = threshold("rain_1h", ">=", 50.0, "DANGER", true);

        evaluate();
        assertThat(violatedEvents()).hasSize(1);

        thresholdService.update(rule, rainRequest(56.0, true));
        events.clear();
        evaluate();
        assertThat(violatedEvents()).isEmpty();

        thresholdService.update(rule, rainRequest(50.0, false));
        events.clear();
        evaluate();
        assertThat(violatedEvents()).isEmpty();

        thresholdService.toggle(rule);
        events.clear();
        evaluate();
        assertThat(violatedEvents()).hasSize(1);
        assertThat(violatedEvents().getFirst().violations().getFirst().thresholdValue())
                .isEqualTo(50.0);
        assertSuccessfulAudit(4);
    }

    @Test
    void missingWeatherAndAbsentActiveStormsSkipOnlyTheirOwnBranches() {
        weather(secondStation, OBSERVED_AT, 55.0, 0.0);
        threshold("rain_1h", ">=", 50.0, "DANGER", true);
        threshold("storm_dist", "<=", 0.0, "CRITICAL", true);
        storm("W4E_NO_WEATHER", "Bão cạnh trạm thiếu số đo", "active", 108.2, 16.0);

        evaluate();

        assertThat(violatedEvents()).hasSize(2);
        assertThat(eventAt(firstStation).violations())
                .extracting(Violation::parameter)
                .containsExactly("storm_dist");
        assertThat(eventAt(secondStation).violations())
                .extracting(Violation::parameter)
                .containsExactly("rain_1h");

        jdbc.update("update storms set status = 'dissipated' where id = 'W4E_NO_WEATHER'");
        events.clear();
        evaluate();

        assertThat(violatedEvents()).hasSize(1);
        assertThat(violatedEvents().getFirst().locationId()).isEqualTo(secondStation);
        assertThat(violatedEvents().getFirst().violations())
                .extracting(Violation::parameter)
                .containsExactly("rain_1h");
        assertSuccessfulAudit(2);
    }

    @Test
    void stormDistanceUsesKilometresAtZeroExactAndOutsideBoundaries() {
        storm("W4E_BOUNDARY", "Bão tại trạm đầu", "active", 108.2, 16.0);
        Double boundaryKm =
                jdbc.queryForObject(
                        """
                        select ST_DistanceSphere(l.geom, s.geom_current) / 1000.0
                        from locations l cross join storms s
                        where l.id = ? and s.id = 'W4E_BOUNDARY'
                        """,
                        Double.class,
                        secondStation);
        assertThat(boundaryKm).isBetween(100.0, 115.0);
        Integer rule = threshold("storm_dist", "=", boundaryKm, "CRITICAL", true);

        evaluate();

        assertThat(violatedEvents()).hasSize(1);
        assertThat(violatedEvents().getFirst().locationId()).isEqualTo(secondStation);
        assertThat(violatedEvents().getFirst().violations().getFirst().distanceKm())
                .isEqualTo(boundaryKm);

        thresholdService.update(rule, stormRequest("<=", boundaryKm));
        events.clear();
        evaluate();
        assertThat(violatedEvents()).hasSize(2);
        assertThat(eventAt(firstStation).violations().getFirst().distanceKm()).isZero();

        thresholdService.update(rule, stormRequest("<", boundaryKm));
        events.clear();
        evaluate();
        assertThat(violatedEvents()).hasSize(1);
        assertThat(violatedEvents().getFirst().locationId()).isEqualTo(firstStation);

        thresholdService.update(rule, stormRequest("<=", Math.nextDown(boundaryKm)));
        events.clear();
        evaluate();
        assertThat(violatedEvents()).hasSize(1);
        assertThat(violatedEvents().getFirst().locationId()).isEqualTo(firstStation);
        assertSuccessfulAudit(4);
    }

    private void evaluate() {
        scheduler.evaluateAfterSync(
                new WeatherSyncResult(
                        "OPENWEATHERMAP", "SUCCESS", 1, 2, OBSERVED_AT, OBSERVED_AT, null));
    }

    private List<ThresholdViolatedEvent> violatedEvents() {
        return events.stream(ThresholdViolatedEvent.class).toList();
    }

    private ThresholdViolatedEvent eventAt(UUID locationId) {
        return violatedEvents().stream()
                .filter(event -> event.locationId().equals(locationId))
                .findFirst()
                .orElseThrow();
    }

    private void assertSuccessfulAudit(int expectedCycles) {
        entityManager.flush();
        assertThat(count("select count(*) from data_sync_logs where source = 'AI_MONITOR'"))
                .isEqualTo(initialMonitorLogs + expectedCycles);
        var audit =
                jdbc.queryForMap(
                        """
                        select status, records_processed, error_message,
                               completed_at >= started_at as valid_times
                        from data_sync_logs where source = 'AI_MONITOR' order by id desc limit 1
                        """);
        assertThat(audit.get("status")).isEqualTo("SUCCESS");
        assertThat(audit.get("records_processed")).isEqualTo(2);
        assertThat(audit.get("error_message")).isNull();
        assertThat(audit.get("valid_times")).isEqualTo(true);
    }

    private UUID station(
            String code, String name, double longitude, double latitude, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into locations (id, code, name, region, geom, is_active)
                values (?, ?, ?, 'W4-02 test', ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                """,
                id,
                code,
                name,
                longitude,
                latitude,
                active);
        return id;
    }

    private void weather(UUID locationId, OffsetDateTime recordedAt, double rain, double wind) {
        jdbc.update(
                """
                insert into weather_records
                    (location_id, recorded_at, temperature, feels_like, humidity, pressure,
                     wind_speed, wind_direction, wind_gust, rain_1h, rain_3h,
                     weather_condition, weather_description)
                values (?, ?, 25, 25, 80, 1000, ?, 90, 30, ?, 2, 'Rain', 'Fixture')
                """,
                locationId,
                recordedAt,
                wind,
                rain);
    }

    private void storm(String id, String name, String status, double longitude, double latitude) {
        jdbc.update(
                """
                insert into storms (id, name, year, category, max_wind_speed, status,
                                    start_date, geom_current, updated_at)
                values (?, ?, 2026, 'Tropical Storm', 80, ?, ?,
                        ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                """,
                id,
                name,
                status,
                STORM_UPDATED_AT.minusDays(1),
                longitude,
                latitude,
                STORM_UPDATED_AT);
    }

    private Integer threshold(
            String parameter, String operator, double value, String severity, boolean enabled) {
        return jdbc.queryForObject(
                """
                insert into alert_thresholds
                    (name, parameter, operator, threshold_value, severity_level, description, is_enabled)
                values ('W4-02 fixture', ?, ?, ?, ?, 'Isolated integration fixture', ?) returning id
                """,
                Integer.class,
                parameter,
                operator,
                value,
                severity,
                enabled);
    }

    private AlertThresholdRequest rainRequest(double value, boolean enabled) {
        return new AlertThresholdRequest(
                "W4-02 rain", "rain_1h", ">=", value, "DANGER", "Fixture", enabled);
    }

    private AlertThresholdRequest stormRequest(String operator, double value) {
        return new AlertThresholdRequest(
                "W4-02 storm", "storm_dist", operator, value, "CRITICAL", "Fixture", true);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
