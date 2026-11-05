package vn.weathergis.automation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
import org.springframework.transaction.annotation.Transactional;
import vn.weathergis.automation.model.StationSnapshot;
import vn.weathergis.automation.model.ThresholdRule;
import vn.weathergis.automation.port.ThresholdRuleSource;
import vn.weathergis.automation.service.ThresholdEvaluator;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.weather.repository.WeatherRecordQueryRepository;

/** Isolated real PostGIS fixtures; all changes, including seed toggles, roll back per test. */
@SpringBootTest
@Transactional
class ThresholdEvaluationQueryRepositoryIT extends AbstractPostGisIntegrationTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-11-05T00:00:00Z");
    private static final Instant STORM_UPDATED_AT = Instant.parse("2026-11-05T00:05:00Z");

    @Autowired private ThresholdEvaluationQueryRepository repository;
    @Autowired private WeatherRecordQueryRepository weatherRepository;
    @Autowired private ThresholdRuleSource thresholdSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private UUID firstStation;
    private UUID secondStation;
    private UUID inactiveStation;

    @BeforeEach
    void createIsolatedStations() {
        jdbc.update("update locations set is_active = false");
        jdbc.update("update storms set status = 'dissipated'");
        firstStation = insertStation("W402_FIRST", 108.0, 16.0, true);
        secondStation = insertStation("W402_SECOND", 109.0, 16.0, true);
        inactiveStation = insertStation("W402_INACTIVE", 108.0, 16.0, false);
    }

    @Test
    void latestObservationUsesTimestampThenIdAndExcludesInactiveStations() {
        insertWeather(firstStation, "2026-11-04T23:00:00Z", 200.0, 250.0, 40.0, 50.0);
        insertWeather(firstStation, "2026-11-05T07:00:00+07:00", 10.0, 20.0, 5.0, 6.0);
        insertWeather(firstStation, "2026-11-05T00:00:00Z", 55.0, 80.0, 16.0, 25.0);
        insertWeather(inactiveStation, "2026-11-05T00:00:00Z", 500.0, 500.0, 50.0, 60.0);

        List<StationSnapshot> stations = repository.findActiveStationSnapshots();

        assertThat(stations)
                .extracting(StationSnapshot::locationId)
                .containsExactlyInAnyOrder(firstStation, secondStation);
        StationSnapshot first = station(stations, firstStation);
        assertThat(first.code()).isEqualTo("W402_FIRST");
        assertThat(first.name()).isEqualTo("Station W402_FIRST");
        assertThat(first.longitude()).isEqualTo(108.0);
        assertThat(first.latitude()).isEqualTo(16.0);
        assertThat(first.weather().rain1h()).isEqualTo(55.0);
        assertThat(first.weather().rain3h()).isEqualTo(80.0);
        assertThat(first.weather().windSpeed()).isEqualTo(16.0);
        assertThat(first.weather().windGust()).isEqualTo(25.0);
        assertThat(first.weather().recordedAt()).isEqualTo(OBSERVED_AT);
        assertThat(station(stations, secondStation).weather()).isNull();
        assertThat(weatherRepository.findLatestWeatherPerActiveStation())
                .singleElement()
                .satisfies(
                        weather -> {
                            assertThat(weather.getLocationId()).isEqualTo(firstStation);
                            assertThat(weather.getRain1h()).isEqualTo(55.0);
                        });
    }

    @Test
    void nearestActiveStormUsesKilometresAndStableIdTieBreakWithZeroDistance() {
        insertStorm("W402_B", "active", 109.0, 16.0);
        insertStorm("W402_A", "active", 109.0, 16.0);
        insertStorm("W402_FAR", "active", 112.0, 18.0);
        insertStorm("W402_DISSIPATED", "dissipated", 108.0, 16.0);

        List<StationSnapshot> stations = repository.findActiveStationSnapshots();

        StationSnapshot first = station(stations, firstStation);
        assertThat(first.storm().stormId()).isEqualTo("W402_A");
        assertThat(first.storm().name()).isEqualTo("Storm W402_A");
        assertThat(first.storm().updatedAt()).isEqualTo(STORM_UPDATED_AT);
        assertThat(first.storm().distanceKm()).isBetween(106.0, 108.0);
        Double metres =
                jdbc.queryForObject(
                        """
                select ST_DistanceSphere(l.geom, s.geom_current)
                  from locations l cross join storms s
                 where l.id = ? and s.id = ?
                """,
                        Double.class,
                        firstStation,
                        "W402_A");
        assertThat(first.storm().distanceKm() * 1000.0).isCloseTo(metres, within(0.00001));
        assertThat(station(stations, secondStation).storm().stormId()).isEqualTo("W402_A");
        assertThat(station(stations, secondStation).storm().distanceKm()).isZero();
        // Storm evaluation remains possible for stations without any weather observation.
        assertThat(first.weather()).isNull();
        assertThat(station(stations, secondStation).weather()).isNull();
    }

    @Test
    void missingWeatherAndActiveStormRemainIndependentlyAbsent() {
        insertWeather(firstStation, "2026-11-05T00:00:00Z", 55.0, 80.0, 16.0, 25.0);
        insertStorm("W402_DISSIPATED", "dissipated", 108.0, 16.0);

        List<StationSnapshot> stations = repository.findActiveStationSnapshots();

        assertThat(stations).hasSize(2).allSatisfy(station -> assertThat(station.storm()).isNull());
        assertThat(station(stations, firstStation).weather()).isNotNull();
        assertThat(station(stations, secondStation).weather()).isNull();
    }

    @Test
    void emptyStormGeometryNeverBecomesZeroDistanceOrHidesAValidStorm() {
        insertStorm("W402_EMPTY", "active", 108.0, 16.0);
        jdbc.update(
                "update storms set geom_current = ST_GeomFromText('POINT EMPTY', 4326) where id = ?",
                "W402_EMPTY");
        List<ThresholdRule> rules =
                List.of(new ThresholdRule(1, "storm_dist", "<=", 300.0, "CRITICAL", true));
        ThresholdEvaluator evaluator = new ThresholdEvaluator();

        List<StationSnapshot> missingDistances = repository.findActiveStationSnapshots();

        assertThat(missingDistances)
                .hasSize(2)
                .allSatisfy(
                        station -> {
                            assertThat(station.storm()).isNull();
                            assertThat(
                                            evaluator.evaluate(
                                                    station,
                                                    rules,
                                                    UUID.randomUUID(),
                                                    Instant.now()))
                                    .isEmpty();
                        });

        insertStorm("W402_VALID", "active", 109.0, 16.0);

        List<StationSnapshot> validDistances = repository.findActiveStationSnapshots();

        assertThat(validDistances)
                .allSatisfy(
                        station -> assertThat(station.storm().stormId()).isEqualTo("W402_VALID"));
        assertThat(station(validDistances, firstStation).storm().distanceKm())
                .isBetween(106.0, 108.0);
        assertThat(station(validDistances, secondStation).storm().distanceKm()).isZero();
    }

    @Test
    void emptyStationCoordinatesCannotProduceFactualEvents() {
        jdbc.update(
                "update locations set geom = ST_GeomFromText('POINT EMPTY', 4326) where id = ?",
                firstStation);
        insertWeather(firstStation, "2026-11-05T00:00:00Z", 55.0, 80.0, 16.0, 25.0);
        insertStorm("W402_VALID", "active", 108.0, 16.0);

        StationSnapshot first = station(repository.findActiveStationSnapshots(), firstStation);

        assertThat(first.longitude()).isNaN();
        assertThat(first.latitude()).isNaN();
        assertThat(first.storm()).isNull();
        assertThat(
                        new ThresholdEvaluator()
                                .evaluate(
                                        first,
                                        List.of(
                                                new ThresholdRule(
                                                        1, "rain_1h", ">=", 50.0, "DANGER", true),
                                                new ThresholdRule(
                                                        2,
                                                        "storm_dist",
                                                        "<=",
                                                        300.0,
                                                        "CRITICAL",
                                                        true)),
                                        UUID.randomUUID(),
                                        Instant.now()))
                .isEmpty();
    }

    @Test
    void enabledThresholdsAreReadAgainAfterAdministrativeChanges() {
        jdbc.update("update alert_thresholds set is_enabled = false");
        Integer id =
                jdbc.queryForObject(
                        """
                insert into alert_thresholds
                    (name, parameter, operator, threshold_value, severity_level, description, is_enabled)
                values ('W4-02 configurable', 'rain_1h', '>=', 50.0, 'DANGER', 'Fixture', true)
                returning id
                """,
                        Integer.class);

        assertThat(thresholdSource.findEnabledThresholds())
                .containsExactly(new ThresholdRule(id, "rain_1h", ">=", 50.0, "DANGER", true));

        jdbc.update("update alert_thresholds set threshold_value = 60.0 where id = ?", id);
        entityManager.clear();
        assertThat(thresholdSource.findEnabledThresholds())
                .containsExactly(new ThresholdRule(id, "rain_1h", ">=", 60.0, "DANGER", true));

        jdbc.update("update alert_thresholds set is_enabled = false where id = ?", id);
        entityManager.clear();
        assertThat(thresholdSource.findEnabledThresholds()).isEmpty();
    }

    private UUID insertStation(String code, double longitude, double latitude, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into locations (id, code, name, region, geom, is_active)
                values (?, ?, ?, 'W4-02 fixture', ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                """,
                id,
                code,
                "Station " + code,
                longitude,
                latitude,
                active);
        return id;
    }

    private void insertWeather(
            UUID station,
            String time,
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
                values (?, ?, 25, 25, 70, 1000, ?, 90, ?, ?, ?, 'Rain', 'Fixture')
                """,
                station,
                OffsetDateTime.parse(time),
                windSpeed,
                windGust,
                rain1h,
                rain3h);
    }

    private void insertStorm(String id, String status, double longitude, double latitude) {
        jdbc.update(
                """
                insert into storms
                    (id, name, year, category, max_wind_speed, min_pressure, status,
                     start_date, geom_current, updated_at)
                values (?, ?, 2026, 'Tropical Storm', 90, 980, ?,
                        '2026-11-04T00:00:00Z', ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                """,
                id,
                "Storm " + id,
                status,
                longitude,
                latitude,
                OffsetDateTime.ofInstant(STORM_UPDATED_AT, java.time.ZoneOffset.UTC));
    }

    private StationSnapshot station(List<StationSnapshot> stations, UUID id) {
        return stations.stream()
                .filter(station -> station.locationId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
