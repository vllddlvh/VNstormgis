package vn.weathergis.alert.ai;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.weathergis.alert.ai.GeminiGenerationResult.GeminiAlertDraft;
import vn.weathergis.alert.domain.AlertJobStatus;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.dto.AlertGenerationJobView;

class AlertContentGuardrailTest {
    private final AlertContentGuardrail guardrail =
            new AlertContentGuardrail(
                    new AlertPromptBuilder(new ObjectMapper().findAndRegisterModules()));

    private AlertGenerationJobView job(
            String name, String operator, double measured, double threshold) {
        UUID location = UUID.randomUUID(), cycle = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var violation =
                new ViolationSnapshot(
                        1, "rain_1h", operator, threshold, "DANGER", measured, "mm", now, now,
                        "key", null, null, null);
        var representative =
                new RepresentativeThresholdSnapshot(
                        1, "rain_1h", operator, threshold, "DANGER", "mm", "key");
        var snapshot =
                new AlertFactualSnapshot(
                        location,
                        "DN",
                        name,
                        List.of(108.2, 16.0),
                        cycle,
                        now,
                        representative,
                        List.of(violation));
        return new AlertGenerationJobView(
                UUID.randomUUID(),
                AlertJobStatus.PENDING,
                location,
                cycle,
                1,
                null,
                "DANGER",
                "rain",
                now,
                snapshot,
                List.of("key"),
                null,
                null,
                null,
                0,
                null,
                null,
                null);
    }

    @Test
    void acceptsOnlyTheExactGroundedGrammar() {
        var job = job("Trạm 2", ">=", 55.5, 50);
        var valid = guardrail.fallback(job);
        assertThat(guardrail.accepts(job, valid)).isTrue();
        assertThat(
                        guardrail.accepts(
                                job,
                                new GeminiAlertDraft(
                                        valid.title() + " nguy hiểm", valid.aiExplanation())))
                .isFalse();
        assertThat(
                        guardrail.accepts(
                                job,
                                new GeminiAlertDraft(
                                        valid.title(),
                                        valid.aiExplanation().replace("55.5", "50"))))
                .isFalse();
        assertThat(
                        guardrail.accepts(
                                job,
                                new GeminiAlertDraft(
                                        valid.title(), valid.aiExplanation() + " Sẽ ngập lụt.")))
                .isFalse();
    }

    @Test
    void usesLocaleIndependentDecimalsAndChecksBoundaries() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertThat(guardrail.fallback(job("Trạm 2", ">=", 0, 0)).aiExplanation())
                    .contains("0 mm", ">= 0 mm");
            assertThat(guardrail.fallback(job("Trạm", "<", -1.5, 0)).aiExplanation())
                    .contains("-1.5 mm");
            assertThatThrownBy(() -> guardrail.fallback(job("Trạm", ">", 0, 0)))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void refusesUnsafeNamesAndContentThatCannotFit() {
        assertThatThrownBy(() -> guardrail.fallback(job("Trạm\nDự báo", ">", 1, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guardrail.fallback(job("A".repeat(255), ">", 1, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(guardrail.accepts(null, null)).isFalse();
    }
}
