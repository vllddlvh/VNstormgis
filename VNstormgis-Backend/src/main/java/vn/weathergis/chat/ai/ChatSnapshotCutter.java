package vn.weathergis.chat.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.chat.config.ChatContextProperties;
import vn.weathergis.chat.config.ChatReplyProperties;
import vn.weathergis.chat.context.FactualContext;
import vn.weathergis.chat.context.FactualContextSerializer;
import vn.weathergis.chat.intent.ChatIntent;

/**
 * Controlled alert-snapshot cut for chat prompts and replies only (W5-04 §7, DL-17 chat side).
 * Consumes the W5-02 {@code violation_order} as-is (severity rank, source_timestamp ASC nulls-last,
 * violation_key ASC) and offers a deterministic ladder top-N → 3 → 1 with a grounded remainder
 * count. The W4 alert publication pipeline and its 4000-char limit are never touched.
 */
@Component
public class ChatSnapshotCutter {

    public record CutCandidate(
            FactualContext context, int remainingViolations, boolean fitsBlock) {}

    private final FactualContextSerializer serializer;
    private final ChatContextProperties contextProperties;
    private final ChatReplyProperties replyProperties;

    public ChatSnapshotCutter(
            FactualContextSerializer serializer,
            ChatContextProperties contextProperties,
            ChatReplyProperties replyProperties) {
        this.serializer = serializer;
        this.contextProperties = contextProperties;
        this.replyProperties = replyProperties;
    }

    public List<CutCandidate> candidates(FactualContext context) {
        if (context.intent() != ChatIntent.ALERT_EXPLAIN
                || context.alert() == null
                || !context.alert().snapshotPresent()
                || context.alert().factualSnapshot() == null
                || fitsBlock(context)) {
            return List.of(new CutCandidate(context, 0, fitsBlock(context)));
        }
        int total = context.alert().factualSnapshot().violations().size();
        List<CutCandidate> candidates = new ArrayList<>();
        candidates.add(new CutCandidate(context, 0, false));
        Set<Integer> seenCounts = new HashSet<>();
        seenCounts.add(total);
        for (int step : ladder()) {
            int count = Math.min(step, total);
            if (!seenCounts.add(count)) {
                continue;
            }
            candidates.add(new CutCandidate(trim(context, count), total - count, true));
        }
        return List.copyOf(candidates);
    }

    private List<Integer> ladder() {
        int top = replyProperties.getSnapshotCutTopN();
        TreeSet<Integer> steps = new TreeSet<>(Comparator.reverseOrder());
        for (int step : new int[] {top, 3, 1}) {
            if (step <= top) {
                steps.add(step);
            }
        }
        return List.copyOf(steps);
    }

    private FactualContext trim(FactualContext context, int count) {
        FactualContext.AlertContext alert = context.alert();
        List<FactualContext.ViolationOrder> selectedOrder =
                alert.violationOrder().subList(0, count);
        Map<String, ViolationSnapshot> byKey = new LinkedHashMap<>();
        for (ViolationSnapshot violation : alert.factualSnapshot().violations()) {
            if (byKey.put(violation.violationKey(), violation) != null) {
                throw new IllegalStateException("Duplicate violation key in snapshot");
            }
        }
        List<ViolationSnapshot> selected = new ArrayList<>();
        for (FactualContext.ViolationOrder order : selectedOrder) {
            ViolationSnapshot violation = byKey.get(order.violationKey());
            if (violation == null) {
                throw new IllegalStateException("Violation order references unknown key");
            }
            selected.add(violation);
        }
        AlertFactualSnapshot snapshot = alert.factualSnapshot();
        AlertFactualSnapshot trimmedSnapshot =
                new AlertFactualSnapshot(
                        snapshot.locationId(),
                        snapshot.locationCode(),
                        snapshot.locationName(),
                        snapshot.coordinates(),
                        snapshot.evaluationCycleId(),
                        snapshot.evaluatedAt(),
                        snapshot.representativeThreshold(),
                        selected);
        long snapshotBytes = serializer.measureBytes(trimmedSnapshot);
        FactualContext.AlertContext trimmedAlert =
                new FactualContext.AlertContext(
                        alert.alertId(),
                        alert.status(),
                        alert.severityLevel(),
                        alert.triggeredAt(),
                        alert.resolvedAt(),
                        true,
                        trimmedSnapshot,
                        selectedOrder,
                        snapshotBytes > contextProperties.getMaxContextBytes(),
                        snapshotBytes);
        FactualContext draft =
                new FactualContext(
                        context.schemaVersion(),
                        context.intent(),
                        context.generatedAt(),
                        context.outcome(),
                        context.noDataReason(),
                        context.stations(),
                        context.storms(),
                        trimmedAlert,
                        context.referencedEntities(),
                        context.meta(),
                        0);
        boolean oversize = serializer.payloadBytes(draft) > contextProperties.getMaxContextBytes();
        FactualContext sized =
                new FactualContext(
                        draft.schemaVersion(),
                        draft.intent(),
                        draft.generatedAt(),
                        draft.outcome(),
                        draft.noDataReason(),
                        draft.stations(),
                        draft.storms(),
                        draft.alert(),
                        draft.referencedEntities(),
                        new FactualContext.ContextMeta(
                                context.meta().staleThresholdMinutes(),
                                context.meta().omittedStationCount(),
                                context.meta().omittedStormCount(),
                                oversize),
                        0);
        return sized.withSizeBytes(serializer.payloadBytes(sized));
    }

    private boolean fitsBlock(FactualContext context) {
        return serializer.payloadBytes(context) <= contextProperties.getMaxContextBytes();
    }
}
