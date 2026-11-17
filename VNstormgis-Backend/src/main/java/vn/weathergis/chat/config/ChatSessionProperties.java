package vn.weathergis.chat.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Session retention policy (D4, contract §3.4): a session expires after {@code retention} of
 * inactivity measured from {@code updated_at}. The threshold is configurable, never a scattered
 * constant; the same value drives auth-time expiry and the daily cleanup job.
 */
@ConfigurationProperties(prefix = "weathergis.chat.session")
public class ChatSessionProperties {

    private Duration retention = Duration.ofDays(30);
    private final Cleanup cleanup = new Cleanup();

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    /** Daily cleanup schedule (D4); disabled in the IT suite so fixtures are never reaped. */
    public static class Cleanup {

        private boolean enabled = true;
        private String cron = "0 0 3 * * *";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }
}
