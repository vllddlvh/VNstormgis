package vn.weathergis.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Phong bao chuẩn hóa phản hồi RESTful API cho toàn bộ hệ thống WeatherGIS.
 *
 * @param <T> Kiểu dữ liệu thực thể payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Phong bao phản hồi chuẩn RESTful Envelope cho mọi API")
public class ApiResponse<T> {

    @Schema(
            description = "Chỉ thị trạng thái xử lý nghiệp vụ thành công hay thất bại",
            example = "true")
    private boolean success;

    @Schema(
            description = "Thông điệp mô tả kết quả xử lý dành cho giao diện người dùng",
            example = "Thao tác thành công")
    private String message;

    @Schema(description = "Dữ liệu payload thực tế trả về")
    private T data;

    @Schema(
            description = "Thời điểm phản hồi theo chuẩn ISO 8601",
            example = "2026-10-08T18:00:00+07:00")
    private OffsetDateTime timestamp;

    public ApiResponse() {
        this.timestamp = OffsetDateTime.now();
    }

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = OffsetDateTime.now();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "Thành công", data);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
