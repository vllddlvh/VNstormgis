package vn.weathergis.chat.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.weathergis.alert.ai.GeminiProperties;
import vn.weathergis.chat.config.ChatContextProperties;
import vn.weathergis.chat.config.ChatReplyProperties;
import vn.weathergis.chat.context.ChatContextOutcome;
import vn.weathergis.chat.context.FactualContext;
import vn.weathergis.chat.context.FactualContextSerializer;
import vn.weathergis.chat.intent.ChatIntent;

/**
 * Explicitly opt-in live check (W5-04 §8.3, D8): synthetic FIXTURE data only, no publication
 * side-effect. Requires GEMINI_LIVE_SMOKE=true plus GEMINI_API_KEY/GEMINI_MODEL from the
 * environment. Records model, tokens, latency and the guardrail verdict — accept AND reject are
 * both valid recorded outcomes; this is not an end-to-end or quota proof (W5-07/08, W5-05).
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_LIVE_SMOKE", matches = "true")
class ChatGeminiLiveSmokeTest {

    @Test
    void groundedRainReplyAgainstLiveGeminiWithSyntheticFixture() {
        var props = new GeminiProperties();
        props.setEnabled(true);
        props.setApiKey(System.getenv("GEMINI_API_KEY"));
        props.setModel(System.getenv("GEMINI_MODEL"));
        var mapper =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var serializer = new FactualContextSerializer(mapper);
        var contextProperties = new ChatContextProperties();
        var replyProperties = new ChatReplyProperties();
        var promptBuilder = new ChatPromptBuilder(serializer, contextProperties);
        var client = new ChatGeminiClient(props, replyProperties, promptBuilder, mapper);
        var guardrail = new ChatReplyGuardrail(mapper);
        var renderer = new ChatReplyRenderer(replyProperties);

        Instant now = Instant.parse("2026-11-17T09:00:00Z");
        FactualContext.StationObservation station =
                new FactualContext.StationObservation(
                        "FIXTURE",
                        "Trạm kiểm thử tổng hợp",
                        List.of(108.2, 16.0),
                        now.minusSeconds(600),
                        false,
                        25.0,
                        70.0,
                        1000.0,
                        5.0,
                        8.0,
                        90.0,
                        82.4,
                        120.0,
                        "Rain",
                        "FIXTURE mưa vừa",
                        Map.of("rain_1h", "mm", "wind_speed", "m/s"));
        FactualContext draft =
                new FactualContext(
                        FactualContext.SCHEMA_VERSION,
                        ChatIntent.RAIN_TOP,
                        now,
                        ChatContextOutcome.CONTEXT_READY,
                        null,
                        List.of(station),
                        List.of(),
                        null,
                        new FactualContext.ReferencedEntities(
                                List.of("FIXTURE"), List.of(), List.of()),
                        new FactualContext.ContextMeta(60, 0, 0, false),
                        0);
        FactualContext context = draft.withSizeBytes(serializer.payloadBytes(draft));
        String question = "Khu vực nào đang có mưa to nhất?";

        String prompt = promptBuilder.build(context, question);
        ChatGeminiResult result = client.generate(prompt);
        String verdict = "not_called";
        String reply = null;
        if (result.outcome() == ChatGeminiResult.Outcome.ANSWER) {
            ChatReplyGuardrail.Verdict v =
                    guardrail.validate(context, result.text(), ChatIntent.RAIN_TOP);
            if (v instanceof ChatReplyGuardrail.Accepted accepted) {
                verdict = "accepted";
                reply = renderer.renderVerified(context, accepted.selection(), 0);
            } else {
                verdict = "rejected:" + ((ChatReplyGuardrail.Rejected) v).reason();
            }
        }
        System.out.printf(
                "W5-04 LIVE SMOKE [FIXTURE] date=%s model=%s modelVersion=%s outcome=%s tokens=%d"
                        + " latencyMs=%d verdict=%s reply=%s%n",
                Instant.now(),
                props.getModel(),
                result.model(),
                result.outcome(),
                result.tokensUsed(),
                result.duration().toMillis(),
                verdict,
                reply);

        assertThat(result.outcome()).isNotNull();
        assertThat(result.tokensUsed()).isGreaterThanOrEqualTo(0);
        if (reply != null) {
            assertThat(reply.length()).isLessThanOrEqualTo(replyProperties.getReplyMaxChars());
            assertThat(reply).contains("82.4 mm");
        }
    }
}
