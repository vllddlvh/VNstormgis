package vn.weathergis.location.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.weathergis.location.domain.LocationEntity;

/** DTO phẳng hóa thông tin trạm quan trắc phục vụ các client REST tiêu chuẩn. */
public record LocationResponse(
        UUID id,
        String code,
        String name,
        String region,
        double longitude,
        double latitude,
        boolean isActive,
        OffsetDateTime updatedAt) {
    public static LocationResponse fromEntity(LocationEntity entity) {
        double lng = entity.getGeom() != null ? entity.getGeom().getX() : 0.0;
        double lat = entity.getGeom() != null ? entity.getGeom().getY() : 0.0;
        return new LocationResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getRegion(),
                lng,
                lat,
                Boolean.TRUE.equals(entity.getIsActive()),
                entity.getUpdatedAt());
    }
}
