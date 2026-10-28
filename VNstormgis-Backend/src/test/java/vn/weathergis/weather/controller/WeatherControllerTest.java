package vn.weathergis.weather.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.weathergis.common.api.GeoJsonFeature;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.cache.GeoJsonCacheService;
import vn.weathergis.common.config.CorsConfig;
import vn.weathergis.common.config.JacksonConfig;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.GlobalExceptionHandler;
import vn.weathergis.common.exception.ResourceNotFoundException;
import vn.weathergis.weather.dto.WeatherCurrentResponse;
import vn.weathergis.weather.dto.WeatherHistoryResponse;
import vn.weathergis.weather.service.WeatherService;

@WebMvcTest(WeatherController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, CorsConfig.class})
@DisplayName("WeatherController — GeoJSON/JSON contract, RFC 7807 & CORS (W2-07)")
class WeatherControllerTest {

    private static final UUID HAN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final OffsetDateTime RECORDED_AT =
            OffsetDateTime.of(2026, 10, 23, 9, 0, 0, 0, ZoneOffset.UTC);

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private WeatherService weatherService;

    @MockBean private GeoJsonCacheService geoJsonCacheService;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * W3-03 (mục 7.2): slice {@code @WebMvcTest} không nạp bean {@code @Component} ngoài controller
     * nên helper cache phải được mock. Pass-through render đúng chuỗi JSON mà production gửi đi —
     * toàn bộ assert hợp đồng W2-07 giữ nguyên, chỉ khác body controller giờ là String đã render
     * sẵn.
     */
    @BeforeEach
    void stubGeoJsonCachePassThrough() {
        when(geoJsonCacheService.getOrLoad(anyString(), any(), any()))
                .thenAnswer(
                        invocation ->
                                objectMapper.writeValueAsString(
                                        ((Supplier<?>) invocation.getArgument(2)).get()));
    }

