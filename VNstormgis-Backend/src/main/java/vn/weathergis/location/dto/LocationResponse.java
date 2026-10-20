package vn.weathergis.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import vn.weathergis.location.domain.LocationEntity;

/** DTO phẳng hóa thông tin trạm quan trắc phục vụ các client REST tiêu chuẩn. */
@Schema(description = "Thông tin chi tiết điểm trắc địa / trạm quan trắc khí tượng")
public record LocationResponse(
        @Schema(
                        description = "Khóa chính UUID duy nhất",
                        example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
                UUID id,
        @Schema(description = "Mã ký hiệu trắc địa 3 ký tự", example = "HAN") String code,
        @Schema(description = "Tên địa danh trạm quan trắc", example = "Hà Nội") String name,
        @Schema(description = "Vùng địa lý hành chính", example = "Bắc Bộ") String region,
        @Schema(description = "Kinh độ trắc địa WGS84 (longitude)", example = "105.8542")
                double longitude,
        @Schema(description = "Vĩ độ trắc địa WGS84 (latitude)", example = "21.0285")
                double latitude,
        @Schema(description = "Trạng thái hoạt động giám sát", example = "true") boolean isActive,
        @Schema(
                        description = "Dấu thời gian cập nhật gần nhất theo chuẩn ISO 8601",
                        example = "2026-10-08T18:00:00+07:00")
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
