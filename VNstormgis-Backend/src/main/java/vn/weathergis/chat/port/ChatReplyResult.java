package vn.weathergis.chat.port;

/**
 * Typed reply outcome. {@link Reply} is verified or deterministic-fallback content safe to store
 * and publish; {@link Unavailable} means the dependency cannot produce even a fallback (contract
 * §2.5 → 503 CHT_503_AI_UNAVAILABLE, nothing written).
 */
public sealed interface ChatReplyResult permits ChatReplyResult.Reply, ChatReplyResult.Unavailable {

    record Reply(String content, boolean isFallback, int tokensUsed) implements ChatReplyResult {}

    record Unavailable() implements ChatReplyResult {}
}
