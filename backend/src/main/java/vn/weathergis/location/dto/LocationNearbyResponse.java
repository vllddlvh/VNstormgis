package vn.weathergis.location.dto;

import java.util.UUID;

/** DTO kết quả tìm kiếm trạm lân cận kèm khoảng cách trắc địa đường chim bay. */
public record LocationNearbyResponse(
        UUID id,
        String code,
        String name,
        String region,
        double longitude,
        double latitude,
        double distanceKm) {}
