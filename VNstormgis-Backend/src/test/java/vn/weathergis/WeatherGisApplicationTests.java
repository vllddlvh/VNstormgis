package vn.weathergis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

@SpringBootTest
class WeatherGisApplicationTests extends AbstractPostGisIntegrationTest {

    @Test
    @DisplayName(
            "Kiểm tra Spring Context khởi tạo thành công với cấu hình Virtual Threads và PostGIS Testcontainers")
    void contextLoads() {
        // Khởi tạo thành công không ném ngoại lệ
    }
}
