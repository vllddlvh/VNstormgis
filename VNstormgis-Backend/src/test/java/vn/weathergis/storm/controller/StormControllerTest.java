package vn.weathergis.storm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import vn.weathergis.common.config.CorsConfig;
import vn.weathergis.common.config.JacksonConfig;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.GlobalExceptionHandler;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.common.exception.ResourceNotFoundException;
import vn.weathergis.storm.dto.StormDetailResponse;
import vn.weathergis.storm.dto.StormSummaryResponse;
import vn.weathergis.storm.service.StormService;

@WebMvcTest(StormController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, CorsConfig.class})
@DisplayName("StormController — GeoJSON RFC 7946, format=json, RFC 7807 & CORS (W3-01)")
class StormControllerTest {

    private static final String STORMS_URL = "/api/v1/storms";
    private static final String TEST_STORM_ID = "teststorm01";
    private static final String TRACK_URL = STORMS_URL + "/" + TEST_STORM_ID + "/track";
    private static final OffsetDateTime UPDATED_AT =
            OffsetDateTime.of(2026, 10, 26, 9, 0, 0, 0, ZoneOffset.UTC);

    @Autowired private MockMvc mockMvc;

    @MockBean private StormService stormService;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------
    // TC-04 / TC-11: GeoJSON mặc định, không envelope
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-04: mặc định trả FeatureCollection thô — Point [lng, lat], không envelope")
    void shouldReturnBareGeoJsonFeatureCollection() throws Exception {
        when(stormService.getStormsAsGeoJson("active")).thenReturn(oneStormCollection());

        mockMvc.perform(get(STORMS_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].id").value(TEST_STORM_ID))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                .andExpect(jsonPath("$.features[0].properties.name").value("TEST STORM A"))
                .andExpect(jsonPath("$.features[0].properties.year").value(2026))
                .andExpect(jsonPath("$.features[0].properties.category").value("Typhoon"))
                .andExpect(jsonPath("$.features[0].properties.max_wind_speed").value(40.0))
                .andExpect(jsonPath("$.features[0].properties.max_wind_speed_kmh").value(144.0))
                .andExpect(jsonPath("$.features[0].properties.min_pressure").value(960.0))
                .andExpect(jsonPath("$.features[0].properties.status").value("active"))
                .andExpect(jsonPath("$.features[0].properties.raw_metadata").doesNotExist());

        verify(stormService).getStormsAsGeoJson("active");
    }

    @Test
    @DisplayName(
            "TC-04: Feature mang geometry JTS serialize ra GeoJSON Point [lng, lat] qua JacksonConfig")
    void shouldSerializeFeatureGeometryAsGeoJsonPoint() throws Exception {
        when(stormService.getStormsAsGeoJson("active")).thenReturn(oneStormCollection());

        JsonNode feature = geoJsonRoot().get("features").get(0);
        assertThat(feature.get("type").asText()).isEqualTo("Feature");
        assertThat(feature.get("id").asText()).isEqualTo(TEST_STORM_ID);
        assertThat(feature.get("geometry").get("type").asText()).isEqualTo("Point");
        assertThat(feature.get("geometry").get("coordinates").get(0).asDouble()).isEqualTo(116.4);
        assertThat(feature.get("geometry").get("coordinates").get(1).asDouble()).isEqualTo(18.2);
    }

    @Test
    @DisplayName("TC-11: coordinates[0]=kinh độ, coordinates[1]=vĩ độ — không hoán đổi")
    void shouldSerializeCoordinatesAsLngLat() throws Exception {
        when(stormService.getStormsAsGeoJson("active")).thenReturn(oneStormCollection());

        mockMvc.perform(get(STORMS_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(116.4))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]").value(18.2))
                .andExpect(
                        jsonPath(
                                "$.features[0].geometry.coordinates[0]",
                                greaterThan(18.2))); // kinh độ Biển Đông luôn lớn hơn vĩ độ
    }

    // ------------------------------------------------------------------
    // TC-05: lọc status
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-05: status truyền xuống service sau khi chuẩn hóa lowercase; mặc định active")
    void shouldForwardNormalizedStatus() throws Exception {
        when(stormService.getStormsAsGeoJson(anyString()))
                .thenReturn(new GeoJsonFeatureCollection());

        mockMvc.perform(get(STORMS_URL).param("status", "dissipated")).andExpect(status().isOk());
        mockMvc.perform(get(STORMS_URL).param("status", "ALL")).andExpect(status().isOk());
        mockMvc.perform(get(STORMS_URL)).andExpect(status().isOk());

        verify(stormService).getStormsAsGeoJson("dissipated");
        verify(stormService).getStormsAsGeoJson("all");
        verify(stormService).getStormsAsGeoJson("active");
    }

    @Test
    @DisplayName("TC-05: ?status= có khóa nhưng rỗng → coi như không gửi, dùng mặc định active")
    void shouldTreatEmptyStatusAsDefault() throws Exception {
        when(stormService.getStormsAsGeoJson(anyString()))
                .thenReturn(new GeoJsonFeatureCollection());

        mockMvc.perform(get(STORMS_URL).param("status", "")).andExpect(status().isOk());

        verify(stormService).getStormsAsGeoJson("active");
    }

    // ------------------------------------------------------------------
    // TC-07: format=json
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-07: format=json trả ApiResponse phẳng camelCase và gọi service đúng 1 lần")
    void shouldReturnJsonEnvelope() throws Exception {
        when(stormService.getStorms("active")).thenReturn(List.of(summary()));

        mockMvc.perform(get(STORMS_URL).param("format", "json").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(TEST_STORM_ID))
                .andExpect(jsonPath("$.data[0].maxWindSpeed").value(40.0))
                .andExpect(jsonPath("$.data[0].maxWindSpeedKmh").value(144.0))
                .andExpect(jsonPath("$.data[0].minPressure").value(960.0))
                .andExpect(jsonPath("$.data[0].status").value("active"))
                .andExpect(jsonPath("$.data[0].code").doesNotExist())
                .andExpect(jsonPath("$.data[0].central_pressure").doesNotExist())
                .andExpect(jsonPath("$.data[0].rawMetadata").doesNotExist());

        verify(stormService).getStorms("active");
        verify(stormService, never()).getStormsAsGeoJson(anyString());
    }

    // ------------------------------------------------------------------
    // TC-08: chi tiết bão
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-08: GET /storms/{id} trả envelope chi tiết kèm totalTrackPoints")
    void shouldReturnStormDetail() throws Exception {
        when(stormService.getStormDetail(TEST_STORM_ID)).thenReturn(detail());

        mockMvc.perform(get(STORMS_URL + "/" + TEST_STORM_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy chi tiết bão thành công"))
                .andExpect(jsonPath("$.data.id").value(TEST_STORM_ID))
                .andExpect(jsonPath("$.data.maxWindSpeed").value(40.0))
                .andExpect(jsonPath("$.data.maxWindSpeedKmh").value(144.0))
                .andExpect(jsonPath("$.data.longitude").value(116.4))
                .andExpect(jsonPath("$.data.latitude").value(18.2))
                .andExpect(jsonPath("$.data.totalTrackPoints").value(3));
    }

    // ------------------------------------------------------------------
    // TC-06 / TC-09: RFC 7807
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-09: id không tồn tại → 404 RFC 7807 errorCode STM_404_NOT_FOUND")
    void shouldReturnProblemDetailForUnknownStormId() throws Exception {
        when(stormService.getStormDetail("khong-ton-tai-999"))
                .thenThrow(
                        new ResourceNotFoundException(
                                ErrorCode.STORM_NOT_FOUND,
                                "Không tìm thấy bão: khong-ton-tai-999"));

        mockMvc.perform(get(STORMS_URL + "/khong-ton-tai-999").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Tài nguyên không tồn tại"))
                .andExpect(jsonPath("$.detail").value("Không tìm thấy bão: khong-ton-tai-999"))
                .andExpect(jsonPath("$.errorCode").value("STM_404_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value(STORMS_URL + "/khong-ton-tai-999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName(
            "TC-06: status ngoài {active, dissipated, all} → 400 STM_400_INVALID_STATUS, không gọi service")
    void shouldRejectUnknownStatus() throws Exception {
        mockMvc.perform(
                        get(STORMS_URL)
                                .param("status", "active-xyz")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Tham số không hợp lệ"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Tham số status phải thuộc {active, dissipated, all}, nhận: "
                                                + "active-xyz"))
                .andExpect(jsonPath("$.errorCode").value("STM_400_INVALID_STATUS"))
                .andExpect(jsonPath("$.instance").value(STORMS_URL))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(stormService);
    }

    @Test
    @DisplayName("TC-06: status khoảng trắng → 400 STM_400_INVALID_STATUS (kể cả format=json)")
    void shouldRejectBlankStatusEvenWithJsonFormat() throws Exception {
        mockMvc.perform(
                        get(STORMS_URL)
                                .param("status", "   ")
                                .param("format", "json")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("STM_400_INVALID_STATUS"));

        verifyNoInteractions(stormService);
    }

    @Test
    @DisplayName("TC-06: 400 ném InvalidParameterException mang ErrorCode STORM_INVALID_STATUS")
    void invalidStatusErrorCodeContract() {
        InvalidParameterException ex =
                new InvalidParameterException(
                        ErrorCode.STORM_INVALID_STATUS,
                        "Tham số status phải thuộc {active, dissipated, all}");

        assertThat(ex.getErrorCode().getCode()).isEqualTo("STM_400_INVALID_STATUS");
        assertThat(ex.getErrorCode().getHttpStatus().value()).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // TC-10: empty state
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-10: không bão active → 200 + features: [] (GeoJSON), không 404/500")
    void shouldReturnEmptyFeatureCollection() throws Exception {
        when(stormService.getStormsAsGeoJson("active")).thenReturn(new GeoJsonFeatureCollection());

        mockMvc.perform(get(STORMS_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(0));
    }

    @Test
    @DisplayName("TC-10: format=json rỗng → 200 + data: [] + message thân thiện của SRS")
    void shouldReturnFriendlyMessageForEmptyActiveList() throws Exception {
        when(stormService.getStorms("active")).thenReturn(List.of());

        mockMvc.perform(get(STORMS_URL).param("format", "json").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Hiện không có cơn bão nào đang hoạt động trong khu vực"
                                                + " theo dõi"));
    }

    @Test
    @DisplayName("TC-10: format=json với status=all rỗng → message nghiệp vụ thông thường")
    void shouldUseGenericMessageForEmptyAllList() throws Exception {
        when(stormService.getStorms("all")).thenReturn(List.of());

        mockMvc.perform(
                        get(STORMS_URL)
                                .param("format", "json")
                                .param("status", "all")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy danh sách bão thành công"));
    }

    // ------------------------------------------------------------------
    // TC-15: CORS
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-15: CORS cho frontend :5173")
    void shouldExposeCorsHeader() throws Exception {
        when(stormService.getStormsAsGeoJson("active")).thenReturn(new GeoJsonFeatureCollection());

        mockMvc.perform(
                        get(STORMS_URL)
                                .header("Origin", "http://localhost:5173")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    // ------------------------------------------------------------------
    // W3-02: GET /storms/{id}/track
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("W3-02 GET /storms/{id}/track — GeoJSON quỹ đạo thô, RFC 7807 & CORS")
    class TrackEndpointTests {

        @Test
        @DisplayName("TC-04: FeatureCollection thô không envelope, đủ 4 loại geometry")
        void shouldReturnBareTrackFeatureCollection() throws Exception {
            when(stormService.getStormTrack(TEST_STORM_ID)).thenReturn(trackCollection());

            mockMvc.perform(get(TRACK_URL).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("FeatureCollection"))
                    .andExpect(jsonPath("$.success").doesNotExist())
                    .andExpect(jsonPath("$.message").doesNotExist())
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.features.length()").value(4))
                    .andExpect(jsonPath("$.features[0].id").value("track-history"))
                    .andExpect(jsonPath("$.features[0].geometry.type").value("LineString"))
                    .andExpect(jsonPath("$.features[0].properties.kind").value("history"))
                    .andExpect(jsonPath("$.features[1].id").value("track-forecast"))
                    .andExpect(jsonPath("$.features[2].id").value("impact-zone"))
                    .andExpect(jsonPath("$.features[2].geometry.type").value("Polygon"))
                    .andExpect(jsonPath("$.features[2].properties.kind").value("impact"))
                    .andExpect(jsonPath("$.features[3].id").value("track-point-1"))
                    .andExpect(jsonPath("$.features[3].geometry.type").value("Point"))
                    .andExpect(jsonPath("$.features[3].properties.raw_metadata").doesNotExist());

            verify(stormService).getStormTrack(TEST_STORM_ID);
        }

        @Test
        @DisplayName("TC-12: LineString serialize [lng, lat] — vertex[0]=kinh độ, vertex[1]=vĩ độ")
        void shouldSerializeTrackLineAsLngLat() throws Exception {
            when(stormService.getStormTrack(TEST_STORM_ID)).thenReturn(trackCollection());

            mockMvc.perform(get(TRACK_URL).accept(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.features[0].geometry.coordinates[0][0]").value(116.4))
                    .andExpect(jsonPath("$.features[0].geometry.coordinates[0][1]").value(18.2))
                    .andExpect(jsonPath("$.features[0].geometry.coordinates[1][0]").value(112.0))
                    .andExpect(
                            jsonPath(
                                    "$.features[0].geometry.coordinates[0][0]", greaterThan(18.2)));
        }

        @Test
        @DisplayName("TC-13: bão hợp lệ chưa có mốc → 200 + features: [] (không 404/500)")
        void shouldReturnEmptyTrackCollection() throws Exception {
            when(stormService.getStormTrack(TEST_STORM_ID))
                    .thenReturn(new GeoJsonFeatureCollection());

            mockMvc.perform(get(TRACK_URL).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("FeatureCollection"))
                    .andExpect(jsonPath("$.features.length()").value(0));
        }

        @Test
        @DisplayName(
                "TC-09: id không tồn tại → 404 RFC 7807 qua GlobalExceptionHandler, errorCode STM_404_NOT_FOUND")
        void shouldReturnProblemDetailForUnknownStormTrackId() throws Exception {
            when(stormService.getStormTrack("khong-ton-tai-999"))
                    .thenThrow(
                            new ResourceNotFoundException(
                                    ErrorCode.STORM_NOT_FOUND,
                                    "Không tìm thấy bão: khong-ton-tai-999"));

            mockMvc.perform(
                            get(STORMS_URL + "/khong-ton-tai-999/track")
                                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Tài nguyên không tồn tại"))
                    .andExpect(jsonPath("$.detail").value("Không tìm thấy bão: khong-ton-tai-999"))
                    .andExpect(jsonPath("$.errorCode").value("STM_404_NOT_FOUND"))
                    .andExpect(
                            jsonPath("$.instance").value("/api/v1/storms/khong-ton-tai-999/track"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("TC-09: null theo mốc serialize ra JSON null, không bị non_null nuốt khóa")
        void shouldKeepNullWindValuesOnTrackWireFormat() throws Exception {
            when(stormService.getStormTrack(TEST_STORM_ID)).thenReturn(trackCollection());

            JsonNode props =
                    objectMapper
                            .readTree(
                                    mockMvc.perform(
                                                    get(TRACK_URL)
                                                            .accept(MediaType.APPLICATION_JSON))
                                            .andExpect(status().isOk())
                                            .andReturn()
                                            .getResponse()
                                            .getContentAsString())
                            .get("features")
                            .get(3)
                            .get("properties");

            assertThat(props.has("wind_speed")).isTrue();
            assertThat(props.get("wind_speed").isNull()).isTrue();
            assertThat(props.has("wind_gust")).isTrue();
            assertThat(props.get("wind_gust").isNull()).isTrue();
            assertThat(props.has("pressure")).isTrue();
            assertThat(props.get("pressure").isNull()).isTrue();
        }

        @Test
        @DisplayName("TC-15: CORS cho frontend :5173 trên endpoint track")
        void shouldExposeCorsHeaderOnTrack() throws Exception {
            when(stormService.getStormTrack(TEST_STORM_ID))
                    .thenReturn(new GeoJsonFeatureCollection());

            mockMvc.perform(
                            get(TRACK_URL)
                                    .header("Origin", "http://localhost:5173")
                                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(
                            header().string(
                                            "Access-Control-Allow-Origin",
                                            "http://localhost:5173"));
        }

        @Test
        @DisplayName("Routing: /{id}/track không đè /{id} — detail vẫn đi đường riêng")
        void shouldNotShadowStormDetail() throws Exception {
            when(stormService.getStormTrack(TEST_STORM_ID))
                    .thenReturn(new GeoJsonFeatureCollection());
            when(stormService.getStormDetail(TEST_STORM_ID)).thenReturn(detail());

            mockMvc.perform(get(TRACK_URL).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("FeatureCollection"));
            mockMvc.perform(
                            get(STORMS_URL + "/" + TEST_STORM_ID)
                                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(stormService).getStormTrack(TEST_STORM_ID);
            verify(stormService).getStormDetail(TEST_STORM_ID);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private JsonNode geoJsonRoot() throws Exception {
        String body =
                mockMvc.perform(get(STORMS_URL).accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body);
    }

    private Map<String, Object> stormProperties() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", TEST_STORM_ID);
        props.put("name", "TEST STORM A");
        props.put("year", 2026);
        props.put("category", "Typhoon");
        props.put("max_wind_speed", 40.0);
        props.put("max_wind_speed_kmh", 144.0);
        props.put("min_pressure", 960.0);
        props.put("status", "active");
        props.put("start_date", UPDATED_AT.minusDays(3));
        props.put("end_date", null);
        props.put("updated_at", UPDATED_AT);
        return props;
    }

    private GeoJsonFeatureCollection oneStormCollection() {
        GeoJsonFeature feature =
                new GeoJsonFeature(
                        TEST_STORM_ID,
                        geometryFactory.createPoint(new Coordinate(116.4, 18.2)),
                        stormProperties());
        return new GeoJsonFeatureCollection(List.of(feature));
    }

    /**
     * FeatureCollection mô phỏng hợp đồng /track (W3-02): LineString lịch sử → LineString dự báo →
     * Polygon vùng ảnh hưởng → Point mốc. Ba khóa gió dùng {@code NullNode.instance} đúng như
     * StormServiceImpl phát ra — khóa Java null thuần sẽ bị {@code non_null} nuốt (D-1).
     */
    private GeoJsonFeatureCollection trackCollection() {
        GeoJsonFeatureCollection collection = new GeoJsonFeatureCollection();
        collection.addFeature(
                new GeoJsonFeature(
                        "track-history",
                        line(new double[][] {{116.4, 18.2}, {112.0, 19.0}}),
                        trackLineProperties("history", 2)));
        collection.addFeature(
                new GeoJsonFeature(
                        "track-forecast",
                        line(new double[][] {{112.0, 19.0}, {110.0, 19.5}}),
                        trackLineProperties("forecast", 1)));
        Map<String, Object> impactProps = new LinkedHashMap<>();
        impactProps.put("kind", "impact");
        impactProps.put("storm_id", TEST_STORM_ID);
        impactProps.put("radius_km", 150.0);
        collection.addFeature(
                new GeoJsonFeature(
                        "impact-zone",
                        geometryFactory.createPolygon(
                                new Coordinate[] {
                                    new Coordinate(110.0, 18.0),
                                    new Coordinate(114.0, 18.0),
                                    new Coordinate(114.0, 22.0),
                                    new Coordinate(110.0, 22.0),
                                    new Coordinate(110.0, 18.0),
                                }),
                        impactProps));

        Map<String, Object> pointProps = new LinkedHashMap<>();
        pointProps.put("track_id", 1L);
        pointProps.put("storm_id", TEST_STORM_ID);
        pointProps.put("track_type", "historical");
        pointProps.put("forecast_time", UPDATED_AT);
        pointProps.put("wind_speed", NullNode.instance);
        pointProps.put("wind_gust", NullNode.instance);
        pointProps.put("pressure", NullNode.instance);
        pointProps.put("category", "Typhoon");
        pointProps.put("impact_radius_km", 150.0);
        collection.addFeature(
                new GeoJsonFeature(
                        "track-point-1",
                        geometryFactory.createPoint(new Coordinate(116.4, 18.2)),
                        pointProps));
        return collection;
    }

    private Map<String, Object> trackLineProperties(String kind, int points) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("kind", kind);
        props.put("storm_id", TEST_STORM_ID);
        props.put("points", points);
        return props;
    }

    private org.locationtech.jts.geom.LineString line(double[][] lngLat) {
        Coordinate[] coords = new Coordinate[lngLat.length];
        for (int i = 0; i < lngLat.length; i++) {
            coords[i] = new Coordinate(lngLat[i][0], lngLat[i][1]);
        }
        return geometryFactory.createLineString(coords);
    }

    private StormSummaryResponse summary() {
        return new StormSummaryResponse(
                TEST_STORM_ID,
                "TEST STORM A",
                2026,
                "Typhoon",
                40.0,
                144.0,
                960.0,
                "active",
                UPDATED_AT.minusDays(3),
                null,
                UPDATED_AT);
    }

    private StormDetailResponse detail() {
        return new StormDetailResponse(
                TEST_STORM_ID,
                "TEST STORM A",
                2026,
                "Typhoon",
                40.0,
                144.0,
                960.0,
                "active",
                116.4,
                18.2,
                3L,
                UPDATED_AT.minusDays(3),
                null,
                UPDATED_AT);
    }
}
