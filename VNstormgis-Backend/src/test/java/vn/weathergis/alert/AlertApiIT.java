package vn.weathergis.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import vn.weathergis.alert.dto.AlertGenerationJobView;
import vn.weathergis.alert.service.AlertPersistenceService;
import vn.weathergis.automation.event.ThresholdViolatedEvent;
import vn.weathergis.automation.event.ThresholdViolatedEvent.Violation;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "weathergis.admin.api-key=w403-http-test-key")
@ActiveProfiles("dev")
class AlertApiIT extends AbstractPostGisIntegrationTest {

    private static final String BASE = "/api/v1/alerts";
    private static final Instant EARLIER_TIME = Instant.parse("2026-11-05T03:00:00Z");
    private static final Instant LATER_TIME = Instant.parse("2026-11-05T03:10:00Z");

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private AlertPersistenceService persistenceService;

    private final List<UUID> stationIds = new ArrayList<>();
    private final List<Integer> thresholdIds = new ArrayList<>();
    private final List<String> stormIds = new ArrayList<>();

    @AfterEach
    void cleanFixtures() {
        for (UUID stationId : stationIds) {
            jdbc.update(
                    """
                    delete from alert_violation_keys
                    where job_id in (select id from alert_generation_jobs where location_id = ?)
                    """,
                    stationId);
            jdbc.update(
                    "update alert_generation_jobs set alert_id = null where location_id = ?",
                    stationId);
            jdbc.update("delete from alerts where location_id = ?", stationId);
            jdbc.update("delete from alert_generation_jobs where location_id = ?", stationId);
            jdbc.update("delete from locations where id = ?", stationId);
        }
        thresholdIds.forEach(id -> jdbc.update("delete from alert_thresholds where id = ?", id));
        stormIds.forEach(id -> jdbc.update("delete from storms where id = ?", id));
    }

