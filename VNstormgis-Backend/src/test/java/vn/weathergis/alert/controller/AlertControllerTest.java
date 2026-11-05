package vn.weathergis.alert.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.weathergis.alert.dto.AlertDetailResponse;
import vn.weathergis.alert.dto.AlertDetailResponse.AlertLocationSummary;
import vn.weathergis.alert.dto.AlertDetailResponse.AlertStormSummary;
import vn.weathergis.alert.dto.AlertDetailResponse.AlertThresholdSummary;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.security.AdminApiKeyFilter;
import vn.weathergis.alert.service.AlertService;
import vn.weathergis.common.api.GeoJsonFeature;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.config.CorsConfig;
import vn.weathergis.common.config.JacksonConfig;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.GlobalExceptionHandler;
import vn.weathergis.common.exception.ResourceNotFoundException;

@WebMvcTest(
        controllers = AlertController.class,
        properties = "weathergis.admin.api-key=strict-admin-key")
@Import({
    GlobalExceptionHandler.class,
    JacksonConfig.class,
    CorsConfig.class,
    AdminApiKeyFilter.class
})
class AlertControllerTest {

    private static final String BASE = "/api/v1/alerts";
    private static final UUID ALERT_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final UUID SECOND_ALERT_ID =
            UUID.fromString("4fa85f64-5717-4562-b3fc-2c963f66afa7");
    private static final UUID LOCATION_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID CYCLE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant TRIGGERED_AT = Instant.parse("2026-11-05T03:10:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-11-05T03:10:05Z");

    private final GeometryFactory geoFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AlertService alertService;

