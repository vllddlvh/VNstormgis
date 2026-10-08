package vn.weathergis.common.exception;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Khung xử lý lỗi toàn cục theo chuẩn quốc tế RFC 7807 Problem Details. Đảm bảo mọi lỗi trả về từ
 * API đều có cấu trúc nhất quán, dễ debug cho Frontend.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE_URI = "https://weathergis.vn/errors/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorCode ec = ex.getErrorCode();

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(ec.getHttpStatus(), ex.getMessage());
        problemDetail.setType(URI.create(ERROR_BASE_URI + "not-found"));
        problemDetail.setTitle("Tài nguyên không tồn tại");
        enrichProblemDetail(problemDetail, ec.getCode(), request);

        return ResponseEntity.status(ec.getHttpStatus()).body(problemDetail);
    }

    @ExceptionHandler(SpatialOperationException.class)
    public ResponseEntity<ProblemDetail> handleSpatialOperationException(
            SpatialOperationException ex, WebRequest request) {
        log.error("Spatial operation failure: {}", ex.getMessage(), ex);
        ErrorCode ec = ex.getErrorCode();

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(ec.getHttpStatus(), ex.getMessage());
        problemDetail.setType(URI.create(ERROR_BASE_URI + "spatial-error"));
        problemDetail.setTitle("Lỗi xử lý trắc địa không gian");
        enrichProblemDetail(problemDetail, ec.getCode(), request);

        return ResponseEntity.status(ec.getHttpStatus()).body(problemDetail);
    }

    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ProblemDetail> handleInvalidParameterException(
            InvalidParameterException ex, WebRequest request) {
        log.warn("Invalid parameter: {}", ex.getMessage());
        ErrorCode ec = ex.getErrorCode();

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(ec.getHttpStatus(), ex.getMessage());
        problemDetail.setType(URI.create(ERROR_BASE_URI + "invalid-parameter"));
        problemDetail.setTitle("Tham số không hợp lệ");
        enrichProblemDetail(problemDetail, ec.getCode(), request);

        return ResponseEntity.status(ec.getHttpStatus()).body(problemDetail);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.warn("Validation failure: {}", ex.getMessage());

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu vi phạm quy tắc ràng buộc");
        problemDetail.setType(URI.create(ERROR_BASE_URI + "validation-error"));
        problemDetail.setTitle("Dữ liệu không hợp lệ");

        List<Map<String, String>> invalidParams = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> param = new HashMap<>();
            param.put("field", fieldError.getField());
            param.put("message", fieldError.getDefaultMessage());
            invalidParams.add(param);
        }
        problemDetail.setProperty("invalidParams", invalidParams);
        enrichProblemDetail(problemDetail, ErrorCode.VALIDATION_ERROR.getCode(), request);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Unhandled internal server error: {}", ex.getMessage(), ex);

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Hệ thống gặp sự cố ngoài dự kiến. Vui lòng thử lại sau.");
        problemDetail.setType(URI.create(ERROR_BASE_URI + "internal-server-error"));
        problemDetail.setTitle("Lỗi hệ thống nội bộ");
        enrichProblemDetail(problemDetail, ErrorCode.INTERNAL_SERVER_ERROR.getCode(), request);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    private void enrichProblemDetail(
            ProblemDetail problemDetail, String errorCode, WebRequest request) {
        problemDetail.setProperty("errorCode", errorCode);
        problemDetail.setProperty("timestamp", OffsetDateTime.now());
        if (request instanceof ServletWebRequest servletWebRequest) {
            problemDetail.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
        }
    }
}
