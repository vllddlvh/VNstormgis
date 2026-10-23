package vn.weathergis.common.exception;

import org.springframework.http.HttpStatus;

/** Danh mục mã lỗi định lượng toàn hệ thống WeatherGIS. */
public enum ErrorCode {

    // Common errors (COM_xxx)
    INTERNAL_SERVER_ERROR(
            "COM_500_INTERNAL_ERROR",
            "Lỗi xử lý nội bộ hệ thống",
            HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_PARAMETER(
            "COM_400_INVALID_PARAM", "Tham số yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(
            "COM_400_VALIDATION_ERROR", "Dữ liệu xác thực không hợp lệ", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(
            "COM_404_NOT_FOUND", "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),

    // Location Domain errors (LOC_xxx)
    LOCATION_NOT_FOUND(
            "LOC_404_NOT_FOUND", "Không tìm thấy trạm quan trắc yêu cầu", HttpStatus.NOT_FOUND),
    LOCATION_CODE_DUPLICATE(
            "LOC_409_DUPLICATE_CODE",
            "Mã trạm quan trắc đã tồn tại trên hệ thống",
            HttpStatus.CONFLICT),
    SPATIAL_QUERY_FAILED(
            "LOC_400_SPATIAL_ERROR",
            "Lỗi thực thi toán học không gian địa lý",
            HttpStatus.BAD_REQUEST),

    // Automation / Sync Domain errors (SYN_xxx)
    SYNC_UNAUTHORIZED(
            "SYN_401_INVALID_SECRET",
            "Tham số bí mật nội bộ không hợp lệ",
            HttpStatus.UNAUTHORIZED),
    SYNC_IN_PROGRESS(
            "SYN_409_SYNC_IN_PROGRESS",
            "Tiến trình đồng bộ đang chạy, vui lòng thử lại sau",
            HttpStatus.CONFLICT),

    // Weather Domain errors (WTH_xxx)
    WEATHER_NOT_FOUND(
            "WTH_404_NOT_FOUND",
            "Không tìm thấy trạm quan trắc với mã yêu cầu",
            HttpStatus.NOT_FOUND);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
