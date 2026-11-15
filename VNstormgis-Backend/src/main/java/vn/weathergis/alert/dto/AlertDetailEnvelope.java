package vn.weathergis.alert.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.weathergis.common.api.ApiResponse;

/** OpenAPI schema for the alert detail response; the controller still returns ApiResponse. */
@Schema(
        description = "Phong bao phản hồi chứa chi tiết cảnh báo",
        requiredProperties = {"success", "message", "data", "timestamp"})
public class AlertDetailEnvelope extends ApiResponse<AlertDetailResponse> {}
