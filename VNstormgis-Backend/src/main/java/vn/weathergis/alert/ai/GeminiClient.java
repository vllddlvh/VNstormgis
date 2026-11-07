package vn.weathergis.alert.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import vn.weathergis.alert.ai.GeminiGenerationResult.GeminiAlertDraft;
import vn.weathergis.alert.ai.GeminiGenerationResult.Outcome;
import vn.weathergis.alert.domain.AlertJobStatus;
import vn.weathergis.alert.dto.AlertGenerationJobView;

@Service
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiClient {
    private static final Logger LOG = LoggerFactory.getLogger(GeminiClient.class);
    private final GeminiProperties properties;
    private final AlertPromptBuilder promptBuilder;
    private final ObjectMapper mapper;
    private final String instruction;
    private final WebClient client;

    public GeminiClient(
            GeminiProperties properties, AlertPromptBuilder promptBuilder, ObjectMapper mapper)
            throws java.io.IOException {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.mapper =
                mapper.copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(
                                com.fasterxml.jackson.databind.DeserializationFeature
                                        .FAIL_ON_TRAILING_TOKENS);
        this.instruction =
                new ClassPathResource("prompts/proactive-alert-v1.txt")
                        .getContentAsString(StandardCharsets.UTF_8);
        properties.validate();
        if (properties.isEnabled()) {
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
        } else this.client = null;
    }

    /** Invoke on a non-reactor worker thread, outside a database transaction. */
    public GeminiGenerationResult generate(AlertGenerationJobView job) {
        long start = System.nanoTime();
        if (job == null || job.status() != AlertJobStatus.PENDING)
            return result(job, null, Outcome.SKIPPED, null, start, null);
        if (!properties.isEnabled()) return result(job, null, Outcome.DISABLED, null, start, null);
        String prompt;
        try {
            prompt = promptBuilder.build(job);
        } catch (RuntimeException e) {
            return result(job, null, Outcome.INVALID_FACTUAL_DATA, null, start, null);
        }
        Map<String, Object> body =
                Map.of(
                        "systemInstruction",
                        Map.of("parts", new Object[] {Map.of("text", instruction)}),
                        "contents",
                        new Object[] {
                            Map.of("role", "user", "parts", new Object[] {Map.of("text", prompt)})
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
                                properties.getMaxOutputTokens()));
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
                                        if (status < 200 || status >= 300)
                                            return r.releaseBody()
                                                    .thenReturn(
                                                            new Response(
                                                                    null,
                                                                    httpOutcome(status),
                                                                    null,
                                                                    status));
                                        return r.bodyToMono(byte[].class)
                                                .defaultIfEmpty(new byte[0])
                                                .map(bytes -> parse(bytes, status));
                                    })
                            .timeout(properties.getRequestTimeout())
                            .block();
            return result(
                    job,
                    response.draft(),
                    response.outcome(),
                    response.model(),
                    start,
                    response.status());
        } catch (RuntimeException e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            Outcome outcome =
                    root instanceof TimeoutException
                            ? Outcome.TIMEOUT
                            : (root
                                            instanceof
                                            org.springframework.core.io.buffer
                                                    .DataBufferLimitException
                                    ? Outcome.INVALID_RESPONSE
                                    : Outcome.NETWORK_ERROR);
            return result(job, null, outcome, null, start, null);
        }
    }

    private Response parse(byte[] bytes, int status) {
        try {
            JsonNode root = mapper.readTree(bytes);
            if (root == null) return new Response(null, Outcome.INVALID_RESPONSE, null, status);
            String model =
                    root.path("modelVersion").isTextual()
                            ? root.path("modelVersion").asText()
                            : null;
            JsonNode feedback = root.path("promptFeedback").path("blockReason");
            if (feedback.isTextual()
                    && !feedback.asText().isBlank()
                    && !feedback.asText().equals("BLOCK_REASON_UNSPECIFIED"))
                return new Response(null, Outcome.BLOCKED, model, status);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.size() != 1)
                return new Response(null, Outcome.INVALID_RESPONSE, model, status);
            JsonNode candidate = candidates.get(0);
            String finish = candidate.path("finishReason").asText();
            if (java.util.Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT")
                    .contains(finish)) return new Response(null, Outcome.BLOCKED, model, status);
            if (!finish.equals("STOP") || !candidate.path("content").path("parts").isArray())
                return new Response(null, Outcome.INVALID_RESPONSE, model, status);
            StringBuilder text = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts"))
                if (!part.path("thought").asBoolean(false) && part.path("text").isTextual())
                    text.append(part.path("text").asText());
            JsonNode draft = mapper.readTree(text.toString());
            if (draft == null
                    || !draft.isObject()
                    || draft.size() != 2
                    || !draft.path("title").isTextual()
                    || !draft.path("ai_explanation").isTextual())
                return new Response(null, Outcome.INVALID_RESPONSE, model, status);
            String title = draft.path("title").asText(),
                    explanation = draft.path("ai_explanation").asText();
            if (title.isBlank()
                    || title.length() > 255
                    || explanation.isBlank()
                    || explanation.length() > 4000)
                return new Response(null, Outcome.INVALID_RESPONSE, model, status);
            return new Response(
                    new GeminiAlertDraft(title, explanation), Outcome.DRAFT, model, status);
        } catch (Exception e) {
            return new Response(null, Outcome.INVALID_RESPONSE, null, status);
        }
    }

    private Outcome httpOutcome(int status) {
        if (status == 401 || status == 403) return Outcome.UPSTREAM_AUTH_ERROR;
        if (status == 429) return Outcome.RATE_LIMITED;
        return Outcome.UPSTREAM_HTTP_ERROR;
    }

    private GeminiGenerationResult result(
            AlertGenerationJobView job,
            GeminiAlertDraft draft,
            Outcome outcome,
            String model,
            long start,
            Integer status) {
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        LOG.info(
                "Gemini draft job={} cycle={} model={} prompt={} durationMs={} outcome={} status={}",
                job == null ? null : job.jobId(),
                job == null ? null : job.evaluationCycleId(),
                properties.getModel(),
                AlertPromptBuilder.VERSION,
                duration.toMillis(),
                outcome,
                status);
        return new GeminiGenerationResult(
                job, draft, outcome, model, AlertPromptBuilder.VERSION, duration, status);
    }

    private record Response(GeminiAlertDraft draft, Outcome outcome, String model, int status) {}
}
