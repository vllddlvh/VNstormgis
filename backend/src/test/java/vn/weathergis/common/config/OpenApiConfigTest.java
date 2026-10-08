package vn.weathergis.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;

class OpenApiConfigTest {

    private final OpenApiConfig openApiConfig = new OpenApiConfig();

    @Test
    @DisplayName("OpenAPI Bean: Siêu dữ liệu tiêu đề, phiên bản, servers hợp lệ")
    void testCustomOpenAPI() {
        OpenAPI openAPI = openApiConfig.customOpenAPI(8080, "weather-gis-backend");

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle())
                .isEqualTo("Vietnam WeatherGIS - RESTful & Spatial GeoJSON APIs");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("1.0.0-SNAPSHOT");
        assertThat(openAPI.getInfo().getContact().getEmail())
                .isEqualTo("engineering@weathergis.vn");
        assertThat(openAPI.getInfo().getLicense().getName()).contains("Apache 2.0");

        assertThat(openAPI.getServers()).hasSize(2);
        assertThat(openAPI.getServers().get(0).getUrl()).isEqualTo("http://localhost:8080");
        assertThat(openAPI.getServers().get(1).getUrl()).isEqualTo("https://staging.weathergis.vn");
    }

    @Test
    @DisplayName("GroupedOpenApi Beans: Khởi tạo đầy đủ 3 nhóm API")
    void testGroupedOpenApis() {
        GroupedOpenApi publicApi = openApiConfig.publicApi();
        assertThat(publicApi.getGroup()).isEqualTo("1. Toàn Bộ API Hệ Thống (v1-all)");
        assertThat(publicApi.getPathsToMatch()).containsExactly("/api/v1/**");

        GroupedOpenApi locationApi = openApiConfig.locationApi();
        assertThat(locationApi.getGroup())
                .isEqualTo("2. Trạm Quan Trắc & Không Gian (v1-locations)");
        assertThat(locationApi.getPathsToMatch()).containsExactly("/api/v1/locations/**");

        GroupedOpenApi actuatorApi = openApiConfig.actuatorApi();
        assertThat(actuatorApi.getGroup()).isEqualTo("3. Giám Sát Sức Khỏe Vận Hành (actuator)");
        assertThat(actuatorApi.getPathsToMatch()).containsExactly("/actuator/**");
    }
}
