package vn.weathergis.automation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.weathergis.common.exception.SyncInProgressException;

/**
 * Worker tự động nạp thời tiết 68 trạm mỗi 15 phút. Vô hiệu hóa khi weathergis.sync.enabled=false
 * (môi trường test/Testcontainers) để tránh cron bắn cước OWM trong suite kiểm thử.
 */
@Component
@ConditionalOnProperty(
        prefix = "weathergis.sync",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WeatherSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeatherSyncScheduler.class);

    private final WeatherSyncService weatherSyncService;

    public WeatherSyncScheduler(WeatherSyncService weatherSyncService) {
        this.weatherSyncService = weatherSyncService;
    }

    /** Chu kỳ 15 phút = 900.000ms (weathergis.sync.weather-rate-ms). */
    @Scheduled(
            fixedRateString = "${weathergis.sync.weather-rate-ms:900000}",
            initialDelayString = "${weathergis.sync.weather-initial-delay-ms:20000}")
    public void syncWeather() {
        log.info("[CRON] Khởi động chu kỳ đồng bộ thời tiết OWM");
        try {
            weatherSyncService.triggerWeatherSync();
        } catch (SyncInProgressException e) {
            log.info("[CRON] Bỏ qua lượt này — chu kỳ trước vẫn đang chạy");
        } catch (Exception e) {
            log.error("[CRON] Chu kỳ đồng bộ OWM thất bại: {}", e.getMessage(), e);
        }
    }
}
