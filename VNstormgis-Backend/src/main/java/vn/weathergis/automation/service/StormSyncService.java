package vn.weathergis.automation.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import vn.weathergis.automation.client.GdacsStormClient;
import vn.weathergis.automation.client.exception.GdacsClientException;
import vn.weathergis.automation.domain.DataSyncLogEntity;
import vn.weathergis.automation.dto.StormSyncResult;
import vn.weathergis.automation.repository.DataSyncLogRepository;
import vn.weathergis.common.cache.CacheRegions;
import vn.weathergis.common.exception.SyncInProgressException;
import vn.weathergis.storm.domain.StormEntity;
import vn.weathergis.storm.repository.StormRepository;
import vn.weathergis.storm.repository.StormTrackRepository;

/**
 * Điều phối một chu kỳ đồng bộ bão GDACS + hiện thực SYS-RULE-001:
 *
 * <pre>
 *   [Nhánh A] Sự kiện MỚI trên feed, gió ≥ ngưỡng  → INSERT storms (status='active') + mốc quỹ đạo
 *   [Nhánh B] Sự kiện CÓ SẴN, dữ liệu cập nhật      → UPDATE geom_current/max_wind_speed/
 *                                                     min_pressure/category (+ trigger V4 tự gán
 *                                                     updated_at) + chèn mốc mới (ON CONFLICT)
 *   [Nhánh B'] Sự kiện trên feed nhưng gió < ngưỡng → status='dissipated', end_date=now, WARN log
 *   [Nhánh C] Bão active trong CSDL vắng khỏi feed  → status='dissipated', end_date=now, WARN log
 * </pre>
 *
 * Điều kiện tiên quyết của nhánh C: tầng 1 (fetch danh sách) phải THÀNH CÔNG — feed lỗi toàn bộ thì
 * tuyệt đối không đánh dấu dissipated hàng loạt (quyết định #9, Mục 2 Task W2-05).
 */
@Service
public class StormSyncService {

    private static final Logger log = LoggerFactory.getLogger(StormSyncService.class);
    private static final String SOURCE = "GDACS";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_DISSIPATED = "dissipated";

    private final GdacsStormClient client;
    private final StormRepository stormRepository;
    private final StormTrackRepository stormTrackRepository;
    private final DataSyncLogRepository dataSyncLogRepository;
    private final ObjectProvider<CacheManager> cacheManagerProvider;
    private final double dissipateWindThresholdMs;

    /**
     * Guard chống chạy chồng lấn giữa cron và trigger tay — độc lập với worker OWM (quyết định
     * #12).
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public StormSyncService(
            GdacsStormClient client,
            StormRepository stormRepository,
            StormTrackRepository stormTrackRepository,
            DataSyncLogRepository dataSyncLogRepository,
            ObjectProvider<CacheManager> cacheManagerProvider,
            @Value("${weathergis.sync.storm-dissipate-wind-ms:17.2}")
                    double dissipateWindThresholdMs) {
        this.client = client;
        this.stormRepository = stormRepository;
        this.stormTrackRepository = stormTrackRepository;
        this.dataSyncLogRepository = dataSyncLogRepository;
        this.cacheManagerProvider = cacheManagerProvider;
        this.dissipateWindThresholdMs = dissipateWindThresholdMs;
    }

    /** Chạy một chu kỳ đồng bộ bão. Ném SyncInProgressException nếu chu kỳ trước chưa xong. */
    public StormSyncResult triggerStormSync() {
        if (!running.compareAndSet(false, true)) {
            throw new SyncInProgressException();
        }
        try {
            StormSyncResult result = runCycle();
            if ("SUCCESS".equals(result.status())) {
                evictActiveStormsCache();
            }
            return result;
        } finally {
            running.set(false);
        }
    }

    private void evictActiveStormsCache() {
        try {
            CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
            if (cacheManager == null) {
                return;
            }
            Cache cache = cacheManager.getCache(CacheRegions.ACTIVE_STORMS);
            if (cache != null) {
                cache.clear();
            }
        } catch (RuntimeException ex) {
            log.warn("Không xóa được cache active_storms sau sync: {}", ex.getMessage());
        }
    }

