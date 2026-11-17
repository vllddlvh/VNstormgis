package vn.weathergis.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

/**
 * OpenAPI acceptance (contract §3.3 & W5-09): the three canonical chat endpoints, the
 * X-Session-Token apiKey security scheme, request/response schemas, required/nullable properties,
 * the numbered chat tag and the CHT_* error matrix are actually emitted by SpringDoc.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatOpenApiIT extends AbstractPostGisIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void chatEndpointsSecuritySchemeAndErrorMatrixAreDocumented() throws Exception {
        String json =
                mockMvc.perform(get("/v3/api-docs/{group}", "1. Toàn Bộ API Hệ Thống (v1-all)"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(json).contains("/api/v1/chat/sessions");
        assertThat(json).contains("/api/v1/chat/sessions/{sessionId}/messages");
        assertThat(json).contains("X-Session-Token");
        assertThat(json).contains("7. Hội Thoại AI Thụ Động (Chat)");
        assertThat(json).contains("CHT_400_VALIDATION");
        assertThat(json).contains("CHT_401_SESSION_TOKEN_MISSING");
        assertThat(json).contains("CHT_404_SESSION_NOT_FOUND");
        assertThat(json).contains("CHT_404_ALERT_NOT_FOUND");
        assertThat(json).contains("CHT_503_AI_UNAVAILABLE");
        assertThat(json).contains("CHT_429_RATE_LIMITED");
        assertThat(json).contains("CHT_503_RATE_LIMITER_UNAVAILABLE");
        assertThat(json).contains("Retry-After");
        assertThat(json).contains("retry_after_seconds");
        assertThat(json).doesNotContain("no-op tới W5-05");
        assertThat(json).contains("fallback-first");
        assertThat(json).contains("is_fallback=true");
        assertThat(json).doesNotContain("TỚI W5-04");
        assertThat(json.toLowerCase()).doesNotContain("interim");

        JsonNode root = mapper.readTree(json);
        JsonNode paths = root.path("paths");
        assertThat(paths.has("/api/v1/chat/message")).isFalse();
        assertThat(paths.has("/api/v1/chat/history")).isFalse();

        JsonNode createSessionPost = paths.path("/api/v1/chat/sessions").path("post");
        assertThat(createSessionPost.has("security")).isFalse();
        assertThat(createSessionPost.path("responses").has("201")).isTrue();

        JsonNode messagesPath = paths.path("/api/v1/chat/sessions/{sessionId}/messages");
        JsonNode sendPost = messagesPath.path("post");
        assertThat(sendPost.path("security").toString()).contains("X-Session-Token");
        assertThat(
                        sendPost.path("requestBody")
                                .path("content")
                                .path("application/json")
                                .path("schema")
                                .path("$ref")
                                .asText())
                .isEqualTo("#/components/schemas/SendMessageRequest");
        for (String code : new String[] {"200", "400", "401", "404", "429", "500", "503"}) {
            assertThat(sendPost.path("responses").has(code)).isTrue();
        }

        JsonNode historyGet = messagesPath.path("get");
        assertThat(historyGet.path("security").toString()).contains("X-Session-Token");
        assertThat(historyGet.path("responses").has("200")).isTrue();

        JsonNode schemas = root.path("components").path("schemas");
        JsonNode reqSchema = schemas.path("SendMessageRequest");
        assertThat(reqSchema.path("required").toString()).contains("\"message\"");
        assertThat(
                        reqSchema
                                .path("properties")
                                .path("referenced_alert_id")
                                .path("nullable")
                                .asBoolean())
                .isTrue();
        assertThat(reqSchema.path("properties").path("spatial_filter").path("nullable").asBoolean())
                .isTrue();

        JsonNode viewSchema = schemas.path("ChatMessageView");
        assertThat(
                        viewSchema
                                .path("properties")
                                .path("referenced_alert_id")
                                .path("nullable")
                                .asBoolean())
                .isTrue();
        assertThat(viewSchema.path("properties").has("is_fallback")).isTrue();
        assertThat(viewSchema.path("properties").has("session_token")).isFalse();
        assertThat(viewSchema.path("properties").has("tokens_used")).isFalse();
    }
}
