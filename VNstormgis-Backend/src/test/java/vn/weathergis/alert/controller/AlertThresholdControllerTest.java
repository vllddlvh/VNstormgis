package vn.weathergis.alert.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.weathergis.alert.dto.AlertThresholdRequest;
import vn.weathergis.alert.dto.AlertThresholdResponse;
import vn.weathergis.alert.security.AdminApiKeyFilter;
import vn.weathergis.alert.service.AlertThresholdService;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.GlobalExceptionHandler;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.common.exception.ResourceNotFoundException;

@WebMvcTest(
        controllers = AlertThresholdController.class,
        properties = "weathergis.admin.api-key=controller-test-admin-key")
@Import({GlobalExceptionHandler.class, AdminApiKeyFilter.class})
class AlertThresholdControllerTest {

    private static final String URL = "/api/v1/alert-thresholds";
    private static final String ADMIN_KEY = "controller-test-admin-key";
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-11-05T09:15:30Z");
    private static final String BODY =
            """
            {
              "name": "Mưa rất to 1h",
              "parameter": "rain_1h",
              "operator": ">=",
              "threshold_value": 50.0,
              "severity_level": "DANGER",
              "description": "Lượng mưa đạt 50 mm trong 1 giờ",
              "is_enabled": true
            }
            """;
    private static final AlertThresholdRequest VALID_REQUEST =
            new AlertThresholdRequest(
                    "Mưa rất to 1h",
                    "rain_1h",
                    ">=",
                    50.0,
                    "DANGER",
                    "Lượng mưa đạt 50 mm trong 1 giờ",
                    true);

    @Autowired private MockMvc mockMvc;
    @MockBean private AlertThresholdService service;

    @Test
    void publicListReturnsEnvelopeAndSnakeCaseFields() throws Exception {
        when(service.findAll(null, null)).thenReturn(List.of(response(true)));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].name").value("Mưa rất to 1h"))
                .andExpect(jsonPath("$.data[0].parameter").value("rain_1h"))
                .andExpect(jsonPath("$.data[0].operator").value(">="))
                .andExpect(jsonPath("$.data[0].threshold_value").value(50.0))
                .andExpect(jsonPath("$.data[0].severity_level").value("DANGER"))
                .andExpect(jsonPath("$.data[0].description").value(VALID_REQUEST.description()))
                .andExpect(jsonPath("$.data[0].is_enabled").value(true))
                .andExpect(jsonPath("$.data[0].updated_at").value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.data[0].thresholdValue").doesNotExist())
                .andExpect(jsonPath("$.data[0].isEnabled").doesNotExist());
    }

    @Test
    void listForwardsBothOptionalFilters() throws Exception {
        when(service.findAll("wind_gust", false)).thenReturn(List.of());

        mockMvc.perform(get(URL).param("parameter", "wind_gust").param("is_enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(service).findAll("wind_gust", false);
    }

    @Test
    void publicDetailRequiresNoAdminKey() throws Exception {
        when(service.findById(7)).thenReturn(response(true));

        mockMvc.perform(get(URL + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7));
    }

    @Test
    void createParsesSnakeCaseBodyAndReturnsLocation() throws Exception {
        when(service.create(VALID_REQUEST)).thenReturn(response(true));

        mockMvc.perform(
                        post(URL)
                                .header(AdminApiKeyFilter.HEADER_NAME, ADMIN_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + URL + "/7"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.threshold_value").value(50.0))
                .andExpect(jsonPath("$.data.is_enabled").value(true));

        verify(service).create(VALID_REQUEST);
    }

    @Test
    void updateReplacesBusinessFields() throws Exception {
        when(service.update(7, VALID_REQUEST)).thenReturn(response(true));

        mockMvc.perform(
                        put(URL + "/7")
                                .header(AdminApiKeyFilter.HEADER_NAME, ADMIN_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7));

        verify(service).update(7, VALID_REQUEST);
    }

    @Test
    void toggleReturnsNewEnabledState() throws Exception {
        when(service.toggle(7)).thenReturn(response(false));

        mockMvc.perform(patch(URL + "/7/toggle").header(AdminApiKeyFilter.HEADER_NAME, ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.is_enabled").value(false));

        verify(service).toggle(7);
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete(URL + "/7").header(AdminApiKeyFilter.HEADER_NAME, ADMIN_KEY))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(7);
    }

    @ParameterizedTest
    @MethodSource("unauthorizedWrites")
    void eachWriteRejectsMissingAndWrongKeys(String method, String path, String key)
            throws Exception {
        MockHttpServletRequestBuilder builder =
                request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY);
        if (key != null) {
            builder.header(AdminApiKeyFilter.HEADER_NAME, key);
        }

        mockMvc.perform(builder)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("ALT_401_UNAUTHORIZED"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());

        verifyNoInteractions(service);
    }

    @Test
    void matrixPathCannotBypassAdminFilter() throws Exception {
        mockMvc.perform(
                        post(URL + ";version=1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ALT_401_UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    @Test
    void missingThresholdUsesAlertNotFoundProblem() throws Exception {
        when(service.findById(999))
                .thenThrow(
                        new ResourceNotFoundException(
                                ErrorCode.ALERT_THRESHOLD_NOT_FOUND, "Không tìm thấy ngưỡng: 999"));

        mockMvc.perform(get(URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("ALT_404_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value(URL + "/999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void businessValidationUsesAlertValidationProblem() throws Exception {
        when(service.create(any()))
                .thenThrow(
                        new InvalidParameterException(
                                ErrorCode.ALERT_THRESHOLD_VALIDATION, "Tên không được để trống"));

        mockMvc.perform(
                        post(URL)
                                .header(AdminApiKeyFilter.HEADER_NAME, ADMIN_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ALT_400_VALIDATION"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"threshold_value\":\"not-a-number\"}"})
    void malformedJsonUsesAlertValidationProblem(String body) throws Exception {
        mockMvc.perform(
                        post(URL)
                                .header(AdminApiKeyFilter.HEADER_NAME, ADMIN_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ALT_400_VALIDATION"))
                .andExpect(jsonPath("$.instance").value(URL));

        verifyNoInteractions(service);
    }

    @Test
    void malformedBooleanQueryUsesAlertParameterProblem() throws Exception {
        mockMvc.perform(get(URL).param("is_enabled", "invalid-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ALT_400_INVALID_PARAMETER"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "2147483648", "1.5"})
    void nonintegerIdUsesAlertParameterProblem(String id) throws Exception {
        mockMvc.perform(get(URL + "/" + id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ALT_400_INVALID_PARAMETER"));

        verifyNoInteractions(service);
    }

    private static Stream<Arguments> unauthorizedWrites() {
        return Stream.of(
                        new String[] {"POST", URL},
                        new String[] {"PUT", URL + "/7"},
                        new String[] {"PATCH", URL + "/7/toggle"},
                        new String[] {"DELETE", URL + "/7"})
                .flatMap(
                        route ->
                                Stream.of(
                                        Arguments.of(route[0], route[1], null),
                                        Arguments.of(route[0], route[1], "wrong-admin-key")));
    }

    private static AlertThresholdResponse response(boolean enabled) {
        return new AlertThresholdResponse(
                7,
                VALID_REQUEST.name(),
                VALID_REQUEST.parameter(),
                VALID_REQUEST.operator(),
                VALID_REQUEST.thresholdValue(),
                VALID_REQUEST.severityLevel(),
                VALID_REQUEST.description(),
                enabled,
                UPDATED_AT);
    }
}
