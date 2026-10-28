package vn.weathergis.storm.repository;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.weathergis.storm.domain.StormTrackEntity;

/**
 * JpaRepository cho bảng storm_tracks. Chèn lũy đẳng qua native ON CONFLICT dựa trên ràng buộc
 * uq_storm_track (storm_id, track_type, forecast_time) — chống trùng mốc khi nạp lại bản tin (V4).
 * wind_speed/wind_gust/pressure nhận NULL từ V6 (nguồn GDACS không công bố theo mốc).
 */
@Repository
public interface StormTrackRepository extends JpaRepository<StormTrackEntity, Long> {

    /**
     * Bulk upsert-idempotent: mọi mốc trùng (storm_id, track_type, forecast_time) bị bỏ qua âm
     * thầm. Trả về số dòng THỰC SỰ chèn mới (0 hoặc 1) — phục vụ audit.
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    INSERT INTO storm_tracks
                        (storm_id, track_type, forecast_time, geom, wind_speed, wind_gust,
                         pressure, category, impact_radius_km, created_at)
                    VALUES (:stormId, :trackType, :atTime, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                            :windMs, :gustMs, :pressure, :category, :radiusKm, now())
                    ON CONFLICT ON CONSTRAINT uq_storm_track DO NOTHING
                    """,
            nativeQuery = true)
    int insertTrackPointIfAbsent(
            @Param("stormId") String stormId,
            @Param("trackType") String trackType,
            @Param("atTime") OffsetDateTime atTime,
            @Param("lng") double lng,
            @Param("lat") double lat,
            @Param("windMs") Double windMs,
            @Param("gustMs") Double gustMs,
            @Param("pressure") Double pressure,
            @Param("category") String category,
            @Param("radiusKm") double radiusKm);

    /**
     * Tổng số mốc quỹ đạo của một bão (historical + forecast) — total_track_points của
     * /storms/{id}.
     */
    @Query("SELECT count(t) FROM StormTrackEntity t WHERE t.storm.id = :stormId")
    long countByStormId(@Param("stormId") String stormId);

    /**
     * Toàn bộ mốc quỹ đạo của một bão theo trật tự thời gian tăng dần — phục vụ {@code GET
     * /storms/{id}/track} (W3-02). Quét chỉ mục {@code idx_storm_tracks_time (storm_id,
     * forecast_time ASC)} dựng sẵn từ V4; service phân tách historical/forecast trong bộ nhớ để giữ
     * đúng 1 truy vấn cho mọi kích thước quỹ đạo.
     */
    @Query(
            "SELECT t FROM StormTrackEntity t WHERE t.storm.id = :stormId"
                    + " ORDER BY t.forecastTime ASC")
    List<StormTrackEntity> findByStormIdOrderByForecastTimeAsc(@Param("stormId") String stormId);
}
