package vn.weathergis.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.weathergis.automation.event.ThresholdViolatedEvent;
import vn.weathergis.automation.model.NearestStorm;
import vn.weathergis.automation.model.StationSnapshot;
import vn.weathergis.automation.model.ThresholdRule;
import vn.weathergis.automation.model.WeatherObservation;

class ThresholdEvaluatorTest {

    private static final UUID LOCATION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CYCLE = UUID.randomUUID();
    private static final Instant OBSERVED = Instant.parse("2026-11-05T01:02:03.123456Z");
    private static final Instant UPDATED = Instant.parse("2026-11-05T01:04:00Z");
    private static final Instant EVALUATED = Instant.parse("2026-11-05T02:00:00Z");
    private final ThresholdEvaluator evaluator = new ThresholdEvaluator();

    @ParameterizedTest(name = "{0} {1} threshold: measured={2}, violates={3}")
    @MethodSource("boundaries")
    void comparesEveryParameterAndOperatorAtBothSidesAndExactBoundary(
            String parameter, String operator, double measured, boolean violates) {
        var result =
                evaluator.evaluate(
                        station(measured),
                        List.of(rule(1, parameter, operator, 10)),
                        CYCLE,
                        EVALUATED);
        assertThat(result.isPresent()).isEqualTo(violates);
        result.ifPresent(
                event -> {
                    assertThat(event.violations()).hasSize(1);
                    assertThat(event.violations().getFirst().measuredValue()).isEqualTo(measured);
                    assertThat(event.violations().getFirst().parameter()).isEqualTo(parameter);
                });
    }

    static Stream<Arguments> boundaries() {
        List<Arguments> cases = new ArrayList<>();
        // Explicit expected below/equal/above outcomes, independent of the production switch.
        boolean[][] expected = {
            {false, true, true},
            {false, false, true},
            {true, true, false},
            {true, false, false},
            {false, true, false}
        };
        String[] operators = {">=", ">", "<=", "<", "="};
        for (String parameter :
                List.of("rain_1h", "rain_3h", "wind_speed", "wind_gust", "storm_dist")) {
            for (int operator = 0; operator < operators.length; operator++) {
                for (int side = 0; side < 3; side++) {
                    cases.add(
                            Arguments.of(
                                    parameter,
                                    operators[operator],
                                    9.0 + side,
                                    expected[operator][side]));
                }
            }
        }
        return cases.stream();
    }

