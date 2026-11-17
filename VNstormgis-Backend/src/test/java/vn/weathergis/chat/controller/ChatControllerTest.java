package vn.weathergis.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.weathergis.chat.context.FactualContext;
import vn.weathergis.chat.dto.ChatMessageView;
import vn.weathergis.chat.dto.CreateSessionResponse;
import vn.weathergis.chat.dto.SendMessageResponse;
import vn.weathergis.chat.exception.ChatAiUnavailableException;
import vn.weathergis.chat.exception.ChatAlertNotFoundException;
import vn.weathergis.chat.exception.ChatRateLimitedException;
import vn.weathergis.chat.exception.ChatRateLimiterUnavailableException;
import vn.weathergis.chat.exception.ChatSessionNotFoundException;
import vn.weathergis.chat.exception.ChatSessionTokenMissingException;
import vn.weathergis.chat.exception.ChatValidationException;
import vn.weathergis.chat.service.ChatMessageService;
import vn.weathergis.chat.service.ChatRequestParser;
import vn.weathergis.chat.service.ChatSessionService;
import vn.weathergis.common.config.CorsConfig;
import vn.weathergis.common.config.JacksonConfig;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.GlobalExceptionHandler;

/**
 * HTTP contract W5-01 §2 (D1/D9): three canonical endpoints, X-Session-Token header, ApiResponse
 * envelope with UTC {@code ...Z} chat timestamps, RFC 7807 CHT_* error matrix with invalidParams, 8
 * KiB body cap (Content-Length and chunked), and no Retry-After outside the W5-05 429 branch.
 */
@WebMvcTest(controllers = ChatController.class)
@Import({
    GlobalExceptionHandler.class,
    JacksonConfig.class,
    CorsConfig.class,
    ChatRequestParser.class
})
class ChatControllerTest {

    private static final String BASE = "/api/v1/chat/sessions";
    private static final UUID SESSION_ID = UUID.fromString("7c8e9f10-1234-5678-abcd-ef0123456789");
    private static final UUID MESSAGE_ID = UUID.fromString("9e0f1a22-3456-789a-cdef-0123456789ab");
    private static final UUID ALERT_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final Instant CREATED = Instant.parse("2026-11-17T09:00:00Z");
    private static final String MESSAGES = BASE + "/" + SESSION_ID + "/messages";

    @Autowired private MockMvc mockMvc;
    @MockBean private ChatSessionService sessionService;
    @MockBean private ChatMessageService messageService;

    private SendMessageResponse reply() {
        return new SendMessageResponse(
                MESSAGE_ID,
                "Trả lời mẫu",
                false,
                new FactualContext.ReferencedEntities(List.of("DNG"), List.of(), List.of(ALERT_ID)),
                CREATED);
    }

    private void stubSend(SendMessageResponse response) {
        when(messageService.sendMessage(eq(SESSION_ID), eq("token-a"), any(), any(), any()))
                .thenReturn(response);
    }

