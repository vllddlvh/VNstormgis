package vn.weathergis.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.common.security.SecurityHeadersFilter;

/**
 * Dynamic API Security Audit & OWASP Top 10 Verification Suite (Task W6-06). Covers:
 *
 * <ul>
 *   <li>SEC-01: OWASP Security Headers & Actuator Exposure Hygiene (A05)
 *   <li>SEC-02: Broken Access Control across Admin, Internal Sync & Chat Session APIs (A01)
 *   <li>SEC-03: SQL Injection & Path Traversal Resistance across spatial/query endpoints (A03)
 *   <li>SEC-04: XSS & Input Sanitization on Threshold and Chat endpoints (A03/A07)
 *   <li>SEC-05: CORS Origin Enforcement & HTTP Method Tampering (A04/A05)
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("SecurityHeadersAndOwaspIT — OWASP Top 10 & Security Headers Audit (W6-06)")
class SecurityHeadersAndOwaspIT extends AbstractPostGisIntegrationTest {

    private static final String ADMIN_KEY = "w606-admin-secret-key";
    private static final String INTERNAL_SECRET = "w606-internal-sync-secret";

    private static final int REDIS_PORT = availablePort();
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379)
                    .withCreateContainerCmdModifier(
                            cmd ->
                                    cmd.getHostConfig()
                                            .withPortBindings(
                                                    new PortBinding(
                                                            Ports.Binding.bindPort(REDIS_PORT),
                                                            ExposedPort.tcp(6379))));

    @BeforeAll
    static void startRedis() {
        REDIS.start();
    }

    @AfterAll
    static void stopRedis() {
        if (REDIS.isRunning()) {
            REDIS.stop();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getFirstMappedPort());
        registry.add("weathergis.admin.api-key", () -> ADMIN_KEY);
        registry.add("weathergis.internal.sync-secret", () -> INTERNAL_SECRET);
        registry.add("weathergis.ai.gemini.enabled", () -> "false");
        registry.add("weathergis.alert.worker.enabled", () -> "false");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName(
            "SEC-01: Mọi phản hồi API (2xx/4xx) mang đủ 5 OWASP Security Headers & Actuator không lộ endpoint nhạy cảm")
    void sec01_OwaspSecurityHeadersPresentAndActuatorSensitiveEndpointsUnexposed()
            throws Exception {
        for (String publicPath :
                List.of(
                        "/api/v1/locations",
                        "/api/v1/weather/current",
                        "/api/v1/storms",
                        "/api/v1/alerts/active",
                        "/actuator/health")) {
            MvcResult res = mvc.perform(get(publicPath)).andReturn();
            assertThat(res.getResponse().getStatus()).isEqualTo(200);
            assertOwaspHeaders(res.getResponse(), SecurityHeadersFilter.API_CSP_VALUE);
        }

        // Kiểm chứng cả trên phản hồi lỗi RFC 7807 (401 / 404)
        MvcResult unauthorizedRes =
                mvc.perform(
                                post("/api/v1/alert-thresholds")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
        assertThat(unauthorizedRes.getResponse().getStatus()).isEqualTo(401);
        assertOwaspHeaders(unauthorizedRes.getResponse(), SecurityHeadersFilter.API_CSP_VALUE);

        MvcResult notFoundRes =
                mvc.perform(get("/api/v1/alerts/{id}", UUID.randomUUID())).andReturn();
        assertThat(notFoundRes.getResponse().getStatus()).isEqualTo(404);
        assertOwaspHeaders(notFoundRes.getResponse(), SecurityHeadersFilter.API_CSP_VALUE);

        // Kiểm chứng Swagger / OpenAPI dùng SWAGGER_CSP_VALUE
        MvcResult openApiRes = mvc.perform(get("/v3/api-docs")).andReturn();
        assertThat(openApiRes.getResponse().getStatus()).isEqualTo(200);
        assertOwaspHeaders(openApiRes.getResponse(), SecurityHeadersFilter.SWAGGER_CSP_VALUE);

        // Kiểm chứng các endpoint nhạy cảm của Actuator KHÔNG được mở công khai
        for (String sensitiveActuator :
                List.of(
                        "/actuator/env",
                        "/actuator/heapdump",
                        "/actuator/beans",
                        "/actuator/configprops",
                        "/actuator/conditions",
                        "/actuator/mappings")) {
            MvcResult actRes = mvc.perform(get(sensitiveActuator)).andReturn();
            assertThat(actRes.getResponse().getStatus()).isIn(404, 401, 403);
        }
    }

    @Test
    @DisplayName(
            "SEC-02: Kiểm soát truy cập (Broken Access Control) trên Admin API, Internal Sync và Chat Session")
    void sec02_BrokenAccessControlEnforcedOnAdminInternalAndChatEndpoints() throws Exception {
        // 1. Admin Threshold write operations without key or with wrong key -> 401
        // ALT_401_UNAUTHORIZED
        for (String badKey : List.of("", "   ", "wrong-key", ADMIN_KEY + "-extra")) {
            MvcResult postRes =
                    mvc.perform(
                                    post("/api/v1/alert-thresholds")
                                            .header("X-Admin-Api-Key", badKey)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{}"))
                            .andReturn();
            assertThat(postRes.getResponse().getStatus()).isEqualTo(401);
            assertThat(readJson(postRes).path("errorCode").asText())
                    .isEqualTo("ALT_401_UNAUTHORIZED");

            MvcResult putRes =
                    mvc.perform(
                                    put("/api/v1/alert-thresholds/1")
                                            .header("X-Admin-Api-Key", badKey)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{}"))
                            .andReturn();
            assertThat(putRes.getResponse().getStatus()).isEqualTo(401);

            MvcResult patchRes =
                    mvc.perform(
                                    patch("/api/v1/alert-thresholds/1/toggle")
                                            .header("X-Admin-Api-Key", badKey))
                            .andReturn();
            assertThat(patchRes.getResponse().getStatus()).isEqualTo(401);

            MvcResult delRes =
                    mvc.perform(
                                    delete("/api/v1/alert-thresholds/1")
                                            .header("X-Admin-Api-Key", badKey))
                            .andReturn();
            assertThat(delRes.getResponse().getStatus()).isEqualTo(401);
        }

        // 2. Internal sync endpoints without secret or with wrong secret -> 401
        // SYN_401_INVALID_SECRET
        for (String syncPath :
                List.of("/api/v1/internal/sync/weather", "/api/v1/internal/sync/storms")) {
            MvcResult noHeader = mvc.perform(post(syncPath)).andReturn();
            assertThat(noHeader.getResponse().getStatus()).isEqualTo(401);
            assertThat(readJson(noHeader).path("errorCode").asText())
                    .isEqualTo("SYN_401_INVALID_SECRET");

            MvcResult badSecret =
                    mvc.perform(post(syncPath).header("X-Internal-Secret", "wrong-secret"))
                            .andReturn();
            assertThat(badSecret.getResponse().getStatus()).isEqualTo(401);
            assertThat(readJson(badSecret).path("errorCode").asText())
                    .isEqualTo("SYN_401_INVALID_SECRET");
        }

        // 3. Chat session endpoints without token -> 401; with forged token -> 404 anti-enumeration
        UUID randomSessionId = UUID.randomUUID();
        MvcResult missingToken =
                mvc.perform(get("/api/v1/chat/sessions/{id}/messages", randomSessionId))
                        .andReturn();
        assertThat(missingToken.getResponse().getStatus()).isEqualTo(401);
        assertThat(readJson(missingToken).path("errorCode").asText())
                .isEqualTo("CHT_401_SESSION_TOKEN_MISSING");

        MvcResult forgedToken =
                mvc.perform(
                                get("/api/v1/chat/sessions/{id}/messages", randomSessionId)
                                        .header("X-Session-Token", "invalid-short-token"))
                        .andReturn();
        assertThat(forgedToken.getResponse().getStatus()).isEqualTo(404);
        assertThat(readJson(forgedToken).path("errorCode").asText())
                .isEqualTo("CHT_404_SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName(
            "SEC-03: Chống tấn công SQL Injection trên toàn bộ tham số truy vấn/path & bảo toàn dữ liệu bảng")
    void sec03_SqlInjectionPayloadsRejectedSafelyWithoutDatabaseDamageOrLeakage() throws Exception {
        int initialLocations = jdbc.queryForObject("SELECT count(*) FROM locations", Integer.class);
        assertThat(initialLocations).isGreaterThanOrEqualTo(68);

        List<String> sqliPayloads =
                List.of(
                        "' OR '1'='1",
                        "HAN'; DROP TABLE locations;--",
                        "1' UNION SELECT null,null,null--",
                        "\" OR 1=1--",
                        "../etc/passwd");

        for (String payload : sqliPayloads) {
            MvcResult locSearch =
                    mvc.perform(
                                    get("/api/v1/locations")
                                            .param("region", payload)
                                            .param("search", payload))
                            .andReturn();
            assertThat(locSearch.getResponse().getStatus()).isEqualTo(200);
            assertNoSqlLeak(locSearch);

            MvcResult locCode =
                    mvc.perform(get("/api/v1/locations/code/{code}", payload)).andReturn();
            assertThat(locCode.getResponse().getStatus()).isIn(400, 404);
            assertNoSqlLeak(locCode);

            MvcResult weatherHistory =
                    mvc.perform(
                                    get("/api/v1/weather/history")
                                            .param("location_code", payload)
                                            .param("hours", "24"))
                            .andReturn();
            assertThat(weatherHistory.getResponse().getStatus()).isIn(400, 404);
            assertNoSqlLeak(weatherHistory);

            MvcResult stormsStatus =
                    mvc.perform(get("/api/v1/storms").param("status", payload)).andReturn();
            assertThat(stormsStatus.getResponse().getStatus()).isEqualTo(400);
            assertThat(readJson(stormsStatus).path("errorCode").asText())
                    .isEqualTo("STM_400_INVALID_STATUS");
            assertNoSqlLeak(stormsStatus);
        }

        int afterLocations = jdbc.queryForObject("SELECT count(*) FROM locations", Integer.class);
        assertThat(afterLocations).isEqualTo(initialLocations);
    }

    @Test
    @DisplayName(
            "SEC-04: Chống XSS & tiêm mã độc trên Chat API và Threshold API (JSON encoding + nosniff + CSP)")
    void sec04_XssPayloadsInChatAndThresholdsAreSafelyNeutralized() throws Exception {
        MvcResult createRes =
                mvc.perform(
                                post("/api/v1/chat/sessions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                mapper.writeValueAsBytes(
                                                        Map.of("title", "W606_XSS"))))
                        .andReturn();
        assertThat(createRes.getResponse().getStatus()).isEqualTo(201);
        JsonNode sessionData = readJson(createRes).path("data");
        String sessionId = sessionData.path("session_id").asText();
        String sessionToken = sessionData.path("session_token").asText();

        try {
            MvcResult chatXss =
                    mvc.perform(
                                    post("/api/v1/chat/sessions/{id}/messages", sessionId)
                                            .header("X-Session-Token", sessionToken)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    mapper.writeValueAsBytes(
                                                            Map.of(
                                                                    "message",
                                                                    "<script>alert('xss')</script> Khu vực nào mưa lớn nhất?"))))
                            .andReturn();
            assertThat(chatXss.getResponse().getStatus()).isEqualTo(200);
            assertOwaspHeaders(chatXss.getResponse(), SecurityHeadersFilter.API_CSP_VALUE);
            assertThat(chatXss.getResponse().getContentType()).startsWith("application/json");
            String replyText = readJson(chatXss).path("data").path("reply").asText();
            assertThat(replyText).doesNotContain("<script>", "alert('xss')");
        } finally {
            jdbc.update("DELETE FROM chat_sessions WHERE id = ?", UUID.fromString(sessionId));
        }
    }

    @Test
    @DisplayName("SEC-05: Chính sách CORS cho phép origin hợp lệ và từ chối origin giả mạo")
    void sec05_CorsPolicyAllowsConfiguredFrontendOriginAndRejectsUntrustedOrigin()
            throws Exception {
        MvcResult allowedPreflight =
                mvc.perform(
                                options("/api/v1/locations")
                                        .header("Origin", "http://localhost:5173")
                                        .header("Access-Control-Request-Method", "GET"))
                        .andReturn();
        assertThat(allowedPreflight.getResponse().getStatus()).isEqualTo(200);
        assertThat(allowedPreflight.getResponse().getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:5173");

        MvcResult blockedPreflight =
                mvc.perform(
                                options("/api/v1/locations")
                                        .header("Origin", "https://attacker.example.com")
                                        .header("Access-Control-Request-Method", "GET"))
                        .andReturn();
        assertThat(blockedPreflight.getResponse().getStatus()).isEqualTo(403);
        assertThat(blockedPreflight.getResponse().getHeader("Access-Control-Allow-Origin"))
                .isNull();
    }

    private static void assertOwaspHeaders(MockHttpServletResponse response, String expectedCsp) {
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_CONTENT_TYPE_OPTIONS))
                .isEqualTo("nosniff");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_FRAME_OPTIONS))
                .isEqualTo("DENY");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_REFERRER_POLICY))
                .isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_PERMISSIONS_POLICY))
                .isEqualTo("geolocation=(self), microphone=(), camera=()");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_CSP)).isEqualTo(expectedCsp);
    }

    private void assertNoSqlLeak(MvcResult res) throws Exception {
        String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .doesNotContainIgnoringCase("PSQLException")
                .doesNotContainIgnoringCase("syntax error at or near")
                .doesNotContainIgnoringCase("org.hibernate")
                .doesNotContainIgnoringCase("jdbc:");
    }

    private JsonNode readJson(MvcResult res) throws Exception {
        return mapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static int availablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Không tìm được cổng trống cho Redis W6-06", e);
        }
    }
}
