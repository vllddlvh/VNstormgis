package vn.weathergis.alert.domain;

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
import org.locationtech.jts.geom.Geometry;

/**
 * Published proactive alert entity mapped to table {@code alerts}.
 *
 * <p>Foreign keys {@code threshold_id}, {@code location_id}, and {@code storm_id} use {@code ON
 * DELETE SET NULL} in PostgreSQL, while {@code factual_snapshot} retains the immutable historical
 * evidence recorded at detection time.
 */
@Entity
@Table(name = "alerts")
public class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "threshold_id")
    private Integer thresholdId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "storm_id", length = 64)
    private String stormId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "severity_level", nullable = false, length = 32)
    private String severityLevel;

    @Column(name = "trigger_reason", nullable = false, columnDefinition = "text")
    private String triggerReason;

    @Column(name = "ai_explanation", nullable = false, columnDefinition = "text")
    private String aiExplanation;

    @Column(name = "is_fallback", nullable = false)
    private Boolean isFallback = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factual_snapshot", columnDefinition = "jsonb")
    private String factualSnapshot;

    @Column(name = "affected_geom", columnDefinition = "geometry(Geometry, 4326)")
    private Geometry affectedGeom;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "active";

    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getThresholdId() {
        return thresholdId;
    }

    public void setThresholdId(Integer thresholdId) {
        this.thresholdId = thresholdId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public String getStormId() {
        return stormId;
    }

    public void setStormId(String stormId) {
        this.stormId = stormId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getAiExplanation() {
        return aiExplanation;
    }

    public void setAiExplanation(String aiExplanation) {
        this.aiExplanation = aiExplanation;
    }

    public Boolean getIsFallback() {
        return isFallback;
    }

    public void setIsFallback(Boolean isFallback) {
        this.isFallback = isFallback;
    }

    public String getFactualSnapshot() {
        return factualSnapshot;
    }

    public void setFactualSnapshot(String factualSnapshot) {
        this.factualSnapshot = factualSnapshot;
    }

    public Geometry getAffectedGeom() {
        return affectedGeom;
    }

    public void setAffectedGeom(Geometry affectedGeom) {
        this.affectedGeom = affectedGeom;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(OffsetDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
