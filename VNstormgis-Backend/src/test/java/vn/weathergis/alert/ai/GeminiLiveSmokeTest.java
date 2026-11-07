package vn.weathergis.alert.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.weathergis.alert.domain.AlertJobStatus;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.dto.AlertGenerationJobView;

/** Explicitly opt-in live check; uses synthetic data and never publishes an alert. */
@EnabledIfEnvironmentVariable(named = "GEMINI_LIVE_SMOKE", matches = "true")
class GeminiLiveSmokeTest {
    @Test
    void generatesUnverifiedDraftFromSyntheticRainSnapshot() throws Exception {
        var props = new GeminiProperties();
        props.setEnabled(true);
        props.setApiKey(System.getenv("GEMINI_API_KEY"));
        props.setModel(System.getenv("GEMINI_MODEL"));
        var mapper =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var client = new GeminiClient(props, new AlertPromptBuilder(mapper), mapper);
        var location = UUID.randomUUID();
        var cycle = UUID.randomUUID();
        var time = Instant.parse("2026-01-01T00:00:00Z");
        var violation =
                new ViolationSnapshot(
                        1,
                        "rain_1h",
                        ">=",
                        50.0,
                        "DANGER",
                        55.0,
                        "mm",
                        time,
                        time,
                        "synthetic-key",
                        null,
                        null,
                        null);
        var rep =
                new RepresentativeThresholdSnapshot(
                        1, "rain_1h", ">=", 50.0, "DANGER", "mm", "synthetic-key");
        var facts =
                new AlertFactualSnapshot(
                        location,
                        "TEST",
                        "Trạm kiểm thử",
                        List.of(108.2, 16.0),
                        cycle,
                        time,
                        rep,
                        List.of(violation));
        var job =
                new AlertGenerationJobView(
                        UUID.randomUUID(),
                        AlertJobStatus.PENDING,
                        location,
                        cycle,
                        1,
                        null,
                        "DANGER",
                        "synthetic rain",
                        time,
                        facts,
                        List.of("synthetic-key"),
                        null,
                        null,
                        null,
                        0,
                        null,
                        null,
                        null);
        var result = client.generate(job);
        assertThat(result.outcome())
                .as(
                        "outcome=%s status=%s durationMs=%s model=%s",
                        result.outcome(),
                        result.httpStatus(),
                        result.duration().toMillis(),
                        result.model())
                .isEqualTo(GeminiGenerationResult.Outcome.DRAFT);
        assertThat(result.draft()).isNotNull();
    }
}