    private StormSyncResult runCycle() {
        DataSyncLogEntity syncLog = startLog();

        // 1. TẦNG 1 — danh sách sự kiện (thất bại → FAILED, KHÔNG chạy nhánh C — quyết định #9)
        GdacsStormClient.GdacsEventFeed feed;
        try {
            feed = client.fetchActiveTropicalCyclones();
        } catch (GdacsClientException e) {
            return finishLog(
                    syncLog,
                    0,
                    0,
                    0,
                    0,
                    "FAILED",
                    "Không đọc được danh sách GDACS: " + e.getMessage());
        }

        // 2. TẦNG 2 + nhánh A/B — upsert từng sự kiện + nạp quỹ đạo
        int processed = 0;
        int trackPointsInserted = 0;
        List<String> skipped = new ArrayList<>(feed.skipReasons());
        for (GdacsStormClient.GdacsEvent event : feed.events()) {
            try {
                boolean belowThreshold = event.maxWindSpeedMs() < dissipateWindThresholdMs;
                StormEntity storm =
                        upsertStorm(event, belowThreshold); // nhánh A/B (và B' khi yếu đi)
                trackPointsInserted +=
                        persistTracks(
                                storm.getId(), client.fetchStormTrack(event), event.category());
                processed++;
            } catch (Exception e) {
                // Parser phòng vệ từng sự kiện: 1 bão lỗi không sập cả chu kỳ (RSK-W2-02)
                log.warn("Nạp thất bại sự kiện {}: {}", event.eventId(), e.getMessage());
                skipped.add(event.eventId() + ": " + e.getMessage());
            }
        }

        // 3. NHÁNH C — active trong CSDL nhưng vắng khỏi feed lượt này
        int dissipatedCount = dissipateMissingStorms(feed.events());

        // 4. SUCCESS ngay cả khi records=0 (0 bão toàn cầu là trạng thái bình thường — quyết định
        // #9)
        String errorMessage =
                skipped.isEmpty()
                        ? null
                        : "Bỏ qua "
                                + skipped.size()
                                + " sự kiện lỗi parse ["
                                + String.join("; ", skipped)
                                + "]";
        return finishLog(
                syncLog,
                processed,
                feed.events().size(),
                trackPointsInserted,
                dissipatedCount,
                "SUCCESS",
                errorMessage);
    }

    /** Nhánh A (insert) / B (update) / B' (gió dưới ngưỡng → dissipated). */
    private StormEntity upsertStorm(GdacsStormClient.GdacsEvent event, boolean belowThreshold) {
        StormEntity storm = stormRepository.findById(event.eventId()).orElse(null);
        if (storm == null) {
            storm = new StormEntity();
            storm.setId(event.eventId());
            storm.setName(event.name());
            storm.setYear(event.fromDate().getYear());
            storm.setStartDate(event.fromDate());
            storm.setCategory(event.category());
            storm.setMaxWindSpeed(event.maxWindSpeedMs());
            storm.setMinPressure(event.minPressureHpa()); // NULL khi nguồn không công bố (V6)
            storm.setGeomCurrent(toPoint(event.longitude(), event.latitude()));
            storm.setRawMetadata(event.rawJson());
            if (belowThreshold) {
                storm.setStatus(STATUS_DISSIPATED); // sinh ra đã dưới ngưỡng bão nhiệt đới
                storm.setEndDate(OffsetDateTime.now());
                log.warn(
                        "[SYS-RULE-001] Sự kiện mới {} ({}) gió {} m/s dưới ngưỡng {} → dissipated",
                        storm.getId(),
                        storm.getName(),
                        event.maxWindSpeedMs(),
                        dissipateWindThresholdMs);
            }
            return stormRepository.save(storm);
        }

        storm.setMaxWindSpeed(event.maxWindSpeedMs());
        storm.setMinPressure(event.minPressureHpa());
        storm.setCategory(event.category());
        storm.setGeomCurrent(toPoint(event.longitude(), event.latitude()));
        storm.setRawMetadata(event.rawJson());
        if (belowThreshold && STATUS_ACTIVE.equals(storm.getStatus())) {
            storm.setStatus(STATUS_DISSIPATED);
            storm.setEndDate(OffsetDateTime.now());
            log.warn(
                    "[SYS-RULE-001] Bão {} ({}) gió {} m/s dưới ngưỡng {} → dissipated",
                    storm.getId(),
                    storm.getName(),
                    event.maxWindSpeedMs(),
                    dissipateWindThresholdMs);
        }
        // KHÔNG set updatedAt thủ công — trigger trg_storms_updated_at (V4) + @PreUpdate đảm nhiệm
        return stormRepository.save(storm);
    }

