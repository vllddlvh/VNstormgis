package vn.weathergis.chat.repository;

import java.time.Instant;
import java.util.UUID;

/** Raw latest-observation row; {@code hasWeather=false} means the station has no record at all. */
public record StationObservationRow(
        UUID locationId,
        String code,
        String name,
        Double longitude,
        Double latitude,
        boolean hasWeather,
        Instant recordedAt,
        Double temperature,
        Double humidity,
        Double pressure,
        Double windSpeed,
        Double windGust,
        Double windDirection,
        Double rain1h,
        Double rain3h,
        String weatherCondition,
        String weatherDescription) {}
