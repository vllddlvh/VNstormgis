package vn.weathergis.chat.ai;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.weathergis.chat.config.ChatReplyProperties;
import vn.weathergis.chat.context.ChatContextOutcome;
import vn.weathergis.chat.context.ChatContextResult;
import vn.weathergis.chat.context.FactualContext;
import vn.weathergis.chat.intent.ChatIntent;
import vn.weathergis.chat.intent.IntentClassification;
import vn.weathergis.chat.port.ChatReplyGenerator;
import vn.weathergis.chat.port.ChatReplyResult;

/**
 * Real reply port (W5-04): grounded Gemini call + closed-grammar guardrail + deterministic
 * Vietnamese renderer, with D5 fallback-first routing (§6.1). Called by the orchestrator OUTSIDE
 * any transaction; never logs or stores question/reply/prompt content at INFO. {@link
 * ChatReplyResult.Unavailable} is the narrow §3.3 branch only (broken input invariants); every
 * provider fault, guardrail rejection and missing-data case yields a 200 fallback. Unexpected
 * runtime failures propagate to the global 500 handler — never a fake reply.
 */
@Service
public class GeminiChatReplyGenerator implements ChatReplyGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatReplyGenerator.class);

    private final ChatGeminiClient client;
    private final ChatPromptBuilder promptBuilder;
    private final ChatSnapshotCutter cutter;
    private final ChatReplyGuardrail guardrail;
    private final ChatReplyRenderer renderer;
    private final ChatReplyProperties properties;
    private final MeterRegistry meterRegistry;

    public GeminiChatReplyGenerator(
            ChatGeminiClient client,
            ChatPromptBuilder promptBuilder,
            ChatSnapshotCutter cutter,
            ChatReplyGuardrail guardrail,
            ChatReplyRenderer renderer,
            ChatReplyProperties properties,
            MeterRegistry meterRegistry) {
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.cutter = cutter;
        this.guardrail = guardrail;
        this.renderer = renderer;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ChatReplyResult generate(
            IntentClassification classification, ChatContextResult contextResult, String question) {
        long start = System.nanoTime();
        ChatIntent intent = classification.intent();
        Trace trace = new Trace();
        ChatReplyResult result;
        try {
            result = route(intent, contextResult, question, trace);
        } catch (IllegalStateException invariant) {
            // Renderer/cutter input invariants are the narrow Unavailable branch (§3.3).
            trace.fallbackReason = "invariant";
            result = new ChatReplyResult.Unavailable();
        }
        if (result instanceof ChatReplyResult.Reply reply && reply.isFallback()) {
            meterRegistry
                    .counter(
                            "weathergis.chat.fallback",
                            "intent",
                            intent.wireName(),
                            "reason",
                            trace.fallbackReason)
                    .increment();
        }
        log.info(
                "chat reply intent={} outcome={} clientOutcome={} verdict={} fallbackReason={}"
                        + " model={} promptVersion={} durationMs={} tokens={} violationsSelected={}"
                        + " violationsRemaining={}",
                intent.wireName(),
                contextResult.outcome(),
                trace.clientOutcome,
                trace.verdict,
                trace.fallbackReason,
                trace.model,
                ChatPromptBuilder.VERSION,
                Duration.ofNanos(System.nanoTime() - start).toMillis(),
                trace.tokens,
                trace.violationsSelected,
                trace.violationsRemaining);
        return result;
    }

    private ChatReplyResult route(
            ChatIntent intent, ChatContextResult contextResult, String question, Trace trace) {
        ChatContextOutcome outcome = contextResult.outcome();
        switch (outcome) {
            case OUT_OF_SCOPE:
                trace.fallbackReason = "out_of_scope";
                return fallback(renderer.renderHonest(outcome, null));
            case NO_DATA:
                trace.fallbackReason = "no_data";
                return fallback(renderer.renderHonest(outcome, contextResult.noDataReason()));
            case NO_SNAPSHOT:
                trace.fallbackReason = "no_snapshot";
                return fallback(renderer.renderHonest(outcome, null));
            case ALERT_NOT_FOUND:
                // Orchestration maps 404 before the port; reaching here is a broken invariant.
                return new ChatReplyResult.Unavailable();
            case CONTEXT_READY:
                return contextReady(intent, contextResult.context(), question, trace);
            default:
                return new ChatReplyResult.Unavailable();
        }
    }

    private ChatReplyResult contextReady(
            ChatIntent intent, FactualContext context, String question, Trace trace) {
        if (context == null || context.intent() != intent) {
            return new ChatReplyResult.Unavailable();
        }
        if (intent == ChatIntent.ALERT_EXPLAIN
                && (context.alert() == null
                        || !context.alert().snapshotPresent()
                        || context.alert().factualSnapshot() == null)) {
            return new ChatReplyResult.Unavailable();
        }
        List<ChatSnapshotCutter.CutCandidate> candidates = cutter.candidates(context);
        ChatSnapshotCutter.CutCandidate selected = null;
        ChatReplyRenderer.RenderedReply rendered = null;
        for (ChatSnapshotCutter.CutCandidate candidate : candidates) {
            if (!candidate.fitsBlock()) {
                continue;
            }
            ChatReplyRenderer.RenderedReply attempt =
                    renderer.renderBestEffort(candidate.context(), candidate.remainingViolations());
            if (!attempt.minimal()) {
                selected = candidate;
                rendered = attempt;
                break;
            }
        }
        if (selected == null) {
            ChatSnapshotCutter.CutCandidate last = candidates.get(candidates.size() - 1);
            trace.fallbackReason = "oversize";
            trace.violationsRemaining = last.remainingViolations();
            return fallback(
                    renderer.renderBestEffort(last.context(), last.remainingViolations()).text());
        }
        trace.violationsSelected =
                selected.context().alert() == null
                        ? 0
                        : selected.context().alert().factualSnapshot().violations().size();
        trace.violationsRemaining = selected.remainingViolations();
        String fallbackText = rendered.text();

        String prompt = promptBuilder.build(selected.context(), question);
        log.debug("chat prompt version={} text={}", ChatPromptBuilder.VERSION, prompt);
        ChatGeminiResult ai = client.generate(prompt);
        trace.clientOutcome = ai.outcome();
        trace.model = ai.model();
        log.debug("chat response text={}", ai.text());
        if (ai.outcome() != ChatGeminiResult.Outcome.ANSWER) {
            trace.fallbackReason =
                    ai.outcome() == ChatGeminiResult.Outcome.DISABLED ? "disabled" : "ai_error";
            return fallback(fallbackText);
        }
        ChatReplyGuardrail.Verdict verdict =
                guardrail.validate(selected.context(), ai.text(), intent);
        if (verdict instanceof ChatReplyGuardrail.Rejected rejected) {
            trace.verdict = "rejected";
            meterRegistry
                    .counter(
                            "weathergis.chat.guardrail.rejected",
                            "intent",
                            intent.wireName(),
                            "reason",
                            rejected.reason())
                    .increment();
            trace.fallbackReason = "guardrail_reject";
            return fallback(fallbackText);
        }
        trace.verdict = "accepted";
        ChatReplyGuardrail.Selection selection =
                ((ChatReplyGuardrail.Accepted) verdict).selection();
        int renderedViolations =
                intent == ChatIntent.ALERT_EXPLAIN ? selection.violationKeys().size() : 0;
        int remainingForReply =
                selected.remainingViolations() + trace.violationsSelected - renderedViolations;
        String verified = renderer.renderVerified(selected.context(), selection, remainingForReply);
        if (verified.length() > properties.getReplyMaxChars()) {
            trace.fallbackReason = "oversize";
            return fallback(fallbackText);
        }
        trace.tokens = ai.tokensUsed();
        return new ChatReplyResult.Reply(verified, false, ai.tokensUsed());
    }

    private static ChatReplyResult.Reply fallback(String content) {
        return new ChatReplyResult.Reply(content, true, 0);
    }

    private static final class Trace {
        private ChatGeminiResult.Outcome clientOutcome;
        private String verdict = "not_called";
        private String fallbackReason = "none";
        private String model;
        private int tokens;
        private int violationsSelected;
        private int violationsRemaining;
    }
}
