package vn.weathergis.alert.exception;

import vn.weathergis.common.exception.ErrorCode;

/** Missing or invalid credentials for threshold administration. */
public class AdminUnauthorizedException extends RuntimeException {

    public AdminUnauthorizedException() {
        super(ErrorCode.ALERT_THRESHOLD_UNAUTHORIZED.getDefaultMessage());
    }

    public ErrorCode getErrorCode() {
        return ErrorCode.ALERT_THRESHOLD_UNAUTHORIZED;
    }
}
