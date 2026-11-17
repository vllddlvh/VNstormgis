package vn.weathergis.chat.context;

import vn.weathergis.chat.intent.IntentClassification;

/**
 * Bounded factual-context retrieval for anonymous chat (W5-02). Reads PostGIS directly — never the
 * Redis cache — so grounding uses the newest stored data at question time. Real database failures
 * propagate to the caller (W5-03 → 500); missing data is a typed outcome, not an error.
 */
public interface ChatContextService {

    ChatContextResult retrieve(ChatContextRequest request);

    /**
     * W5-03 additive overload: the orchestrator classifies once and reuses the same classification
     * for retrieval and the reply port, so the deterministic classifier never runs twice per
     * question. Behaviour is identical to {@link #retrieve(ChatContextRequest)} with the given
     * classification.
     */
    ChatContextResult retrieve(ChatContextRequest request, IntentClassification classification);
}
