package vn.weathergis.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** DTO kết quả tìm kiếm trạm lân cận kèm khoảng cách trắc địa đường chim bay. */
@Schema(
        description =
                "Kết quả trạm quan trắc lân cận kèm khoảng cách tính toán bởi ST_DistanceSphere")
public record LocationNearbyResponse(
        @Schema(
                        description = "Khóa chính UUID của trạm",
                        example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
                UUID id,
        @Schema(description = "Mã trạm trắc địa", example = "HAN") String code,
        @Schema(description = "Tên trạm quan trắc", example = "Hà Nội") String name,
        @Schema(description = "Vùng địa lý", example = "Bắc Bộ") String region,
        @Schema(description = "Kinh độ WGS84", example = "105.8542") double longitude,
        @Schema(description = "Vĩ độ WGS84", example = "21.0285") double latitude,
        @Schema(description = "Khoảng cách đường chim bay tính bằng Kilometers", example = "12.45")
                double distanceKm) {}
