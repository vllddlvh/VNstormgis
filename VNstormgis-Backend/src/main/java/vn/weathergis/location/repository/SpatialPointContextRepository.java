package vn.weathergis.location.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.weathergis.location.dto.SpatialPointContextResponse.CurrentWeatherSnapshot;
import vn.weathergis.location.dto.SpatialPointContextResponse.NearbyAlertSummary;
import vn.weathergis.location.dto.SpatialPointContextResponse.NearestActiveStorm;
import vn.weathergis.location.dto.SpatialPointContextResponse.NearestStation;

/**
 * Truy vấn đọc phân tích không gian PostGIS cho chức năng "Vị trí hiện tại của tôi" (SRID 4326, thứ
 * tự tọa độ [lng, lat]). Sử dụng toán tử KNN GiST ({@code <->}) và {@code ST_DistanceSphere}.
 */
@Repository
public class SpatialPointContextRepository {

    private static final String NEAREST_STATION_WITH_WEATHER_SQL =
            """
            SELECT l.id AS station_id,
                   l.code AS station_code,
                   l.name AS station_name,
                   l.region AS station_region,
                   ST_X(l.geom) AS station_lng,
                   ST_Y(l.geom) AS station_lat,
                   ST_DistanceSphere(l.geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)) / 1000.0 AS distance_km,
                   w.recorded_at,
                   w.temperature,
                   w.feels_like,
                   w.humidity,
                   w.pressure,
                   w.wind_speed,
                   w.wind_gust,
                   w.wind_direction,
                   w.rain_1h,
                   w.rain_3h,
                   w.weather_condition,
                   w.weather_description
            FROM locations l
            LEFT JOIN LATERAL (
                SELECT wr.*
                FROM weather_records wr
                WHERE wr.location_id = l.id
                ORDER BY wr.recorded_at DESC, wr.id DESC
                LIMIT 1
            ) w ON true
            WHERE l.is_active = true
              AND l.geom IS NOT NULL
              AND NOT ST_IsEmpty(l.geom)
            ORDER BY l.geom <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                     distance_km ASC,
                     l.code ASC
            LIMIT 1
            """;

    private static final String NEAREST_ACTIVE_STORM_SQL =
            """
            SELECT s.id,
                   s.name,
                   s.category,
                   s.max_wind_speed,
                   s.min_pressure,
                   ST_X(s.geom_current) AS storm_lng,
                   ST_Y(s.geom_current) AS storm_lat,
                   ST_DistanceSphere(s.geom_current, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)) / 1000.0 AS distance_km,
                   s.updated_at
            FROM storms s
            WHERE s.status = 'active'
              AND s.geom_current IS NOT NULL
              AND NOT ST_IsEmpty(s.geom_current)
            ORDER BY distance_km ASC, s.id ASC
            LIMIT 1
            """;

    private static final String NEARBY_ACTIVE_ALERTS_SQL =
            """
            SELECT a.id,
                   a.title,
                   a.severity_level,
                   l.code AS location_code,
                   l.name AS location_name,
                   ST_DistanceSphere(
                       COALESCE(a.affected_geom, l.geom),
                       ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
                   ) / 1000.0 AS distance_km,
                   a.triggered_at
            FROM alerts a
            LEFT JOIN locations l ON l.id = a.location_id
            WHERE a.status = 'active'
              AND COALESCE(a.affected_geom, l.geom) IS NOT NULL
              AND NOT ST_IsEmpty(COALESCE(a.affected_geom, l.geom))
              AND ST_DWithin(
                  COALESCE(a.affected_geom, l.geom)::geography,
                  ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                  :radiusMeters
              )
            ORDER BY CASE a.severity_level
                         WHEN 'CRITICAL' THEN 1
                         WHEN 'DANGER' THEN 2
                         WHEN 'WARNING' THEN 3
                         ELSE 4
                     END ASC,
                     distance_km ASC,
                     a.triggered_at DESC
            LIMIT 10
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SpatialPointContextRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record StationWeatherPair(
            NearestStation station, CurrentWeatherSnapshot weatherSnapshot) {}

    public Optional<StationWeatherPair> findNearestStationWithWeather(double lng, double lat) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("lng", lng).addValue("lat", lat);
        List<StationWeatherPair> list =
                jdbc.query(
                        NEAREST_STATION_WITH_WEATHER_SQL,
                        params,
                        (rs, rowNum) -> mapStationWeather(rs));
        return list.stream().findFirst();
    }

    public Optional<NearestActiveStorm> findNearestActiveStorm(double lng, double lat) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("lng", lng).addValue("lat", lat);
        List<NearestActiveStorm> list =
                jdbc.query(NEAREST_ACTIVE_STORM_SQL, params, (rs, rowNum) -> mapNearestStorm(rs));
        return list.stream().findFirst();
    }

    public List<NearbyAlertSummary> findNearbyActiveAlerts(
            double lng, double lat, double radiusMeters) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("lng", lng)
                        .addValue("lat", lat)
                        .addValue("radiusMeters", radiusMeters);
        return jdbc.query(NEARBY_ACTIVE_ALERTS_SQL, params, (rs, rowNum) -> mapNearbyAlert(rs));
    }

    private static StationWeatherPair mapStationWeather(ResultSet rs) throws SQLException {
        NearestStation station =
                new NearestStation(
                        rs.getObject("station_id", UUID.class),
                        rs.getString("station_code"),
                        rs.getString("station_name"),
                        rs.getString("station_region"),
                        rs.getDouble("station_lng"),
                        rs.getDouble("station_lat"),
                        roundOneDecimal(rs.getDouble("distance_km")));

        OffsetDateTime recordedAt = rs.getObject("recorded_at", OffsetDateTime.class);
        CurrentWeatherSnapshot weather = null;
        if (recordedAt != null) {
            weather =
                    new CurrentWeatherSnapshot(
                            recordedAt,
                            nullableDouble(rs, "temperature"),
                            nullableDouble(rs, "feels_like"),
                            nullableDouble(rs, "humidity"),
                            nullableDouble(rs, "pressure"),
                            nullableDouble(rs, "wind_speed"),
                            nullableDouble(rs, "wind_gust"),
                            nullableDouble(rs, "wind_direction"),
                            nullableDouble(rs, "rain_1h"),
                            nullableDouble(rs, "rain_3h"),
                            rs.getString("weather_condition"),
                            rs.getString("weather_description"));
        }
        return new StationWeatherPair(station, weather);
    }

    private static NearestActiveStorm mapNearestStorm(ResultSet rs) throws SQLException {
        return new NearestActiveStorm(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("category"),
                nullableDouble(rs, "max_wind_speed"),
                nullableDouble(rs, "min_pressure"),
                rs.getDouble("storm_lng"),
                rs.getDouble("storm_lat"),
                roundOneDecimal(rs.getDouble("distance_km")),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private static NearbyAlertSummary mapNearbyAlert(ResultSet rs) throws SQLException {
        return new NearbyAlertSummary(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("severity_level"),
                rs.getString("location_code"),
                rs.getString("location_name"),
                roundOneDecimal(rs.getDouble("distance_km")),
                rs.getObject("triggered_at", OffsetDateTime.class));
    }

    private static Double nullableDouble(ResultSet rs, String col) throws SQLException {
        double val = rs.getDouble(col);
        return rs.wasNull() ? null : val;
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
