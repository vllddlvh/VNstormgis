package vn.weathergis.chat.port;

import vn.weathergis.chat.context.ChatContextResult;
import vn.weathergis.chat.intent.IntentClassification;

/**
 * Reply-generation seam (contract §3.2, W5-04). The orchestrator calls this port OUTSIDE any
 * database transaction. {@code question} is the validated user text; it travels to the prompt as
 * neutralized data only (contract §4.1) and is never logged or stored by the generator.
 */
public interface ChatReplyGenerator {

    ChatReplyResult generate(
            IntentClassification classification, ChatContextResult context, String question);
}
