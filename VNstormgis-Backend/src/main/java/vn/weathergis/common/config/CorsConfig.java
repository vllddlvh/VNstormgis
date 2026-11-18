package vn.weathergis.common.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình chia sẻ tài nguyên nguồn gốc chéo (CORS) cho máy chủ phát triển Frontend và đường hầm
 * demo HTTPS Cloudflare Quick Tunnel (*.trycloudflare.com).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${weathergis.cors.extra-origin-patterns:}")
    private String extraOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> patterns =
                new ArrayList<>(
                        List.of(
                                "http://localhost:5173",
                                "http://localhost:3000",
                                "http://localhost:*",
                                "http://127.0.0.1:*",
                                "https://*.trycloudflare.com"));
        if (extraOriginPatterns != null && !extraOriginPatterns.isBlank()) {
            Arrays.stream(extraOriginPatterns.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(patterns::add);
        }

        registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
