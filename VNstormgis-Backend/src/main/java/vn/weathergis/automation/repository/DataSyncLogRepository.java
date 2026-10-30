package vn.weathergis.automation.repository;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.weathergis.automation.domain.DataSyncLogEntity;

/** JpaRepository cho bảng data_sync_logs — chỉ Ingestion ghi, W2-10 script đọc bằng SQL. */
@Repository
public interface DataSyncLogRepository extends JpaRepository<DataSyncLogEntity, Long> {

    /** Lượt nạp gần nhất theo nguồn — phục vụ debug và huy hiệu Last Sync (W2-02). */
    List<DataSyncLogEntity> findTop3BySourceOrderByStartedAtDesc(String source);

    /**
     * Một câu UPDATE nguyên tử: không ghi đè lượt sync vừa khởi động sau cutoff và không đua với
     * trạng thái terminal mà worker có thể ghi trong lúc ứng dụng khởi động.
     */
    @Modifying
    @Query(
            """
            UPDATE DataSyncLogEntity syncLog
               SET syncLog.status = 'FAILED',
                   syncLog.completedAt = :completedAt,
                   syncLog.errorMessage = :errorMessage
             WHERE syncLog.status = 'RUNNING'
               AND syncLog.startedAt < :startupCutoff
            """)
    int failInterruptedRunningBefore(
            @Param("startupCutoff") OffsetDateTime startupCutoff,
            @Param("completedAt") OffsetDateTime completedAt,
            @Param("errorMessage") String errorMessage);
}
