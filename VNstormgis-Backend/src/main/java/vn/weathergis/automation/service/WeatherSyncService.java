package vn.weathergis.automation.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import vn.weathergis.automation.client.OpenWeatherMapClient;
import vn.weathergis.automation.client.dto.OpenWeatherMapCurrentWeather;
import vn.weathergis.automation.client.exception.OpenWeatherMapRateLimitException;
import vn.weathergis.automation.domain.DataSyncLogEntity;
import vn.weathergis.automation.dto.WeatherSyncResult;
import vn.weathergis.automation.repository.DataSyncLogRepository;
import vn.weathergis.automation.repository.WeatherRecordRepository;
import vn.weathergis.common.cache.CacheRegions;
import vn.weathergis.common.exception.SyncInProgressException;
import vn.weathergis.location.domain.LocationEntity;
import vn.weathergis.location.repository.LocationRepository;
import vn.weathergis.weather.domain.WeatherRecordEntity;

/**
 * Điều phối một chu kỳ đồng bộ thời tiết OWM: đọc trạm active → gọi client từng trạm (throttle
 * 100ms) → ghi weather_records → ghi audit log data_sync_logs theo vòng đời RUNNING→SUCCESS/FAILED.
 */
@Service
public class WeatherSyncService {

    private static final Logger log = LoggerFactory.getLogger(WeatherSyncService.class);
    private static final String SOURCE = "OPENWEATHERMAP"; // đúng chú thích V5

    private final OpenWeatherMapClient client;
    private final LocationRepository locationRepository;
    private final WeatherRecordRepository weatherRecordRepository;
    private final DataSyncLogRepository dataSyncLogRepository;
    private final ObjectProvider<CacheManager> cacheManagerProvider;
    private final long throttleMs;
    private final String apiKey;

    /** Guard chống chạy chồng lấn giữa cron và trigger tay (quyết định #11, Mục 2). */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public WeatherSyncService(
            OpenWeatherMapClient client,
            LocationRepository locationRepository,
            WeatherRecordRepository weatherRecordRepository,
            DataSyncLogRepository dataSyncLogRepository,
            ObjectProvider<CacheManager> cacheManagerProvider,
            @Value("${weathergis.sync.throttle-ms:100}") long throttleMs,
            @Value("${weathergis.external.owm.api-key:}") String apiKey) {
        this.client = client;
        this.locationRepository = locationRepository;
        this.weatherRecordRepository = weatherRecordRepository;
        this.dataSyncLogRepository = dataSyncLogRepository;
        this.cacheManagerProvider = cacheManagerProvider;
        this.throttleMs = throttleMs;
        this.apiKey = apiKey;
    }

    /** Chạy một chu kỳ đồng bộ. Ném SyncInProgressException nếu chu kỳ trước chưa xong. */
    public WeatherSyncResult triggerWeatherSync() {
        if (!running.compareAndSet(false, true)) {
            throw new SyncInProgressException();
        }
        try {
            WeatherSyncResult result = runCycle();
            if ("SUCCESS".equals(result.status())) {
                evictWeatherCurrentCache();
            }
            return result;
        } finally {
            running.set(false);
        }
    }

    private void evictWeatherCurrentCache() {
        try {
            CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
            if (cacheManager == null) {
                return;
            }
            Cache cache = cacheManager.getCache(CacheRegions.WEATHER_CURRENT);
            if (cache != null) {
                cache.clear();
            }
        } catch (RuntimeException ex) {
            log.warn("Không xóa được cache weather_current sau sync: {}", ex.getMessage());
        }
    }