    @Test
    @DisplayName("TC-04: mặc định trả GeoJSON FeatureCollection thô (không envelope ApiResponse)")
    void shouldReturnGeoJsonByDefault() throws Exception {
        when(weatherService.getCurrentWeatherAsGeoJson()).thenReturn(hanoiCollection());

        mockMvc.perform(get("/api/v1/weather/current").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].id").value("HAN"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(105.8542))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]").value(21.0285))
                .andExpect(jsonPath("$.features[0].properties.code").value("HAN"))
                .andExpect(jsonPath("$.features[0].properties.severity_rain").value("WARNING"));
    }

    @Test
    @DisplayName("TC-05: format=json trả ApiResponse danh sách phẳng có longitude/latitude")
    void shouldReturnJsonEnvelopeForJsonFormat() throws Exception {
        WeatherCurrentResponse hanoi =
                new WeatherCurrentResponse(
                        HAN_ID,
                        "HAN",
                        "Hà Nội",
                        "Bắc Bộ",
                        105.8542,
                        21.0285,
                        28.5,
                        31.2,
                        78.0,
                        1008.0,
                        4.5,
                        135.0,
                        7.2,
                        18.5,
                        25.5,
                        "Rain",
                        "mưa rào",
                        "WARNING",
                        RECORDED_AT);
        when(weatherService.getCurrentWeather()).thenReturn(List.of(hanoi));

        mockMvc.perform(
                        get("/api/v1/weather/current")
                                .param("format", "json")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("HAN"))
                .andExpect(jsonPath("$.data[0].longitude").value(105.8542))
                .andExpect(jsonPath("$.data[0].latitude").value(21.0285))
                .andExpect(jsonPath("$.data[0].severityRain").value("WARNING"));
    }

    @Test
    @DisplayName("TC-07: history trả envelope với readings tăng dần")
    void shouldReturnHistoryEnvelope() throws Exception {
        WeatherHistoryResponse history =
                new WeatherHistoryResponse(
                        HAN_ID,
                        "HAN",
                        "Hà Nội",
                        "Bắc Bộ",
                        24,
                        RECORDED_AT.minusHours(24),
                        RECORDED_AT,
                        2,
                        List.of(
                                new WeatherHistoryResponse.WeatherReading(
                                        RECORDED_AT.minusHours(2),
                                        2.0,
                                        4.0,
                                        4.0,
                                        120.0,
                                        6.0,
                                        27.0,
                                        30.0,
                                        75.0,
                                        1009.0,
                                        "Rain"),
                                new WeatherHistoryResponse.WeatherReading(
                                        RECORDED_AT.minusHours(1),
                                        18.5,
                                        25.5,
                                        5.0,
                                        135.0,
                                        8.0,
                                        28.0,
                                        31.0,
                                        80.0,
                                        1007.0,
                                        "Rain")));
        when(weatherService.getWeatherHistory("han", 24)).thenReturn(history);

        mockMvc.perform(
                        get("/api/v1/weather/history")
                                .param("location_code", "han")
                                .param("hours", "24")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("HAN"))
                .andExpect(jsonPath("$.data.hours").value(24))
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.readings.length()").value(2))
                .andExpect(jsonPath("$.data.readings[0].rain1h").value(2.0))
                .andExpect(jsonPath("$.data.readings[1].rain1h").value(18.5));
    }

    @Test
    @DisplayName("TC-09: hours=0 → 400 RFC 7807 COM_400_INVALID_PARAM, không gọi service")
    void shouldRejectHoursBelowRange() throws Exception {
        mockMvc.perform(
                        get("/api/v1/weather/history")
                                .param("location_code", "HAN")
                                .param("hours", "0")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Tham số không hợp lệ"))
                .andExpect(
                        jsonPath("$.detail").value("Tham số hours phải nằm trong phạm vi 1..168"))
                .andExpect(jsonPath("$.errorCode").value("COM_400_INVALID_PARAM"))
                .andExpect(jsonPath("$.instance").value("/api/v1/weather/history"))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(weatherService);
    }

    @Test
    @DisplayName("TC-09: hours=200 → 400 RFC 7807 COM_400_INVALID_PARAM")
    void shouldRejectHoursAboveRange() throws Exception {
        mockMvc.perform(
                        get("/api/v1/weather/history")
                                .param("location_code", "HAN")
                                .param("hours", "200")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COM_400_INVALID_PARAM"))
                .andExpect(
                        jsonPath("$.detail").value("Tham số hours phải nằm trong phạm vi 1..168"));

        verifyNoInteractions(weatherService);
    }

    @Test
    @DisplayName("TC-08: mã trạm không tồn tại → 404 RFC 7807 WTH_404_NOT_FOUND")
    void shouldReturnProblemDetailForUnknownStation() throws Exception {
        when(weatherService.getWeatherHistory(org.mockito.ArgumentMatchers.eq("XXX"), anyInt()))
                .thenThrow(
                        new ResourceNotFoundException(
                                ErrorCode.WEATHER_NOT_FOUND, "Không tìm thấy trạm: XXX"));

        mockMvc.perform(
                        get("/api/v1/weather/history")
                                .param("location_code", "XXX")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Tài nguyên không tồn tại"))
                .andExpect(jsonPath("$.detail").value("Không tìm thấy trạm: XXX"))
                .andExpect(jsonPath("$.errorCode").value("WTH_404_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/v1/weather/history"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("TC-15: CORS cho frontend :5173")
    void shouldExposeCorsHeader() throws Exception {
        when(weatherService.getCurrentWeatherAsGeoJson())
                .thenReturn(new GeoJsonFeatureCollection());

        mockMvc.perform(
                        get("/api/v1/weather/current")
                                .header("Origin", "http://localhost:5173")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    private GeoJsonFeatureCollection hanoiCollection() {
        Map<String, Object> properties =
                new java.util.LinkedHashMap<>(
                        Map.of(
                                "location_id",
                                HAN_ID.toString(),
                                "code",
                                "HAN",
                                "name",
                                "Hà Nội",
                                "region",
                                "Bắc Bộ",
                                "temperature",
                                28.5,
                                "rain_1h",
                                18.5,
                                "severity_rain",
                                "WARNING",
                                "recorded_at",
                                RECORDED_AT));
        GeoJsonFeature feature =
                new GeoJsonFeature(
                        "HAN",
                        geometryFactory.createPoint(new Coordinate(105.8542, 21.0285)),
                        properties);
        return new GeoJsonFeatureCollection(List.of(feature));
    }
}
