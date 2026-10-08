package vn.weathergis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:postgresql://localhost:5432/weather_gis_db",
            "spring.datasource.username=gis_user",
            "spring.datasource.password=${DB_PASSWORD:gis_password_secret}",
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.flyway.enabled=false"
        })
class WeatherGisApplicationTests {

    @Test
    @DisplayName("Kiểm tra Spring Context khởi tạo thành công với cấu hình Virtual Threads")
    void contextLoads() {
        // Khởi tạo thành công không ném ngoại lệ
    }
}
