package vn.weathergis.subscription.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import vn.weathergis.common.config.CorsConfig;
import vn.weathergis.common.config.JacksonConfig;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.GlobalExceptionHandler;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.common.security.SecurityHeadersFilter;
import vn.weathergis.subscription.config.SubscriptionConfig;
import vn.weathergis.subscription.dto.CreateSubscriptionRequest;
import vn.weathergis.subscription.dto.SubscriptionResponse;
import vn.weathergis.subscription.dto.TokenActionRequest;
import vn.weathergis.subscription.dto.VerifyOtpRequest;
import vn.weathergis.subscription.service.EmailSubscriptionService;

@WebMvcTest(EmailSubscriptionController.class)
@Import({
    GlobalExceptionHandler.class,
    JacksonConfig.class,
    CorsConfig.class,
    SecurityHeadersFilter.class,
    SubscriptionConfig.class
})
class EmailSubscriptionControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private EmailSubscriptionService subscriptionService;

    @Test
    @DisplayName("SUB-CTRL-01: Đăng ký theo trạm hợp lệ trả về 201 Created")
    void createSubscriptionReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        UUID locId = UUID.randomUUID();
        OffsetDateTime exp = OffsetDateTime.parse("2026-11-18T02:00:00Z");

        SubscriptionResponse resp =
                new SubscriptionResponse(
                        id,
                        "ng***@gmail.com",
                        "STATION",
                        locId,
                        "DNG",
                        "Đà Nẵng (DNG)",
                        108.2022,
                        16.0544,
                        50.0,
                        "WARNING",
                        "PENDING",
                        "SENT",
                        exp,
                        null,
                        null);

        when(subscriptionService.createSubscription(any(CreateSubscriptionRequest.class)))
                .thenReturn(resp);

        CreateSubscriptionRequest req =
                new CreateSubscriptionRequest(
                        "nguyen@gmail.com",
                        "STATION",
                        null,
                        "DNG",
                        null,
                        null,
                        null,
                        50.0,
                        "WARNING");

        mockMvc.perform(
                        post("/api/v1/subscriptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.maskedEmail").value("ng***@gmail.com"));
    }

    @Test
    @DisplayName(
            "SUB-CTRL-02: Email sai định dạng trả về 400 RFC 7807 với errorCode SUB_400_VALIDATION")
    void createSubscriptionInvalidEmailReturnsSub400() throws Exception {
        String badBody =
                """
                {"email":"not-an-email","scopeType":"STATION","locationCode":"DNG"}
                """;

        mockMvc.perform(
                        post("/api/v1/subscriptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("SUB_400_VALIDATION"));
    }

    @Test
    @DisplayName(
            "SUB-CTRL-03: Xác thực OTP 6 chữ số thành công trả về trạng thái ACTIVE và unsubscribeToken")
    void verifyByOtpReturnsActive() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-11-18T02:05:00Z");
        SubscriptionResponse resp =
                new SubscriptionResponse(
                        id,
                        "ng***@gmail.com",
                        "COORDINATE",
                        null,
                        null,
                        "Vị trí của tôi",
                        108.21,
                        16.06,
                        50.0,
                        "DANGER",
                        "ACTIVE",
                        "SENT",
                        null,
                        now,
                        "unsub-token-xyz");

        when(subscriptionService.verifyByOtp(eq(id), eq("123456"))).thenReturn(resp);

        mockMvc.perform(
                        post("/api/v1/subscriptions/{id}/verify", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new VerifyOtpRequest("123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.unsubscribeToken").value("unsub-token-xyz"));
    }

    @Test
    @DisplayName(
            "SUB-CTRL-04: Liên kết GET /verify và /unsubscribe trả về trang HTML San Rita tiếng Việt kèm CSP hợp lệ")
    void htmlVerifyAndUnsubscribeLinksRenderHtml() throws Exception {
        UUID id = UUID.randomUUID();
        SubscriptionResponse activeResp =
                new SubscriptionResponse(
                        id,
                        "ng***@gmail.com",
                        "STATION",
                        null,
                        "HAN",
                        "Hà Nội (HAN)",
                        105.8542,
                        21.0285,
                        50.0,
                        "WARNING",
                        "ACTIVE",
                        "SENT",
                        null,
                        OffsetDateTime.now(),
                        "unsub-1");

        when(subscriptionService.verifyByToken("good-token")).thenReturn(activeResp);
        when(subscriptionService.verifyByToken("bad-token"))
                .thenThrow(
                        new InvalidParameterException(
                                ErrorCode.SUBSCRIPTION_INVALID_TOKEN, "Token hết hạn"));

        mockMvc.perform(get("/api/v1/subscriptions/verify").param("token", "good-token"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Security-Policy",
                                        SecurityHeadersFilter.SWAGGER_CSP_VALUE))
                .andExpect(
                        content()
                                .string(
                                        Matchers.containsString(
                                                "ĐÃ KÍCH HOẠT NHẬN CẢNH BÁO EMAIL")));

        mockMvc.perform(get("/api/v1/subscriptions/verify").param("token", "bad-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.containsString("XÁC THỰC KHÔNG THÀNH CÔNG")));

        SubscriptionResponse unsubResp =
                new SubscriptionResponse(
                        id,
                        "ng***@gmail.com",
                        "STATION",
                        null,
                        "HAN",
                        "Hà Nội (HAN)",
                        105.8542,
                        21.0285,
                        50.0,
                        "WARNING",
                        "UNSUBSCRIBED",
                        "UNSUBSCRIBED",
                        null,
                        null,
                        null);
        when(subscriptionService.unsubscribeByToken("unsub-1")).thenReturn(unsubResp);

        mockMvc.perform(get("/api/v1/subscriptions/unsubscribe").param("token", "unsub-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ĐÃ HỦY ĐĂNG KÝ NHẬN EMAIL")));
    }

    @Test
    @DisplayName(
            "SUB-CTRL-05: Vượt tần suất trả về 429 SUB_429_RATE_LIMITED & gửi test notification thành công")
    void rateLimitAndTestNotification() throws Exception {
        when(subscriptionService.createSubscription(any()))
                .thenThrow(
                        new InvalidParameterException(
                                ErrorCode.SUBSCRIPTION_RATE_LIMITED, "Quá hạn mức"));

        CreateSubscriptionRequest req =
                new CreateSubscriptionRequest(
                        "spam@gmail.com",
                        "COORDINATE",
                        null,
                        null,
                        106.0,
                        20.0,
                        null,
                        50.0,
                        "INFO");

        mockMvc.perform(
                        post("/api/v1/subscriptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("SUB_429_RATE_LIMITED"));

        UUID id = UUID.randomUUID();
        SubscriptionResponse testResp =
                new SubscriptionResponse(
                        id,
                        "sp***@gmail.com",
                        "COORDINATE",
                        null,
                        null,
                        "Vị trí",
                        106.0,
                        20.0,
                        50.0,
                        "INFO",
                        "ACTIVE",
                        "SENT",
                        null,
                        OffsetDateTime.now(),
                        "tok-123");
        when(subscriptionService.sendTestNotification(id, "tok-123")).thenReturn(testResp);

        mockMvc.perform(
                        post("/api/v1/subscriptions/{id}/test-notification", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new TokenActionRequest("tok-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryMode").value("SENT"));
    }
}
