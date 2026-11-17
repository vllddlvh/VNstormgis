package vn.weathergis.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import vn.weathergis.chat.dto.SendMessageRequest;
import vn.weathergis.chat.exception.ChatValidationException;
import vn.weathergis.common.exception.ErrorCode;

/**
 * Chat-only 8 KiB body cap (contract §2.3): checks Content-Length AND the bytes actually read, so
 * chunked requests without Content-Length are covered too. Oversize is rejected before JSON parsing
 * and before session auth; unreadable JSON is routed through GlobalExceptionHandler's
 * handleHttpMessageNotReadable chat branch.
 */
@Component
public class ChatRequestParser {

    static final int MAX_BODY_BYTES = 8192;

    private final ObjectMapper objectMapper;

    public ChatRequestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SendMessageRequest parse(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw oversize();
        }
        byte[] body = readBounded(request.getInputStream());
        try {
            return objectMapper
                    .reader()
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(body, SendMessageRequest.class);
        } catch (JsonProcessingException e) {
            throw new HttpMessageNotReadableException(
                    "Nội dung JSON không hợp lệ hoặc không đọc được", e);
        }
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(chunk)) != -1) {
            total += read;
            if (total > MAX_BODY_BYTES) {
                throw oversize();
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private ChatValidationException oversize() {
        return new ChatValidationException(
                ErrorCode.CHAT_VALIDATION, "body", "Kích thước body vượt quá 8192 byte");
    }
}
