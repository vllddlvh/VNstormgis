package vn.weathergis.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.weathergis.chat.context.ChatContextRequest;
import vn.weathergis.chat.dto.SendMessageRequest;
import vn.weathergis.chat.exception.ChatValidationException;
import vn.weathergis.common.exception.ErrorCode;

/**
 * VAL-CHT-001 + D1/DL-18 input rules (contract §2.3): message 1–500 code points after Unicode
 * strip, C0 rejected except tab/newline, alert id strict UUID, bbox [lng,lat]
 * finite/range/min&lt;max. Validation never silently swaps bbox axes and never "executes" question
 * content.
 */
class ChatMessageValidatorTest {

    private final ChatMessageValidator validator = new ChatMessageValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    private SendMessageRequest request(String message, String alertId, String spatialJson) {
        try {
            JsonNode spatial = spatialJson == null ? null : mapper.readTree(spatialJson);
            return new SendMessageRequest(message, alertId, spatial);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ChatValidationException expectValidation(
            String message, String alertId, String spatial) {
        Throwable thrown =
                org.assertj.core.api.Assertions.catchThrowable(
                        () -> validator.validate(request(message, alertId, spatial)));
        assertThat(thrown).isInstanceOf(ChatValidationException.class);
        return (ChatValidationException) thrown;
    }

    @Test
    void nullMessageIsRejected() {
        ChatValidationException ex = expectValidation(null, null, null);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CHAT_VALIDATION);
        assertThat(ex.getField()).isEqualTo("message");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n", "\u2003\u2003"})
    void blankOrWhitespaceOnlyMessageIsRejected(String message) {
        ChatValidationException ex = expectValidation(message, null, null);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CHAT_VALIDATION);
    }

    @Test
    void messageIsStrippedOfUnicodeWhitespaceBeforeStorage() {
        ChatContextRequest result =
                validator.validate(request("\u2003 Mưa to nhất ở đâu? \n\t", null, null));
        assertThat(result.message()).isEqualTo("Mưa to nhất ở đâu?");
    }

    @Test
    void exactly500CodePointsIsAccepted() {
        String message = "a".repeat(500);
        assertThat(validator.validate(request(message, null, null)).message()).isEqualTo(message);
    }

    @Test
    void codePointsAreCountedAfterStrip() {
        String message = "  " + "a".repeat(500) + "  ";
        assertThat(validator.validate(request(message, null, null)).message()).hasSize(500);
    }

    @Test
    void message501CodePointsIsRejected() {
        ChatValidationException ex = expectValidation("a".repeat(501), null, null);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CHAT_VALIDATION);
    }

    @Test
    void emojiCountsAsOneCodePointSo500EmojiFit() {
        String message = "😀".repeat(500);
        assertThat(message.length()).isEqualTo(1000);
        assertThat(validator.validate(request(message, null, null)).message()).isEqualTo(message);
    }

    @Test
    void emoji501CodePointsIsRejected() {
        expectValidation("😀".repeat(501), null, null);
    }

    @Test
    void vietnameseNfcAndNfdAreBothAcceptedWithoutNormalization() {
        String nfc = "Hà Nội";
        String nfd = java.text.Normalizer.normalize(nfc, java.text.Normalizer.Form.NFD);
        assertThat(nfd).isNotEqualTo(nfc);
        assertThat(validator.validate(request(nfc, null, null)).message()).isEqualTo(nfc);
        assertThat(validator.validate(request(nfd, null, null)).message()).isEqualTo(nfd);
    }

    @Test
    void tabAndNewlineAreAllowedInsideMessage() {
        String message = "Trạm nào\tmưa to\nnhất?";
        assertThat(validator.validate(request(message, null, null)).message()).isEqualTo(message);
    }

