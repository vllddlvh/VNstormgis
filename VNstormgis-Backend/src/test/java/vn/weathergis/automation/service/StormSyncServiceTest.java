package vn.weathergis.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.weathergis.automation.client.GdacsStormClient;
import vn.weathergis.automation.client.exception.GdacsClientException;
import vn.weathergis.automation.domain.DataSyncLogEntity;
import vn.weathergis.automation.dto.StormSyncResult;
import vn.weathergis.automation.repository.DataSyncLogRepository;
import vn.weathergis.common.exception.SyncInProgressException;
import vn.weathergis.storm.domain.StormEntity;
import vn.weathergis.storm.repository.StormRepository;
import vn.weathergis.storm.repository.StormTrackRepository;

/**
 * Unit test máy trạng thái SYS-RULE-001 của StormSyncService (Task W2-05) — không mạng (client
 * mock), không CSDL (repository mock). Chứng minh: nhánh A insert active; nhánh B update; nhánh B'
 * gió dưới ngưỡng → dissipated; nhánh C vắng khỏi feed → dissipated; feed lỗi → FAILED và KHÔNG
 * dissipated ai cả (quyết định #9); sự kiện thiếu áp suất vẫn nạp với NULL (V6); guard 409.
 */
@DisplayName("StormSyncService — SYS-RULE-001 State Machine Tests (W2-05)")
@ExtendWith(MockitoExtension.class)
class StormSyncServiceTest {

    private static final double THRESHOLD_MS = 17.2;

    @Mock private GdacsStormClient client;
    @Mock private StormRepository stormRepository;
    @Mock private StormTrackRepository stormTrackRepository;
    @Mock private DataSyncLogRepository dataSyncLogRepository;

    private StormSyncService newService(double thresholdMs) {
        return new StormSyncService(
                client, stormRepository, stormTrackRepository, dataSyncLogRepository, thresholdMs);
    }

    private GdacsStormClient.GdacsEvent event(String id, String name, double windMs) {
        return new GdacsStormClient.GdacsEvent(
                id,
                "1",
                name,
                OffsetDateTime.parse("2026-10-14T12:00:00Z"),
                OffsetDateTime.parse("2026-10-20T12:00:00Z"),
                "Red",
                windMs,
                null, // áp suất: nguồn GDACS không công bố (V6)
                125.4, // lng
                20.9, // lat
                "{\"eventid\":" + id + "}");
    }

    private GdacsStormClient.GdacsTrackPoint point(String type) {
        return new GdacsStormClient.GdacsTrackPoint(
                type,
                OffsetDateTime.parse("2026-10-15T00:00:00Z"),
                125.0,
                20.5,
                null,
                null,
                null,
                "TS",
                100.0);
    }

    private StormEntity existingStorm(String id, String status) {
        StormEntity storm = new StormEntity();
        storm.setId(id);
        storm.setName("Bão " + id);
        storm.setYear(2026);
        storm.setCategory("Green");
        storm.setMaxWindSpeed(20.0);
        storm.setMinPressure(980.0);
        storm.setStatus(status);
        storm.setStartDate(OffsetDateTime.parse("2026-10-10T00:00:00Z"));
        return storm;
    }

