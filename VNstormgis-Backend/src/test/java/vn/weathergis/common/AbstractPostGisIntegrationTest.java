package vn.weathergis.common;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Lớp cơ sở cung cấp Singleton PostGIS 16 Container phục vụ toàn bộ chuỗi Integration Tests. Áp
 * dụng Shared Singleton Container Pattern (RSK-W1-05 Mitigation). Tự động kích hoạt toàn bộ Flyway
 * Migrations (V1, V2, V3) trên container tạm thời.
 */
public abstract class AbstractPostGisIntegrationTest {

    private static final DockerImageName POSTGIS_IMAGE =
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                    .asCompatibleSubstituteFor("postgres");

    protected static final PostgreSQLContainer<?> POSTGIS_CONTAINER;

    /**
     * W3-03 quyết định #11: toàn bộ suite IT chạy mà không có Redis nên cache mặc định <b>TẮT</b> —
     * mọi truy vấn đi thẳng PostGIS, hợp đồng W2-07/W3-01/W3-02 giữ nguyên. Supplier của
     * {@code @DynamicPropertySource} chỉ được đọc khi context khởi tạo, nên {@code RedisCacheIT}
     * (IT duy nhất có Testcontainers Redis) bật lại qua {@link #setRedisCacheEnabled(boolean)}
     * trong {@code @BeforeAll} — so sánh với giá trị hằng {@code "false"}: đăng ký lại cùng khóa ở
     * subclass không thắng được, vì Spring gọi method theo trật tự subclass trước, superclass sau
     * và {@code DynamicPropertiesContextCustomizer} ghi đè theo khóa (last write wins).
     */
    private static volatile boolean redisCacheEnabled = false;

    static {
        POSTGIS_CONTAINER =
                new PostgreSQLContainer<>(POSTGIS_IMAGE)
                        .withDatabaseName("test_weather_gis_db")
                        .withUsername("test_gis_user")
                        .withPassword("test_gis_password");
        POSTGIS_CONTAINER.start();
    }

    protected static void setRedisCacheEnabled(boolean enabled) {
        redisCacheEnabled = enabled;
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGIS_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGIS_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGIS_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGIS_CONTAINER::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        // Tắt @Scheduled Ingestion trong suite test — cấm phát request mạng thật ra OWM (Mục 7.6)
        registry.add("weathergis.sync.enabled", () -> "false");
        registry.add("weathergis.cache.enabled", () -> String.valueOf(redisCacheEnabled));
    }
}
