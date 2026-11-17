package vn.weathergis.chat.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.chat.config.ChatContextProperties;
import vn.weathergis.chat.config.ChatReplyProperties;
import vn.weathergis.chat.context.ChatContextOutcome;
import vn.weathergis.chat.context.FactualContext;
import vn.weathergis.chat.context.FactualContextSerializer;
import vn.weathergis.chat.intent.ChatIntent;

/** Controlled snapshot cut ladder (W5-04 §7, DL-17 chat side; alert pipeline untouched). */
class ChatSnapshotCutterTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final Map<String, Integer> SEVERITY_RANK =
            Map.of("CRITICAL", 0, "DANGER", 1, "WARNING", 2, "INFO", 3);

    private final ObjectMapper mapper =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .disable(
                            com.fasterxml.jackson.databind.SerializationFeature
                                    .WRITE_DATES_AS_TIMESTAMPS);
    private final FactualContextSerializer serializer = new FactualContextSerializer(mapper);
    private final ChatContextProperties contextProperties = new ChatContextProperties();
    private final ChatReplyProperties replyProperties = new ChatReplyProperties();
    private final ChatSnapshotCutter cutter =
            new ChatSnapshotCutter(serializer, contextProperties, replyProperties);

    private ViolationSnapshot violation(String key, String severity, Instant sourceTimestamp) {
        return new ViolationSnapshot(
                1,
                "rain_1h",
                ">=",
                50.0,
                severity,
                65.5,
                "mm",
                sourceTimestamp,
                NOW.minusSeconds(300),
                key,
                null,
                null,
                null);
    }

    /**
     * violation_order exactly as W5-02 computes it: severity rank, timestamp ASC nulls-last, key
     * ASC.
     */
    private List<FactualContext.ViolationOrder> order(List<ViolationSnapshot> violations) {
        return violations.stream()
                .sorted(
                        Comparator.comparingInt(
                                        (ViolationSnapshot v) ->
                                                SEVERITY_RANK.getOrDefault(
                                                        v.severityLevel(), Integer.MAX_VALUE))
                                .thenComparing(
                                        ViolationSnapshot::sourceTimestamp,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(ViolationSnapshot::violationKey))
                .map(
                        v ->
                                new FactualContext.ViolationOrder(
                                        v.violationKey(),
                                        v.severityLevel(),
                                        v.measuredValue() - v.thresholdValue()))
                .toList();
    }

    private FactualContext alertContext(List<ViolationSnapshot> violations, int keyPadding) {
        List<ViolationSnapshot> padded =
                violations.stream()
                        .map(
                                v ->
                                        new ViolationSnapshot(
                                                v.thresholdId(),
                                                v.parameter(),
                                                v.operator(),
                                                v.thresholdValue(),
                                                v.severityLevel(),
                                                v.measuredValue(),
                                                v.unit(),
                                                v.sourceTimestamp(),
                                                v.evaluationWindow(),
                                                v.violationKey() + "x".repeat(keyPadding),
                                                null,
                                                null,
                                                null))
                        .toList();
        AlertFactualSnapshot snapshot =
                new AlertFactualSnapshot(
                        UUID.randomUUID(),
                        "W504A",
                        "Trạm W504A",
                        List.of(108.0, 16.0),
                        UUID.randomUUID(),
                        NOW.minusSeconds(300),
                        new RepresentativeThresholdSnapshot(
                                1,
                                "rain_1h",
                                ">=",
                                50.0,
                                padded.get(0).severityLevel(),
                                "mm",
                                padded.get(0).violationKey()),
                        padded);
        long snapshotBytes = serializer.measureBytes(snapshot);
        FactualContext.AlertContext alert =
                new FactualContext.AlertContext(
                        UUID.randomUUID(),
                        "active",
                        "DANGER",
                        NOW.minusSeconds(300),
                        null,
                        true,
                        snapshot,
                        order(padded),
                        snapshotBytes > contextProperties.getMaxContextBytes(),
                        snapshotBytes);
        FactualContext draft =
                new FactualContext(
                        FactualContext.SCHEMA_VERSION,
                        ChatIntent.ALERT_EXPLAIN,
                        NOW,
                        ChatContextOutcome.CONTEXT_READY,
                        null,
                        List.of(),
                        List.of(),
                        alert,
                        new FactualContext.ReferencedEntities(
                                List.of("W504A"), List.of(), List.of(alert.alertId())),
                        new FactualContext.ContextMeta(60, 0, 0, false),
                        0);
        boolean oversize = serializer.payloadBytes(draft) > contextProperties.getMaxContextBytes();
        FactualContext withMeta =
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
                        new FactualContext.ContextMeta(60, 0, 0, oversize),
                        0);
        return withMeta.withSizeBytes(serializer.payloadBytes(withMeta));
    }

    private FactualContext rainContext() {
        FactualContext draft =
                new FactualContext(
                        FactualContext.SCHEMA_VERSION,
                        ChatIntent.RAIN_TOP,
                        NOW,
                        ChatContextOutcome.CONTEXT_READY,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        new FactualContext.ReferencedEntities(List.of(), List.of(), List.of()),
                        new FactualContext.ContextMeta(60, 0, 0, false),
                        0);
        return draft.withSizeBytes(serializer.payloadBytes(draft));
    }

    @Test
    void nonAlertContextPassesThroughUncut() {
        FactualContext ctx = rainContext();
        List<ChatSnapshotCutter.CutCandidate> candidates = cutter.candidates(ctx);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).context()).isSameAs(ctx);
        assertThat(candidates.get(0).remainingViolations()).isZero();
        assertThat(candidates.get(0).fitsBlock()).isTrue();
    }

    @Test
    void alertSnapshotWithinLimitKeepsAllViolations() {
        FactualContext ctx =
                alertContext(
                        List.of(
                                violation("K1", "DANGER", NOW.minusSeconds(600)),
                                violation("K2", "WARNING", NOW.minusSeconds(600))),
                        0);

        List<ChatSnapshotCutter.CutCandidate> candidates = cutter.candidates(ctx);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).context()).isSameAs(ctx);
        assertThat(candidates.get(0).remainingViolations()).isZero();
    }

    @Test
    void oversizeSnapshotLadderFollowsViolationOrderAndCountsRemainder() {
        List<ViolationSnapshot> many = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            many.add(violation("FIXTURE_KEY_" + i, "CRITICAL", NOW.minusSeconds(600)));
        }
        FactualContext ctx = alertContext(many, 200);
        assertThat(ctx.alert().snapshotOversize()).isTrue();

        List<ChatSnapshotCutter.CutCandidate> candidates = cutter.candidates(ctx);

        assertThat(candidates).hasSize(4);
        assertThat(candidates.get(0).fitsBlock()).isFalse();
        assertThat(candidates.get(0).remainingViolations()).isZero();
        List<String> expectedKeys =
                ctx.alert().violationOrder().stream()
                        .map(FactualContext.ViolationOrder::violationKey)
                        .toList();
        int[] expectedCounts = {5, 3, 1};
        for (int i = 0; i < 3; i++) {
            ChatSnapshotCutter.CutCandidate candidate = candidates.get(i + 1);
            FactualContext.AlertContext alert = candidate.context().alert();
            assertThat(alert.factualSnapshot().violations()).hasSize(expectedCounts[i]);
            assertThat(alert.factualSnapshot().violations())
                    .extracting(ViolationSnapshot::violationKey)
                    .containsExactlyElementsOf(expectedKeys.subList(0, expectedCounts[i]));
            assertThat(alert.violationOrder())
                    .extracting(FactualContext.ViolationOrder::violationKey)
                    .containsExactlyElementsOf(expectedKeys.subList(0, expectedCounts[i]));
            assertThat(candidate.remainingViolations()).isEqualTo(75 - expectedCounts[i]);
            assertThat(candidate.fitsBlock()).isTrue();
        }
    }

    @Test
    void consumesGivenOrderInsteadOfResortingSeverityTimestampKeyTies() {
        // Deliberately shuffled input: CRITICAL later in list, equal severities tie by timestamp
        // then key; one null timestamp must sort last within its severity.
        List<ViolationSnapshot> shuffled =
                List.of(
                        violation("B_WARN", "WARNING", NOW.minusSeconds(600)),
                        violation("A_CRIT", "CRITICAL", NOW.minusSeconds(900)),
                        violation("D_WARN", "WARNING", null),
                        violation("C_WARN", "WARNING", NOW.minusSeconds(600)),
                        violation("E_INFO", "INFO", NOW.minusSeconds(1200)),
                        violation("B_CRIT", "CRITICAL", NOW.minusSeconds(900)));
        FactualContext ctx = alertContext(shuffled, 400);
        contextProperties.setMaxContextBytes(2500);

        List<ChatSnapshotCutter.CutCandidate> candidates = cutter.candidates(ctx);

        List<String> expectedOrder =
                List.of("A_CRIT", "B_CRIT", "B_WARN", "C_WARN", "D_WARN", "E_INFO");
        assertThat(ctx.alert().violationOrder())
                .extracting(FactualContext.ViolationOrder::violationKey)
                .containsExactlyElementsOf(
                        expectedOrder.stream().map(k -> k + "x".repeat(400)).toList());
        ChatSnapshotCutter.CutCandidate top3 =
                candidates.stream()
                        .filter(c -> c.context().alert().factualSnapshot().violations().size() == 3)
                        .findFirst()
                        .orElseThrow();
        assertThat(top3.context().alert().factualSnapshot().violations())
                .extracting(ViolationSnapshot::violationKey)
                .containsExactly(
                        "A_CRIT" + "x".repeat(400),
                        "B_CRIT" + "x".repeat(400),
                        "B_WARN" + "x".repeat(400));
        assertThat(top3.remainingViolations()).isEqualTo(3);
    }

    @Test
    void ladderDeduplicatesStepsAtOrAboveTotalViolations() {
        List<ViolationSnapshot> four =
                List.of(
                        violation("K1", "CRITICAL", NOW.minusSeconds(600)),
                        violation("K2", "DANGER", NOW.minusSeconds(600)),
                        violation("K3", "WARNING", NOW.minusSeconds(600)),
                        violation("K4", "INFO", NOW.minusSeconds(600)));
        FactualContext ctx = alertContext(four, 400);
        contextProperties.setMaxContextBytes(2500);

        List<ChatSnapshotCutter.CutCandidate> candidates = cutter.candidates(ctx);

        assertThat(candidates)
                .extracting(c -> c.context().alert().factualSnapshot().violations().size())
                .containsExactly(4, 3, 1);
        assertThat(candidates).extracting(c -> c.remainingViolations()).containsExactly(0, 1, 3);
    }

    @Test
    void trimmedContextRecomputesSizesAndOversizeFlagsConsistently() {
        List<ViolationSnapshot> many = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            many.add(violation("FIXTURE_KEY_" + i, "CRITICAL", NOW.minusSeconds(600)));
        }
        FactualContext ctx = alertContext(many, 200);

        ChatSnapshotCutter.CutCandidate top5 = cutter.candidates(ctx).get(1);
        FactualContext trimmed = top5.context();

        assertThat(trimmed.sizeBytes()).isEqualTo(serializer.payloadBytes(trimmed));
        assertThat(trimmed.alert().snapshotSizeBytes())
                .isEqualTo(serializer.measureBytes(trimmed.alert().factualSnapshot()));
        assertThat(trimmed.alert().snapshotOversize())
                .isEqualTo(
                        trimmed.alert().snapshotSizeBytes()
                                > contextProperties.getMaxContextBytes());
        assertThat(trimmed.meta().contextOversize())
                .isEqualTo(
                        serializer.payloadBytes(trimmed) > contextProperties.getMaxContextBytes());
        assertThat(trimmed.alert().alertId()).isEqualTo(ctx.alert().alertId());
        assertThat(trimmed.alert().status()).isEqualTo("active");
        assertThat(trimmed.alert().triggeredAt()).isEqualTo(ctx.alert().triggeredAt());
        assertThat(trimmed.referencedEntities()).isEqualTo(ctx.referencedEntities());
        // original untouched
        assertThat(ctx.alert().factualSnapshot().violations()).hasSize(75);
    }
}
