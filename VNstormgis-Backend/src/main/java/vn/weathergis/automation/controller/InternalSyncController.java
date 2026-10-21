package vn.weathergis.automation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.weathergis.automation.dto.WeatherSyncResult;
import vn.weathergis.automation.service.WeatherSyncService;
import vn.weathergis.common.api.ApiResponse;
import vn.weathergis.common.exception.SyncUnauthorizedException;

/**
 * Endpoint kích hoạt đồng bộ thủ công phục vụ kiểm thử/debug — không phải API công khai. Bảo vệ
 * bằng header X-Internal-Secret, so khớp constant-time chống timing attack (quyết định #9, Mục 2).
 */
@RestController
@RequestMapping("/api/v1/internal/sync")
@Tag(name = "Internal Sync", description = "Kích hoạt thủ công Ingestion Engine (bảo vệ bí mật)")
public class InternalSyncController {

    private final WeatherSyncService weatherSyncService;
    private final String expectedSecret;

    public InternalSyncController(
            WeatherSyncService weatherSyncService,
            @Value("${weathergis.internal.sync-secret:}") String expectedSecret) {
        this.weatherSyncService = weatherSyncService;
        this.expectedSecret = expectedSecret;
    }

    @Operation(summary = "Kích hoạt đồng bộ thời tiết OpenWeatherMap cho toàn bộ trạm active")
    @PostMapping("/weather")
    public ResponseEntity<ApiResponse<WeatherSyncResult>> syncWeather(
            @RequestHeader(value = "X-Internal-Secret", required = false) String providedSecret) {
        if (expectedSecret == null
                || expectedSecret.isBlank()
                || providedSecret == null
                || !MessageDigest.isEqual(
                        expectedSecret.getBytes(StandardCharsets.UTF_8),
                        providedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new SyncUnauthorizedException(); // -> 401 RFC 7807 SYN_401_INVALID_SECRET
        }
        WeatherSyncResult result = weatherSyncService.triggerWeatherSync(); // có thể ném 409
        return ResponseEntity.ok(
                ApiResponse.ok(
                        result,
                        "Đồng bộ "
                                + result.recordsProcessed()
                                + "/"
                                + result.totalStations()
                                + " trạm"));
    }
}
