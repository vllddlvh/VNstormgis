package vn.weathergis.chat.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounded factual-context parameters (W5-02 §4.6–4.7). The stale threshold is proposed at 60
 * minutes (4 OWM cycles) pending decision D-W5-02-01; it must stay configurable, never scattered
 * constants.
 */
@ConfigurationProperties(prefix = "weathergis.chat.context")
public class ChatContextProperties {

    private Duration staleThreshold = Duration.ofMinutes(60);
    private int maxContextBytes = 8192;

    public Duration getStaleThreshold() {
        return staleThreshold;
    }

    public void setStaleThreshold(Duration staleThreshold) {
        this.staleThreshold = staleThreshold;
    }

    public int getMaxContextBytes() {
        return maxContextBytes;
    }

    public void setMaxContextBytes(int maxContextBytes) {
        this.maxContextBytes = maxContextBytes;
    }
}
