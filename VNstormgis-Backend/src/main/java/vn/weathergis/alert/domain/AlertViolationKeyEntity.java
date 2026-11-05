package vn.weathergis.alert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Durable violation key claim linked to its owning generation job and published alert. */
@Entity
@Table(name = "alert_violation_keys")
public class AlertViolationKeyEntity implements Persistable<String> {

    @Id
    @Column(name = "violation_key", nullable = false, length = 255)
    private String violationKey;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Transient private boolean isNew = true;

    public AlertViolationKeyEntity() {}

    public AlertViolationKeyEntity(String violationKey, UUID jobId, OffsetDateTime createdAt) {
        this.violationKey = violationKey;
        this.jobId = jobId;
        this.createdAt = createdAt;
        this.isNew = true;
    }

    @Override
    public String getId() {
        return violationKey;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public String getViolationKey() {
        return violationKey;
    }

    public void setViolationKey(String violationKey) {
        this.violationKey = violationKey;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public void setAlertId(UUID alertId) {
        this.alertId = alertId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
