package vn.weathergis.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenActionRequest(
        @NotBlank(message = "Mã token không được để trống")
                @Size(max = 128, message = "Mã token không hợp lệ")
                String token) {}