    @Test
    void aggregatesRealFactsIntoOneImmutableStationEvent() {
        StationSnapshot station =
                new StationSnapshot(
                        LOCATION,
                        "DAN",
                        "Đà Nẵng",
                        108.206,
                        16.047,
                        new WeatherObservation(55.0, 70.0, 20.0, 25.0, OBSERVED),
                        new NearestStorm("TC-01", "Fixture storm", 100.0, UPDATED));
        var event =
                evaluator
                        .evaluate(
                                station,
                                List.of(
                                        rule(1, "rain_1h", ">=", 50),
                                        rule(2, "rain_3h", ">", 60),
                                        rule(3, "wind_speed", ">=", 15),
                                        rule(4, "wind_gust", ">", 20),
                                        rule(5, "storm_dist", "<=", 300)),
                                CYCLE,
                                EVALUATED)
                        .orElseThrow();

        assertThat(event.locationId()).isEqualTo(LOCATION);
        assertThat(event.locationCode()).isEqualTo("DAN");
        assertThat(event.locationName()).isEqualTo("Đà Nẵng");
        assertThat(event.longitude()).isEqualTo(108.206);
        assertThat(event.latitude()).isEqualTo(16.047);
        assertThat(event.evaluationCycleId()).isEqualTo(CYCLE);
        assertThat(event.evaluatedAt()).isEqualTo(EVALUATED);
        assertThat(event.violations()).hasSize(5);
        assertThat(event.violations())
                .extracting(v -> v.unit())
                .containsExactly("mm", "mm", "m/s", "m/s", "km");
        assertThat(event.violations())
                .extracting(v -> v.measuredValue())
                .containsExactly(55.0, 70.0, 20.0, 25.0, 100.0);
        var rain = event.violations().getFirst();
        assertThat(rain.thresholdId()).isEqualTo(1);
        assertThat(rain.operator()).isEqualTo(">=");
        assertThat(rain.thresholdValue()).isEqualTo(50);
        assertThat(rain.severityLevel()).isEqualTo("DANGER");
        assertThat(rain.sourceTimestamp()).isEqualTo(OBSERVED);
        assertThat(rain.evaluationWindow()).isEqualTo(OBSERVED);
        assertThat(rain.stormId()).isNull();
        assertThat(rain.stormName()).isNull();
        assertThat(rain.distanceKm()).isNull();
        var storm = event.violations().getLast();
        assertThat(storm.stormId()).isEqualTo("TC-01");
        assertThat(storm.stormName()).isEqualTo("Fixture storm");
        assertThat(storm.distanceKm()).isEqualTo(100.0);
        assertThat(storm.sourceTimestamp()).isEqualTo(UPDATED);
        assertThat(storm.evaluationWindow()).isEqualTo(UPDATED);
        assertThatThrownBy(() -> event.violations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mapsEachWeatherMetricIndependently() {
        StationSnapshot station =
                new StationSnapshot(
                        LOCATION,
                        "DAN",
                        "Đà Nẵng",
                        108,
                        16,
                        new WeatherObservation(1.0, 2.0, 3.0, 4.0, OBSERVED),
                        null);
        var result =
                evaluator
                        .evaluate(
                                station,
                                List.of(
                                        rule(1, "rain_1h", "=", 1), rule(2, "rain_3h", "=", 2),
                                        rule(3, "wind_speed", "=", 3),
                                                rule(4, "wind_gust", "=", 4)),
                                CYCLE,
                                EVALUATED)
                        .orElseThrow();
        assertThat(result.violations())
                .extracting(v -> v.measuredValue())
                .containsExactly(1.0, 2.0, 3.0, 4.0);
    }

    @ParameterizedTest
    @MethodSource("missingOrNonfinite")
    void skipsMissingAndNonfiniteWeatherValues(Double value) {
        StationSnapshot station =
                new StationSnapshot(
                        LOCATION,
                        "DAN",
                        "Đà Nẵng",
                        108,
                        16,
                        new WeatherObservation(value, value, value, value, OBSERVED),
                        null);
        assertThat(
                        evaluator.evaluate(
                                station,
                                List.of(
                                        rule(1, "rain_1h", ">=", 1), rule(2, "rain_3h", "<=", 1),
                                        rule(3, "wind_speed", ">=", 1),
                                                rule(4, "wind_gust", "<=", 1)),
                                CYCLE,
                                EVALUATED))
                .isEmpty();
    }

    static Stream<Arguments> missingOrNonfinite() {
        return Stream.of(
                Arguments.of((Double) null),
                Arguments.of(Double.NaN),
                Arguments.of(Double.POSITIVE_INFINITY),
                Arguments.of(Double.NEGATIVE_INFINITY));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1})
    void skipsInvalidStormDistances(double distance) {
        StationSnapshot station =
                new StationSnapshot(
                        LOCATION,
                        "DAN",
                        "Đà Nẵng",
                        108,
                        16,
                        null,
                        new NearestStorm("TC-01", "Storm", distance, UPDATED));
        assertThat(
                        evaluator.evaluate(
                                station,
                                List.of(rule(1, "storm_dist", "<=", 100)),
                                CYCLE,
                                EVALUATED))
                .isEmpty();
    }

    @Test
    void handlesIndependentMissingBranchesAndSafeScans() {
        StationSnapshot weatherOnly =
                new StationSnapshot(
                        LOCATION, "DAN", "Đà Nẵng", 108, 16, station(55).weather(), null);
        StationSnapshot stormOnly =
                new StationSnapshot(LOCATION, "DAN", "Đà Nẵng", 108, 16, null, station(55).storm());
        List<ThresholdRule> rules =
                List.of(rule(1, "rain_1h", ">=", 50), rule(2, "storm_dist", "<=", 100));
        assertThat(
                        evaluator
                                .evaluate(weatherOnly, rules, CYCLE, EVALUATED)
                                .orElseThrow()
                                .violations())
                .extracting(v -> v.parameter())
                .containsExactly("rain_1h");
        assertThat(
                        evaluator
                                .evaluate(stormOnly, rules, CYCLE, EVALUATED)
                                .orElseThrow()
                                .violations())
                .extracting(v -> v.parameter())
                .containsExactly("storm_dist");
        assertThat(
                        evaluator.evaluate(
                                new StationSnapshot(
                                        LOCATION, "DAN", "Đà Nẵng", 108, 16, null, null),
                                rules,
                                CYCLE,
                                EVALUATED))
                .isEmpty();
        assertThat(
                        evaluator.evaluate(
                                station(0), List.of(rule(1, "rain_1h", ">", 1)), CYCLE, EVALUATED))
                .isEmpty();
        assertThat(evaluator.evaluate(station(55), List.of(), CYCLE, EVALUATED)).isEmpty();
    }

    @Test
    void skipsDisabledUnsupportedAndInvalidThresholds() {
        assertThat(
                        evaluator.evaluate(
                                station(55),
                                List.of(
                                        new ThresholdRule(1, "rain_1h", ">=", 50, "DANGER", false),
                                        rule(2, "storm_distance_km", "<=", 100),
                                        rule(3, "rain_1h", "!=", 10),
                                        rule(4, "rain_1h", ">", Double.NaN),
                                        rule(5, "rain_1h", "<", Double.POSITIVE_INFINITY)),
                                CYCLE,
                                EVALUATED))
                .isEmpty();
    }

    @Test
    void neverUsesEvaluationTimeWhenSourceTimeIsMissing() {
        StationSnapshot missingTimes =
                new StationSnapshot(
                        LOCATION,
                        "DAN",
                        "Đà Nẵng",
                        108,
                        16,
                        new WeatherObservation(55.0, 55.0, 55.0, 55.0, null),
                        new NearestStorm("TC-01", "Storm", 0, null));
        assertThat(
                        evaluator.evaluate(
                                missingTimes,
                                List.of(
                                        rule(1, "rain_1h", ">=", 50),
                                        rule(2, "storm_dist", "<=", 100)),
                                CYCLE,
                                EVALUATED))
                .isEmpty();
    }

    @Test
    void keysDependOnSourceSnapshotAndIdentityAndAreDeduplicated() {
        var rules = List.of(rule(1, "rain_1h", ">=", 50), rule(2, "storm_dist", "<=", 100));
        var first = evaluator.evaluate(station(55), rules, CYCLE, EVALUATED).orElseThrow();
        var repeated =
                evaluator
                        .evaluate(station(55), rules, UUID.randomUUID(), EVALUATED.plusSeconds(900))
                        .orElseThrow();
        assertThat(repeated.violations())
                .extracting(v -> v.violationKey())
                .containsExactlyElementsOf(
                        first.violations().stream().map(v -> v.violationKey()).toList());
        var duplicate =
                evaluator
                        .evaluate(
                                station(55),
                                List.of(rules.getFirst(), rules.getFirst()),
                                CYCLE,
                                EVALUATED)
                        .orElseThrow();
        assertThat(duplicate.violations()).hasSize(1);
        Instant sameUtc = OffsetDateTime.parse("2026-11-05T08:02:03.123456+07:00").toInstant();
        assertThat(first.violations().getFirst().violationKey())
                .contains(LOCATION.toString(), ":1:", sameUtc.toString());
        var changed =
                evaluator
                        .evaluate(
                                new StationSnapshot(
                                        UUID.randomUUID(),
                                        "NEW",
                                        "New station",
                                        108,
                                        16,
                                        new WeatherObservation(
                                                55.0, null, null, null, OBSERVED.plusSeconds(1)),
                                        new NearestStorm("TC-02", "Other storm", 55, UPDATED)),
                                rules,
                                CYCLE,
                                EVALUATED)
                        .orElseThrow();
        assertThat(changed.violations())
                .extracting(v -> v.violationKey())
                .doesNotContainAnyElementsOf(
                        first.violations().stream().map(v -> v.violationKey()).toList());
        assertThat(first.violations().getLast().violationKey()).endsWith(":TC-01");
    }

    @Test
    void eventCopiesTheProvidedViolationList() {
        var list =
                new ArrayList<>(
                        evaluator
                                .evaluate(
                                        station(55),
                                        List.of(rule(1, "rain_1h", ">=", 50)),
                                        CYCLE,
                                        EVALUATED)
                                .orElseThrow()
                                .violations());
        var event =
                new ThresholdViolatedEvent(
                        LOCATION, "DAN", "Đà Nẵng", 108, 16, CYCLE, EVALUATED, list);
        list.clear();
        assertThat(event.violations()).hasSize(1);
    }

    private static ThresholdRule rule(int id, String parameter, String operator, double threshold) {
        return new ThresholdRule(id, parameter, operator, threshold, "DANGER", true);
    }

    private static StationSnapshot station(double value) {
        return new StationSnapshot(
                LOCATION,
                "DAN",
                "Đà Nẵng",
                108.206,
                16.047,
                new WeatherObservation(value, value, value, value, OBSERVED),
                new NearestStorm("TC-01", "Fixture storm", value, UPDATED));
    }
}
