package vn.weathergis.chat.repository;

import java.time.Instant;

/** Raw active-storm row with at most one nearest forecast track point (W5-02 §4.3). */
public record StormSummaryRow(
        String stormId,
        String name,
        String category,
        Double maxWindSpeed,
        Double minPressure,
        Double longitude,
        Double latitude,
        Instant updatedAt,
        boolean hasForecast,
        Instant forecastTime,
        String trackType,
        Double forecastLongitude,
        Double forecastLatitude,
        Double forecastWindSpeed,
        Double forecastWindGust,
        Double forecastPressure,
        String forecastCategory) {}
