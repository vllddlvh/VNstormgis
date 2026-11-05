package vn.weathergis.alert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Durable inbox job tracking threshold violation intake, verified content, and publication retry.
 */
@Entity
@Table(name = "alert_generation_jobs")
public class AlertGenerationJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "evaluation_cycle_id", nullable = false)
    private UUID evaluationCycleId;

    @Column(name = "representative_threshold_id", nullable = false)
    private Integer representativeThresholdId;

    @Column(name = "storm_id", length = 64)
    private String stormId;

    @Column(name = "severity_level", nullable = false, length = 32)
    private String severityLevel;

    @Column(name = "trigger_reason", nullable = false, columnDefinition = "text")
    private String triggerReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factual_snapshot", nullable = false, columnDefinition = "jsonb")
    private String factualSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AlertJobStatus status = AlertJobStatus.PENDING;

    @Column(name = "verified_title", length = 255)
    private String verifiedTitle;

    @Column(name = "verified_ai_explanation", columnDefinition = "text")
    private String verifiedAiExplanation;

    @Column(name = "is_fallback")
    private Boolean isFallback;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "alert_id", unique = true)
    private UUID alertId;

    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt;

    @Column(name = "ready_at")
    private OffsetDateTime readyAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getEvaluationCycleId() {
        return evaluationCycleId;
    }

    public void setEvaluationCycleId(UUID evaluationCycleId) {
        this.evaluationCycleId = evaluationCycleId;
    }

    public Integer getRepresentativeThresholdId() {
        return representativeThresholdId;
    }

    public void setRepresentativeThresholdId(Integer representativeThresholdId) {
        this.representativeThresholdId = representativeThresholdId;
    }

    public String getStormId() {
        return stormId;
    }

    public void setStormId(String stormId) {
        this.stormId = stormId;
    }

    public String getSeverityLevel() {
        return severityLevel;
    }

    public void setSeverityLevel(String severityLevel) {
        this.severityLevel = severityLevel;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
        this.triggerReason = triggerReason;
    }

    public String getFactualSnapshot() {
        return factualSnapshot;
    }

    public void setFactualSnapshot(String factualSnapshot) {
        this.factualSnapshot = factualSnapshot;
    }

    public AlertJobStatus getStatus() {
        return status;
    }

    public void setStatus(AlertJobStatus status) {
        this.status = status;
    }

    public String getVerifiedTitle() {
        return verifiedTitle;
    }

    public void setVerifiedTitle(String verifiedTitle) {
        this.verifiedTitle = verifiedTitle;
    }

    public String getVerifiedAiExplanation() {
        return verifiedAiExplanation;
    }

    public void setVerifiedAiExplanation(String verifiedAiExplanation) {
        this.verifiedAiExplanation = verifiedAiExplanation;
    }

    public Boolean getIsFallback() {
        return isFallback;
    }

    public void setIsFallback(Boolean isFallback) {
        this.isFallback = isFallback;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public void setAlertId(UUID alertId) {
        this.alertId = alertId;
    }

    public OffsetDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(OffsetDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public OffsetDateTime getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(OffsetDateTime readyAt) {
        this.readyAt = readyAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
