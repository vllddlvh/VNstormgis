package vn.weathergis.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One chat message mapped to {@code chat_messages} (V5 + V9 {@code is_fallback}). The user row
 * carries the stripped question, validated alert reference and bbox JSONB; the assistant row
 * carries the verified/fallback reply and token telemetry. {@code tokens_used} is internal and
 * never exposed through the history API.
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "sender_type", nullable = false, length = 32)
    private String senderType;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "referenced_alert_id")
    private UUID referencedAlertId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spatial_filter", columnDefinition = "jsonb")
    private String spatialFilter;

    @Column(name = "tokens_used", nullable = false)
    private int tokensUsed;

    @Column(name = "is_fallback", nullable = false)
    private boolean isFallback;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getSenderType() {
        return senderType;
    }

    public void setSenderType(String senderType) {
        this.senderType = senderType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public UUID getReferencedAlertId() {
        return referencedAlertId;
    }

    public void setReferencedAlertId(UUID referencedAlertId) {
        this.referencedAlertId = referencedAlertId;
    }

    public String getSpatialFilter() {
        return spatialFilter;
    }

    public void setSpatialFilter(String spatialFilter) {
        this.spatialFilter = spatialFilter;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public boolean isFallback() {
        return isFallback;
    }

    public void setFallback(boolean fallback) {
        isFallback = fallback;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