    private WeatherSyncResult runCycle() {
        DataSyncLogEntity syncLog = startLog();
        if (apiKey == null || apiKey.isBlank()) {
            return finishLog(
                    syncLog,
                    0,
                    0,
                    "FAILED",
                    "OPENWEATHERMAP_API_KEY chưa được cấu hình trong môi trường — bỏ qua chu kỳ");
        }

        List<LocationEntity> stations = locationRepository.findByIsActiveTrue();
        List<WeatherRecordEntity> batch = new ArrayList<>(stations.size());
        List<String> failures = new ArrayList<>();
        boolean rateLimited = false;

        for (int i = 0; i < stations.size(); i++) {
            LocationEntity station = stations.get(i);
            try {
                // RÀNG BUỘC TỌA ĐỘ (AGENTS.md mục 1): JTS Point.getX() = KINH ĐỘ, getY() = VĨ ĐỘ
                OpenWeatherMapClient.OpenWeatherMapResponse response =
                        client.fetchCurrentWeather(
                                station.getGeom().getY(), station.getGeom().getX());
                batch.add(toEntity(station, response.data(), response.rawJson()));
            } catch (OpenWeatherMapRateLimitException e) {
                log.warn(
                        "Chu kỳ OWM bị rate limit tại trạm {} ({} / {}) — dừng an toàn phần còn lại",
                        station.getCode(),
                        i + 1,
                        stations.size());
                rateLimited = true;
                break;
            } catch (Exception e) {
                log.warn(
                        "Nạp thất bại trạm {} ({}): {}",
                        station.getCode(),
                        station.getName(),
                        e.getMessage());
                failures.add(station.getCode() + ": " + e.getMessage());
            }
            throttle(i, stations.size());
        }

        if (!batch.isEmpty()) {
            weatherRecordRepository.saveAll(batch); // tận dụng jdbc.batch_size: 50
        }

        String status;
        String errorMessage = null;
        if (rateLimited) {
            status = "FAILED";
            errorMessage =
                    "Rate limit OWM 429 — dừng an toàn sau "
                            + batch.size()
                            + "/"
                            + stations.size()
                            + " trạm (giữ nguyên dữ liệu chu kỳ trước)";
        } else if (!failures.isEmpty()) {
            status = "FAILED";
            errorMessage =
                    "Nạp một phần: "
                            + failures.size()
                            + " trạm lỗi ["
                            + String.join("; ", failures)
                            + "]";
        } else if (stations.isEmpty()) {
            status = "FAILED";
            errorMessage = "Không có trạm active nào để đồng bộ";
        } else {
            // SUCCESS chỉ khi TOÀN BỘ trạm thành công (quyết định #3/#4, Mục 2 — giữ SUCCESS tinh
            // sạch)
            status = "SUCCESS";
        }
        return finishLog(syncLog, batch.size(), stations.size(), status, errorMessage);
    }

    private WeatherRecordEntity toEntity(
            LocationEntity station, OpenWeatherMapCurrentWeather dto, String rawJson) {
        WeatherRecordEntity record = new WeatherRecordEntity();
        record.setLocation(station);
        record.setRecordedAt(
                OffsetDateTime.ofInstant(
                        Instant.ofEpochSecond(dto.observationEpochSeconds()), ZoneOffset.UTC));
        record.setTemperature(dto.main().temp());
        record.setFeelsLike(dto.main().feelsLike());
        record.setHumidity(dto.main().humidity());
        record.setPressure(dto.main().pressure());
        record.setWindSpeed(dto.wind().speed());
        record.setWindDirection(dto.wind().deg());
        record.setWindGust(dto.wind().gust() == null ? 0.0 : dto.wind().gust());
        record.setRain1h(
                dto.rain() == null || dto.rain().oneHour() == null
                        ? 0.0
                        : dto.rain().oneHour()); // mặc định 0 khi OWM bỏ trường "rain"
        record.setRain3h(
                dto.rain() == null || dto.rain().threeHour() == null
                        ? 0.0
                        : dto.rain().threeHour());
        record.setWeatherCondition(dto.weather().get(0).main()); // "Rain", "Clear"...
        record.setWeatherDescription(dto.weather().get(0).description()); // "moderate rain"...
        record.setRawData(rawJson); // audit — COMMENT V4
        return record;
    }

    private DataSyncLogEntity startLog() {
        DataSyncLogEntity syncLog = new DataSyncLogEntity();
        syncLog.setSource(SOURCE);
        syncLog.setStatus("RUNNING");
        syncLog.setStartedAt(OffsetDateTime.now());
        return dataSyncLogRepository.save(syncLog); // commit ngay để script verify thấy RUNNING
    }

    private WeatherSyncResult finishLog(
            DataSyncLogEntity syncLog,
            int processed,
            int total,
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
                "Chu kỳ OWM kết thúc: status={}, records={}/{}, durationMs={}",
                status,
                processed,
                total,
                durationMs);
        return new WeatherSyncResult(
                SOURCE,
                status,
                processed,
                total,
                syncLog.getStartedAt(),
                syncLog.getCompletedAt(),
                errorMessage);
    }

    private void throttle(int currentIndex, int total) {
        if (currentIndex < total - 1 && throttleMs > 0) {
            try {
                Thread.sleep(throttleMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Chu kỳ đồng bộ bị gián đoạn", e);
            }
        }
    }
}
