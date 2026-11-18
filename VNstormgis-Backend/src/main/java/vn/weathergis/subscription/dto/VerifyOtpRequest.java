package vn.weathergis.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank(message = "Mã xác thực OTP không được để trống")
                @Pattern(regexp = "^\\d{6}$", message = "Mã xác thực OTP phải gồm đúng 6 chữ số")
                String otp) {}
