package vn.weathergis.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tập trung tài liệu hóa OpenAPI 3.0 và Swagger UI cho hệ thống VNstormgis. Định nghĩa
 * siêu dữ liệu kỹ thuật, danh sách máy chủ, phân nhóm Bounded Context và chuẩn hóa lược đồ dữ liệu
 * hình học OGC JTS Geometry.
 */
@Configuration
public class OpenApiConfig {

    static {
        // [RSK-W1-12 HÓA GIẢI]: Thay thế toàn bộ đối tượng JTS Geometry phức tạp bằng schema
        // GeoJSON phẳng.
        // Ngăn chặn reflection quét sâu vào các trường nội bộ của JTS gây tràn bộ nhớ hoặc vỡ UI
        // Swagger.
        SpringDocUtils.getConfig()
                .replaceWithSchema(
                        Geometry.class,
                        new Schema<>()
                                .type("object")
                                .description(
                                        "Đối tượng hình học OGC GeoJSON RFC 7946 (Point, Polygon)")
                                .example(
                                        Map.of(
                                                "type",
                                                "Point",
                                                "coordinates",
                                                List.of(105.8542, 21.0285))))
                .replaceWithSchema(
                        Point.class,
                        new Schema<>()
                                .type("object")
                                .description("Điểm trắc địa WGS84 GeoJSON [longitude, latitude]")
                                .example(
                                        Map.of(
                                                "type",
                                                "Point",
                                                "coordinates",
                                                List.of(105.8542, 21.0285))));
    }

    @Bean
    public OpenAPI customOpenAPI(
            @Value("${server.port:8080}") int serverPort,
            @Value("${spring.application.name:weather-gis-backend}") String appName) {

        Contact contact =
                new Contact()
                        .name("Đội Ngũ Kỹ Thuật Vietnam WeatherGIS")
                        .email("engineering@weathergis.vn")
                        .url("https://github.com/dllv/VNstormgis");

        License license =
                new License()
                        .name("Apache 2.0 / Bản Quyền Hệ Thống Dự Án Mở")
                        .url("https://www.apache.org/licenses/LICENSE-2.0.html");

        Info info =
                new Info()
                        .title("Vietnam WeatherGIS - RESTful & Spatial GeoJSON APIs")
                        .version("1.0.0-SNAPSHOT")
                        .description(
                                """
                                ### Hệ Thống WebGIS Giám Sát Mưa Gió Bão Tích Hợp AI Agent Cảnh Báo Thiên Tai

                                Bộ tài liệu API đặc tả các điểm cuối truy vấn dữ liệu không gian, trắc địa khí tượng,
                                dữ liệu dự báo thời tiết và kênh tương tác với trợ lý thông minh.

                                #### Quy Chuẩn Phản Hồi Dữ Liệu:
                                - **Chuẩn Không Gian:** Hệ quy chiếu trắc địa toàn cầu WGS84 (EPSG:4326), tọa độ `[kinh độ (lng), vĩ độ (lat)]`.
                                - **Chuẩn Bản Đồ:** GeoJSON FeatureCollection theo đặc tả RFC 7946 (sẵn sàng cho Leaflet `L.geoJSON()`).
                                - **Chuẩn Phong Bao REST:** Phong bao hợp nhất `ApiResponse<T>` chứa `success`, `message`, `data`, `timestamp`.
                                - **Chuẩn Báo Lỗi Toàn Cục:** Chuẩn công nghiệp RFC 7807 Problem Details (`ProblemDetail`).
                                """)
                        .contact(contact)
                        .license(license);

        Server localServer =
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Máy chủ phát triển cục bộ (Local Development Server)");

        Server stagingServer =
                new Server()
                        .url("https://staging.weathergis.vn")
                        .description("Môi trường kiểm thử tích hợp (Staging Integration Server)");

        return new OpenAPI().info(info).servers(List.of(localServer, stagingServer));
    }

    /** Nhóm toàn bộ các API nghiệp vụ công khai của hệ thống (/api/v1/**). */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("1. Toàn Bộ API Hệ Thống (v1-all)")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    /** Nhóm chuyên biệt cho Bounded Context quản lý điểm trắc địa và trạm quan trắc. */
    @Bean
    public GroupedOpenApi locationApi() {
        return GroupedOpenApi.builder()
                .group("2. Trạm Quan Trắc & Không Gian (v1-locations)")
                .pathsToMatch("/api/v1/locations/**")
                .build();
    }

    /** Nhóm giám sát vận hành hệ thống thông qua Spring Boot Actuator. */
    @Bean
    public GroupedOpenApi actuatorApi() {
        return GroupedOpenApi.builder()
                .group("3. Giám Sát Sức Khỏe Vận Hành (actuator)")
                .pathsToMatch("/actuator/**")
                .build();
    }
}