    private void stubAuditSaves() {
        when(dataSyncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubStormSave() {
        when(stormRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubTrackInsert(int rows) {
        when(stormTrackRepository.insertTrackPointIfAbsent(
                        anyString(),
                        anyString(),
                        any(),
                        anyDouble(),
                        anyDouble(),
                        any(),
                        any(),
                        any(),
                        anyString(),
                        anyDouble()))
                .thenReturn(rows);
    }

    @Test
    @DisplayName("TC-A: nhánh A — sự kiện mới gió ≥ ngưỡng → INSERT status='active' + mốc quỹ đạo")
    void shouldInsertNewActiveStorm() {
        when(client.fetchActiveTropicalCyclones())
                .thenReturn(
                        new GdacsStormClient.GdacsEventFeed(
                                List.of(event("1001338", "CHAMPI-26", 30.0)), List.of()));
        when(client.fetchStormTrack(any()))
                .thenReturn(List.of(point("historical"), point("forecast")));
        when(stormRepository.findById("1001338")).thenReturn(Optional.empty());
        stubAuditSaves();
        stubStormSave();
        stubTrackInsert(1);

        StormSyncResult result = newService(THRESHOLD_MS).triggerStormSync();

        ArgumentCaptor<StormEntity> saved = ArgumentCaptor.forClass(StormEntity.class);
        verify(stormRepository).save(saved.capture());
        StormEntity storm = saved.getValue();
        assertThat(storm.getId()).isEqualTo("1001338");
        assertThat(storm.getStatus()).isEqualTo("active");
        assertThat(storm.getMaxWindSpeed()).isEqualTo(30.0);
        assertThat(storm.getMinPressure()).isNull(); // V6 — không bịa áp suất
        assertThat(storm.getGeomCurrent()).isNotNull();
        assertThat(storm.getGeomCurrent().getX()).isEqualTo(125.4); // x = KINH ĐỘ (AGENTS.md mục 1)
        assertThat(storm.getGeomCurrent().getY()).isEqualTo(20.9);
        assertThat(storm.getGeomCurrent().getSRID()).isEqualTo(4326);
        assertThat(storm.getYear()).isEqualTo(2026);
        assertThat(storm.getRawMetadata()).contains("1001338");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.stormsProcessed()).isEqualTo(1);
        assertThat(result.stormsOnFeed()).isEqualTo(1);
        assertThat(result.trackPointsInserted()).isEqualTo(2);
        assertThat(result.stormsDissipated()).isEqualTo(0);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("TC-B: nhánh B — bão có sẵn → UPDATE geom/gió/cấp, không sinh bản ghi mới")
    void shouldUpdateExistingStorm() {
        StormEntity existing = existingStorm("1001338", "active");
        when(client.fetchActiveTropicalCyclones())
                .thenReturn(
                        new GdacsStormClient.GdacsEventFeed(
                                List.of(event("1001338", "CHAMPI-26", 30.0)), List.of()));
        when(client.fetchStormTrack(any())).thenReturn(List.of());
        when(stormRepository.findById("1001338")).thenReturn(Optional.of(existing));
        stubAuditSaves();
        stubStormSave();

        StormSyncResult result = newService(THRESHOLD_MS).triggerStormSync();

        verify(stormRepository).save(existing);
        assertThat(existing.getMaxWindSpeed()).isEqualTo(30.0);
        assertThat(existing.getCategory()).isEqualTo("Red");
        assertThat(existing.getStatus()).isEqualTo("active"); // giữ nguyên trạng thái
        assertThat(existing.getEndDate()).isNull();
        assertThat(result.stormsProcessed()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("TC-B': nhánh B' — bão có sẵn, gió < ngưỡng → dissipated + end_date")
    void shouldDissipateWeakenedStorm() {
        StormEntity existing = existingStorm("1001338", "active");
        when(client.fetchActiveTropicalCyclones())
                .thenReturn(
                        new GdacsStormClient.GdacsEventFeed(
                                List.of(event("1001338", "CHAMPI-26", 10.0)), List.of()));
        when(client.fetchStormTrack(any())).thenReturn(List.of());
        when(stormRepository.findById("1001338")).thenReturn(Optional.of(existing));
        stubAuditSaves();
        stubStormSave();

        StormSyncResult result = newService(THRESHOLD_MS).triggerStormSync();

        assertThat(existing.getStatus()).isEqualTo("dissipated");
        assertThat(existing.getEndDate()).isNotNull();
        assertThat(result.stormsDissipated()).isEqualTo(0); // nhánh B' không tính vào nhánh C
        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("TC-A': sự kiện mới sinh ra đã dưới ngưỡng → INSERT luôn dissipated")
    void shouldInsertWeakenedNewEventAsDissipated() {
        when(client.fetchActiveTropicalCyclones())
                .thenReturn(
                        new GdacsStormClient.GdacsEventFeed(
                                List.of(event("1001338", "CHAMPI-26", 10.0)), List.of()));
        when(client.fetchStormTrack(any())).thenReturn(List.of());
        when(stormRepository.findById("1001338")).thenReturn(Optional.empty());
        stubAuditSaves();
        stubStormSave();

        newService(THRESHOLD_MS).triggerStormSync();

        ArgumentCaptor<StormEntity> saved = ArgumentCaptor.forClass(StormEntity.class);
        verify(stormRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("dissipated");
        assertThat(saved.getValue().getEndDate()).isNotNull();
    }

    @Test
    @DisplayName("TC-C: nhánh C — bão active trong CSDL vắng khỏi feed → dissipated + end_date")
    void shouldDissipateStormsMissingFromFeed() {
        StormEntity active = existingStorm("TCFAKE999", "active");
        when(client.fetchActiveTropicalCyclones())
                .thenReturn(
                        new GdacsStormClient.GdacsEventFeed(
                                List.of(event("1001338", "CHAMPI-26", 30.0)), List.of()));
        when(client.fetchStormTrack(any())).thenReturn(List.of());
        when(stormRepository.findById("1001338")).thenReturn(Optional.empty());
        when(stormRepository.findByStatusIgnoreCase("active")).thenReturn(List.of(active));
        stubAuditSaves();
        stubStormSave();

        StormSyncResult result = newService(THRESHOLD_MS).triggerStormSync();

        assertThat(active.getStatus()).isEqualTo("dissipated");
        assertThat(active.getEndDate()).isNotNull();
        assertThat(result.stormsDissipated()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("TC-D: 0 sự kiện toàn cầu — SUCCESS hợp lệ, không phải FAILED (quyết định #9)")
    void shouldSucceedOnEmptyFeed() {
        when(client.fetchActiveTropicalCyclones())
                .thenReturn(new GdacsStormClient.GdacsEventFeed(List.of(), List.of()));
        when(stormRepository.findByStatusIgnoreCase("active")).thenReturn(List.of());
        stubAuditSaves();

        StormSyncResult result = newService(THRESHOLD_MS).triggerStormSync();

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.stormsProcessed()).isZero();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName(
            "TC-E: feed lỗi toàn bộ → FAILED và TUYỆT ĐỐI không dissipated ai cả (quy tắc quản trị #1)")
    void shouldFailOnFeedErrorWithoutDissipatingAnyone() {
        when(client.fetchActiveTropicalCyclones())
                .thenThrow(new GdacsClientException("Lỗi mạng/timeout khi gọi GDACS search", true));
        stubAuditSaves();

        StormSyncResult result = newService(THRESHOLD_MS).triggerStormSync();

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorMessage()).contains("Không đọc được danh sách GDACS");
        verify(stormRepository, never()).findByStatusIgnoreCase(anyString()); // nhánh C bị chặn
        verify(stormRepository, never()).save(any());
    }

    @Test
    @DisplayName(
            "TC-F: 1 sự kiện lỗi track không sập chu kỳ — các sự kiện khác vẫn nạp (RSK-W2-02)")
    void shouldContinueWhenOneEventFails() {
        when(client.fetchActiveTropicalCyclones())
                .thenReturn(
                        new GdacsStormClient.GdacsEventFeed(
                                List.of(
                                        event("1001338", "CHAMPI-26", 30.0),
                                        event("1001335", "SIMON-26", 25.0)),
                                List.of("event 999 thiếu name")));
        when(client.fetchStormTrack(any()))
                .thenReturn(List.of(point("historical")))
                .thenThrow(new GdacsClientException("GDACS trả lỗi máy chủ 500", true));
        when(stormRepository.findById(anyString())).thenReturn(Optional.empty());
        stubAuditSaves();
        stubStormSave();
        stubTrackInsert(1);

        StormSyncResult result = newService(THRESHOLD_MS).triggerStormSync();

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.stormsProcessed()).isEqualTo(1);
        assertThat(result.errorMessage()).contains("Bỏ qua 2 sự kiện lỗi parse");
    }

    @Test
    @DisplayName("TC-G: audit log 2 pha — save RUNNING trước, SUCCESS sau, đúng source GDACS")
    void shouldWriteTwoPhaseAuditLog() {
        when(client.fetchActiveTropicalCyclones())
                .thenReturn(
                        new GdacsStormClient.GdacsEventFeed(
                                List.of(event("1001338", "CHAMPI-26", 30.0)), List.of()));
        when(client.fetchStormTrack(any())).thenReturn(List.of());
        when(stormRepository.findById("1001338")).thenReturn(Optional.empty());
        stubStormSave();
        List<String> savedStatuses = new ArrayList<>();
        List<String> savedSources = new ArrayList<>();
        List<OffsetDateTime> savedCompletedAts = new ArrayList<>();
        when(dataSyncLogRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            DataSyncLogEntity entity = inv.getArgument(0);
                            savedStatuses.add(entity.getStatus());
                            savedSources.add(entity.getSource());
                            savedCompletedAts.add(entity.getCompletedAt());
                            return entity;
                        });

        newService(THRESHOLD_MS).triggerStormSync();

        assertThat(savedStatuses).containsExactly("RUNNING", "SUCCESS");
        assertThat(savedSources).containsExactly("GDACS", "GDACS");
        assertThat(savedCompletedAts.get(0)).isNull();
        assertThat(savedCompletedAts.get(1)).isNotNull();
    }

    @Test
    @DisplayName("TC-H: guard chồng lấn — trigger thứ 2 khi chu kỳ đang chạy nhận 409")
    void shouldRejectConcurrentTrigger() throws Exception {
        StormSyncService service = newService(THRESHOLD_MS);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.fetchActiveTropicalCyclones())
                .thenAnswer(
                        inv -> {
                            entered.countDown();
                            if (!release.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Test timeout khi giữ chu kỳ 1");
                            }
                            return new GdacsStormClient.GdacsEventFeed(List.of(), List.of());
                        });
        when(stormRepository.findByStatusIgnoreCase("active")).thenReturn(List.of());
        stubAuditSaves();

        Thread first = new Thread(service::triggerStormSync);
        first.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(service::triggerStormSync).isInstanceOf(SyncInProgressException.class);

        release.countDown();
        first.join(5000);
        assertThat(first.isAlive()).isFalse();
    }
}
