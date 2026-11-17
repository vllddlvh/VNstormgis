package vn.weathergis.chat.exception;

import vn.weathergis.common.exception.ErrorCode;

/**
 * Referenced alert UUID is well-formed but does not exist (D9, outcome ALERT_NOT_FOUND of W5-02).
 */
public class ChatAlertNotFoundException extends ChatException {

    public ChatAlertNotFoundException() {
        super(
                ErrorCode.CHAT_ALERT_NOT_FOUND,
                "not-found",
                "Không tìm thấy cảnh báo với mã định danh yêu cầu");
    }
}
