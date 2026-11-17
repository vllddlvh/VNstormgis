package vn.weathergis.chat.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import vn.weathergis.alert.ai.GeminiProperties;
import vn.weathergis.chat.config.ChatReplyProperties;

/**
 * Chat-side Gemini HTTP client (W5-04 §8.1, hardened W4 GeminiClient pattern; the W4 class itself
 * is untouched). Shares {@code weathergis.ai.gemini.*} configuration, adds chat-only
 * maxOutputTokens and usageMetadata token parsing. No in-request retry; invoke outside any database
 * transaction. Disabled or missing key/model yields DISABLED without any network call (D5
 * fallback-first).
 */
@Service
public class ChatGeminiClient {

    private static final Logger LOG = LoggerFactory.getLogger(ChatGeminiClient.class);
    private static final Set<String> BLOCKED_FINISH =
            Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT");

    private final GeminiProperties properties;
    private final ChatReplyProperties replyProperties;
    private final ChatPromptBuilder promptBuilder;
    private final ObjectMapper mapper;
    private final WebClient client;

    public ChatGeminiClient(
            GeminiProperties properties,
            ChatReplyProperties replyProperties,
            ChatPromptBuilder promptBuilder,
            ObjectMapper mapper) {
        this.properties = properties;
        this.replyProperties = replyProperties;
        this.promptBuilder = promptBuilder;
        this.mapper =
                mapper.copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.replyProperties.validate();
        if (usable()) {
            properties.validate();
            HttpClient http =
                    HttpClient.create()
                            .disableRetry(true)
                            .followRedirect(false)
                            .option(
                                    ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                    Math.toIntExact(properties.getConnectTimeout().toMillis()));
            this.client =
                    WebClient.builder()
                            .baseUrl(properties.getBaseUrl())
                            .clientConnector(new ReactorClientHttpConnector(http))
                            .codecs(
                                    c ->
                                            c.defaultCodecs()
                                                    .maxInMemorySize(
                                                            properties.getMaxResponseBytes()))
                            .build();
        } else {
            this.client = null;
        }
    }

    /** Invoke on a non-reactor worker thread, outside a database transaction. */
    public ChatGeminiResult generate(String userPrompt) {
        long start = System.nanoTime();
        if (!usable() || client == null) {
            return result(null, ChatGeminiResult.Outcome.DISABLED, null, 0, start, null);
        }
        Map<String, Object> body =
                Map.of(
                        "systemInstruction",
                        Map.of("parts", new Object[] {Map.of("text", promptBuilder.instruction())}),
                        "contents",
                        new Object[] {
                            Map.of(
                                    "role",
                                    "user",
                                    "parts",
                                    new Object[] {Map.of("text", userPrompt)})
                        },
                        "generationConfig",
                        Map.of(
                                "temperature",
                                0.2,
                                "responseMimeType",
                                "application/json",
                                "candidateCount",
                                1,
                                "maxOutputTokens",
                                replyProperties.getMaxOutputTokens()));
        try {
            Response response =
                    client.post()
                            .uri("/v1beta/models/{model}:generateContent", properties.getModel())
                            .header("x-goog-api-key", properties.getApiKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(body)
                            .exchangeToMono(
                                    r -> {
                                        int status = r.statusCode().value();
                                        if (status < 200 || status >= 300) {
                                            return r.releaseBody()
                                                    .thenReturn(
                                                            new Response(
                                                                    null,
                                                                    httpOutcome(status),
                                                                    null,
                                                                    0,
                                                                    status));
                                        }
                                        return r.bodyToMono(byte[].class)
                                                .defaultIfEmpty(new byte[0])
                                                .map(bytes -> parse(bytes, status));
                                    })
                            .timeout(properties.getRequestTimeout())
                            .block();
            return result(
                    response.text(),
                    response.outcome(),
                    response.model(),
                    response.tokens(),
                    start,
                    response.status());
        } catch (RuntimeException e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            ChatGeminiResult.Outcome outcome =
                    root instanceof TimeoutException
                            ? ChatGeminiResult.Outcome.TIMEOUT
                            : root instanceof DataBufferLimitException
                                    ? ChatGeminiResult.Outcome.INVALID_RESPONSE
                                    : ChatGeminiResult.Outcome.NETWORK_ERROR;
            return result(null, outcome, null, 0, start, null);
        }
    }

    private boolean usable() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank()
                && properties.getModel() != null
                && !properties.getModel().isBlank();
    }

    private Response parse(byte[] bytes, int status) {
        try {
            JsonNode root = mapper.readTree(bytes);
            if (root == null) {
                return new Response(
                        null, ChatGeminiResult.Outcome.INVALID_RESPONSE, null, 0, status);
            }
            String model =
                    root.path("modelVersion").isTextual()
                            ? root.path("modelVersion").asText()
                            : null;
            int tokens = tokens(root.path("usageMetadata").path("totalTokenCount"));
            JsonNode feedback = root.path("promptFeedback").path("blockReason");
            if (feedback.isTextual()
                    && !feedback.asText().isBlank()
                    && !feedback.asText().equals("BLOCK_REASON_UNSPECIFIED")) {
                return new Response(null, ChatGeminiResult.Outcome.BLOCKED, model, 0, status);
            }
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.size() != 1) {
                return new Response(
                        null, ChatGeminiResult.Outcome.INVALID_RESPONSE, model, 0, status);
            }
            JsonNode candidate = candidates.get(0);
            String finish = candidate.path("finishReason").asText();
            if (BLOCKED_FINISH.contains(finish)) {
                return new Response(null, ChatGeminiResult.Outcome.BLOCKED, model, 0, status);
            }
            if (!finish.equals("STOP") || !candidate.path("content").path("parts").isArray()) {
                return new Response(
                        null, ChatGeminiResult.Outcome.INVALID_RESPONSE, model, 0, status);
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false) && part.path("text").isTextual()) {
                    text.append(part.path("text").asText());
                }
            }
            JsonNode output = mapper.readTree(text.toString());
            if (output == null || !output.isObject()) {
                return new Response(
                        null, ChatGeminiResult.Outcome.INVALID_RESPONSE, model, 0, status);
            }
            return new Response(
                    text.toString(), ChatGeminiResult.Outcome.ANSWER, model, tokens, status);
        } catch (Exception e) {
            return new Response(null, ChatGeminiResult.Outcome.INVALID_RESPONSE, null, 0, status);
        }
    }

    private static int tokens(JsonNode node) {
        if (!node.isIntegralNumber()) {
            return 0;
        }
        long value = node.asLong();
        return value >= 0 && value <= Integer.MAX_VALUE ? (int) value : 0;
    }

    private ChatGeminiResult.Outcome httpOutcome(int status) {
        if (status == 401 || status == 403) {
            return ChatGeminiResult.Outcome.UPSTREAM_AUTH_ERROR;
        }
        if (status == 429) {
            return ChatGeminiResult.Outcome.RATE_LIMITED;
        }
        return ChatGeminiResult.Outcome.UPSTREAM_HTTP_ERROR;
    }

    private ChatGeminiResult result(
            String text,
            ChatGeminiResult.Outcome outcome,
            String model,
            int tokens,
            long start,
            Integer status) {
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        LOG.info(
                "Gemini chat model={} prompt={} durationMs={} outcome={} status={} tokens={}",
                model,
                ChatPromptBuilder.VERSION,
                duration.toMillis(),
                outcome,
                status,
                tokens);
        return new ChatGeminiResult(outcome, text, model, tokens, duration, status);
    }

    private record Response(
            String text, ChatGeminiResult.Outcome outcome, String model, int tokens, int status) {}
}
