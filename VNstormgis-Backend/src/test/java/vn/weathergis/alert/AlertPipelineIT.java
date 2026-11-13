package vn.weathergis.alert;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.weathergis.alert.dto.AlertGenerationJobView;

class AlertPipelineIT extends AlertPipelineFixture {

    @Test
    void noViolationForBelowThresholdDisabledRuleOrMissingWeather() throws Exception {
        rule("rain_1h", ">", 60, "DANGER", true);
        rule("wind_speed", ">=", 20, "WARNING", false);
        weather(55, 7, 18, 23);
        evaluate();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_generation_jobs where location_id = ?",
                                Long.class,
                                stationId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from alert_violation_keys", Long.class))
                .isZero();
        assertThat(
                        mapper.readTree(
                                        http.getForEntity("/api/v1/alerts/active", String.class)
                                                .getBody())
                                .path("features"))
                .isEmpty();

        jdbc.update("delete from weather_records where location_id = ?", stationId);
        evaluate();
        worker.runBatch();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_generation_jobs where location_id = ?",
                                Long.class,
                                stationId))
                .isZero();
        GEMINI.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void storedWeatherAndPostGisStormReachVerifiedGeminiDraftAndBothApis() throws Exception {
        int rain = rule("rain_1h", ">=", 50, "DANGER", true);
        int wind = rule("wind_speed", ">", 17, "WARNING", true);
        int distance = rule("storm_dist", "=", 0, "CRITICAL", true);
        rule("rain_3h", ">", 100, "INFO", false);
        weather(55, 7, 18, 23);
        String stormId = storm(108.2, 16.0);
        storm(115.0, 20.0);

        evaluate();
        AlertGenerationJobView pending = onlyJob();
        assertThat(pending.status().name()).isEqualTo("PENDING");
        assertThat(pending.violationKeys()).hasSize(3).allMatch(key -> key.startsWith("v1:"));
        assertThat(pending.factualSnapshot().violations())
                .extracting(v -> v.parameter())
                .containsExactly("rain_1h", "wind_speed", "storm_dist");
        assertThat(pending.factualSnapshot().violations())
                .extracting(v -> v.measuredValue())
                .containsExactly(55.0, 18.0, 0.0);
        assertThat(pending.factualSnapshot().violations())
                .extracting(v -> v.unit())
                .containsExactly("mm", "m/s", "km");
        assertThat(pending.factualSnapshot().violations().get(2).stormId()).isEqualTo(stormId);
        assertThat(pending.factualSnapshot().violations().get(2).distanceKm()).isZero();
        assertThat(pending.representativeThresholdId()).isEqualTo(distance);
        assertThat(pending.severityLevel()).isEqualTo("CRITICAL");
        assertThat(pending.triggerReason()).contains("rain_1h", "wind_speed", "storm_dist");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where location_id = ?",
                                Long.class,
                                stationId))
                .isZero();

        validGeminiResponse(pending);
        JsonNode data = publishAndRead(pending, false);
        assertThat(data.path("threshold_id").asInt()).isEqualTo(distance);
        assertThat(data.path("severity_level").asText()).isEqualTo("CRITICAL");
        assertThat(data.path("storm_id").asText()).isEqualTo(stormId);
        assertThat(
                        data.path("factual_snapshot")
                                .path("representative_threshold")
                                .path("threshold_id")
                                .asInt())
                .isEqualTo(distance);
        assertThat(pending.factualSnapshot().violations())
                .extracting(v -> v.thresholdId())
                .containsExactly(rain, wind, distance);

        GEMINI.verify(
                1,
                postRequestedFor(urlEqualTo("/v1beta/models/w407-test-model:generateContent"))
                        .withRequestBody(
                                matchingJsonPath(
                                        "$.generationConfig.temperature", equalTo("0.2"))));
        JsonNode request =
                mapper.readTree(
                        GEMINI.getAllServeEvents().getFirst().getRequest().getBodyAsString());
        String prompt = request.path("contents").get(0).path("parts").get(0).path("text").asText();
        assertThat(prompt)
                .contains(
                        "[FACTUAL_SYSTEM_DATA]",
                        "[/FACTUAL_SYSTEM_DATA]",
                        "55.0",
                        "50.0",
                        "18.0",
                        "17.0",
                        stormId);
        assertThat(request.path("generationConfig").path("temperature").asDouble()).isEqualTo(0.2);
    }

    @Test
    void replayClaimsOnlyNewViolationEvenAfterFirstAlertIsResolved() throws Exception {
        rule("rain_1h", ">=", 50, "DANGER", true);
        rule("wind_speed", ">=", 15, "WARNING", true);
        weather(55, 7, 18, 23);
        evaluate();
        AlertGenerationJobView first = onlyJob();
        assertThat(first.violationKeys()).hasSize(2);
        validGeminiResponse(first);
        publishAndRead(first, false);

        evaluate();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_generation_jobs where location_id = ?",
                                Long.class,
                                stationId))
                .isEqualTo(1);
        jdbc.update(
                "update alerts set status = 'resolved' where id = ?",
                persistence.findJobById(first.jobId()).orElseThrow().alertId());
        rule("wind_gust", ">", 20, "CRITICAL", true);
        evaluate();
        AlertGenerationJobView second = onlyJob();
        assertThat(second.jobId()).isNotEqualTo(first.jobId());
        assertThat(second.violationKeys())
                .hasSize(1)
                .doesNotContainAnyElementsOf(first.violationKeys());
        assertThat(second.factualSnapshot().violations())
                .extracting(v -> v.parameter())
                .containsExactly("wind_gust");
        assertThat(second.severityLevel()).isEqualTo("CRITICAL");
        validGeminiResponse(second);
        publishAndRead(second, false);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where location_id = ?",
                                Long.class,
                                stationId))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_violation_keys where alert_id in (select id from alerts where location_id = ?)",
                                Long.class,
                                stationId))
                .isEqualTo(3);

        jdbc.update(
                "update alerts set status = 'dismissed' where id = ?",
                persistence.findJobById(second.jobId()).orElseThrow().alertId());
        evaluate();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_generation_jobs where location_id = ?",
                                Long.class,
                                stationId))
                .isEqualTo(2);
    }

    @Test
    void oversizedEvidenceIsQuarantinedWithoutTruncatingOrCallingGemini() throws Exception {
        weather(55, 7, 18, 23);
        for (int i = 0; i < 75; i++) {
            rule("rain_1h", ">=", 50, "DANGER", true);
        }
        evaluate();
        AlertGenerationJobView pending = onlyJob();
        assertThat(pending.violationKeys()).hasSize(75);
        worker.runBatch();
        AlertGenerationJobView quarantined = persistence.findJobById(pending.jobId()).orElseThrow();
        assertThat(quarantined.status().name()).isEqualTo("PENDING");
        assertThat(quarantined.lastError()).isEqualTo("INVALID_FACTUAL_DATA");
        assertThat(
                        jdbc.queryForObject(
                                "select quarantine_until > clock_timestamp() from alert_generation_jobs where id = ?",
                                Boolean.class,
                                pending.jobId()))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where location_id = ?",
                                Long.class,
                                stationId))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_violation_keys where job_id = ? and alert_id is null",
                                Long.class,
                                pending.jobId()))
                .isEqualTo(75);
        GEMINI.verify(0, postRequestedFor(anyUrl()));
    }
}
