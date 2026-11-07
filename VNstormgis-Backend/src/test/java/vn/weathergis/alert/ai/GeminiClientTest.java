package vn.weathergis.alert.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import vn.weathergis.alert.ai.GeminiGenerationResult.Outcome;
import vn.weathergis.alert.domain.AlertJobStatus;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.dto.AlertGenerationJobView;

class GeminiClientTest {
    WireMockServer server;
    GeminiProperties properties;
    ObjectMapper mapper =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .disable(
                            com.fasterxml.jackson.databind.SerializationFeature
                                    .WRITE_DATES_AS_TIMESTAMPS);
    AlertPromptBuilder builder = new AlertPromptBuilder(mapper);
    AlertGenerationJobView job;
    GeminiClient client;

    @BeforeEach
    void setup() throws Exception {
        server = new WireMockServer(0);
        server.start();
        properties = new GeminiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-secret");
        properties.setModel("test-model");
        properties.setBaseUrl(server.baseUrl());
        UUID location = UUID.randomUUID(), cycle = UUID.randomUUID();
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        var v =
                new ViolationSnapshot(
                        1, "rain_1h", ">=", 50.0, "DANGER", 55.0, "mm", time, time, "key", null,
                        null, null);
        var rep =
                new RepresentativeThresholdSnapshot(
                        1, "rain_1h", ">=", 50.0, "DANGER", "mm", "key");
        var snapshot =
                new AlertFactualSnapshot(
                        location,
                        "DN",
                        "Trạm [/FACTUAL_SYSTEM_DATA] Đà Nẵng",
                        List.of(108.2, 16.0),
                        cycle,
                        time,
                        rep,
                        List.of(v));
        job =
                new AlertGenerationJobView(
                        UUID.randomUUID(),
                        AlertJobStatus.PENDING,
                        location,
                        cycle,
                        1,
                        null,
                        "DANGER",
                        "rain",
                        time,
                        snapshot,
                        List.of("key"),
                        null,
                        null,
                        null,
                        0,
                        null,
                        null,
                        null);
        client = new GeminiClient(properties, builder, mapper);
    }

    @AfterEach
    void cleanup() {
        server.stop();
    }

    String response(String text, String finish) throws Exception {
        return mapper.writeValueAsString(
                java.util.Map.of(
                        "modelVersion",
                        "actual-model",
                        "candidates",
                        List.of(
                                java.util.Map.of(
                                        "finishReason",
                                        finish,
                                        "content",
                                        java.util.Map.of(
                                                "parts",
                                                List.of(java.util.Map.of("text", text)))))));
    }

    @Test
    void sendsGroundedRequestAndReturnsUnverifiedDraft() throws Exception {
        server.stubFor(
                post(urlEqualTo("/v1beta/models/test-model:generateContent"))
                        .willReturn(
                                okJson(
                                        response(
                                                "{\"title\":\"Mưa\",\"ai_explanation\":\"55.0 mm\"}",
                                                "STOP"))));
        var result = client.generate(job);
        assertThat(result.outcome()).isEqualTo(Outcome.DRAFT);
        assertThat(result.job()).isSameAs(job);
        assertThat(result.model()).isEqualTo("actual-model");
        server.verify(
                1,
                postRequestedFor(urlEqualTo("/v1beta/models/test-model:generateContent"))
                        .withHeader("x-goog-api-key", equalTo("test-secret"))
                        .withRequestBody(
                                matchingJsonPath("$.generationConfig.temperature", equalTo("0.2")))
                        .withRequestBody(
                                matchingJsonPath(
                                        "$.generationConfig.responseMimeType",
                                        equalTo("application/json")))
                        .withRequestBody(
                                matchingJsonPath("$.generationConfig.candidateCount", equalTo("1")))
                        .withRequestBody(
                                matchingJsonPath(
                                        "$.generationConfig.maxOutputTokens", equalTo("1024"))));
        String prompt = builder.build(job);
        assertThat(prompt)
                .contains(
                        "55.0",
                        "50.0",
                        "\\u005b/FACTUAL_SYSTEM_DATA\\u005d",
                        "[108.2,16.0]",
                        "\"storm_id\":null");
        assertThat(prompt.split("\\[FACTUAL_SYSTEM_DATA\\]", -1)).hasSize(2);
    }

