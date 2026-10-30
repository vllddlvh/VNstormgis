package vn.weathergis.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.weathergis.automation.repository.DataSyncLogRepository;

@DisplayName("DataSyncLogRecoveryService — reconcile RUNNING bị gián đoạn (W3-04)")
@ExtendWith(MockitoExtension.class)
class DataSyncLogRecoveryServiceTest {

    @Mock private DataSyncLogRepository repository;

    @Test
    @DisplayName("Update nguyên tử dùng cutoff khởi động, completed_at và lý do gián đoạn")
    void shouldFailInterruptedRunningLogs() {
        when(repository.failInterruptedRunningBefore(any(), any(), any())).thenReturn(1);
        DataSyncLogRecoveryService service = new DataSyncLogRecoveryService(repository);

        service.recoverInterruptedSyncs();

        ArgumentCaptor<OffsetDateTime> startupCutoff =
                ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> completedAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<String> errorMessage = ArgumentCaptor.forClass(String.class);
        verify(repository)
                .failInterruptedRunningBefore(
                        startupCutoff.capture(), completedAt.capture(), errorMessage.capture());
        assertThat(startupCutoff.getValue()).isBefore(completedAt.getValue());
        assertThat(errorMessage.getValue()).contains("khởi động lại");
    }

    @Test
    @DisplayName("Không có RUNNING tồn đọng → update nguyên tử trả 0")
    void shouldDoNothingWhenNoInterruptedLogsExist() {
        when(repository.failInterruptedRunningBefore(any(), any(), any())).thenReturn(0);
        DataSyncLogRecoveryService service = new DataSyncLogRecoveryService(repository);

        service.recoverInterruptedSyncs();

        verify(repository).failInterruptedRunningBefore(any(), any(), any());
    }
}
