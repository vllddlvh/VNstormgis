package vn.weathergis.alert.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("weathergis.alert.worker")
public class AlertWorkerProperties {
    private boolean enabled = true;
    private int batchSize = 20;
    private int concurrency = 1;
    private long leaseMs = 30000;
    private long quarantineMs = 300000;
    private long fixedDelayMs = 30000;
    private long initialDelayMs = 60000;

    @PostConstruct
    public void validate() {
        if (batchSize < 1
                || batchSize > 100
                || concurrency < 1
                || concurrency > 8
                || leaseMs < 10000
                || leaseMs > 600000
                || quarantineMs < 10000
                || quarantineMs > 86400000
                || fixedDelayMs < 1000
                || fixedDelayMs > 3600000
                || initialDelayMs < 0
                || initialDelayMs > 3600000) {
            throw new IllegalStateException("Invalid alert worker configuration");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int value) {
        concurrency = value;
    }

    public void setBatchSize(int value) {
        batchSize = value;
    }

    public long getLeaseMs() {
        return leaseMs;
    }

    public void setLeaseMs(long value) {
        leaseMs = value;
    }

    public long getQuarantineMs() {
        return quarantineMs;
    }

    public void setQuarantineMs(long value) {
        quarantineMs = value;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long value) {
        fixedDelayMs = value;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long value) {
        initialDelayMs = value;
    }
}