    @Test
    void createSessionReturns201EnvelopeWithOneTimeTokenAndUtcCreatedAt() throws Exception {
        when(sessionService.createSession())
                .thenReturn(
                        new CreateSessionResponse(
                                SESSION_ID, "t".repeat(43), "Hội thoại mới", CREATED));

        mockMvc.perform(post(BASE).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Khởi tạo phiên hội thoại AI thành công"))
                .andExpect(jsonPath("$.data.session_id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.data.session_token").value("t".repeat(43)))
                .andExpect(jsonPath("$.data.title").value("Hội thoại mới"))
                .andExpect(jsonPath("$.data.created_at").value("2026-11-17T09:00:00Z"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void createSessionIgnoresClientSuppliedBodyAndToken() throws Exception {
        when(sessionService.createSession())
                .thenReturn(
                        new CreateSessionResponse(
                                SESSION_ID, "t".repeat(43), "Hội thoại mới", CREATED));

        mockMvc.perform(
                        post(BASE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"session_token\":\"client-hack\",\"title\":\"x\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.session_token").value("t".repeat(43)));
    }

    @Test
    void sendMessageReturns200EnvelopeWithReferencedEntitiesFromContext() throws Exception {
        stubSend(reply());

        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"Câu hỏi?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message_id").value(MESSAGE_ID.toString()))
                .andExpect(jsonPath("$.data.reply").value("Trả lời mẫu"))
                .andExpect(jsonPath("$.data.is_fallback").value(false))
                .andExpect(jsonPath("$.data.referenced_entities.stations[0]").value("DNG"))
                .andExpect(
                        jsonPath("$.data.referenced_entities.alert_ids[0]")
                                .value(ALERT_ID.toString()))
                .andExpect(jsonPath("$.data.created_at").value("2026-11-17T09:00:00Z"));
    }

    @Test
    void joinsAllForwardedForHeadersBeforeIpResolution() throws Exception {
        stubSend(reply());
        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .header("X-Forwarded-For", "198.51.100.1")
                                .header("X-Forwarded-For", "10.0.0.1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"x\"}"))
                .andExpect(status().isOk());
        verify(messageService)
                .sendMessage(
                        eq(SESSION_ID), eq("token-a"), any(), any(), eq("198.51.100.1,10.0.0.1"));
    }

    @Test
    void missingTokenHeaderIs401WithRfc7807Shape() throws Exception {
        when(messageService.sendMessage(eq(SESSION_ID), isNull(), any(), any(), any()))
                .thenThrow(new ChatSessionTokenMissingException());

        mockMvc.perform(
                        post(MESSAGES)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"Câu hỏi?\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("CHT_401_SESSION_TOKEN_MISSING"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.type").value("https://weathergis.vn/errors/unauthorized"))
                .andExpect(jsonPath("$.instance").value(MESSAGES))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void wrongTokenIsUniform404() throws Exception {
        when(messageService.sendMessage(eq(SESSION_ID), eq("wrong"), any(), any(), any()))
                .thenThrow(new ChatSessionNotFoundException());

        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "wrong")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"Câu hỏi?\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CHT_404_SESSION_NOT_FOUND"));
    }

    @Test
    void validationErrorCarriesInvalidParams() throws Exception {
        when(messageService.sendMessage(eq(SESSION_ID), eq("token-a"), any(), any(), any()))
                .thenThrow(
                        new ChatValidationException(
                                ErrorCode.CHAT_VALIDATION,
                                "message",
                                "Câu hỏi vượt quá 500 ký tự"));

        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHT_400_VALIDATION"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("message"))
                .andExpect(
                        jsonPath("$.invalidParams[0].message").value("Câu hỏi vượt quá 500 ký tự"));
    }

    @Test
    void invalidAlertIdAndSpatialFilterUseDedicatedCodes() throws Exception {
        when(messageService.sendMessage(eq(SESSION_ID), eq("token-a"), any(), any(), any()))
                .thenThrow(
                        new ChatValidationException(
                                ErrorCode.CHAT_INVALID_ALERT_ID, "referenced_alert_id", "UUID"))
                .thenThrow(
                        new ChatValidationException(
                                ErrorCode.CHAT_INVALID_SPATIAL_FILTER, "spatial_filter", "bbox"));

        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHT_400_INVALID_ALERT_ID"));
        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHT_400_INVALID_SPATIAL_FILTER"));
    }

    @Test
    void alertNotFoundIs404AndAiUnavailableIs503() throws Exception {
        when(messageService.sendMessage(eq(SESSION_ID), eq("token-a"), any(), any(), any()))
                .thenThrow(new ChatAlertNotFoundException())
                .thenThrow(new ChatAiUnavailableException());

        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CHT_404_ALERT_NOT_FOUND"));
        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"x\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("CHT_503_AI_UNAVAILABLE"));
    }

    @Test
    void rateLimitedBranchHasMatchingRetryAfterHeaderAndExtension() throws Exception {
        when(messageService.sendMessage(eq(SESSION_ID), eq("token-a"), any(), any(), any()))
                .thenThrow(new ChatRateLimitedException("session", 37));

        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("CHT_429_RATE_LIMITED"))
                .andExpect(header().string("Retry-After", "37"))
                .andExpect(jsonPath("$.retry_after_seconds").value(37))
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("chờ 37 giây")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void limiterOutageHas503WithoutRetryAfter() throws Exception {
        when(messageService.sendMessage(eq(SESSION_ID), eq("token-a"), any(), any(), any()))
                .thenThrow(new ChatRateLimiterUnavailableException());

        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"x\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("CHT_503_RATE_LIMITER_UNAVAILABLE"))
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void malformedSessionUuidPathIs400ChatValidation() throws Exception {
        mockMvc.perform(
                        post(BASE + "/not-a-uuid/messages")
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHT_400_VALIDATION"));
    }

    @Test
    void bodyOver8KiBWithContentLengthIsRejectedBeforeService() throws Exception {
        byte[] oversize =
                ("{\"message\":\"" + "a".repeat(8200) + "\"}").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(oversize))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHT_400_VALIDATION"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("body"));
        verifyNoInteractions(messageService);
    }

    @Test
    void bodyExactly8KiBIsAccepted() throws Exception {
        stubSend(reply());
        String prefix = "{\"message\":\"x\",\"padding\":\"";
        String suffix = "\"}";
        int padding =
                8192
                        - prefix.getBytes(StandardCharsets.UTF_8).length
                        - suffix.getBytes(StandardCharsets.UTF_8).length;
        String body = prefix + "p".repeat(padding) + suffix;
        assertThat(body.getBytes(StandardCharsets.UTF_8)).hasSize(8192);

        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());
        verify(messageService).sendMessage(eq(SESSION_ID), eq("token-a"), any(), any(), any());
    }

    @Test
    void brokenJsonIs400ChatValidationWithInvalidParams() throws Exception {
        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHT_400_VALIDATION"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("body"));
        verifyNoInteractions(messageService);
    }

    @Test
    void unknownBodyFieldsAreIgnored() throws Exception {
        stubSend(reply());

        mockMvc.perform(
                        post(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"x\",\"unknown_field\":123}"))
                .andExpect(status().isOk());
    }

    @Test
    void historyReturnsArrayWithDefaultLimitAndNoTelemetryFields() throws Exception {
        when(messageService.getHistory(SESSION_ID, "token-a", 50, null))
                .thenReturn(
                        List.of(
                                new ChatMessageView(
                                        MESSAGE_ID, "user", "Câu hỏi", ALERT_ID, false, CREATED),
                                new ChatMessageView(
                                        UUID.randomUUID(),
                                        "assistant",
                                        "Trả lời",
                                        null,
                                        true,
                                        CREATED)));

        mockMvc.perform(get(MESSAGES).header("X-Session-Token", "token-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(MESSAGE_ID.toString()))
                .andExpect(jsonPath("$.data[0].sender_type").value("user"))
                .andExpect(jsonPath("$.data[0].referenced_alert_id").value(ALERT_ID.toString()))
                .andExpect(jsonPath("$.data[0].is_fallback").value(false))
                .andExpect(jsonPath("$.data[0].created_at").value("2026-11-17T09:00:00Z"))
                .andExpect(
                        jsonPath("$.data[1].referenced_alert_id")
                                .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].tokens_used").doesNotExist())
                .andExpect(jsonPath("$.data[0].session_token").doesNotExist());
    }

    @Test
    void historyPassesLimitAndAfterCursor() throws Exception {
        when(messageService.getHistory(eq(SESSION_ID), eq("token-a"), eq(10), eq(MESSAGE_ID)))
                .thenReturn(List.of());

        mockMvc.perform(
                        get(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .param("limit", "10")
                                .param("after", MESSAGE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void nonNumericLimitAndMalformedAfterAre400ChatValidation() throws Exception {
        mockMvc.perform(get(MESSAGES).header("X-Session-Token", "token-a").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHT_400_VALIDATION"));
        mockMvc.perform(
                        get(MESSAGES)
                                .header("X-Session-Token", "token-a")
                                .param("after", "bad-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHT_400_VALIDATION"));
    }

    @Test
    void outOfRangeLimitIs400FromServiceValidation() throws Exception {
        when(messageService.getHistory(SESSION_ID, "token-a", 201, null))
                .thenThrow(
                        new ChatValidationException(ErrorCode.CHAT_VALIDATION, "limit", "1..200"));

        mockMvc.perform(get(MESSAGES).header("X-Session-Token", "token-a").param("limit", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHT_400_VALIDATION"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("limit"));
    }

    @Test
    void emptyHistoryIs200EmptyArray() throws Exception {
        when(messageService.getHistory(SESSION_ID, "token-a", 50, null)).thenReturn(List.of());

        mockMvc.perform(get(MESSAGES).header("X-Session-Token", "token-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
