package vn.weathergis.chat.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.weathergis.chat.config.ChatRateLimitProperties;

class ChatClientIpResolverTest {
    private ChatClientIpResolver resolver(String... proxies) {
        ChatRateLimitProperties properties = new ChatRateLimitProperties();
        properties.setTrustedProxies(List.of(proxies));
        return new ChatClientIpResolver(properties);
    }

    @Test
    void ignoresForwardedHeaderWithoutTrustedRemote() {
        assertThat(resolver().resolve("203.0.113.7", "198.51.100.1")).isEqualTo("203.0.113.7");
        assertThat(resolver("10.0.0.0/8").resolve("203.0.113.7", "198.51.100.1"))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void walksTrustedHopsFromRightAndFallsBackOnMalformedHop() {
        ChatClientIpResolver resolver = resolver("10.0.0.0/8", "2001:db8::/32");
        assertThat(resolver.resolve("10.0.0.5", "198.51.100.1, 10.0.0.2"))
                .isEqualTo("198.51.100.1");
        assertThat(resolver.resolve("10.0.0.5", "198.51.100.1, bad.example")).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve("10.0.0.5", "10.0.0.2, 2001:db8::1")).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve("10.0.0.5", "")).isEqualTo("10.0.0.5");
    }

    @Test
    void normalizesV4V6MappedAndZonesWithoutDns() {
        ChatClientIpResolver resolver = resolver("::1", "127.0.0.1");
        assertThat(resolver.resolve("::1", "::ffff:192.0.2.9")).isEqualTo("192.0.2.9");
        assertThat(resolver.resolve("127.0.0.1", "2001:0DB8:0:0:0:0:0:1%eth0"))
                .isEqualTo("2001:db8::1");
        assertThat(resolver.resolve("127.0.0.1", "host.example")).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve("127.0.0.1", "999.0.0.1")).isEqualTo("127.0.0.1");
    }

    @Test
    void rejectsInvalidTrustedCidrAtStartup() {
        assertThatThrownBy(() -> resolver("10.0.0.0/33")).isInstanceOf(IllegalStateException.class);
    }
}
