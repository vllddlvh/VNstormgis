package vn.weathergis.chat.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.weathergis.chat.context.ChatContextRequest;

/**
 * Direct PostGIS reads for chat grounding (pattern D-W4-02-03): one LATERAL statement per intent,
 * latest row per station by {@code recorded_at DESC, id DESC}, never a history scan, never Redis.
 * Bbox parameters follow WGS84 {@code [minLng, minLat, maxLng, maxLat]} and use the GiST index via
 * {@code &&}.
 */
@Repository
public class ChatContextQueryRepository {

    private static final String LATEST_PER_ACTIVE_STATION_SQL =
            """
            SELECT l.id AS location_id, l.code, l.name,
                   ST_X(l.geom) AS longitude, ST_Y(l.geom) AS latitude,
                   w.id AS weather_id, w.recorded_at,
                   w.temperature, w.humidity, w.pressure,
                   w.wind_speed, w.wind_gust, w.wind_direction,
                   w.rain_1h, w.rain_3h, w.weather_condition, w.weather_description
              FROM locations l
              LEFT JOIN LATERAL (
                  SELECT wr.id, wr.recorded_at, wr.temperature, wr.humidity, wr.pressure,
                         wr.wind_speed, wr.wind_gust, wr.wind_direction,
                         wr.rain_1h, wr.rain_3h, wr.weather_condition, wr.weather_description
                    FROM weather_records wr
                   WHERE wr.location_id = l.id
                   ORDER BY wr.recorded_at DESC, wr.id DESC
                   LIMIT 1
              ) w ON true
             WHERE l.is_active = true
            """;

    private static final String BBOX_CLAUSE = " AND l.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)";

    private static final String LATEST_FOR_STATION_SQL =
            """
            SELECT l.id AS location_id, l.code, l.name,
                   ST_X(l.geom) AS longitude, ST_Y(l.geom) AS latitude,
                   w.id AS weather_id, w.recorded_at,
                   w.temperature, w.humidity, w.pressure,
                   w.wind_speed, w.wind_gust, w.wind_direction,
                   w.rain_1h, w.rain_3h, w.weather_condition, w.weather_description
              FROM locations l
              LEFT JOIN LATERAL (
                  SELECT wr.id, wr.recorded_at, wr.temperature, wr.humidity, wr.pressure,
                         wr.wind_speed, wr.wind_gust, wr.wind_direction,
                         wr.rain_1h, wr.rain_3h, wr.weather_condition, wr.weather_description
                    FROM weather_records wr
                   WHERE wr.location_id = l.id
                   ORDER BY wr.recorded_at DESC, wr.id DESC
                   LIMIT 1
              ) w ON true
             WHERE l.id = ?
            """;

    private static final String ACTIVE_STORMS_SQL =
            """
            SELECT s.id, s.name, s.category, s.max_wind_speed, s.min_pressure,
                   ST_X(s.geom_current) AS longitude, ST_Y(s.geom_current) AS latitude,
                   s.updated_at,
                   t.id AS track_id, t.forecast_time, t.track_type,
                   ST_X(t.geom) AS track_longitude, ST_Y(t.geom) AS track_latitude,
                   t.wind_speed AS track_wind_speed, t.wind_gust AS track_wind_gust,
                   t.pressure AS track_pressure, t.category AS track_category
              FROM storms s
              LEFT JOIN LATERAL (
                  SELECT st.id, st.forecast_time, st.track_type, st.geom,
                         st.wind_speed, st.wind_gust, st.pressure, st.category
                    FROM storm_tracks st
                   WHERE st.storm_id = s.id AND st.track_type = 'forecast'
                   ORDER BY ABS(EXTRACT(EPOCH FROM (st.forecast_time - CAST(? AS timestamptz)))) ASC,
                            st.forecast_time ASC, st.id ASC
                   LIMIT 1
              ) t ON true
             WHERE s.status = 'active'
             ORDER BY s.updated_at DESC, s.id ASC
             LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public ChatContextQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StationObservationRow> findLatestPerActiveStation(ChatContextRequest.Bbox bbox) {
        if (bbox == null) {
            return jdbc.query(
                    LATEST_PER_ACTIVE_STATION_SQL + " ORDER BY l.code",
                    (row, index) -> mapStation(row));
        }
        return jdbc.query(
                LATEST_PER_ACTIVE_STATION_SQL + BBOX_CLAUSE + " ORDER BY l.code",
                (row, index) -> mapStation(row),
                bbox.minLng(),
                bbox.minLat(),
                bbox.maxLng(),
                bbox.maxLat());
    }

    public StationObservationRow findLatestForStation(UUID locationId) {
        List<StationObservationRow> rows =
                jdbc.query(LATEST_FOR_STATION_SQL, (row, index) -> mapStation(row), locationId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<StormSummaryRow> findActiveStormSummaries(Instant now, int limit) {
        return jdbc.query(
                ACTIVE_STORMS_SQL,
                (row, index) -> mapStorm(row),
                OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
                limit);
    }

    private StationObservationRow mapStation(ResultSet row) throws SQLException {
        return new StationObservationRow(
                row.getObject("location_id", UUID.class),
                row.getString("code"),
                row.getString("name"),
                row.getObject("longitude", Double.class),
                row.getObject("latitude", Double.class),
                row.getObject("weather_id") != null,
                instant(row, "recorded_at"),
                row.getObject("temperature", Double.class),
                row.getObject("humidity", Double.class),
                row.getObject("pressure", Double.class),
                row.getObject("wind_speed", Double.class),
                row.getObject("wind_gust", Double.class),
                row.getObject("wind_direction", Double.class),
                row.getObject("rain_1h", Double.class),
                row.getObject("rain_3h", Double.class),
                row.getString("weather_condition"),
                row.getString("weather_description"));
    }

    private StormSummaryRow mapStorm(ResultSet row) throws SQLException {
        return new StormSummaryRow(
                row.getString("id"),
                row.getString("name"),
                row.getString("category"),
                row.getObject("max_wind_speed", Double.class),
                row.getObject("min_pressure", Double.class),
                row.getObject("longitude", Double.class),
                row.getObject("latitude", Double.class),
                instant(row, "updated_at"),
                row.getObject("track_id") != null,
                instant(row, "forecast_time"),
                row.getString("track_type"),
                row.getObject("track_longitude", Double.class),
                row.getObject("track_latitude", Double.class),
                row.getObject("track_wind_speed", Double.class),
                row.getObject("track_wind_gust", Double.class),
                row.getObject("track_pressure", Double.class),
                row.getString("track_category"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