    /** Nhánh C: mọi bão status='active' không nằm trong feed lượt này → dissipated + end_date. */
    private int dissipateMissingStorms(List<GdacsStormClient.GdacsEvent> currentFeedEvents) {
        Set<String> activeIdsOnFeed =
                currentFeedEvents.stream()
                        .map(GdacsStormClient.GdacsEvent::eventId)
                        .collect(Collectors.toSet());
        int count = 0;
        for (StormEntity storm : stormRepository.findByStatusIgnoreCase(STATUS_ACTIVE)) {
            if (!activeIdsOnFeed.contains(storm.getId())) {
                storm.setStatus(STATUS_DISSIPATED);
                storm.setEndDate(OffsetDateTime.now());
                stormRepository.save(storm);
                log.warn(
                        "[SYS-RULE-001] Bão {} ({}) vắng khỏi nguồn GDACS → dissipated",
                        storm.getId(),
                        storm.getName());
                count++;
            }
        }
        return count;
    }

    /** Chèn lũy đẳng từng mốc qua ON CONFLICT ON CONSTRAINT uq_storm_track DO NOTHING. */
    private int persistTracks(
            String stormId,
            List<GdacsStormClient.GdacsTrackPoint> points,
            String fallbackCategory) {
        int inserted = 0;
        for (GdacsStormClient.GdacsTrackPoint point : points) {
            try {
                inserted +=
                        stormTrackRepository.insertTrackPointIfAbsent(
                                stormId,
                                point.trackType(),
                                point.atTime(),
                                point.longitude(),
                                point.latitude(),
                                point.windSpeedMs(),
                                point.windGustMs(),
                                point.pressureHpa(),
                                point.category() != null ? point.category() : fallbackCategory,
                                point.impactRadiusKm());
            } catch (Exception e) {
                log.warn(
                        "Bỏ qua mốc quỹ đạo lỗi của {} tại {}: {}",
                        stormId,
                        point.atTime(),
                        e.getMessage());
            }
        }
        return inserted;
    }

    /** Dựng Point JTS chuẩn SRID 4326 — RÀNG BUỘC TỌA ĐỘ (AGENTS.md mục 1): x=KINH ĐỘ, y=VĨ ĐỘ. */
    private Point toPoint(double lng, double lat) {
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        return factory.createPoint(new Coordinate(lng, lat));
    }

    private DataSyncLogEntity startLog() {
        DataSyncLogEntity syncLog = new DataSyncLogEntity();
        syncLog.setSource(SOURCE);
        syncLog.setStatus("RUNNING");
        syncLog.setStartedAt(OffsetDateTime.now());
        return dataSyncLogRepository.save(syncLog); // commit ngay để script verify thấy RUNNING
    }

    private StormSyncResult finishLog(
            DataSyncLogEntity syncLog,
            int processed,
            int onFeed,
            int trackPointsInserted,
            int dissipated,
            String status,
            String errorMessage) {
        syncLog.setStatus(status);
        syncLog.setRecordsProcessed(processed);
        syncLog.setErrorMessage(errorMessage);
        syncLog.setCompletedAt(OffsetDateTime.now());
        dataSyncLogRepository.save(syncLog);
        long durationMs =
                Duration.between(syncLog.getStartedAt(), syncLog.getCompletedAt()).toMillis();
        log.info(
                "Chu kỳ GDACS kết thúc: status={}, storms={}/{}, trackPoints={}, dissipated={}, durationMs={}",
                status,
                processed,
                onFeed,
                trackPointsInserted,
                dissipated,
                durationMs);
        return new StormSyncResult(
                SOURCE,
                status,
                processed,
                onFeed,
                trackPointsInserted,
                dissipated,
                syncLog.getStartedAt(),
                syncLog.getCompletedAt(),
                errorMessage);
    }
}
