package vn.weathergis.weather.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.weathergis.weather.domain.WeatherRecordEntity;

/**
 * Repository ĐỌC của bounded context weather (bên GHI là automation.WeatherRecordRepository —
 * W2-03). DISTINCT ON khớp chỉ mục idx_weather_records_loc_time (V4).
 */
@Repository
public interface WeatherRecordQueryRepository extends JpaRepository<WeatherRecordEntity, Long> {

    /** Bản ghi mới nhất của từng trạm active (native DISTINCT ON — SQL README Mục 3.4). */
    @Query(
            value =
                    """
                    SELECT DISTINCT ON (w.location_id)
                        l.id AS "locationId", l.code AS "code", l.name AS "name",
                        l.region AS "region",
                        ST_X(l.geom) AS "longitude", ST_Y(l.geom) AS "latitude",
                        w.temperature AS "temperature", w.feels_like AS "feelsLike",
                        w.humidity AS "humidity", w.pressure AS "pressure",
                        w.wind_speed AS "windSpeed", w.wind_direction AS "windDirection",
                        w.wind_gust AS "windGust", w.rain_1h AS "rain1h", w.rain_3h AS "rain3h",
                        w.weather_condition AS "weatherCondition",
                        w.weather_description AS "weatherDescription",
                        w.recorded_at AS "recordedAt"
                    FROM locations l
                    INNER JOIN weather_records w ON l.id = w.location_id
                    WHERE l.is_active = true
                    ORDER BY w.location_id, w.recorded_at DESC
                    """,
            nativeQuery = true)
    List<LatestWeatherPerStation> findLatestWeatherPerActiveStation();

    /** Interface projection — Spring Data map theo alias cột. */
    interface LatestWeatherPerStation {
        UUID getLocationId();

        String getCode();

        String getName();

        String getRegion();

        double getLongitude(); // ST_X = KINH ĐỘ

        double getLatitude(); // ST_Y = VĨ ĐỘ

        Double getTemperature();

        Double getFeelsLike();

        Double getHumidity();

        Double getPressure();

        Double getWindSpeed();

        Double getWindDirection();

        Double getWindGust();

        Double getRain1h();

        Double getRain3h();

        String getWeatherCondition();

        String getWeatherDescription();

        // Hibernate 6.5 project TIMESTAMPTZ native thành Instant (runbook W2-07 mục 7.3)
        Instant getRecordedAt();
    }

    /** Chuỗi thời gian của một trạm trong cửa sổ [from, now] — tăng dần theo recorded_at. */
    @Query(
            """
            SELECT w FROM WeatherRecordEntity w
            WHERE w.location.id = :locationId AND w.recordedAt >= :from
            ORDER BY w.recordedAt ASC
            """)
    List<WeatherRecordEntity> findHistoryByLocationId(
            @Param("locationId") UUID locationId, @Param("from") OffsetDateTime from);
}
