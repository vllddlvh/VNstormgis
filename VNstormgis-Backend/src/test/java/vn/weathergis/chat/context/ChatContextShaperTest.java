package vn.weathergis.chat.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.chat.config.ChatContextProperties;
import vn.weathergis.chat.intent.ChatIntent;

/** Deterministic shrink ladder and entity recomputation (W5-02 §4.7). */
class ChatContextShaperTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");

    private FactualContextSerializer serializer;
    private ChatContextProperties properties;
    private ChatContextShaper shaper;

    @BeforeEach
    void setUp() {
        serializer =
                new FactualContextSerializer(
                        new ObjectMapper().registerModule(new JavaTimeModule()));
        properties = new ChatContextProperties();
        shaper = new ChatContextShaper(serializer, properties);
    }

    private FactualContext.StationObservation station(String code, int descriptionLength) {
        return new FactualContext.StationObservation(
                code,
                "Trạm " + code,
                List.of(108.2, 16.0),
                NOW.minusSeconds(600),
                false,
                25.0,
                70.0,
                1000.0,
                5.0,
                12.0,
                90.0,
                10.0,
                30.0,
                "Rain",
                "x".repeat(descriptionLength),
                Map.of("rain_1h", "mm"));
    }

    private FactualContext.StormSummary storm(String id, int nameLength, boolean withForecast) {
        return new FactualContext.StormSummary(
                id,
                "x".repeat(nameLength),
                "Red",
                45.0,
                960.0,
                List.of(127.4, 31.2),
                NOW.minusSeconds(600),
                "db_update",
                false,
                "GDACS",
                withForecast
                        ? new FactualContext.ForecastPoint(
                                NOW.plusSeconds(3600),
                                "forecast",
                                List.of(126.0, 30.0),
                                40.0,
                                null,
                                955.0,
                                "HU")
                        : null,
                Map.of("max_wind_speed", "m/s"));
    }

    private FactualContext draft(
            ChatIntent intent,
            List<FactualContext.StationObservation> stations,
            List<FactualContext.StormSummary> storms,
            FactualContext.AlertContext alert) {
        return new FactualContext(
                FactualContext.SCHEMA_VERSION,
                intent,
                NOW,
                ChatContextOutcome.CONTEXT_READY,
                null,
                stations,
                storms,
                alert,
                new FactualContext.ReferencedEntities(List.of(), List.of(), List.of()),
                new FactualContext.ContextMeta(60, 0, 0, false),
                0);
    }

    @Test
    void smallContextPassesThroughWithEntitiesFromBlocks() {
        FactualContext shaped =
                shaper.shape(
                        draft(
                                ChatIntent.RAIN_TOP,
                                List.of(station("AAA", 10), station("BBB", 10)),
                                List.of(),
                                null));

        assertThat(shaped.stations()).hasSize(2);
        assertThat(shaped.meta().omittedStationCount()).isZero();
        assertThat(shaped.meta().contextOversize()).isFalse();
        assertThat(shaped.referencedEntities().stations()).containsExactly("AAA", "BBB");
    }

    @Test
    void stationLadderReducesFiveToThreeWhenOverLimit() {
        List<FactualContext.StationObservation> stations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            stations.add(station("S" + i, 1500));
        }

        FactualContext shaped = shaper.shape(draft(ChatIntent.RAIN_TOP, stations, List.of(), null));

        assertThat(serializer.payloadBytes(shaped))
                .isLessThanOrEqualTo(properties.getMaxContextBytes());
        assertThat(shaped.stations()).hasSize(3);
        assertThat(shaped.stations())
                .extracting(FactualContext.StationObservation::code)
                .containsExactly("S0", "S1", "S2");
        assertThat(shaped.meta().omittedStationCount()).isEqualTo(2);
        assertThat(shaped.referencedEntities().stations()).containsExactly("S0", "S1", "S2");
    }

    @Test
    void stationLadderReducesToOneWhenStillOverLimit() {
        List<FactualContext.StationObservation> stations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            stations.add(station("S" + i, 3000));
        }

        FactualContext shaped = shaper.shape(draft(ChatIntent.RAIN_TOP, stations, List.of(), null));

        assertThat(serializer.payloadBytes(shaped))
                .isLessThanOrEqualTo(properties.getMaxContextBytes());
        assertThat(shaped.stations()).hasSize(1);
        assertThat(shaped.meta().omittedStationCount()).isEqualTo(4);
    }

    @Test
    void stormTrackDetailIsReducedBeforeStormCount() {
        List<FactualContext.StormSummary> storms = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            storms.add(storm("TC" + i, 1200, true));
        }

        FactualContext shaped =
                shaper.shape(draft(ChatIntent.STORM_ACTIVE, List.of(), storms, null));

        assertThat(serializer.payloadBytes(shaped))
                .isLessThanOrEqualTo(properties.getMaxContextBytes());
        assertThat(shaped.storms()).hasSize(5);
        assertThat(shaped.storms()).allSatisfy(s -> assertThat(s.nearestForecast()).isNull());
        assertThat(shaped.meta().omittedStormCount()).isZero();
    }

    @Test
    void stormLadderReducesCountWhenTrackRemovalIsNotEnough() {
        List<FactualContext.StormSummary> storms = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            storms.add(storm("TC" + i, 3000, true));
        }

        FactualContext shaped =
                shaper.shape(draft(ChatIntent.STORM_ACTIVE, List.of(), storms, null));

        assertThat(shaped.storms().size()).isLessThan(5);
        assertThat(shaped.meta().omittedStormCount()).isEqualTo(5 - shaped.storms().size());
        assertThat(shaped.referencedEntities().stormIds())
                .containsExactlyElementsOf(
                        shaped.storms().stream()
                                .map(FactualContext.StormSummary::stormId)
                                .toList());
    }

    @Test
    void askedAlertIsNeverDroppedOrTruncated() {
        List<ViolationSnapshot> violations = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            violations.add(
                    new ViolationSnapshot(
                            1,
                            "rain_1h",
                            ">=",
                            50.0,
                            "CRITICAL",
                            65.5,
                            "mm",
                            NOW,
                            NOW,
                            "K" + i + "x".repeat(300),
                            null,
                            null,
                            null));
        }
        AlertFactualSnapshot snapshot =
                new AlertFactualSnapshot(
                        UUID.randomUUID(),
                        "DNG",
                        "Đà Nẵng",
                        List.of(108.2, 16.0),
                        UUID.randomUUID(),
                        NOW,
                        new RepresentativeThresholdSnapshot(
                                1, "rain_1h", ">=", 50.0, "CRITICAL", "mm", "K0"),
                        violations);
        FactualContext.AlertContext alert =
                new FactualContext.AlertContext(
                        UUID.randomUUID(),
                        "active",
                        "CRITICAL",
                        NOW,
                        null,
                        true,
                        snapshot,
                        List.of(),
                        true,
                        30000);

        FactualContext shaped =
                shaper.shape(draft(ChatIntent.ALERT_EXPLAIN, List.of(), List.of(), alert));

        assertThat(shaped.alert()).isEqualTo(alert);
        assertThat(shaped.alert().factualSnapshot().violations()).hasSize(75);
        assertThat(shaped.meta().contextOversize()).isTrue();
    }

    @Test
    void shapingIsDeterministic() {
        List<FactualContext.StationObservation> stations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            stations.add(station("S" + i, 3000));
        }
        FactualContext input = draft(ChatIntent.RAIN_TOP, stations, List.of(), null);

        assertThat(shaper.shape(input)).isEqualTo(shaper.shape(input));
    }
}
