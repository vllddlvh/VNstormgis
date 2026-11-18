package vn.weathergis.subscription.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateSubscriptionRequest(
        @NotBlank(message = "Địa chỉ email không được để trống")
                @Email(message = "Địa chỉ email không đúng định dạng")
                @Size(max = 255, message = "Địa chỉ email tối đa 255 ký tự")
                String email,
        @NotBlank(message = "Phạm vi đăng ký (scopeType) bắt buộc là STATION hoặc COORDINATE")
                String scopeType,
        UUID locationId,
        String locationCode,
        Double longitude,
        Double latitude,
        @Size(max = 160, message = "Nhãn khu vực tối đa 160 ký tự") String label,
        Double radiusKm,
        String minSeverity) {}
