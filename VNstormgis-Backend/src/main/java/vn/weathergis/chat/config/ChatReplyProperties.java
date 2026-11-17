package vn.weathergis.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reply-side limits (W5-04 §8.4, proposed defaults pending D-W5-04-02): chat output tokens, reply
 * character cap and the snapshot cut ladder start. The 8 KiB prompt block bound stays in {@link
 * ChatContextProperties}; alert publication limits are untouched.
 */
@ConfigurationProperties(prefix = "weathergis.chat.reply")
public class ChatReplyProperties {

    private int maxOutputTokens = 1024;
    private int replyMaxChars = 4000;
    private int snapshotCutTopN = 5;

    public void validate() {
        if (maxOutputTokens <= 0 || replyMaxChars <= 0 || snapshotCutTopN <= 0) {
            throw new IllegalStateException("Invalid chat reply configuration (limits)");
        }
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getReplyMaxChars() {
        return replyMaxChars;
    }

    public void setReplyMaxChars(int replyMaxChars) {
        this.replyMaxChars = replyMaxChars;
    }

    public int getSnapshotCutTopN() {
        return snapshotCutTopN;
    }

    public void setSnapshotCutTopN(int snapshotCutTopN) {
        this.snapshotCutTopN = snapshotCutTopN;
    }
}
