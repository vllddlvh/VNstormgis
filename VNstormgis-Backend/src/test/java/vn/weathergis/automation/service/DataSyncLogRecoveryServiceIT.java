package vn.weathergis.automation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

@SpringBootTest
@DisplayName("DataSyncLogRecoveryService IT — chỉ reconcile RUNNING từ tiến trình trước (W3-04)")
class DataSyncLogRecoveryServiceIT extends AbstractPostGisIntegrationTest {

    @Autowired private DataSyncLogRecoveryService recoveryService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanSyncLogs() {
        jdbcTemplate.update("DELETE FROM data_sync_logs");
    }

    @Test
    @DisplayName("RUNNING cũ → FAILED; RUNNING mới và bản ghi terminal không bị ghi đè")
    void shouldRecoverOnlyInterruptedLogsFromPreviousProcess() {
        OffsetDateTime oldStartedAt = OffsetDateTime.now().minusDays(1);
        insertSyncLog("RECOVERY_STALE", "RUNNING", oldStartedAt);
        insertSyncLog("RECOVERY_TERMINAL", "SUCCESS", oldStartedAt);
        insertSyncLog("RECOVERY_FRESH", "RUNNING", OffsetDateTime.now().plusSeconds(1));

        recoveryService.recoverInterruptedSyncs();

        Map<String, Object> stale = syncLog("RECOVERY_STALE");
        assertThat(stale.get("status")).isEqualTo("FAILED");
        assertThat(stale.get("completed_at")).isNotNull();
        assertThat((String) stale.get("error_message")).contains("khởi động lại");

        Map<String, Object> fresh = syncLog("RECOVERY_FRESH");
        assertThat(fresh.get("status")).isEqualTo("RUNNING");
        assertThat(fresh.get("completed_at")).isNull();
        assertThat(fresh.get("error_message")).isNull();

        Map<String, Object> terminal = syncLog("RECOVERY_TERMINAL");
        assertThat(terminal.get("status")).isEqualTo("SUCCESS");
        assertThat(terminal.get("error_message")).isNull();
    }

    private void insertSyncLog(String source, String status, OffsetDateTime startedAt) {
        jdbcTemplate.update(
                "INSERT INTO data_sync_logs "
                        + "(source, status, records_processed, error_message, started_at, completed_at) "
                        + "VALUES (?, ?, 0, NULL, ?, ?)",
                source,
                status,
                startedAt,
                "SUCCESS".equals(status) ? startedAt.plusMinutes(1) : null);
    }

    private Map<String, Object> syncLog(String source) {
        return jdbcTemplate.queryForMap(
                "SELECT status, completed_at, error_message FROM data_sync_logs WHERE source = ?",
                source);
    }
}