    @Test
    void activeAlertsReturnsBareGeoJsonFeatureCollectionInOrderAndRequiresNoAdminKey()
            throws Exception {
        GeoJsonFeature first =
                new GeoJsonFeature(
                        ALERT_ID.toString(),
                        geoFactory.createPoint(new Coordinate(108.2, 16.0)),
                        featureProps(ALERT_ID, "W4E_FIRST", "Trạm Đà Nẵng", "DANGER", true));
        GeoJsonFeature legacyNullGeom =
                new GeoJsonFeature(
                        SECOND_ALERT_ID.toString(),
                        null,
                        featureProps(SECOND_ALERT_ID, null, null, "WARNING", false));
        when(alertService.getActiveAlertsAsGeoJson())
                .thenReturn(new GeoJsonFeatureCollection(List.of(first, legacyNullGeom)));

        String body =
                mockMvc.perform(get(BASE + "/active").accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.type").value("FeatureCollection"))
                        .andExpect(jsonPath("$.success").doesNotExist())
                        .andExpect(jsonPath("$.data").doesNotExist())
                        .andExpect(jsonPath("$.features.length()").value(2))
                        .andExpect(jsonPath("$.features[0].id").value(ALERT_ID.toString()))
                        .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                        .andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(108.2))
                        .andExpect(jsonPath("$.features[0].geometry.coordinates[1]").value(16.0))
                        .andExpect(
                                jsonPath("$.features[0].properties.alert_id")
                                        .value(ALERT_ID.toString()))
                        .andExpect(
                                jsonPath("$.features[0].properties.location_code")
                                        .value("W4E_FIRST"))
                        .andExpect(
                                jsonPath("$.features[0].properties.location_name")
                                        .value("Trạm Đà Nẵng"))
                        .andExpect(
                                jsonPath("$.features[0].properties.severity_level").value("DANGER"))
                        .andExpect(
                                jsonPath("$.features[0].properties.title")
                                        .value("Cảnh báo mưa lớn tại Trạm Đà Nẵng"))
                        .andExpect(
                                jsonPath("$.features[0].properties.trigger_reason")
                                        .value(
                                                "rain_1h đo được 55.0 mm, đạt điều kiện >= 50.0 mm."))
                        .andExpect(
                                jsonPath("$.features[0].properties.triggered_at")
                                        .value("2026-11-05T03:10:00Z"))
                        .andExpect(jsonPath("$.features[0].properties.is_fallback").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        JsonNode secondFeature = root.path("features").get(1);
        assertThat(secondFeature.has("geometry")).isTrue();
        assertThat(secondFeature.get("geometry").isNull()).isTrue();
        assertThat(secondFeature.path("properties").has("location_code")).isTrue();
        assertThat(secondFeature.path("properties").get("location_code").isNull()).isTrue();
        assertThat(secondFeature.path("properties").get("is_fallback").asBoolean()).isFalse();

        verify(alertService).getActiveAlertsAsGeoJson();
    }

    @Test
    void activeAlertsReturnsEmptyCollectionWithHttp200() throws Exception {
        when(alertService.getActiveAlertsAsGeoJson())
                .thenReturn(new GeoJsonFeatureCollection(List.of()));

        mockMvc.perform(get(BASE + "/active"))
                .andExpect(status().isOk())
                .andExpect(
                        content().json("{\"type\":\"FeatureCollection\",\"features\":[]}", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "resolved", "dismissed"})
    void detailReturnsEnvelopeAndSnakeCaseFieldsForAllAlertStatuses(String alertStatus)
            throws Exception {
        when(alertService.getAlertById(ALERT_ID)).thenReturn(detailResponse(alertStatus, false));

        mockMvc.perform(get(BASE + "/" + ALERT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ALERT_ID.toString()))
                .andExpect(jsonPath("$.data.threshold_id").value(2))
                .andExpect(jsonPath("$.data.location_id").value(LOCATION_ID.toString()))
                .andExpect(jsonPath("$.data.storm_id").value("W4E_NEAREST"))
                .andExpect(jsonPath("$.data.title").value("Cảnh báo mưa lớn tại Trạm Đà Nẵng"))
                .andExpect(jsonPath("$.data.severity_level").value("DANGER"))
                .andExpect(
                        jsonPath("$.data.trigger_reason")
                                .value("rain_1h đo được 55.0 mm, đạt điều kiện >= 50.0 mm."))
                .andExpect(
                        jsonPath("$.data.ai_explanation")
                                .value("Lượng mưa 1h tại Trạm Đà Nẵng đạt 55.0 mm."))
                .andExpect(jsonPath("$.data.is_fallback").value(false))
                .andExpect(jsonPath("$.data.status").value(alertStatus))
                .andExpect(jsonPath("$.data.triggered_at").value("2026-11-05T03:10:00Z"))
                .andExpect(jsonPath("$.data.created_at").value("2026-11-05T03:10:05Z"))
                .andExpect(jsonPath("$.data.location.code").value("W4E_FIRST"))
                .andExpect(jsonPath("$.data.location.coordinates[0]").value(108.2))
                .andExpect(jsonPath("$.data.location.coordinates[1]").value(16.0))
                .andExpect(jsonPath("$.data.threshold.parameter").value("rain_1h"))
                .andExpect(jsonPath("$.data.storm.id").value("W4E_NEAREST"))
                .andExpect(jsonPath("$.data.factual_snapshot.location_code").value("W4E_FIRST"))
                .andExpect(
                        jsonPath("$.data.factual_snapshot.representative_threshold.threshold_id")
                                .value(2))
                .andExpect(
                        jsonPath("$.data.factual_snapshot.violations[0].measured_value")
                                .value(55.0))
                .andExpect(jsonPath("$.data.severityLevel").doesNotExist())
                .andExpect(jsonPath("$.data.isFallback").doesNotExist());
    }

    @Test
    void detailPreservesExplicitNullRelationsAndLegacyNullSnapshot() throws Exception {
        AlertDetailResponse legacy =
                new AlertDetailResponse(
                        ALERT_ID,
                        null,
                        null,
                        null,
                        "Cảnh báo legacy",
                        "WARNING",
                        "Lý do legacy",
                        "Giải thích legacy",
                        true,
                        "resolved",
                        TRIGGERED_AT,
                        CREATED_AT,
                        CREATED_AT,
                        null,
                        null,
                        null,
                        null);
        when(alertService.getAlertById(ALERT_ID)).thenReturn(legacy);

        String raw =
                mockMvc.perform(get(BASE + "/" + ALERT_ID))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode data = objectMapper.readTree(raw).path("data");
        assertThat(data.has("threshold_id") && data.get("threshold_id").isNull()).isTrue();
        assertThat(data.has("location_id") && data.get("location_id").isNull()).isTrue();
        assertThat(data.has("storm_id") && data.get("storm_id").isNull()).isTrue();
        assertThat(data.has("location") && data.get("location").isNull()).isTrue();
        assertThat(data.has("threshold") && data.get("threshold").isNull()).isTrue();
        assertThat(data.has("storm") && data.get("storm").isNull()).isTrue();
        assertThat(data.has("factual_snapshot") && data.get("factual_snapshot").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "12345", "3fa85f64-5717-4562-b3fc-invalid"})
    void malformedUuidReturns400WithAlertInvalidIdErrorCode(String invalidId) throws Exception {
        String path = BASE + "/" + invalidId;
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("ALR_400_INVALID_ID"))
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(alertService);
    }

    @Test
    void unknownUuidReturns404WithAlertNotFoundErrorCode() throws Exception {
        when(alertService.getAlertById(ALERT_ID))
                .thenThrow(
                        new ResourceNotFoundException(
                                ErrorCode.ALERT_NOT_FOUND, "Không tìm thấy cảnh báo: " + ALERT_ID));

        String path = BASE + "/" + ALERT_ID;
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("ALR_404_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").value("Không tìm thấy cảnh báo: " + ALERT_ID))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unexpectedFailureReturns500WithCommonInternalErrorCode() throws Exception {
        when(alertService.getActiveAlertsAsGeoJson())
                .thenThrow(new IllegalStateException("Unexpected failure"));

        mockMvc.perform(get(BASE + "/active"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.errorCode").value("COM_500_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.instance").value(BASE + "/active"));
    }

    private static Map<String, Object> featureProps(
            UUID alertId,
            String locationCode,
            String locationName,
            String severity,
            boolean isFallback) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("alert_id", alertId.toString());
        props.put("location_code", locationCode == null ? NullNode.instance : locationCode);
        props.put("location_name", locationName == null ? NullNode.instance : locationName);
        props.put("severity_level", severity);
        props.put("title", "Cảnh báo mưa lớn tại Trạm Đà Nẵng");
        props.put("trigger_reason", "rain_1h đo được 55.0 mm, đạt điều kiện >= 50.0 mm.");
        props.put("triggered_at", TRIGGERED_AT);
        props.put("is_fallback", isFallback);
        return props;
    }

    private static AlertDetailResponse detailResponse(String status, boolean isFallback) {
        AlertLocationSummary location =
                new AlertLocationSummary(
                        LOCATION_ID,
                        "W4E_FIRST",
                        "Trạm Đà Nẵng",
                        "Nam Trung Bộ",
                        List.of(108.2, 16.0));
        AlertThresholdSummary threshold =
                new AlertThresholdSummary(
                        2,
                        "Mưa rất to 1h",
                        "rain_1h",
                        ">=",
                        50.0,
                        "DANGER",
                        "Lượng mưa 1h đạt 50 mm",
                        true,
                        TRIGGERED_AT);
        AlertStormSummary storm =
                new AlertStormSummary(
                        "W4E_NEAREST",
                        "Bão kiểm thử",
                        "Typhoon",
                        "active",
                        40.0,
                        975.0,
                        List.of(110.5, 16.2),
                        TRIGGERED_AT);
        AlertFactualSnapshot snapshot =
                new AlertFactualSnapshot(
                        LOCATION_ID,
                        "W4E_FIRST",
                        "Trạm Đà Nẵng",
                        List.of(108.2, 16.0),
                        CYCLE_ID,
                        TRIGGERED_AT,
                        new RepresentativeThresholdSnapshot(
                                2,
                                "rain_1h",
                                ">=",
                                50.0,
                                "DANGER",
                                "mm",
                                "v1:" + LOCATION_ID + ":2:2026-11-05T03:00:00Z"),
                        List.of(
                                new ViolationSnapshot(
                                        2,
                                        "rain_1h",
                                        ">=",
                                        50.0,
                                        "DANGER",
                                        55.0,
                                        "mm",
                                        Instant.parse("2026-11-05T03:00:00Z"),
                                        Instant.parse("2026-11-05T03:00:00Z"),
                                        "v1:" + LOCATION_ID + ":2:2026-11-05T03:00:00Z",
                                        null,
                                        null,
                                        null)));
        return new AlertDetailResponse(
                ALERT_ID,
                2,
                LOCATION_ID,
                "W4E_NEAREST",
                "Cảnh báo mưa lớn tại Trạm Đà Nẵng",
                "DANGER",
                "rain_1h đo được 55.0 mm, đạt điều kiện >= 50.0 mm.",
                "Lượng mưa 1h tại Trạm Đà Nẵng đạt 55.0 mm.",
                isFallback,
                status,
                TRIGGERED_AT,
                "resolved".equals(status) ? CREATED_AT : null,
                CREATED_AT,
                location,
                threshold,
                storm,
                snapshot);
    }
}
