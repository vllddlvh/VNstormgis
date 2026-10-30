package vn.weathergis.automation.service;

import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.weathergis.automation.repository.DataSyncLogRepository;

@Service
public class DataSyncLogRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(DataSyncLogRecoveryService.class);

    private final DataSyncLogRepository repository;
    private final OffsetDateTime startupCutoff;

    public DataSyncLogRecoveryService(DataSyncLogRepository repository) {
        this.repository = repository;
        // Bean chỉ được tạo trong lúc context khởi động, trước khi scheduler có thể bắt đầu lượt
        // mới.
        this.startupCutoff = OffsetDateTime.now();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedSyncs() {
        int recovered =
                repository.failInterruptedRunningBefore(
                        startupCutoff,
                        OffsetDateTime.now(),
                        "Ứng dụng khởi động lại trước khi chu kỳ đồng bộ hoàn tất");
        if (recovered > 0) {
            log.warn("Đã reconcile {} bản ghi data_sync_logs RUNNING bị gián đoạn", recovered);
        }
    }
}
