package vn.weathergis.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.weathergis.automation.client.OpenWeatherMapClient;
import vn.weathergis.automation.client.dto.OpenWeatherMapCurrentWeather;
import vn.weathergis.automation.client.exception.OpenWeatherMapClientException;
import vn.weathergis.automation.client.exception.OpenWeatherMapRateLimitException;
import vn.weathergis.automation.domain.DataSyncLogEntity;
import vn.weathergis.automation.dto.WeatherSyncResult;
import vn.weathergis.automation.repository.DataSyncLogRepository;
import vn.weathergis.automation.repository.WeatherRecordRepository;
import vn.weathergis.common.exception.SyncInProgressException;
import vn.weathergis.location.domain.LocationEntity;
import vn.weathergis.location.repository.LocationRepository;

/**
 * Unit test orchestration WeatherSyncService (Task W2-04) — không mạng (client mock), không CSDL
 * (repository mock), throttle = 0ms. Chứng minh: safe-stop 429 giữ batch, audit log
 * RUNNING→SUCCESS/ FAILED với error_message chuẩn, thiếu key không gọi client, guard 409 chống
 * chồng lấn.
 */
@DisplayName("WeatherSyncService — Safe-Stop & Audit Log Tests (W2-04)")
@ExtendWith(MockitoExtension.class)
class WeatherSyncServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Mock private OpenWeatherMapClient client;
    @Mock private LocationRepository locationRepository;
    @Mock private WeatherRecordRepository weatherRecordRepository;
    @Mock private DataSyncLogRepository dataSyncLogRepository;

    /** Trạm giả lập — JTS Point: X = KINH ĐỘ, Y = VĨ ĐỘ (AGENTS.md mục 1). */
    private LocationEntity station(String code) {
        LocationEntity entity = new LocationEntity();
        entity.setCode(code);
        entity.setName("Trạm " + code);
        entity.setGeom(GEOMETRY_FACTORY.createPoint(new Coordinate(105.8542, 21.0285)));
        return entity;
    }

    private WeatherSyncService newService(String apiKey) {
        return new WeatherSyncService(
                client,
                locationRepository,
                weatherRecordRepository,
                dataSyncLogRepository,
                0L /* throttle-ms = 0 — không ngủ */,
                apiKey);
    }

    private OpenWeatherMapClient.OpenWeatherMapResponse okResponse() {
        String json =
                """
                {"coord": {"lon": 105.8542, "lat": 21.0285},
                 "weather": [{"main": "Rain", "description": "moderate rain"}],
                 "main": {"temp": 28.5, "feels_like": 32.0, "pressure": 1008, "humidity": 82},
                 "wind": {"speed": 4.5, "deg": 45}, "rain": {"1h": 8.5}, "dt": 1750000000}
                """;
        try {
            return new OpenWeatherMapClient.OpenWeatherMapResponse(
                    new ObjectMapper().readValue(json, OpenWeatherMapCurrentWeather.class), json);
        } catch (Exception e) {
            throw new IllegalStateException("Payload test không parse được", e);
        }
    }

    @Test
    @DisplayName(
            "TC-12: 429 giữa chu kỳ — safe-stop: giữ batch đã nạp, audit FAILED, ĐÚNG số lượt gọi")
    void shouldSafeStopOnRateLimit() {
        when(locationRepository.findByIsActiveTrue())
                .thenReturn(List.of(station("HAN"), station("DAD"), station("SGN")));
        when(dataSyncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(client.fetchCurrentWeather(anyDouble(), anyDouble()))
                .thenReturn(okResponse())
                .thenThrow(new OpenWeatherMapRateLimitException("OWM trả 429"));

        WeatherSyncResult result = newService("test-key").triggerWeatherSync();

        // Safe-stop RSK-W2-01: trạm HAN đã nạp THÀNH CÔNG vẫn được giữ (batch không bỏ)
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.recordsProcessed()).isEqualTo(1);
        assertThat(result.totalStations()).isEqualTo(3);
        assertThat(result.errorMessage()).contains("Rate limit").contains("1/3");
        // DỪNG GỌI: SGN (trạm thứ 3) không bao giờ bị gọi
        verify(client, times(2)).fetchCurrentWeather(anyDouble(), anyDouble());
        verify(weatherRecordRepository).saveAll(any());
    }

    @Test
    @DisplayName("TC-12b: Audit log 2 pha — save RUNNING trước, FAILED sau, error_message chuẩn")
    void shouldWriteTwoPhaseAuditLogOnRateLimit() {
        when(locationRepository.findByIsActiveTrue()).thenReturn(List.of(station("HAN")));
        when(client.fetchCurrentWeather(anyDouble(), anyDouble()))
                .thenThrow(new OpenWeatherMapRateLimitException("OWM trả 429"));

        // Chụp trạng thái TẠI THỜI ĐIỂM mỗi lần save — entity bị mutate giữa 2 lần save nên
        // ArgumentCaptor (giữ tham chiếu) không phân biệt được 2 pha
        List<String> savedStatuses = new ArrayList<>();
        List<String> savedSources = new ArrayList<>();
        List<String> savedErrorMessages = new ArrayList<>();
        List<OffsetDateTime> savedCompletedAts = new ArrayList<>();
        when(dataSyncLogRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            DataSyncLogEntity entity = inv.getArgument(0);
                            savedStatuses.add(entity.getStatus());
                            savedSources.add(entity.getSource());
                            savedErrorMessages.add(entity.getErrorMessage());
                            savedCompletedAts.add(entity.getCompletedAt());
                            return entity;
                        });

        newService("test-key").triggerWeatherSync();

        assertThat(savedStatuses).containsExactly("RUNNING", "FAILED");
        assertThat(savedSources).containsExactly("OPENWEATHERMAP", "OPENWEATHERMAP");
        assertThat(savedErrorMessages.get(0)).isNull();
        assertThat(savedErrorMessages.get(1)).contains("Rate limit");
        assertThat(savedCompletedAts.get(0)).isNull();
        assertThat(savedCompletedAts.get(1)).isNotNull();
    }

    @Test
    @DisplayName("TC-13: 1 trạm lỗi thường — FAILED 'Nạp một phần', các trạm khác vẫn lưu")
    void shouldContinueOnStationFailure() {
        when(locationRepository.findByIsActiveTrue())
                .thenReturn(List.of(station("HAN"), station("DAD")));
        when(dataSyncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(client.fetchCurrentWeather(anyDouble(), anyDouble()))
                .thenReturn(okResponse())
                .thenThrow(new OpenWeatherMapClientException("Lỗi mạng", true));

        WeatherSyncResult result = newService("test-key").triggerWeatherSync();

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorMessage()).contains("Nạp một phần").contains("1 trạm lỗi");
        assertThat(result.recordsProcessed()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-14: Toàn bộ thành công — SUCCESS, error_message null, records = total")
    void shouldSucceedWhenAllStationsOk() {
        when(locationRepository.findByIsActiveTrue())
                .thenReturn(List.of(station("HAN"), station("DAD")));
        when(dataSyncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(client.fetchCurrentWeather(anyDouble(), anyDouble())).thenReturn(okResponse());

        WeatherSyncResult result = newService("test-key").triggerWeatherSync();

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.recordsProcessed()).isEqualTo(2);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("TC-15: Thiếu API key — FAILED trong sáng, KHÔNG gọi client lần nào")
    void shouldFailFastWhenApiKeyBlank() {
        when(dataSyncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeatherSyncResult result = newService("  ").triggerWeatherSync();

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorMessage()).contains("chưa được cấu hình");
        verifyNoInteractions(client, locationRepository, weatherRecordRepository);
    }

    @Test
    @DisplayName("TC-16: Không trạm active — FAILED 'Không có trạm active nào để đồng bộ'")
    void shouldFailWhenNoActiveStations() {
        when(locationRepository.findByIsActiveTrue()).thenReturn(List.of());
        when(dataSyncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeatherSyncResult result = newService("test-key").triggerWeatherSync();

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorMessage()).contains("Không có trạm active");
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("TC-17: Guard chồng lấn — trigger thứ 2 khi chu kỳ đang chạy nhận 409")
    void shouldRejectConcurrentTrigger() throws Exception {
        when(locationRepository.findByIsActiveTrue()).thenReturn(List.of(station("HAN")));
        when(dataSyncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Guard nằm trên instance (AtomicBoolean) — dùng CHUNG một service cho cả hai luồng
        WeatherSyncService service = newService("test-key");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.fetchCurrentWeather(anyDouble(), anyDouble()))
                .thenAnswer(
                        inv -> {
                            entered.countDown();
                            if (!release.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Test timeout khi giữ chu kỳ 1");
                            }
                            return okResponse();
                        });

        Thread first = new Thread(service::triggerWeatherSync);
        first.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue(); // chu kỳ 1 đã vào vùng gọi client

        assertThatThrownBy(service::triggerWeatherSync)
                .isInstanceOf(SyncInProgressException.class); // 409

        release.countDown();
        first.join(5000);
        assertThat(first.isAlive()).isFalse();
    }
}