    @Test
    void c0ControlCharactersOtherThanTabNewlineAreRejected() {
        for (int c = 0x00; c <= 0x1F; c++) {
            if (c == '\t' || c == '\n') {
                continue;
            }
            ChatValidationException ex = expectValidation("câu" + (char) c + "hỏi", null, null);
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CHAT_VALIDATION);
        }
    }

    @Test
    void trailingC0WhitespaceIsStrippedRatherThanStored() {
        assertThat(validator.validate(request("câu hỏi\u000B", null, null)).message())
                .isEqualTo("câu hỏi");
    }

    @Test
    void nullAlertIdAndNullSpatialFilterAreAccepted() {
        ChatContextRequest result =
                validator.validate(request("Bão nào đang hoạt động?", null, null));
        assertThat(result.referencedAlertId()).isNull();
        assertThat(result.bbox()).isNull();
    }

    @Test
    void validAlertUuidIsParsed() {
        UUID alertId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        ChatContextRequest result =
                validator.validate(request("Vì sao có cảnh báo?", alertId.toString(), null));
        assertThat(result.referencedAlertId()).isEqualTo(alertId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "", "3fa85f64-5717-4562-b3fc", "DNG"})
    void malformedAlertIdIsRejectedWithDedicatedCode(String alertId) {
        ChatValidationException ex = expectValidation("câu hỏi", alertId, null);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CHAT_INVALID_ALERT_ID);
        assertThat(ex.getField()).isEqualTo("referenced_alert_id");
    }

    @Test
    void validBboxIsParsedInLngLatOrder() {
        ChatContextRequest result =
                validator.validate(request("câu hỏi", null, "{\"bbox\":[107.5,15.5,109.0,16.8]}"));
        assertThat(result.bbox()).isEqualTo(new ChatContextRequest.Bbox(107.5, 15.5, 109.0, 16.8));
    }

    @Test
    void extremeValidBboxIsAccepted() {
        ChatContextRequest result =
                validator.validate(request("câu hỏi", null, "{\"bbox\":[-180,-90,180,90]}"));
        assertThat(result.bbox()).isEqualTo(new ChatContextRequest.Bbox(-180, -90, 180, 90));
    }

    @Test
    void numericallyValidSwappedAxisBboxIsNotSilentlyReordered() {
        ChatContextRequest result =
                validator.validate(request("câu hỏi", null, "{\"bbox\":[50.0,10.0,60.0,20.0]}"));
        assertThat(result.bbox()).isEqualTo(new ChatContextRequest.Bbox(50.0, 10.0, 60.0, 20.0));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"bbox\":[109.0,15.5,107.5,16.8]}",
                "{\"bbox\":[107.5,16.8,109.0,15.5]}",
                "{\"bbox\":[107.5,15.5,107.5,16.8]}",
                "{\"bbox\":[107.5,15.5,109.0,15.5]}",
                "{\"bbox\":[-181,15.5,109.0,16.8]}",
                "{\"bbox\":[107.5,15.5,181,16.8]}",
                "{\"bbox\":[107.5,-91,109.0,16.8]}",
                "{\"bbox\":[107.5,15.5,109.0,91]}",
                "{\"bbox\":[107.5,15.5,109.0]}",
                "{\"bbox\":[107.5,15.5,109.0,16.8,1]}",
                "{\"bbox\":[\"107.5\",15.5,109.0,16.8]}",
                "{\"bbox\":[true,15.5,109.0,16.8]}",
                "{\"bbox\":[null,15.5,109.0,16.8]}",
                "{\"bbox\":\"107.5,15.5,109.0,16.8\"}",
                "{\"bbox\":null}",
                "{\"center\":[108.2,16.0],\"zoom\":8}",
                "{\"zoom\":8}",
                "[107.5,15.5,109.0,16.8]",
                "\"bbox\"",
                "{}"
            })
    void invalidSpatialFilterIsRejectedWithDedicatedCode(String spatialJson) {
        ChatValidationException ex = expectValidation("câu hỏi", null, spatialJson);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CHAT_INVALID_SPATIAL_FILTER);
        assertThat(ex.getField()).isEqualTo("spatial_filter");
    }

    @Test
    void nonFiniteBboxNumberIsRejected() {
        JsonNode bbox =
                mapper.createArrayNode()
                        .add(DoubleNode.valueOf(Double.NaN))
                        .add(15.5)
                        .add(109.0)
                        .add(16.8);
        JsonNode spatial = mapper.createObjectNode().set("bbox", bbox);
        assertThatThrownBy(
                        () -> validator.validate(new SendMessageRequest("câu hỏi", null, spatial)))
                .isInstanceOf(ChatValidationException.class)
                .satisfies(
                        ex ->
                                assertThat(((ChatValidationException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.CHAT_INVALID_SPATIAL_FILTER));
    }

    @Test
    void messageIsCheckedBeforeAlertIdAndSpatialFilter() {
        ChatValidationException ex = expectValidation("", "bad-id", "{\"zoom\":8}");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CHAT_VALIDATION);
        assertThat(ex.getField()).isEqualTo("message");
    }
}
