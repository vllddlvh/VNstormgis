package vn.weathergis.location.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.weathergis.common.api.GeoJsonFeature;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.config.CorsConfig;
import vn.weathergis.common.config.JacksonConfig;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.GlobalExceptionHandler;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.common.exception.ResourceNotFoundException;
import vn.weathergis.location.dto.LocationResponse;
import vn.weathergis.location.service.LocationService;

@WebMvcTest(LocationController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, CorsConfig.class})
class LocationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private LocationService locationService;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    @DisplayName("TC-02: Lấy danh sách trạm quan trắc định dạng JSON Envelope chuẩn")
    void testGetLocationsJsonEnvelope() throws Exception {
        UUID hanoiId = UUID.randomUUID();
        LocationResponse hanoi =
                new LocationResponse(
                        hanoiId,
                        "HAN",
                        "Hà Nội",
                        "Bắc Bộ",
                        105.8542,
                        21.0285,
                        true,
                        OffsetDateTime.now());

        when(locationService.getAllLocations(any(), any(), any())).thenReturn(List.of(hanoi));

        mockMvc.perform(get("/api/v1/locations").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("HAN"))
                .andExpect(jsonPath("$.data[0].longitude").value(105.8542))
                .andExpect(jsonPath("$.data[0].latitude").value(21.0285));
    }

    @Test
    @DisplayName("TC-03: Lấy danh sách trạm quan trắc định dạng GeoJSON RFC 7946 cho Leaflet")
    void testGetLocationsGeoJson() throws Exception {
        Point hanoiPoint = geometryFactory.createPoint(new Coordinate(105.8542, 21.0285));
        GeoJsonFeature feature =
                new GeoJsonFeature(
                        "hanoi-id", hanoiPoint, java.util.Map.of("code", "HAN", "name", "Hà Nội"));
        GeoJsonFeatureCollection collection = new GeoJsonFeatureCollection(List.of(feature));

        when(locationService.getAllLocationsAsGeoJson(any(), any(), any())).thenReturn(collection);

        mockMvc.perform(get("/api/v1/locations?format=geojson").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(105.8542))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]").value(21.0285))
                .andExpect(jsonPath("$.features[0].properties.code").value("HAN"));
    }

    @Test
    @DisplayName("TC-05: Bắt lỗi 404 chuẩn hóa RFC 7807 ProblemDetail khi không tìm thấy trạm")
    void testGetLocationNotFoundReturnsProblemDetail() throws Exception {
        when(locationService.getLocationByCode("UNKNOWN"))
                .thenThrow(
                        new ResourceNotFoundException(
                                ErrorCode.LOCATION_NOT_FOUND,
                                "Không tìm thấy trạm với mã UNKNOWN"));

        mockMvc.perform(get("/api/v1/locations/code/UNKNOWN").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Tài nguyên không tồn tại"))
                .andExpect(jsonPath("$.detail").value("Không tìm thấy trạm với mã UNKNOWN"))
                .andExpect(jsonPath("$.errorCode").value("LOC_404_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("TC-04: Lấy chi tiết trạm quan trắc theo mã trạm hợp lệ")
    void testGetLocationByCodeSuccess() throws Exception {
        UUID danangId = UUID.randomUUID();
        LocationResponse danang =
                new LocationResponse(
                        danangId,
                        "DNG",
                        "Đà Nẵng",
                        "Nam Trung Bộ",
                        108.2022,
                        16.0544,
                        true,
                        OffsetDateTime.now());

        when(locationService.getLocationByCode("DNG")).thenReturn(danang);

        mockMvc.perform(get("/api/v1/locations/code/DNG").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("DNG"))
                .andExpect(jsonPath("$.data.name").value("Đà Nẵng"))
                .andExpect(jsonPath("$.data.region").value("Nam Trung Bộ"))
                .andExpect(jsonPath("$.data.longitude").value(108.2022))
                .andExpect(jsonPath("$.data.latitude").value(16.0544));
    }

    @Test
    @DisplayName("TC-06: Tìm kiếm trạm lân cận thất bại do kinh độ vượt ngưỡng [-180, 180]")
    void testGetNearbyLocationsInvalidLongitudeReturnsProblemDetail() throws Exception {
        when(locationService.getNearbyLocations(200.0, 21.0, 100.0))
                .thenThrow(
                        new InvalidParameterException(
                                "Kinh độ (longitude) phải nằm trong khoảng [-180, 180]"));

        mockMvc.perform(
                        get("/api/v1/locations/nearby")
                                .param("lng", "200.0")
                                .param("lat", "21.0")
                                .param("radiusKm", "100.0")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Tham số không hợp lệ"))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Kinh độ (longitude) phải nằm trong khoảng [-180, 180]"))
                .andExpect(jsonPath("$.errorCode").value("COM_400_INVALID_PARAM"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("TC-07: Xác nhận phản hồi CORS Header cho Client Frontend 5173")
    void testCorsHeadersAllowedOrigin() throws Exception {
        mockMvc.perform(
                        get("/api/v1/locations")
                                .header("Origin", "http://localhost:5173")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
