package vn.weathergis.alert;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.weathergis.alert.dto.AlertGenerationJobView;

class AlertGeminiFaultIT extends AlertPipelineFixture {

    @ParameterizedTest(name = "Gemini fault: {0}")
    @ValueSource(
            strings = {
                "429",
                "500",
                "503",
                "reset",
                "malformed",
                "missing",
                "safety",
                "oversized",
                "wrong-number",
                "wrong-unit",
                "wrong-station",
                "invented-forecast"
            })
    void rejectsFaultAndPublishesOnlyVerifiedFallback(String fault) throws Exception {
        rule("rain_1h", ">=", 50, "DANGER", true);
        weather(55, 7, 18, 23);
        evaluate();
        AlertGenerationJobView pending = onlyJob();
        String explanation = guardrail.fallback(pending).aiExplanation();
        String title = guardrail.fallback(pending).title();
        var response =
                switch (fault) {
                    case "429" -> aResponse().withStatus(429);
                    case "500" -> aResponse().withStatus(500);
                    case "503" -> aResponse().withStatus(503);
                    case "reset" -> aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER);
                    case "malformed" -> okJson("{invalid-json");
                    case "missing" -> okJson(geminiEnvelope("{\"title\":\"incomplete\"}"));
                    case "safety" -> okJson("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
                    case "oversized" ->
                            okJson(
                                    geminiEnvelope(
                                            "{\"title\":\"x\",\"ai_explanation\":\""
                                                    + "x".repeat(4001)
                                                    + "\"}"));
                    case "wrong-number" ->
                            okJson(
                                    geminiEnvelope(
                                            mapper.writeValueAsString(
                                                    java.util.Map.of(
                                                            "title",
                                                            title,
                                                            "ai_explanation",
                                                            explanation.replace(
                                                                    "55 mm", "56 mm")))));
                    case "wrong-unit" ->
                            okJson(
                                    geminiEnvelope(
                                            mapper.writeValueAsString(
                                                    java.util.Map.of(
                                                            "title",
                                                            title,
                                                            "ai_explanation",
                                                            explanation.replace("mm", "m/s")))));
                    case "wrong-station" ->
                            okJson(
                                    geminiEnvelope(
                                            mapper.writeValueAsString(
                                                    java.util.Map.of(
                                                            "title",
                                                            "Cảnh báo tại Trạm khác",
                                                            "ai_explanation",
                                                            explanation))));
                    case "invented-forecast" ->
                            okJson(
                                    geminiEnvelope(
                                            mapper.writeValueAsString(
                                                    java.util.Map.of(
                                                            "title",
                                                            title,
                                                            "ai_explanation",
                                                            explanation
                                                                    + " Ngày mai mưa 100 mm."))));
                    default -> throw new IllegalArgumentException(fault);
                };
        GEMINI.stubFor(post(anyUrl()).willReturn(response));

        var detail = publishAndRead(pending, true);
        assertThat(detail.path("title").asText()).isEqualTo(title);
        assertThat(detail.path("ai_explanation").asText()).isEqualTo(explanation);
        assertThat(detail.toString())
                .doesNotContain("incomplete", "56 mm", "Trạm khác", "Ngày mai");
        GEMINI.verify(
                1, postRequestedFor(urlEqualTo("/v1beta/models/w407-test-model:generateContent")));
    }

    @Test
    void fixedDelayExceedsFiveSecondDeadlineAndStillPublishesFallback() throws Exception {
        assertTimeoutFallback(okJson("{}").withFixedDelay(12000));
    }

    @Test
    void dribblingBodyExceedsFiveSecondDeadlineAndStillPublishesFallback() throws Exception {
        assertTimeoutFallback(
                okJson("{\"data\":\"" + "x".repeat(128) + "\"}")
                        .withChunkedDribbleDelay(12, 12000));
    }

    @Test
    void disabledGeminiNeverUsesNetworkAndStillPublishesFallback() throws Exception {
        rule("rain_1h", ">=", 50, "DANGER", true);
        weather(55, 7, 18, 23);
        evaluate();
        geminiProperties.setEnabled(false);
        publishAndRead(onlyJob(), true);
        GEMINI.verify(0, postRequestedFor(anyUrl()));
        assertThat(
                        jdbc.queryForObject(
                                "select status from data_sync_logs where source = 'AI_MONITOR' order by id desc limit 1",
                                String.class))
                .isEqualTo("SUCCESS");
    }

    private void assertTimeoutFallback(
            com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response)
            throws Exception {
        rule("rain_1h", ">=", 50, "DANGER", true);
        weather(55, 7, 18, 23);
        evaluate();
        AlertGenerationJobView pending = onlyJob();
        GEMINI.stubFor(post(anyUrl()).willReturn(response));
        long start = System.nanoTime();
        publishAndRead(pending, true);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        // This measures worker plus HTTP reads; the client-only deadline is asserted in
        // GeminiClientTest.
        assertThat(elapsedMs).isBetween(4500L, 10000L);
        GEMINI.verify(1, postRequestedFor(anyUrl()));
    }
}
