package vn.weathergis.chat.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.weathergis.alert.ai.GeminiProperties;
import vn.weathergis.chat.config.ChatContextProperties;
import vn.weathergis.chat.config.ChatReplyProperties;
import vn.weathergis.chat.context.FactualContextSerializer;

/**
 * WireMock sandbox for the chat Gemini client (W5-04 §8.1, pattern GeminiClientTest /
 * AlertGeminiFaultIT): loopback http allowed by GeminiProperties.validate(), every fault branch
 * asserts exactly ONE hit — no in-request retry (D5).
 */
class ChatGeminiFaultIT {

    private static final String PATH = "/v1beta/models/chat-test-model:generateContent";

    WireMockServer server;
    GeminiProperties properties;
    ChatReplyProperties replyProperties;
    ObjectMapper mapper =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .disable(
                            com.fasterxml.jackson.databind.SerializationFeature
                                    .WRITE_DATES_AS_TIMESTAMPS);
    ChatGeminiClient client;

    @BeforeEach
    void setup() {
        server = new WireMockServer(0);
        server.start();
        properties = new GeminiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-secret");
        properties.setModel("chat-test-model");
        properties.setBaseUrl(server.baseUrl());
        replyProperties = new ChatReplyProperties();
        client = newClient();
    }

    private ChatGeminiClient newClient() {
        return new ChatGeminiClient(
                properties,
                replyProperties,
                new ChatPromptBuilder(
                        new FactualContextSerializer(mapper), new ChatContextProperties()),
                mapper);
    }

    @AfterEach
    void cleanup() {
        server.stop();
    }

    private String envelope(String text, String finish) throws Exception {
        return mapper.writeValueAsString(
                Map.of(
                        "modelVersion",
                        "actual-model",
                        "usageMetadata",
                        Map.of("totalTokenCount", 123),
                        "candidates",
                        List.of(
                                Map.of(
                                        "finishReason",
                                        finish,
                                        "content",
                                        Map.of("parts", List.of(Map.of("text", text)))))));
    }

