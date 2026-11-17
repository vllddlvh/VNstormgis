package vn.weathergis.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ChatRateLimitPropertiesTest {
    @Test
    void approvedDefaultsAndPositiveLimits() {
        ChatRateLimitProperties properties = new ChatRateLimitProperties();
        assertThat(properties.getSessionLimit()).isEqualTo(10);
        assertThat(properties.getIpLimit()).isEqualTo(30);
        assertThat(properties.getWindow()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getTrustedProxies()).isEmpty();
        properties.validate();
        properties.setIpLimit(0);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setIpLimit(30);
        properties.setWindow(Duration.ZERO);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configurationOverridesBindToQuotaAndTrustedProxyList() {
        ChatRateLimitProperties properties =
                new Binder(
                                new MapConfigurationPropertySource(
                                        Map.of(
                                                "weathergis.chat.ratelimit.session-limit", "12",
                                                "weathergis.chat.ratelimit.ip-limit", "33",
                                                "weathergis.chat.ratelimit.window", "45s",
                                                "weathergis.chat.ratelimit.trusted-proxies",
                                                        "127.0.0.1,10.0.0.0/8")))
                        .bind(
                                "weathergis.chat.ratelimit",
                                Bindable.of(ChatRateLimitProperties.class))
                        .orElseThrow(IllegalStateException::new);
        assertThat(properties.getSessionLimit()).isEqualTo(12);
        assertThat(properties.getIpLimit()).isEqualTo(33);
        assertThat(properties.getWindow()).isEqualTo(Duration.ofSeconds(45));
        assertThat(properties.getTrustedProxies()).containsExactly("127.0.0.1", "10.0.0.0/8");
    }
}
