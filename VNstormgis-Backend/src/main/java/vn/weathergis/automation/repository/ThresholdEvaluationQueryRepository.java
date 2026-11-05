package vn.weathergis.automation.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.weathergis.automation.model.NearestStorm;
import vn.weathergis.automation.model.StationSnapshot;
import vn.weathergis.automation.model.WeatherObservation;

/** Direct PostGIS snapshot; one statement keeps station, weather and storm reads consistent. */
@Repository
public class ThresholdEvaluationQueryRepository {

    private static final String SNAPSHOT_SQL =
            """
            SELECT l.id AS location_id, l.code, l.name,
                   ST_X(l.geom) AS longitude, ST_Y(l.geom) AS latitude,
                   w.id AS weather_id, w.recorded_at,
                   w.rain_1h, w.rain_3h, w.wind_speed, w.wind_gust,
                   s.storm_id, s.storm_name, s.distance_km, s.updated_at AS storm_updated_at
              FROM locations l
              LEFT JOIN LATERAL (
                  SELECT wr.id, wr.recorded_at, wr.rain_1h, wr.rain_3h,
                         wr.wind_speed, wr.wind_gust
                    FROM weather_records wr
                   WHERE wr.location_id = l.id
                   ORDER BY wr.recorded_at DESC, wr.id DESC
                   LIMIT 1
              ) w ON true
              LEFT JOIN LATERAL (
                  SELECT st.id AS storm_id, st.name AS storm_name, st.updated_at,
                         ST_DistanceSphere(l.geom, st.geom_current) / 1000.0 AS distance_km
                    FROM storms st
                   WHERE st.status = 'active'
                   ORDER BY distance_km NULLS LAST, st.id
                   LIMIT 1
              ) s ON true
             WHERE l.is_active = true
             ORDER BY l.id
            """;

    private final JdbcTemplate jdbc;

    public ThresholdEvaluationQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Indexed latest-row lookup per active station; never loads weather history into Java. */
    public List<StationSnapshot> findActiveStationSnapshots() {
        return jdbc.query(SNAPSHOT_SQL, (row, index) -> mapSnapshot(row));
    }

    private StationSnapshot mapSnapshot(ResultSet row) throws SQLException {
        WeatherObservation weather =
                row.getObject("weather_id") == null
                        ? null
                        : new WeatherObservation(
                                row.getObject("rain_1h", Double.class),
                                row.getObject("rain_3h", Double.class),
                                row.getObject("wind_speed", Double.class),
                                row.getObject("wind_gust", Double.class),
                                row.getObject("recorded_at", OffsetDateTime.class).toInstant());
        String stormId = row.getString("storm_id");
        Double distanceKm = row.getObject("distance_km", Double.class);
        OffsetDateTime stormUpdatedAt = row.getObject("storm_updated_at", OffsetDateTime.class);
        NearestStorm storm =
                stormId == null || distanceKm == null
                        ? null
                        : new NearestStorm(
                                stormId,
                                row.getString("storm_name"),
                                distanceKm,
                                stormUpdatedAt == null ? null : stormUpdatedAt.toInstant());
        Double longitude = row.getObject("longitude", Double.class);
        Double latitude = row.getObject("latitude", Double.class);
        return new StationSnapshot(
                row.getObject("location_id", UUID.class),
                row.getString("code"),
                row.getString("name"),
                longitude == null ? Double.NaN : longitude,
                latitude == null ? Double.NaN : latitude,
                weather,
                storm);
    }
}