    @Test
    void successReturnsAnswerTextModelAndTokens() throws Exception {
        server.stubFor(
                post(urlEqualTo(PATH))
                        .willReturn(okJson(envelope("{\"intent\":\"rain_top\"}", "STOP"))));

        ChatGeminiResult result = client.generate("PROMPT_MARKER");

        assertThat(result.outcome()).isEqualTo(ChatGeminiResult.Outcome.ANSWER);
        assertThat(result.text()).isEqualTo("{\"intent\":\"rain_top\"}");
        assertThat(result.model()).isEqualTo("actual-model");
        assertThat(result.tokensUsed()).isEqualTo(123);
        assertThat(result.httpStatus()).isEqualTo(200);
        server.verify(
                1,
                postRequestedFor(urlEqualTo(PATH))
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
                                        "$.generationConfig.maxOutputTokens", equalTo("1024")))
                        .withRequestBody(
                                matchingJsonPath(
                                        "$.contents[0].parts[0].text", containing("PROMPT_MARKER")))
                        .withRequestBody(
                                matchingJsonPath(
                                        "$.systemInstruction.parts[0].text",
                                        containing("[FACTUAL_SYSTEM_DATA]"))));
    }

    @Test
    void chatMaxOutputTokensComesFromReplyProperties() throws Exception {
        replyProperties.setMaxOutputTokens(512);
        client = newClient();
        server.stubFor(post(anyUrl()).willReturn(okJson(envelope("{}", "STOP"))));

        client.generate("PROMPT_MARKER");

        server.verify(
                1,
                postRequestedFor(anyUrl())
                        .withRequestBody(
                                matchingJsonPath(
                                        "$.generationConfig.maxOutputTokens", equalTo("512"))));
    }

    @Test
    void httpFailuresMapToTypedOutcomesWithExactlyOneHit() {
        for (int status : new int[] {400, 401, 403, 404, 429, 500, 503}) {
            server.resetAll();
            server.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(status)));
            ChatGeminiResult.Outcome expected =
                    status == 401 || status == 403
                            ? ChatGeminiResult.Outcome.UPSTREAM_AUTH_ERROR
                            : status == 429
                                    ? ChatGeminiResult.Outcome.RATE_LIMITED
                                    : ChatGeminiResult.Outcome.UPSTREAM_HTTP_ERROR;

            ChatGeminiResult result = client.generate("PROMPT_MARKER");

            assertThat(result.outcome()).isEqualTo(expected);
            assertThat(result.tokensUsed()).isZero();
            assertThat(result.httpStatus()).isEqualTo(status);
            server.verify(1, postRequestedFor(anyUrl()));
        }
    }

    @Test
    void wholeRequestDeadlineProducesTimeoutWithoutRetry() throws Exception {
        properties.setRequestTimeout(Duration.ofMillis(250));
        properties.setConnectTimeout(Duration.ofMillis(200));
        client = newClient();
        server.stubFor(post(anyUrl()).willReturn(okJson("{}").withFixedDelay(1500)));

        long start = System.nanoTime();
        ChatGeminiResult result = client.generate("PROMPT_MARKER");

        assertThat(result.outcome()).isEqualTo(ChatGeminiResult.Outcome.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        server.verify(1, postRequestedFor(anyUrl()));
    }

    @Test
    void connectionResetIsNetworkErrorWithoutRetry() {
        server.stubFor(
                post(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(client.generate("PROMPT_MARKER").outcome())
                .isEqualTo(ChatGeminiResult.Outcome.NETWORK_ERROR);
        server.verify(1, postRequestedFor(anyUrl()));
    }

    @Test
    void malformedDuplicateOrTrailingJsonIsInvalidResponse() throws Exception {
        for (String body :
                List.of(
                        "{invalid-json",
                        "{\"intent\":\"rain_top\",\"intent\":\"wind_top\"}",
                        "{\"intent\":\"rain_top\"} {}")) {
            server.resetAll();
            server.stubFor(post(anyUrl()).willReturn(okJson(envelope(body, "STOP"))));
            assertThat(client.generate("PROMPT_MARKER").outcome())
                    .isEqualTo(ChatGeminiResult.Outcome.INVALID_RESPONSE);
            server.verify(1, postRequestedFor(anyUrl()));
        }
    }

    @Test
    void candidateCountAndFinishReasonAreEnforced() throws Exception {
        server.stubFor(post(anyUrl()).willReturn(okJson("{}")));
        assertThat(client.generate("PROMPT_MARKER").outcome())
                .isEqualTo(ChatGeminiResult.Outcome.INVALID_RESPONSE);

        String twoCandidates =
                mapper.writeValueAsString(
                        Map.of(
                                "candidates",
                                List.of(
                                        Map.of(
                                                "finishReason",
                                                "STOP",
                                                "content",
                                                Map.of("parts", List.of(Map.of("text", "{}")))),
                                        Map.of(
                                                "finishReason",
                                                "STOP",
                                                "content",
                                                Map.of("parts", List.of(Map.of("text", "{}")))))));
        server.resetAll();
        server.stubFor(post(anyUrl()).willReturn(okJson(twoCandidates)));
        assertThat(client.generate("PROMPT_MARKER").outcome())
                .isEqualTo(ChatGeminiResult.Outcome.INVALID_RESPONSE);

        server.resetAll();
        server.stubFor(post(anyUrl()).willReturn(okJson(envelope("{}", "MAX_TOKENS"))));
        assertThat(client.generate("PROMPT_MARKER").outcome())
                .isEqualTo(ChatGeminiResult.Outcome.INVALID_RESPONSE);

        server.resetAll();
        server.stubFor(
                post(anyUrl())
                        .willReturn(
                                okJson(
                                        "{\"candidates\":[{\"finishReason\":\"STOP\","
                                                + "\"content\":{}}]}")));
        assertThat(client.generate("PROMPT_MARKER").outcome())
                .isEqualTo(ChatGeminiResult.Outcome.INVALID_RESPONSE);
    }

    @Test
    void safetyFinishAndPromptBlockAreBlocked() throws Exception {
        for (String finish : List.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT")) {
            server.resetAll();
            server.stubFor(post(anyUrl()).willReturn(okJson(envelope("{}", finish))));
            assertThat(client.generate("PROMPT_MARKER").outcome())
                    .isEqualTo(ChatGeminiResult.Outcome.BLOCKED);
        }
        server.resetAll();
        server.stubFor(
                post(anyUrl())
                        .willReturn(okJson("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")));
        assertThat(client.generate("PROMPT_MARKER").outcome())
                .isEqualTo(ChatGeminiResult.Outcome.BLOCKED);
    }

    @Test
    void thoughtPartsAreIgnoredAndTextPartsJoined() throws Exception {
        String body =
                mapper.writeValueAsString(
                        Map.of(
                                "candidates",
                                List.of(
                                        Map.of(
                                                "finishReason",
                                                "STOP",
                                                "content",
                                                Map.of(
                                                        "parts",
                                                        List.of(
                                                                Map.of(
                                                                        "thought", true, "text",
                                                                        "ignore"),
                                                                Map.of("text", "{\"intent\":"),
                                                                Map.of(
                                                                        "text",
                                                                        "\"rain_top\"}")))))));
        server.stubFor(post(anyUrl()).willReturn(okJson(body)));

        ChatGeminiResult result = client.generate("PROMPT_MARKER");

        assertThat(result.outcome()).isEqualTo(ChatGeminiResult.Outcome.ANSWER);
        assertThat(result.text()).isEqualTo("{\"intent\":\"rain_top\"}");
    }

    @Test
    void oversizedBodyIsInvalidResponse() {
        properties.setMaxResponseBytes(512);
        client = newClient();
        server.stubFor(
                post(anyUrl()).willReturn(okJson("{\"data\":\"" + "x".repeat(2000) + "\"}")));

        assertThat(client.generate("PROMPT_MARKER").outcome())
                .isEqualTo(ChatGeminiResult.Outcome.INVALID_RESPONSE);
        server.verify(1, postRequestedFor(anyUrl()));
    }

    @Test
    void disabledOrMissingKeyOrModelNeverTouchesNetwork() {
        properties.setEnabled(false);
        client = newClient();
        assertThat(client.generate("PROMPT_MARKER").outcome())
                .isEqualTo(ChatGeminiResult.Outcome.DISABLED);

        properties.setEnabled(true);
        properties.setApiKey("");
        client = newClient();
        assertThat(client.generate("PROMPT_MARKER").outcome())
                .isEqualTo(ChatGeminiResult.Outcome.DISABLED);

        properties.setApiKey("test-secret");
        properties.setModel("");
        client = newClient();
        assertThat(client.generate("PROMPT_MARKER").outcome())
                .isEqualTo(ChatGeminiResult.Outcome.DISABLED);

        server.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void tokensDefaultToZeroWhenUsageMetadataIsMissingOrInvalid() throws Exception {
        String noUsage =
                mapper.writeValueAsString(
                        Map.of(
                                "candidates",
                                List.of(
                                        Map.of(
                                                "finishReason",
                                                "STOP",
                                                "content",
                                                Map.of("parts", List.of(Map.of("text", "{}")))))));
        server.stubFor(post(anyUrl()).willReturn(okJson(noUsage)));
        assertThat(client.generate("PROMPT_MARKER").tokensUsed()).isZero();

        for (Object bad : List.of("abc", -5, 3.5, 2147483648L)) {
            server.resetAll();
            String body =
                    mapper.writeValueAsString(
                            Map.of(
                                    "usageMetadata",
                                    Map.of("totalTokenCount", bad),
                                    "candidates",
                                    List.of(
                                            Map.of(
                                                    "finishReason",
                                                    "STOP",
                                                    "content",
                                                    Map.of(
                                                            "parts",
                                                            List.of(Map.of("text", "{}")))))));
            server.stubFor(post(anyUrl()).willReturn(okJson(body)));
            assertThat(client.generate("PROMPT_MARKER").tokensUsed()).isZero();
        }
    }
}
