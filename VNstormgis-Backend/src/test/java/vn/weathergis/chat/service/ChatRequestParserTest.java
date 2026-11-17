package vn.weathergis.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.HttpMessageNotReadableException;
import vn.weathergis.chat.dto.SendMessageRequest;
import vn.weathergis.chat.exception.ChatValidationException;
import vn.weathergis.common.exception.ErrorCode;

/**
 * 8 KiB body cap (contract §2.3): Content-Length pre-check AND bounded read of the actual stream so
 * chunked requests (length -1) are covered; oversize rejects before parse, broken JSON routes to
 * HttpMessageNotReadableException for the handler's chat branch.
 */
@ExtendWith(MockitoExtension.class)
class ChatRequestParserTest {

    @Mock private HttpServletRequest request;

    private ChatRequestParser parser;

    @BeforeEach
    void setUp() {
        parser = new ChatRequestParser(new ObjectMapper());
    }

    private void stubBody(byte[] body, long contentLength) throws Exception {
        when(request.getContentLengthLong()).thenReturn(contentLength);
        if (contentLength <= ChatRequestParser.MAX_BODY_BYTES) {
            when(request.getInputStream())
                    .thenReturn(
                            new jakarta.servlet.ServletInputStream() {
                                private final ByteArrayInputStream delegate =
                                        new ByteArrayInputStream(body);

                                @Override
                                public int read() {
                                    return delegate.read();
                                }

                                @Override
                                public boolean isFinished() {
                                    return delegate.available() == 0;
                                }

                                @Override
                                public boolean isReady() {
                                    return true;
                                }

                                @Override
                                public void setReadListener(
                                        jakarta.servlet.ReadListener listener) {}
                            });
        }
    }

    @Test
    void contentLengthOver8KiBIsRejectedWithoutReadingTheStream() throws Exception {
        when(request.getContentLengthLong()).thenReturn(8193L);

        assertThatThrownBy(() -> parser.parse(request))
                .isInstanceOf(ChatValidationException.class)
                .satisfies(
                        ex -> {
                            ChatValidationException v = (ChatValidationException) ex;
                            assertThat(v.getErrorCode()).isEqualTo(ErrorCode.CHAT_VALIDATION);
                            assertThat(v.getField()).isEqualTo("body");
                        });
        org.mockito.Mockito.verify(request, org.mockito.Mockito.never()).getInputStream();
    }

    @Test
    void chunkedBodyOver8KiBWithoutContentLengthIsRejectedByBoundedRead() throws Exception {
        byte[] oversize =
                ("{\"message\":\"" + "a".repeat(8200) + "\"}").getBytes(StandardCharsets.UTF_8);
        stubBody(oversize, -1);

        assertThatThrownBy(() -> parser.parse(request))
                .isInstanceOf(ChatValidationException.class)
                .satisfies(
                        ex ->
                                assertThat(((ChatValidationException) ex).getField())
                                        .isEqualTo("body"));
    }

    @Test
    void chunkedBodyExactly8KiBIsParsed() throws Exception {
        String prefix = "{\"message\":\"x\",\"padding\":\"";
        String suffix = "\"}";
        int padding =
                8192
                        - prefix.getBytes(StandardCharsets.UTF_8).length
                        - suffix.getBytes(StandardCharsets.UTF_8).length;
        byte[] body = (prefix + "p".repeat(padding) + suffix).getBytes(StandardCharsets.UTF_8);
        assertThat(body).hasSize(8192);
        stubBody(body, -1);

        SendMessageRequest parsed = parser.parse(request);
        assertThat(parsed.message()).isEqualTo("x");
    }

    @Test
    void validSmallBodyIsParsedWithUnknownFieldsIgnored() throws Exception {
        byte[] body = "{\"message\":\"Câu hỏi?\",\"extra\":1}".getBytes(StandardCharsets.UTF_8);
        stubBody(body, body.length);

        SendMessageRequest parsed = parser.parse(request);
        assertThat(parsed.message()).isEqualTo("Câu hỏi?");
    }

    @Test
    void brokenJsonBecomesHttpMessageNotReadable() throws Exception {
        byte[] body = "{not json".getBytes(StandardCharsets.UTF_8);
        stubBody(body, body.length);

        assertThatThrownBy(() -> parser.parse(request))
                .isInstanceOf(HttpMessageNotReadableException.class);
    }

    @Test
    void emptyBodyBecomesHttpMessageNotReadable() throws Exception {
        stubBody(new byte[0], 0);

        assertThatThrownBy(() -> parser.parse(request))
                .isInstanceOf(HttpMessageNotReadableException.class);
    }
}
