package vn.weathergis.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SubscriptionConfig.SubscriptionMailProperties.class)
public class SubscriptionConfig {

    @ConfigurationProperties(prefix = "weathergis.mail")
    public record SubscriptionMailProperties(
            boolean enabled,
            String from,
            String fromName,
            String publicWebUrl,
            String publicApiUrl,
            int otpTtlMinutes,
            int rateLimitMaxSends,
            int rateLimitWindowMinutes) {

        public SubscriptionMailProperties {
            if (from == null || from.isBlank()) {
                from = "no-reply@weathergis.vn";
            }
            if (fromName == null || fromName.isBlank()) {
                fromName = "VNstormgis Cảnh Báo Khí Tượng";
            }
            if (publicWebUrl == null || publicWebUrl.isBlank()) {
                publicWebUrl = "http://localhost:5173";
            }
            if (publicApiUrl == null || publicApiUrl.isBlank()) {
                publicApiUrl = "http://localhost:8080";
            }
            if (otpTtlMinutes <= 0) {
                otpTtlMinutes = 15;
            }
            if (rateLimitMaxSends <= 0) {
                rateLimitMaxSends = 5;
            }
            if (rateLimitWindowMinutes <= 0) {
                rateLimitWindowMinutes = 10;
            }
        }
    }
}
