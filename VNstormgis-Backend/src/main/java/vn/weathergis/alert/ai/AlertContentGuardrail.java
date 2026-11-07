package vn.weathergis.alert.ai;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.weathergis.alert.ai.GeminiGenerationResult.GeminiAlertDraft;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.dto.AlertGenerationJobView;

/** A deliberately closed grammar: arbitrary model prose is never accepted. */
@Component
public class AlertContentGuardrail {
    private static final Map<String, String> LABELS =
            Map.of(
                    "rain_1h", "Lượng mưa 1 giờ",
                    "rain_3h", "Lượng mưa 3 giờ",
                    "wind_speed", "Tốc độ gió",
                    "wind_gust", "Tốc độ gió giật",
                    "storm_dist", "Khoảng cách đến bão");
    private final AlertPromptBuilder facts;

    public AlertContentGuardrail(AlertPromptBuilder facts) {
        this.facts = facts;
    }

    /** Throws for incomplete or inconsistent facts, including content too long to publish. */
    public GeminiAlertDraft fallback(AlertGenerationJobView job) {
        facts.build(job);
        String name = job.factualSnapshot().locationName();
        if (!name.matches("[\\p{L}\\p{M}\\p{N} .,'’()/-]+")) {
            throw new IllegalArgumentException("Invalid location name");
        }
        String title = "Cảnh báo tại " + name;
        StringBuilder explanation = new StringBuilder("Tại ").append(name).append(": ");
        var violations =
                job.factualSnapshot().violations().stream()
                        .sorted(Comparator.comparing(ViolationSnapshot::violationKey))
                        .toList();
        for (int i = 0; i < violations.size(); i++) {
            ViolationSnapshot v = violations.get(i);
            if (!holds(v)) throw new IllegalArgumentException("Invalid violation relation");
            if (i > 0) explanation.append("; ");
            explanation
                    .append(LABELS.get(v.parameter()))
                    .append(" đo được ")
                    .append(number(v.measuredValue()))
                    .append(' ')
                    .append(v.unit())
                    .append(", so với ngưỡng ")
                    .append(v.operator())
                    .append(' ')
                    .append(number(v.thresholdValue()))
                    .append(' ')
                    .append(v.unit());
        }
        explanation.append('.');
        if (title.length() > 255 || explanation.length() > 4000) {
            throw new IllegalArgumentException("Facts exceed content limits");
        }
        return new GeminiAlertDraft(title, explanation.toString());
    }

    /** Both fields must match the fact-rendered grammar exactly after trimming. */
    public boolean accepts(AlertGenerationJobView job, GeminiAlertDraft draft) {
        if (draft == null || draft.title() == null || draft.aiExplanation() == null) return false;
        try {
            GeminiAlertDraft expected = fallback(job);
            return expected.title().equals(draft.title().trim())
                    && expected.aiExplanation().equals(draft.aiExplanation().trim());
        } catch (RuntimeException invalidFacts) {
            return false;
        }
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static boolean holds(ViolationSnapshot v) {
        int comparison =
                BigDecimal.valueOf(v.measuredValue())
                        .compareTo(BigDecimal.valueOf(v.thresholdValue()));
        return switch (v.operator()) {
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            case "=" -> comparison == 0;
            default -> false;
        };
    }
}
