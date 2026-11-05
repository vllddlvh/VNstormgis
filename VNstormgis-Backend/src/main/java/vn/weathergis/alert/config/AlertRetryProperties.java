package vn.weathergis.alert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configurable retry and bounded-backoff parameters for publishing READY alert jobs. */
@ConfigurationProperties(prefix = "weathergis.alert.retry")
public class AlertRetryProperties {

    private boolean enabled = true;
    private long fixedDelayMs = 30_000L;
    private long initialDelayMs = 5_000L;
    private long baseBackoffMs = 5_000L;
    private long maxBackoffMs = 300_000L;
    private int batchSize = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public long getBaseBackoffMs() {
        return baseBackoffMs;
    }

    public void setBaseBackoffMs(long baseBackoffMs) {
        this.baseBackoffMs = baseBackoffMs;
    }

    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public void setMaxBackoffMs(long maxBackoffMs) {
        this.maxBackoffMs = maxBackoffMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long computeBackoffMs(int attemptCount) {
        long base = Math.max(1L, baseBackoffMs);
        long max = Math.max(base, maxBackoffMs);
        int shift = Math.max(0, Math.min(attemptCount - 1, 20));
        long multiplied = base * (1L << shift);
        if (multiplied <= 0L) {
            return max;
        }
        return Math.min(max, multiplied);
    }
}
