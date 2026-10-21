package vn.weathergis.automation.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.weathergis.automation.domain.DataSyncLogEntity;

/** JpaRepository cho bảng data_sync_logs — chỉ Ingestion ghi, W2-10 script đọc bằng SQL. */
@Repository
public interface DataSyncLogRepository extends JpaRepository<DataSyncLogEntity, Long> {

    /** Lượt nạp gần nhất theo nguồn — phục vụ debug và huy hiệu Last Sync (W2-02). */
    List<DataSyncLogEntity> findTop3BySourceOrderByStartedAtDesc(String source);
}
