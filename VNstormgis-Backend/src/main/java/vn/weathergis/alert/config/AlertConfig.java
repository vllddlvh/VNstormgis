package vn.weathergis.alert.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AlertRetryProperties.class)
public class AlertConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock alertSystemClock() {
        return Clock.systemUTC();
    }
}
