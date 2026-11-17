package vn.weathergis.chat.port;

/**
 * Interim reply bean between W5-03 and W5-04: AI is not wired yet, so every question yields {@link
 * ChatReplyResult.Unavailable} → 503 CHT_503_AI_UNAVAILABLE with no history and no fabricated
 * reply. W5-04 MUST replace this bean (Gemini + guardrail + template); the interim behaviour is
 * documented in OpenAPI and the verification report so nobody mistakes it for the finished feature.
 */
public class InterimChatReplyGenerator implements ChatReplyGenerator {

    @Override
    public ChatReplyResult generate(
            vn.weathergis.chat.intent.IntentClassification classification,
            vn.weathergis.chat.context.ChatContextResult context) {
        return new ChatReplyResult.Unavailable();
    }
}