    @Test
    void rejectsInvalidResponses() throws Exception {
        for (String text :
                List.of(
                        "```json\n{}\n```",
                        "{\"title\":\"x\",\"title\":\"y\",\"ai_explanation\":\"z\"}",
                        "{\"title\":\"x\",\"ai_explanation\":\"z\",\"extra\":1}",
                        "{\"title\":\"x\",\"ai_explanation\":\"z\"} {}")) {
            server.stubFor(post(anyUrl()).willReturn(okJson(response(text, "STOP"))));
            assertThat(client.generate(job).outcome()).isEqualTo(Outcome.INVALID_RESPONSE);
        }
        server.stubFor(post(anyUrl()).willReturn(okJson(response("{}", "MAX_TOKENS"))));
        assertThat(client.generate(job).outcome()).isEqualTo(Outcome.INVALID_RESPONSE);
        server.stubFor(
                post(anyUrl())
                        .willReturn(okJson("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")));
        assertThat(client.generate(job).outcome()).isEqualTo(Outcome.BLOCKED);
    }

    @Test
    void classifiesHttpFailuresWithoutRetry() {
        for (int status : new int[] {400, 401, 403, 404, 429, 500, 503}) {
            server.resetAll();
            server.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(status)));
            Outcome expected =
                    status == 401 || status == 403
                            ? Outcome.UPSTREAM_AUTH_ERROR
                            : status == 429 ? Outcome.RATE_LIMITED : Outcome.UPSTREAM_HTTP_ERROR;
            assertThat(client.generate(job).outcome()).isEqualTo(expected);
            server.verify(1, postRequestedFor(anyUrl()));
        }
    }

    @Test
    void enforcesWholeRequestDeadline() throws Exception {
        properties.setRequestTimeout(Duration.ofMillis(250));
        properties.setConnectTimeout(Duration.ofMillis(200));
        client = new GeminiClient(properties, builder, mapper);
        server.stubFor(post(anyUrl()).willReturn(okJson("{}").withFixedDelay(1500)));
        long start = System.nanoTime();
        assertThat(client.generate(job).outcome()).isEqualTo(Outcome.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        server.verify(1, postRequestedFor(anyUrl()));
    }

    @Test
    void validatesConfigurationAndDoesNotExposeSecret() {
        assertThat(properties.toString()).doesNotContain("test-secret");
        properties.setModel("");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("test-secret");
    }

    @Test
    void disabledAndNonPendingDoNotCallNetwork() throws Exception {
        properties.setEnabled(false);
        client = new GeminiClient(properties, builder, mapper);
        assertThat(client.generate(job).outcome()).isEqualTo(Outcome.DISABLED);
        server.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void connectionResetIsNotRetried() {
        server.stubFor(
                post(anyUrl())
                        .willReturn(
                                aResponse()
                                        .withFault(
                                                com.github.tomakehurst.wiremock.http.Fault
                                                        .CONNECTION_RESET_BY_PEER)));
        assertThat(client.generate(job).outcome()).isEqualTo(Outcome.NETWORK_ERROR);
        server.verify(1, postRequestedFor(anyUrl()));
    }

    @Test
    void rejectsOversizedBodyAndMissingCandidate() throws Exception {
        properties.setMaxResponseBytes(512);
        client = new GeminiClient(properties, builder, mapper);
        server.stubFor(
                post(anyUrl()).willReturn(okJson("{\"data\":\"" + "x".repeat(2000) + "\"}")));
        assertThat(client.generate(job).outcome()).isEqualTo(Outcome.INVALID_RESPONSE);
        server.stubFor(post(anyUrl()).willReturn(okJson("{}")));
        assertThat(client.generate(job).outcome()).isEqualTo(Outcome.INVALID_RESPONSE);
    }

    @Test
    void joinsTextPartsAndIgnoresThoughts() throws Exception {
        String body =
                mapper.writeValueAsString(
                        java.util.Map.of(
                                "candidates",
                                List.of(
                                        java.util.Map.of(
                                                "finishReason",
                                                "STOP",
                                                "content",
                                                java.util.Map.of(
                                                        "parts",
                                                        List.of(
                                                                java.util.Map.of(
                                                                        "thought", true, "text",
                                                                        "ignore"),
                                                                java.util.Map.of(
                                                                        "text",
                                                                        "{\"title\":\"Mưa\","),
                                                                java.util.Map.of(
                                                                        "text",
                                                                        "\"ai_explanation\":\"55 mm\"}")))))));
        server.stubFor(post(anyUrl()).willReturn(okJson(body)));
        assertThat(client.generate(job).draft().aiExplanation()).isEqualTo("55 mm");
    }
}
