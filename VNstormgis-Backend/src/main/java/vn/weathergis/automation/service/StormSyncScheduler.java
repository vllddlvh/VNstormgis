package vn.weathergis.automation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.weathergis.common.exception.SyncInProgressException;

/**
 * Worker tự động nạp bão nhiệt đới GDACS mỗi 30 phút (đúng README Mục 3.3). Initial delay 60s lệch
 * pha với weather worker (20s) để 2 chu kỳ không bắn đồng thời lúc khởi động. Vô hiệu hóa khi
 * weathergis.sync.enabled=false (môi trường test/Testcontainers) — cùng công tắc với OWM.
 */
@Component
@ConditionalOnProperty(
        prefix = "weathergis.sync",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StormSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(StormSyncScheduler.class);

    private final StormSyncService stormSyncService;

    public StormSyncScheduler(StormSyncService stormSyncService) {
        this.stormSyncService = stormSyncService;
    }

    /** Chu kỳ 30 phút = 1.800.000ms (weathergis.sync.storm-rate-ms). */
    @Scheduled(
            fixedRateString = "${weathergis.sync.storm-rate-ms:1800000}",
            initialDelayString = "${weathergis.sync.storm-initial-delay-ms:60000}")
    public void syncStorms() {
        log.info("[CRON] Khởi động chu kỳ đồng bộ bão GDACS");
        try {
            stormSyncService.triggerStormSync();
        } catch (SyncInProgressException e) {
            log.info("[CRON] Bỏ qua lượt này — chu kỳ bão trước vẫn đang chạy");
        } catch (Exception e) {
            log.error("[CRON] Chu kỳ đồng bộ GDACS thất bại: {}", e.getMessage(), e);
        }
    }
}
