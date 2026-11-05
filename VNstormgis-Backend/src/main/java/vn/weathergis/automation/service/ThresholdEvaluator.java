package vn.weathergis.automation.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.weathergis.automation.event.ThresholdViolatedEvent;
import vn.weathergis.automation.event.ThresholdViolatedEvent.Violation;
import vn.weathergis.automation.model.NearestStorm;
import vn.weathergis.automation.model.StationSnapshot;
import vn.weathergis.automation.model.ThresholdRule;
import vn.weathergis.automation.model.WeatherObservation;

/** Pure comparison of source facts; no cache, ingestion, AI calls or alert persistence. */
@Component
public class ThresholdEvaluator {

    public Optional<ThresholdViolatedEvent> evaluate(
            StationSnapshot station,
            List<ThresholdRule> rules,
            UUID evaluationCycleId,
            Instant evaluatedAt) {
        if (!Double.isFinite(station.longitude())
                || !Double.isFinite(station.latitude())
                || Math.abs(station.longitude()) > 180
                || Math.abs(station.latitude()) > 90) {
            return Optional.empty();
        }

        Map<String, Violation> violations = new LinkedHashMap<>();
        for (ThresholdRule rule : rules) {
            if (!rule.enabled() || rule.id() == null || !Double.isFinite(rule.thresholdValue())) {
                continue;
            }
            Measurement measurement = measurement(station, rule.parameter());
            if (measurement == null
                    || measurement.value() == null
                    || !Double.isFinite(measurement.value())
                    || measurement.timestamp() == null
                    || !matches(measurement.value(), rule.operator(), rule.thresholdValue())) {
                continue;
            }
            Instant window = measurement.timestamp();
            String key =
                    "v1:"
                            + station.locationId()
                            + ":"
                            + rule.id()
                            + ":"
                            + window
                            + (measurement.storm() == null
                                    ? ""
                                    : ":" + measurement.storm().stormId());
            NearestStorm storm = measurement.storm();
            violations.putIfAbsent(
                    key,
                    new Violation(
                            rule.id(),
                            rule.parameter(),
                            rule.operator(),
                            rule.thresholdValue(),
                            rule.severityLevel(),
                            measurement.value(),
                            measurement.unit(),
                            window,
                            storm == null ? null : storm.stormId(),
                            storm == null ? null : storm.name(),
                            storm == null ? null : storm.distanceKm(),
                            window,
                            key));
        }
        if (violations.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                new ThresholdViolatedEvent(
                        station.locationId(),
                        station.code(),
                        station.name(),
                        station.longitude(),
                        station.latitude(),
                        evaluationCycleId,
                        evaluatedAt,
                        new ArrayList<>(violations.values())));
    }

    private Measurement measurement(StationSnapshot station, String parameter) {
        if ("storm_dist".equals(parameter)) {
            NearestStorm storm = station.storm();
            return storm == null || storm.distanceKm() < 0 || storm.stormId() == null
                    ? null
                    : new Measurement(storm.distanceKm(), "km", storm.updatedAt(), storm);
        }
        WeatherObservation weather = station.weather();
        if (weather == null) {
            return null;
        }
        return switch (parameter) {
            case "rain_1h" -> new Measurement(weather.rain1h(), "mm", weather.recordedAt(), null);
            case "rain_3h" -> new Measurement(weather.rain3h(), "mm", weather.recordedAt(), null);
            case "wind_speed" ->
                    new Measurement(weather.windSpeed(), "m/s", weather.recordedAt(), null);
            case "wind_gust" ->
                    new Measurement(weather.windGust(), "m/s", weather.recordedAt(), null);
            default -> null;
        };
    }

    private boolean matches(double measured, String operator, double threshold) {
        return switch (operator) {
            case ">=" -> measured >= threshold;
            case ">" -> measured > threshold;
            case "<=" -> measured <= threshold;
            case "<" -> measured < threshold;
            case "=" -> measured == threshold;
            default -> false;
        };
    }

    private record Measurement(Double value, String unit, Instant timestamp, NearestStorm storm) {}
}
