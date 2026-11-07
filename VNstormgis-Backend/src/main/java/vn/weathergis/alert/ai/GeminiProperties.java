package vn.weathergis.alert.ai;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("weathergis.ai.gemini")
public class GeminiProperties {
    private boolean enabled;
    private String apiKey = "";
    private String model = "";
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(5);
    private int maxOutputTokens = 1024;
    private int maxResponseBytes = 65536;

    public void validate() {
        if (!enabled) return;
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid Gemini base URL");
        }
        boolean loopback =
                "localhost".equals(uri.getHost())
                        || "127.0.0.1".equals(uri.getHost())
                        || "::1".equals(uri.getHost());
        if (apiKey == null
                || apiKey.isBlank()
                || model == null
                || !model.matches("[A-Za-z0-9._-]+")
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !("https".equals(uri.getScheme())
                        || ("http".equals(uri.getScheme()) && loopback))
                || connectTimeout == null
                || connectTimeout.isZero()
                || connectTimeout.isNegative()
                || requestTimeout == null
                || requestTimeout.isZero()
                || requestTimeout.isNegative()
                || requestTimeout.compareTo(Duration.ofSeconds(5)) > 0
                || connectTimeout.compareTo(requestTimeout) > 0
                || connectTimeout.toMillis() < 1
                || maxOutputTokens <= 0
                || maxResponseBytes <= 0) {
            throw new IllegalStateException(
                    "Invalid Gemini configuration (key, model, URL, timeout or limits)");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        enabled = v;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String v) {
        apiKey = v;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String v) {
        model = v;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String v) {
        baseUrl = v;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration v) {
        connectTimeout = v;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration v) {
        requestTimeout = v;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int v) {
        maxOutputTokens = v;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int v) {
        maxResponseBytes = v;
    }
}
