package vn.weathergis.automation.model;

import java.time.Instant;

/** Latest station measurements with the source observation time, rather than sync time. */
public record WeatherObservation(
        Double rain1h, Double rain3h, Double windSpeed, Double windGust, Instant recordedAt) {}
