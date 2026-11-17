package vn.weathergis.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.weathergis.chat.context.ChatContextRequest;
import vn.weathergis.chat.dto.SendMessageRequest;
import vn.weathergis.chat.exception.ChatValidationException;
import vn.weathergis.common.exception.ErrorCode;

/**
 * VAL-CHT-001 + D1/DL-18 input validation (contract §2.3). Runs before session auth and any
 * retrieval. The question is data, never an instruction: nothing here interprets or "executes" the
 * content. Bbox axes are never silently swapped — a numerically valid [lat,lng] box passes through
 * and yields honest no-data downstream (W5-02 IT #8).
 */
@Component
public class ChatMessageValidator {

    private static final int MAX_CODE_POINTS = 500;

    public ChatContextRequest validate(SendMessageRequest request) {
        String message = validateMessage(request.message());
        UUID alertId = validateAlertId(request.referencedAlertId());
        ChatContextRequest.Bbox bbox = validateSpatialFilter(request.spatialFilter());
        return new ChatContextRequest(message, alertId, bbox);
    }

    private String validateMessage(String raw) {
        if (raw == null) {
            throw new ChatValidationException(
                    ErrorCode.CHAT_VALIDATION, "message", "Câu hỏi không được để trống");
        }
        String stripped = raw.strip();
        if (stripped.isEmpty()) {
            throw new ChatValidationException(
                    ErrorCode.CHAT_VALIDATION, "message", "Câu hỏi không được để trống");
        }
        if (stripped.codePointCount(0, stripped.length()) > MAX_CODE_POINTS) {
            throw new ChatValidationException(
                    ErrorCode.CHAT_VALIDATION, "message", "Câu hỏi vượt quá 500 ký tự");
        }
        for (int i = 0; i < stripped.length(); i++) {
            char ch = stripped.charAt(i);
            if (ch < 0x20 && ch != '\t' && ch != '\n') {
                throw new ChatValidationException(
                        ErrorCode.CHAT_VALIDATION,
                        "message",
                        "Câu hỏi chứa ký tự điều khiển không được phép");
            }
        }
        return stripped;
    }

    private UUID validateAlertId(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ChatValidationException(
                    ErrorCode.CHAT_INVALID_ALERT_ID,
                    "referenced_alert_id",
                    "Mã cảnh báo phải là UUID hợp lệ");
        }
    }

    private ChatContextRequest.Bbox validateSpatialFilter(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw invalidSpatialFilter();
        }
        JsonNode bbox = node.get("bbox");
        if (bbox == null || !bbox.isArray() || bbox.size() != 4) {
            throw invalidSpatialFilter();
        }
        double[] values = new double[4];
        for (int i = 0; i < 4; i++) {
            JsonNode value = bbox.get(i);
            if (!value.isNumber()) {
                throw invalidSpatialFilter();
            }
            values[i] = value.doubleValue();
            if (!Double.isFinite(values[i])) {
                throw invalidSpatialFilter();
            }
        }
        double minLng = values[0];
        double minLat = values[1];
        double maxLng = values[2];
        double maxLat = values[3];
        if (minLng < -180 || maxLng > 180 || minLat < -90 || maxLat > 90) {
            throw invalidSpatialFilter();
        }
        if (minLng >= maxLng || minLat >= maxLat) {
            throw invalidSpatialFilter();
        }
        return new ChatContextRequest.Bbox(minLng, minLat, maxLng, maxLat);
    }

    private ChatValidationException invalidSpatialFilter() {
        return new ChatValidationException(
                ErrorCode.CHAT_INVALID_SPATIAL_FILTER,
                "spatial_filter",
                "spatial_filter phải là object chứa bbox [minLng, minLat, maxLng, maxLat] hợp lệ");
    }
}
