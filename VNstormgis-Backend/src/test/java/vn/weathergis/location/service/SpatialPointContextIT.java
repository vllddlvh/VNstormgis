package vn.weathergis.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.location.domain.LocationEntity;
import vn.weathergis.location.dto.SpatialPointContextResponse;
import vn.weathergis.location.repository.LocationRepository;

@SpringBootTest
@DisplayName("SpatialPointContext PostGIS Integration Tests")
class SpatialPointContextIT extends AbstractPostGisIntegrationTest {

    @Autowired private LocationService locationService;

    @Autowired private LocationRepository locationRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String TEST_STORM_ID = "TC-TEST-ATPOINT-01";
    private UUID testAlertId;
    private Long testWeatherId;

    @AfterEach
    void cleanUp() {
        if (testAlertId != null) {
            jdbcTemplate.update("DELETE FROM alerts WHERE id = ?", testAlertId);
            testAlertId = null;
        }
        jdbcTemplate.update("DELETE FROM storms WHERE id = ?", TEST_STORM_ID);
        if (testWeatherId != null) {
            jdbcTemplate.update("DELETE FROM weather_records WHERE id = ?", testWeatherId);
            testWeatherId = null;
        }
    }

    @Test
    @DisplayName(
            "Phân tích không gian tại Đà Nẵng: khớp trạm KNN, thời tiết mới nhất, bão active và cảnh báo")
    void getSpatialPointContext_ReturnsNearestStationWeatherStormAndAlerts() {
        LocationEntity danang = locationRepository.findByCode("DNG").orElseThrow();
        Instant now = Instant.parse("2026-11-18T01:00:00Z");

        testWeatherId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO weather_records (
                            location_id, recorded_at, temperature, feels_like, humidity, pressure,
                            wind_speed, wind_direction, wind_gust, rain_1h, rain_3h,
                            weather_condition, weather_description
                        ) VALUES (?, ?, 25.4, 27.1, 91.0, 1002.5, 14.2, 80.0, 20.5, 42.8, 95.0, 'Thunderstorm', 'mưa dông mạnh')
                        RETURNING id
                        """,
                        Long.class,
                        danang.getId(),
                        Timestamp.from(now));

        jdbcTemplate.update(
                """
                INSERT INTO storms (
                    id, name, year, category, max_wind_speed, min_pressure, status, start_date, geom_current, updated_at
                ) VALUES (?, 'TESTSTORM', 2026, 'Red', 148.0, 960.0, 'active', ?, ST_SetSRID(ST_MakePoint(110.0, 16.0544), 4326), ?)
                """,
                TEST_STORM_ID,
                Timestamp.from(now.minusSeconds(3600)),
                Timestamp.from(now));

        testAlertId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO alerts (
                    id, location_id, storm_id, title, severity_level, trigger_reason,
                    ai_explanation, is_fallback, affected_geom, status, triggered_at, created_at
                ) VALUES (
                    ?, ?, ?, 'Cảnh báo dông bão khu vực Đà Nẵng', 'CRITICAL', 'rain_1h >= 40mm',
                    'Bản tin thử nghiệm', true, ST_SetSRID(ST_MakePoint(108.2022, 16.0544), 4326), 'active', ?, ?
                )
                """,
                testAlertId,
                danang.getId(),
                TEST_STORM_ID,
                Timestamp.from(now),
                Timestamp.from(now));

        SpatialPointContextResponse ctx =
                locationService.getSpatialPointContext(108.2100, 16.0600, 50.0);

        assertThat(ctx.withinVietnamBounds()).isTrue();
        assertThat(ctx.nearestStation()).isNotNull();
        assertThat(ctx.nearestStation().code()).isEqualTo("DNG");
        assertThat(ctx.nearestStation().distanceKm()).isCloseTo(1.0, within(0.5));

        assertThat(ctx.currentWeather()).isNotNull();
        assertThat(ctx.currentWeather().rain1h()).isEqualTo(42.8);
        assertThat(ctx.currentWeather().windSpeed()).isEqualTo(14.2);

        assertThat(ctx.nearestStorm()).isNotNull();
        assertThat(ctx.nearestStorm().id()).isEqualTo(TEST_STORM_ID);
        assertThat(ctx.nearestStorm().distanceKm()).isBetween(180.0, 210.0);

        assertThat(ctx.nearbyAlerts())
                .extracting(SpatialPointContextResponse.NearbyAlertSummary::id)
                .contains(testAlertId);
    }

    @Test
    @DisplayName(
            "Tọa độ ngoài khung Việt Nam vẫn tìm được trạm gần nhất KNN và đánh dấu withinVietnamBounds=false")
    void getSpatialPointContext_OutsideVietnamBoundsFlagsCorrectly() {
        SpatialPointContextResponse ctx =
                locationService.getSpatialPointContext(139.6917, 35.6895, 100.0);

        assertThat(ctx.withinVietnamBounds()).isFalse();
        assertThat(ctx.nearestStation()).isNotNull();
        assertThat(ctx.nearestStation().distanceKm()).isGreaterThan(2000.0);
    }

    @Test
    @DisplayName("Từ chối tọa độ hoặc bán kính không hợp lệ")
    void getSpatialPointContext_RejectsInvalidParams() {
        assertThatThrownBy(() -> locationService.getSpatialPointContext(-181.0, 16.0, 50.0))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> locationService.getSpatialPointContext(108.0, 91.0, 50.0))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> locationService.getSpatialPointContext(108.0, 16.0, 0.0))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> locationService.getSpatialPointContext(108.0, 16.0, 2500.0))
                .isInstanceOf(InvalidParameterException.class);
    }
}
