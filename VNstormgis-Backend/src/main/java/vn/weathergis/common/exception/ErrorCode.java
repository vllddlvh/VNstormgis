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
            HttpStatus.NOT_FOUND),

    // Alert Threshold Domain errors (ALT_xxx)
    ALERT_THRESHOLD_UNAUTHORIZED(
            "ALT_401_UNAUTHORIZED", "Khóa quản trị không hợp lệ", HttpStatus.UNAUTHORIZED),
    ALERT_THRESHOLD_NOT_FOUND(
            "ALT_404_NOT_FOUND", "Không tìm thấy ngưỡng cảnh báo", HttpStatus.NOT_FOUND),
    ALERT_THRESHOLD_INVALID_PARAMETER(
            "ALT_400_INVALID_PARAMETER",
            "Tham số ngưỡng cảnh báo không hợp lệ",
            HttpStatus.BAD_REQUEST),
    ALERT_THRESHOLD_VALIDATION(
            "ALT_400_VALIDATION", "Dữ liệu ngưỡng cảnh báo không hợp lệ", HttpStatus.BAD_REQUEST),

    // Alert Domain errors (ALR_xxx)
    ALERT_NOT_FOUND(
            "ALR_404_NOT_FOUND",
            "Không tìm thấy cảnh báo với mã định danh yêu cầu",
            HttpStatus.NOT_FOUND),
    ALERT_INVALID_ID(
            "ALR_400_INVALID_ID",
            "Mã định danh cảnh báo không đúng định dạng UUID",
            HttpStatus.BAD_REQUEST),

    // Storm Domain errors (STM_xxx)
    STORM_NOT_FOUND(
            "STM_404_NOT_FOUND",
            "Không tìm thấy cơn bão với mã sự kiện yêu cầu",
            HttpStatus.NOT_FOUND),
    STORM_INVALID_STATUS(
            "STM_400_INVALID_STATUS",
            "Giá trị tham số status không hợp lệ (chấp nhận: active, dissipated, all)",
            HttpStatus.BAD_REQUEST),

    // Chat Domain errors (CHT_xxx) — hợp đồng W5-01 §2.5.
    CHAT_VALIDATION("CHT_400_VALIDATION", "Dữ liệu câu hỏi không hợp lệ", HttpStatus.BAD_REQUEST),
    CHAT_INVALID_ALERT_ID(
            "CHT_400_INVALID_ALERT_ID",
            "Mã định danh cảnh báo không đúng định dạng UUID",
            HttpStatus.BAD_REQUEST),
    CHAT_INVALID_SPATIAL_FILTER(
            "CHT_400_INVALID_SPATIAL_FILTER",
            "Bộ lọc không gian không hợp lệ",
            HttpStatus.BAD_REQUEST),
    CHAT_SESSION_TOKEN_MISSING(
            "CHT_401_SESSION_TOKEN_MISSING",
            "Thiếu token phiên hội thoại",
            HttpStatus.UNAUTHORIZED),
    CHAT_SESSION_NOT_FOUND(
            "CHT_404_SESSION_NOT_FOUND", "Không tìm thấy phiên hội thoại", HttpStatus.NOT_FOUND),
    CHAT_ALERT_NOT_FOUND(
            "CHT_404_ALERT_NOT_FOUND",
            "Không tìm thấy cảnh báo với mã định danh yêu cầu",
            HttpStatus.NOT_FOUND),
    CHAT_RATE_LIMITED(
            "CHT_429_RATE_LIMITED", "Vượt hạn mức gửi câu hỏi", HttpStatus.TOO_MANY_REQUESTS),
    CHAT_AI_UNAVAILABLE(
            "CHT_503_AI_UNAVAILABLE",
            "Hệ thống trợ lý AI tạm thời gián đoạn",
            HttpStatus.SERVICE_UNAVAILABLE),
    CHAT_RATE_LIMITER_UNAVAILABLE(
            "CHT_503_RATE_LIMITER_UNAVAILABLE",
            "Bộ phận hạn mức tạm thời gián đoạn",
            HttpStatus.SERVICE_UNAVAILABLE),

    // Email Subscription Domain errors (SUB_xxx)
    SUBSCRIPTION_VALIDATION(
            "SUB_400_VALIDATION",
            "Dữ liệu đăng ký nhận cảnh báo email không hợp lệ",
            HttpStatus.BAD_REQUEST),
    SUBSCRIPTION_INVALID_TOKEN(
            "SUB_400_INVALID_TOKEN",
            "Mã xác thực hoặc liên kết hủy đăng ký không hợp lệ hoặc đã hết hạn",
            HttpStatus.BAD_REQUEST),
    SUBSCRIPTION_NOT_ACTIVE(
            "SUB_400_NOT_ACTIVE",
            "Đăng ký chưa được kích hoạt xác thực email",
            HttpStatus.BAD_REQUEST),
    SUBSCRIPTION_NOT_FOUND(
            "SUB_404_NOT_FOUND",
            "Không tìm thấy đăng ký nhận cảnh báo email",
            HttpStatus.NOT_FOUND),
    SUBSCRIPTION_RATE_LIMITED(
            "SUB_429_RATE_LIMITED",
            "Gửi yêu cầu quá tần suất cho phép, vui lòng thử lại sau ít phút",
            HttpStatus.TOO_MANY_REQUESTS);

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
