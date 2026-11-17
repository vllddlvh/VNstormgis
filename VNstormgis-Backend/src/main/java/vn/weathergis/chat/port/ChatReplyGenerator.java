package vn.weathergis.chat.port;

import vn.weathergis.chat.context.ChatContextResult;
import vn.weathergis.chat.intent.IntentClassification;

/**
 * Reply-generation seam handed to W5-04 (contract §3.2). W5-03 ships an interim bean that returns
 * {@link ChatReplyResult.Unavailable} (HTTP 503, no history, no invented content); W5-04 replaces
 * it with Gemini + guardrail + deterministic template. The orchestrator calls this port OUTSIDE any
 * database transaction.
 */
public interface ChatReplyGenerator {

    ChatReplyResult generate(IntentClassification classification, ChatContextResult context);
}
