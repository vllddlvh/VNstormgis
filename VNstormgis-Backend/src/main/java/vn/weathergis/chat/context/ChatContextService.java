package vn.weathergis.chat.context;

/**
 * Bounded factual-context retrieval for anonymous chat (W5-02). Reads PostGIS directly — never the
 * Redis cache — so grounding uses the newest stored data at question time. Real database failures
 * propagate to the caller (W5-03 → 500); missing data is a typed outcome, not an error.
 */
public interface ChatContextService {

    ChatContextResult retrieve(ChatContextRequest request);
}
