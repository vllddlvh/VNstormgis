package vn.weathergis.alert.ai;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({">,1,0", ">=,0,0", "<,-1,0", "<=,0,0", "=,0,0"})
    void acceptsEachOperatorAtItsBoundary(String op, double measured, double threshold) {
        var fixture = job("Trạm 2", op, measured, threshold);
        assertThat(guardrail.accepts(fixture, guardrail.fallback(fixture))).isTrue();
    }

    @Test
    void rejectsChangedMeaningEvenWhenNumbersArePresent() {
        var fixture = job("Trạm 2", ">=", 55, 50);
        var valid = guardrail.fallback(fixture);
        for (String altered :
                List.of(
                        valid.aiExplanation().replace("1 giờ", "3 giờ"),
                        valid.aiExplanation()
                                .replace("55 mm", "50 mm")
                                .replace(">= 50 mm", ">= 55 mm"),
                        valid.aiExplanation().replace("mm", "km"),
                        valid.aiExplanation().replace(">=", "<="),
                        valid.aiExplanation() + " Ngày mai mưa to.",
                        valid.aiExplanation() + " Năm mươi phần trăm.",
                        valid.aiExplanation().replace("Trạm 2", "Trạm 3"))) {
            assertThat(guardrail.accepts(fixture, new GeminiAlertDraft(valid.title(), altered)))
                    .isFalse();
        }
    }

    @Test
    void verifiesMultipleParametersWithoutInterchangingSharedNumbers() {
        var original = job("Trạm 2", ">=", 55, 50);
        var old = original.factualSnapshot();
        Instant time = old.evaluatedAt();
        var wind =
                new ViolationSnapshot(
                        2,
                        "wind_gust",
                        ">=",
                        50,
                        "WARNING",
                        55,
                        "m/s",
                        time,
                        time,
                        "wind",
                        null,
                        null,
                        null);
        var storm =
                new ViolationSnapshot(
                        3,
                        "storm_dist",
                        "<=",
                        50,
                        "WARNING",
                        20,
                        "km",
                        time,
                        time,
                        "storm",
                        "s",
                        "Bão A",
                        20.0);
        var snapshot =
                new AlertFactualSnapshot(
                        old.locationId(),
                        old.locationCode(),
                        old.locationName(),
                        old.coordinates(),
                        old.evaluationCycleId(),
                        time,
                        old.representativeThreshold(),
                        List.of(storm, wind, old.violations().getFirst()));
        var multi =
                new AlertGenerationJobView(
                        original.jobId(),
                        original.status(),
                        original.locationId(),
                        original.evaluationCycleId(),
                        original.representativeThresholdId(),
                        original.stormId(),
                        original.severityLevel(),
                        original.triggerReason(),
                        original.triggeredAt(),
                        snapshot,
                        List.of("key", "wind", "storm"),
                        null,
                        null,
                        null,
                        0,
                        null,
                        null,
                        null);
        var valid = guardrail.fallback(multi);
        assertThat(valid.aiExplanation())
                .contains(
                        "Lượng mưa 1 giờ đo được 55 mm",
                        "Tốc độ gió giật đo được 55 m/s",
                        "Khoảng cách đến bão đo được 20 km");
        assertThat(guardrail.accepts(multi, valid)).isTrue();
        assertThat(
                        guardrail.accepts(
                                multi,
                                new GeminiAlertDraft(
                                        valid.title(),
                                        valid.aiExplanation()
                                                .replace(
                                                        "Tốc độ gió giật đo được 55 m/s",
                                                        "Tốc độ gió đo được 55 m/s"))))
                .isFalse();
        var invalidStorm =
                new ViolationSnapshot(
                        3,
                        "storm_dist",
                        "<=",
                        50,
                        "WARNING",
                        20,
                        "km",
                        time,
                        time,
                        "storm",
                        "s",
                        "Bão A",
                        21.0);
        var badSnapshot =
                new AlertFactualSnapshot(
                        old.locationId(),
                        old.locationCode(),
                        old.locationName(),
                        old.coordinates(),
                        old.evaluationCycleId(),
                        time,
                        old.representativeThreshold(),
                        List.of(invalidStorm, wind, old.violations().getFirst()));
        var bad =
                new AlertGenerationJobView(
                        original.jobId(),
                        original.status(),
                        original.locationId(),
                        original.evaluationCycleId(),
                        original.representativeThresholdId(),
                        original.stormId(),
                        original.severityLevel(),
                        original.triggerReason(),
                        original.triggeredAt(),
                        badSnapshot,
                        List.of("key", "wind", "storm"),
                        null,
                        null,
                        null,
                        0,
                        null,
                        null,
                        null);
        assertThatThrownBy(() -> guardrail.fallback(bad))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                        guardrail.accepts(
                                multi,
                                new GeminiAlertDraft(
                                        valid.title(),
                                        valid.aiExplanation().replace("20 km", "55 km"))))
                .isFalse();
    }
}
