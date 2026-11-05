package vn.weathergis.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "weathergis.admin.api-key=http-integration-test-key")
@ActiveProfiles("dev")
class AlertThresholdApiIT extends AbstractPostGisIntegrationTest {

    private static final String BASE = "/api/v1/alert-thresholds";
    private static final String KEY = "http-integration-test-key";
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    private final List<Integer> createdIds = new ArrayList<>();

    @BeforeEach
    void supportPatchOverRealHttp() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @AfterEach
    void removeOwnFixtures() {
        createdIds.forEach(id -> jdbc.update("DELETE FROM alert_thresholds WHERE id = ?", id));
    }

    @Test
    void realHttpCrudPersistsFiltersTogglesAndDeletes() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("name", "  W4-01 HTTP rainfall  ");
        ResponseEntity<String> created = write(HttpMethod.POST, BASE, request);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode threshold = mapper.readTree(created.getBody()).path("data");
        int id = threshold.path("id").asInt();
        createdIds.add(id);
        String path = BASE + "/" + id;
        assertThat(created.getHeaders().getLocation()).hasPath(path);
        assertThat(threshold.path("name").asText()).isEqualTo("W4-01 HTTP rainfall");
        assertThat(threshold.path("is_enabled").asBoolean()).isTrue();
        OffsetDateTime initialTime = OffsetDateTime.parse(threshold.path("updated_at").asText());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT threshold_value FROM alert_thresholds WHERE id = ?",
                                Double.class,
                                id))
                .isEqualTo(50.0);

        ResponseEntity<String> detail = http.getForEntity(path, String.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(detail.getBody()).path("data")).isEqualTo(threshold);
        assertThat(listIds("?parameter=rain_1h&is_enabled=true")).contains(id);

        request.put("name", "W4-01 HTTP updated wind");
        request.put("parameter", "wind_gust");
        request.put("operator", ">");
        request.put("threshold_value", 28.5);
        request.put("severity_level", "CRITICAL");
        request.put("description", "Updated wind threshold for HTTP verification");
        request.put("is_enabled", false);
        ResponseEntity<String> updated = write(HttpMethod.PUT, path, request);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode changed = mapper.readTree(updated.getBody()).path("data");
        assertThat(changed.path("name").asText()).isEqualTo(request.get("name"));
        assertThat(changed.path("parameter").asText()).isEqualTo("wind_gust");
        assertThat(changed.path("operator").asText()).isEqualTo(">");
        assertThat(changed.path("threshold_value").asDouble()).isEqualTo(28.5);
        assertThat(changed.path("severity_level").asText()).isEqualTo("CRITICAL");
        assertThat(changed.path("description").asText()).isEqualTo(request.get("description"));
        assertThat(changed.path("is_enabled").asBoolean()).isFalse();
        assertThat(OffsetDateTime.parse(changed.path("updated_at").asText())).isAfter(initialTime);
        assertThat(listIds("?parameter=rain_1h")).doesNotContain(id);
        assertThat(listIds("?parameter=wind_gust&is_enabled=false")).contains(id);
        assertThat(listIds("?parameter=wind_gust&is_enabled=true")).doesNotContain(id);

        ResponseEntity<String> enabled = write(HttpMethod.PATCH, path + "/toggle", null);
        assertThat(enabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(enabled.getBody()).path("data").path("is_enabled").asBoolean())
                .isTrue();
        ResponseEntity<String> disabled = write(HttpMethod.PATCH, path + "/toggle", null);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(disabled.getBody()).path("data").path("is_enabled").asBoolean())
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_enabled FROM alert_thresholds WHERE id = ?",
                                Boolean.class,
                                id))
                .isFalse();

        ResponseEntity<String> deleted = write(HttpMethod.DELETE, path, null);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deleted.getBody()).isNull();
        assertProblem(http.getForEntity(path, String.class), 404, "ALT_404_NOT_FOUND", path);
        assertProblem(write(HttpMethod.DELETE, path, null), 404, "ALT_404_NOT_FOUND", path);
    }

    @ParameterizedTest
    @CsvSource({
        "name,'',ALT_400_VALIDATION",
        "description,' ',ALT_400_VALIDATION",
        "parameter,storm_distance_km,ALT_400_INVALID_PARAMETER",
        "operator,!=,ALT_400_INVALID_PARAMETER",
        "severity_level,danger,ALT_400_INVALID_PARAMETER",
        "threshold_value,-1,ALT_400_VALIDATION",
        "threshold_value,0,ALT_400_VALIDATION",
        "threshold_value,NaN,ALT_400_VALIDATION",
        "threshold_value,Infinity,ALT_400_VALIDATION"
    })
    void invalidValuesReturnModuleProblemDetailsWithoutSaving(
            String field, String value, String errorCode) throws Exception {
        Map<String, Object> request = validRequest();
        request.put(field, value);
        long before = jdbc.queryForObject("SELECT count(*) FROM alert_thresholds", Long.class);
        assertProblem(write(HttpMethod.POST, BASE, request), 400, errorCode, BASE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_thresholds", Long.class))
                .isEqualTo(before);
    }

    @Test
    void publicReadsWorkAndWritesRequireAdminKey() throws Exception {
        ResponseEntity<String> list = http.getForEntity(BASE, String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(list.getBody()).path("success").asBoolean()).isTrue();
        assertThat(mapper.readTree(list.getBody()).path("data").isArray()).isTrue();
        for (HttpMethod method :
                List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            String path =
                    method == HttpMethod.POST
                            ? BASE
                            : BASE + "/1" + (method == HttpMethod.PATCH ? "/toggle" : "");
            for (String key : List.of("", "wrong-http-test-key")) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (!key.isEmpty()) headers.set("X-Admin-Api-Key", key);
                assertProblem(
                        http.exchange(
                                path,
                                method,
                                new HttpEntity<>(validRequest(), headers),
                                String.class),
                        401,
                        "ALT_401_UNAUTHORIZED",
                        path);
            }
        }
    }

    @Test
    void frameworkInputErrorsUseAlertCodes() throws Exception {
        assertProblem(write(HttpMethod.POST, BASE, "{broken"), 400, "ALT_400_VALIDATION", BASE);
        assertProblem(write(HttpMethod.POST, BASE, null), 400, "ALT_400_VALIDATION", BASE);
        assertProblem(
                http.getForEntity(BASE + "/abc", String.class),
                400,
                "ALT_400_INVALID_PARAMETER",
                BASE + "/abc");
        assertProblem(
                http.getForEntity(BASE + "/0", String.class),
                400,
                "ALT_400_INVALID_PARAMETER",
                BASE + "/0");
        assertProblem(
                http.getForEntity(BASE + "?is_enabled=invalid", String.class),
                400,
                "ALT_400_INVALID_PARAMETER",
                BASE);
    }

    @Test
    void openApiDocumentsAllRoutesAndOnlyWritesRequireHeader() throws Exception {
        ResponseEntity<String> response = http.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode spec = mapper.readTree(response.getBody());
        JsonNode security = spec.path("components").path("securitySchemes").path("AdminApiKey");
        assertThat(security.path("type").asText()).isEqualTo("apiKey");
        assertThat(security.path("in").asText()).isEqualTo("header");
        assertThat(security.path("name").asText()).isEqualTo("X-Admin-Api-Key");
        JsonNode paths = spec.path("paths");
        assertThat(paths.path(BASE).path("post").path("responses").has("201")).isTrue();
        assertThat(paths.path(BASE + "/{id}").path("delete").path("responses").has("204")).isTrue();
        assertThat(paths.path(BASE).path("get").has("security")).isFalse();
        assertThat(paths.path(BASE + "/{id}").path("get").has("security")).isFalse();
        for (String[] operation :
                List.of(
                        new String[] {BASE, "post"},
                        new String[] {BASE + "/{id}", "put"},
                        new String[] {BASE + "/{id}/toggle", "patch"},
                        new String[] {BASE + "/{id}", "delete"})) {
            JsonNode write = paths.path(operation[0]).path(operation[1]);
            assertThat(write.path("tags").get(0).asText())
                    .isEqualTo("5. Quản Trị Ngưỡng Cảnh Báo (Alert Thresholds)");
            assertThat(write.path("security").get(0).has("AdminApiKey")).isTrue();
        }
    }

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "W4-01 HTTP rainfall");
        request.put("parameter", "rain_1h");
        request.put("operator", ">=");
        request.put("threshold_value", 50.0);
        request.put("severity_level", "DANGER");
        request.put("description", "HTTP integration fixture");
        return request;
    }

    private ResponseEntity<String> write(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Admin-Api-Key", KEY);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private List<Integer> listIds(String query) throws Exception {
        ResponseEntity<String> list = http.getForEntity(BASE + query, String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Integer> ids = new ArrayList<>();
        mapper.readTree(list.getBody())
                .path("data")
                .forEach(row -> ids.add(row.path("id").asInt()));
        return ids;
    }

    private void assertProblem(
            ResponseEntity<String> response, int status, String errorCode, String path)
            throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(
                        response.getHeaders()
                                .getContentType()
                                .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        JsonNode problem = mapper.readTree(response.getBody());
        assertThat(problem.path("status").asInt()).isEqualTo(status);
        assertThat(problem.path("errorCode").asText()).isEqualTo(errorCode);
        assertThat(problem.path("instance").asText()).isEqualTo(path);
        assertThat(problem.path("title").asText()).isNotBlank();
        assertThat(problem.path("detail").asText()).isNotBlank();
        assertThat(OffsetDateTime.parse(problem.path("timestamp").asText())).isNotNull();
        assertThat(problem.has("trace")).isFalse();
        assertThat(response.getBody()).doesNotContain(KEY, "org.hibernate", "java.lang", "SELECT ");
    }
}
