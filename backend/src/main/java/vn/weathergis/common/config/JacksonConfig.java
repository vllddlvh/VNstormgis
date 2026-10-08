package vn.weathergis.common.config;

import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Jackson ObjectMapper đăng ký JtsModule. Giải quyết dứt điểm rủi ro RSK-W1-02: Chuyển đổi
 * đối tượng JTS Point, Polygon sang định dạng chuẩn GeoJSON RFC 7946 cho giao diện WebGIS Leaflet.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JtsModule jtsModule() {
        return new JtsModule();
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer(JtsModule jtsModule) {
        return builder -> builder.modulesToInstall(jtsModule);
    }
}