    @Test
    void eventToInboxToVerifiedPublicationExposesActiveGeoJsonAndDetailWithoutExposingPendingJobs()
            throws Exception {
        UUID daNangId = createStation("W4E_FIRST", "Trạm Đà Nẵng", 108.2, 16.0);
        UUID hueId = createStation("W4E_SECOND", "Trạm Huế", 107.6, 16.46);
        int rainRule = createThreshold("rain_1h", ">=", 50.0, "DANGER");
        int windRule = createThreshold("wind_speed", ">=", 15.0, "WARNING");
        int stormRule = createThreshold("storm_dist", "<=", 300.0, "CRITICAL");
        String stormId = createStorm("W4E_NEAREST", "Bão kiểm thử", 109.5, 16.1);

        Violation daNangRain =
                new Violation(
                        rainRule,
                        "rain_1h",
                        ">=",
                        50.0,
                        "DANGER",
                        55.0,
                        "mm",
                        EARLIER_TIME,
                        null,
                        null,
                        null,
                        EARLIER_TIME,
                        "v1:" + daNangId + ":" + rainRule + ":" + EARLIER_TIME);
        Violation daNangWind =
                new Violation(
                        windRule,
                        "wind_speed",
                        ">=",
                        15.0,
                        "WARNING",
                        18.0,
                        "m/s",
                        EARLIER_TIME,
                        null,
                        null,
                        null,
                        EARLIER_TIME,
                        "v1:" + daNangId + ":" + windRule + ":" + EARLIER_TIME);

        // 1. Emit event for Da Nang -> creates PENDING job; /alerts/active must NOT expose PENDING
        eventPublisher.publishEvent(
                new ThresholdViolatedEvent(
                        daNangId,
                        "W4E_FIRST",
                        "Trạm Đà Nẵng",
                        108.2,
                        16.0,
                        UUID.randomUUID(),
                        LATER_TIME,
                        List.of(daNangRain, daNangWind)));

        ResponseEntity<String> emptyActive = http.getForEntity(BASE + "/active", String.class);
        assertThat(emptyActive.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode emptyJson = mapper.readTree(emptyActive.getBody());
        assertThat(emptyJson.path("type").asText()).isEqualTo("FeatureCollection");
        assertThat(emptyJson.path("features")).isEmpty();

        AlertGenerationJobView daNangJob =
                persistenceService.findPendingJobs().stream()
                        .filter(j -> j.locationId().equals(daNangId))
                        .findFirst()
                        .orElseThrow();

        // 2. Emit event for Hue with EARLIER_TIME
        Violation hueStorm =
                new Violation(
                        stormRule,
                        "storm_dist",
                        "<=",
                        300.0,
                        "CRITICAL",
                        195.5,
                        "km",
                        EARLIER_TIME,
                        stormId,
                        "Bão kiểm thử",
                        195.5,
                        EARLIER_TIME,
                        "v1:" + hueId + ":" + stormRule + ":" + EARLIER_TIME + ":" + stormId);
        AlertGenerationJobView hueJob =
                persistenceService
                        .intake(
                                new ThresholdViolatedEvent(
                                        hueId,
                                        "W4E_SECOND",
                                        "Trạm Huế",
                                        107.6,
                                        16.46,
                                        UUID.randomUUID(),
                                        EARLIER_TIME,
                                        List.of(hueStorm)))
                        .orElseThrow();

        // 3. Submit verified content: Da Nang uses fallback=true, Hue uses fallback=false
        UUID daNangAlertId =
                persistenceService.submitAndPublish(
                        vn.weathergis.alert.AlertTestSubmission.claim(
                                daNangJob, true, jdbc, mapper));
        UUID hueAlertId =
                persistenceService.submitAndPublish(
                        vn.weathergis.alert.AlertTestSubmission.claim(hueJob, false, jdbc, mapper));

        // 4. GET /api/v1/alerts/active returns raw GeoJSON ordered by triggered_at DESC (Da Nang
        // first, Hue second)
        ResponseEntity<String> activeResponse = http.getForEntity(BASE + "/active", String.class);
        assertThat(activeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode activeRoot = mapper.readTree(activeResponse.getBody());
        assertThat(activeRoot.path("type").asText()).isEqualTo("FeatureCollection");
        assertThat(activeRoot.has("success")).isFalse();
        JsonNode features = activeRoot.path("features");
        assertThat(features).hasSize(2);

        JsonNode firstFeature = features.get(0);
        assertThat(firstFeature.path("id").asText()).isEqualTo(daNangAlertId.toString());
        assertThat(firstFeature.path("geometry").path("type").asText()).isEqualTo("Point");
        assertThat(firstFeature.path("geometry").path("coordinates").get(0).asDouble())
                .isEqualTo(108.2);
        assertThat(firstFeature.path("geometry").path("coordinates").get(1).asDouble())
                .isEqualTo(16.0);
        assertThat(firstFeature.path("properties").path("alert_id").asText())
                .isEqualTo(daNangAlertId.toString());
        assertThat(firstFeature.path("properties").path("location_code").asText())
                .isEqualTo("W4E_FIRST");
        assertThat(firstFeature.path("properties").path("location_name").asText())
                .isEqualTo("Trạm Đà Nẵng");
        assertThat(firstFeature.path("properties").path("severity_level").asText())
                .isEqualTo("DANGER");
        assertThat(firstFeature.path("properties").path("triggered_at").asText())
                .isEqualTo("2026-11-05T03:10:00Z");
        assertThat(firstFeature.path("properties").path("is_fallback").asBoolean()).isTrue();

        JsonNode secondFeature = features.get(1);
        assertThat(secondFeature.path("id").asText()).isEqualTo(hueAlertId.toString());
        assertThat(secondFeature.path("properties").path("is_fallback").asBoolean()).isFalse();

        // 5. GET /api/v1/alerts/{id} returns full envelope & snapshot; remains readable in resolved
        // & dismissed
        ResponseEntity<String> detailResponse =
                http.getForEntity(BASE + "/" + daNangAlertId, String.class);
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode detailData = mapper.readTree(detailResponse.getBody()).path("data");
        assertThat(detailData.path("id").asText()).isEqualTo(daNangAlertId.toString());
        assertThat(detailData.path("threshold_id").asInt()).isEqualTo(rainRule);
        assertThat(detailData.path("location_id").asText()).isEqualTo(daNangId.toString());
        assertThat(detailData.path("storm_id").isNull()).isTrue();
        assertThat(detailData.path("is_fallback").asBoolean()).isTrue();
        assertThat(detailData.path("status").asText()).isEqualTo("active");
        assertThat(detailData.path("factual_snapshot").path("violations")).hasSize(2);

        // Transition Hue alert to resolved and dismissed -> excluded from /active, still readable
        // via /{id}
        jdbc.update("update alerts set status = 'resolved' where id = ?", hueAlertId);
        JsonNode activeAfterResolve =
                mapper.readTree(http.getForEntity(BASE + "/active", String.class).getBody());
        assertThat(activeAfterResolve.path("features")).hasSize(1);
        assertThat(activeAfterResolve.path("features").get(0).path("id").asText())
                .isEqualTo(daNangAlertId.toString());

        ResponseEntity<String> resolvedDetail =
                http.getForEntity(BASE + "/" + hueAlertId, String.class);
        assertThat(resolvedDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(resolvedDetail.getBody()).path("data").path("status").asText())
                .isEqualTo("resolved");

        jdbc.update("update alerts set status = 'dismissed' where id = ?", hueAlertId);
        ResponseEntity<String> dismissedDetail =
                http.getForEntity(BASE + "/" + hueAlertId, String.class);
        assertThat(dismissedDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(dismissedDetail.getBody()).path("data").path("status").asText())
                .isEqualTo("dismissed");
    }

    @Test
    void problemDetailsAndOpenApiContractMatchSpecification() throws Exception {
        UUID missingId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        assertProblem(
                http.getForEntity(BASE + "/not-a-uuid", String.class),
                400,
                "ALR_400_INVALID_ID",
                BASE + "/not-a-uuid");
        assertProblem(
                http.getForEntity(BASE + "/" + missingId, String.class),
                404,
                "ALR_404_NOT_FOUND",
                BASE + "/" + missingId);

        ResponseEntity<String> openApiResp = http.getForEntity("/v3/api-docs", String.class);
        assertThat(openApiResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode paths = mapper.readTree(openApiResp.getBody()).path("paths");

        JsonNode activeGet = paths.path(BASE + "/active").path("get");
        assertThat(activeGet.isMissingNode()).isFalse();
        assertThat(activeGet.path("tags").get(0).asText())
                .isEqualTo("6. Cảnh Báo Thiên Tai Chủ Động (Alerts)");
        assertThat(activeGet.has("security")).isFalse();
        assertThat(activeGet.path("responses").has("200")).isTrue();

        JsonNode detailGet = paths.path(BASE + "/{id}").path("get");
        assertThat(detailGet.isMissingNode()).isFalse();
        assertThat(detailGet.path("tags").get(0).asText())
                .isEqualTo("6. Cảnh Báo Thiên Tai Chủ Động (Alerts)");
        assertThat(detailGet.has("security")).isFalse();
        assertThat(detailGet.path("responses").has("200")).isTrue();
        assertThat(detailGet.path("responses").has("400")).isTrue();
        assertThat(detailGet.path("responses").has("404")).isTrue();
    }

    private void assertProblem(
            ResponseEntity<String> response, int status, String errorCode, String instance)
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
        assertThat(problem.path("instance").asText()).isEqualTo(instance);
        assertThat(problem.path("title").asText()).isNotBlank();
        assertThat(problem.path("detail").asText()).isNotBlank();
        assertThat(OffsetDateTime.parse(problem.path("timestamp").asText())).isNotNull();
    }

    private UUID createStation(String code, String name, double lng, double lat) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into locations (id, code, name, region, geom, is_active)
                values (?, ?, ?, 'Trung Bộ', ST_SetSRID(ST_MakePoint(?, ?), 4326), true)
                """,
                id,
                code,
                name,
                lng,
                lat);
        stationIds.add(id);
        return id;
    }

    private int createThreshold(String parameter, String operator, double value, String severity) {
        Integer id =
                jdbc.queryForObject(
                        """
                        insert into alert_thresholds
                            (name, parameter, operator, threshold_value, severity_level, description, is_enabled)
                        values ('W4-03 API fixture', ?, ?, ?, ?, 'API integration fixture', true)
                        returning id
                        """,
                        Integer.class,
                        parameter,
                        operator,
                        value,
                        severity);
        thresholdIds.add(id);
        return id;
    }

    private String createStorm(String id, String name, double lng, double lat) {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-05T03:00:00Z");
        jdbc.update(
                """
                insert into storms
                    (id, name, year, category, max_wind_speed, status, start_date, geom_current, updated_at)
                values (?, ?, 2026, 'Typhoon', 42.0, 'active', ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                """,
                id,
                name,
                now.minusDays(1),
                lng,
                lat,
                now);
        stormIds.add(id);
        return id;
    }
}
