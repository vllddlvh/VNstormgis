package vn.weathergis.location.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO phản hồi phân tích tình huống địa không gian toàn diện tại một tọa độ bất kỳ (ví dụ: Vị trí
 * hiện tại của người dùng qua GPS). Mọi chỉ số đều trích xuất trung thực từ CSDL PostGIS theo
 * SYS-RULE-004, không suy diễn số liệu.
 */
public record SpatialPointContextResponse(
        double longitude,
        double latitude,
        double radiusKm,
        boolean withinVietnamBounds,
        NearestStation nearestStation,
        CurrentWeatherSnapshot currentWeather,
        NearestActiveStorm nearestStorm,
        List<NearbyAlertSummary> nearbyAlerts) {

    public record NearestStation(
            UUID id,
            String code,
            String name,
            String region,
            double longitude,
            double latitude,
            double distanceKm) {}

    public record CurrentWeatherSnapshot(
            OffsetDateTime recordedAt,
            Double temperature,
            Double feelsLike,
            Double humidity,
            Double pressure,
            Double windSpeed,
            Double windGust,
            Double windDirection,
            Double rain1h,
            Double rain3h,
            String weatherCondition,
            String weatherDescription) {}

    public record NearestActiveStorm(
            String id,
            String name,
            String category,
            Double maxWindSpeedKmh,
            Double minPressure,
            double longitude,
            double latitude,
            double distanceKm,
            OffsetDateTime updatedAt) {}

    public record NearbyAlertSummary(
            UUID id,
            String title,
            String severityLevel,
            String locationCode,
            String locationName,
            double distanceKm,
            OffsetDateTime triggeredAt) {}
}
