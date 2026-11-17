package vn.weathergis.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

/**
 * OpenAPI acceptance (contract §3.3): the three canonical chat endpoints, the X-Session-Token
 * apiKey security scheme, the numbered chat tag and the CHT_* error matrix are actually emitted by
 * SpringDoc — not just declared in annotations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatOpenApiIT extends AbstractPostGisIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void chatEndpointsSecuritySchemeAndErrorMatrixAreDocumented() throws Exception {
        String json =
                mockMvc.perform(get("/v3/api-docs/{group}", "1. Toàn Bộ API Hệ Thống (v1-all)"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

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
        // W5-04: fallback-first replaces the interim 503 note; response shape stays stable.
        assertThat(json).contains("fallback-first");
        assertThat(json).contains("is_fallback=true");
        assertThat(json).doesNotContain("TỚI W5-04");
        assertThat(json.toLowerCase()).doesNotContain("interim");
    }
}
