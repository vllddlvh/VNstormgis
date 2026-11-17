package vn.weathergis.chat.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Approved D6 quota and trusted proxy configuration. */
@ConfigurationProperties(prefix = "weathergis.chat.ratelimit")
public class ChatRateLimitProperties {
    private int sessionLimit = 10;
    private int ipLimit = 30;
    private Duration window = Duration.ofSeconds(60);
    private List<String> trustedProxies = new ArrayList<>();

    public void validate() {
        if (sessionLimit <= 0
                || ipLimit <= 0
                || window == null
                || window.isZero()
                || window.isNegative()) {
            throw new IllegalStateException("Invalid chat rate limit configuration");
        }
    }

    public int getSessionLimit() {
        return sessionLimit;
    }

    public void setSessionLimit(int sessionLimit) {
        this.sessionLimit = sessionLimit;
    }

    public int getIpLimit() {
        return ipLimit;
    }

    public void setIpLimit(int ipLimit) {
        this.ipLimit = ipLimit;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }
}
