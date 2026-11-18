package vn.weathergis.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "email_subscriptions")
public class EmailSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "scope_type", nullable = false, length = 16)
    private String scopeType;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "target_geom", nullable = false, columnDefinition = "geometry(Point, 4326)")
    private Point targetGeom;

    @Column(name = "radius_km", nullable = false)
    private Double radiusKm = 50.0;

    @Column(name = "min_severity", nullable = false, length = 16)
    private String minSeverity = "WARNING";

    @Column(name = "label", nullable = false, length = 160)
    private String label;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "otp_hash", length = 64)
    private String otpHash;

    @Column(name = "verify_token_hash", unique = true, length = 64)
    private String verifyTokenHash;

    @Column(name = "verify_expires_at")
    private OffsetDateTime verifyExpiresAt;

    @Column(name = "unsubscribe_token", nullable = false, unique = true, length = 64)
    private String unsubscribeToken;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "unsubscribed_at")
    private OffsetDateTime unsubscribedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public Point getTargetGeom() {
        return targetGeom;
    }

    public void setTargetGeom(Point targetGeom) {
        this.targetGeom = targetGeom;
    }

    public Double getRadiusKm() {
        return radiusKm;
    }

    public void setRadiusKm(Double radiusKm) {
        this.radiusKm = radiusKm;
    }

    public String getMinSeverity() {
        return minSeverity;
    }

    public void setMinSeverity(String minSeverity) {
        this.minSeverity = minSeverity;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public void setOtpHash(String otpHash) {
        this.otpHash = otpHash;
    }

    public String getVerifyTokenHash() {
        return verifyTokenHash;
    }

    public void setVerifyTokenHash(String verifyTokenHash) {
        this.verifyTokenHash = verifyTokenHash;
    }

    public OffsetDateTime getVerifyExpiresAt() {
        return verifyExpiresAt;
    }

    public void setVerifyExpiresAt(OffsetDateTime verifyExpiresAt) {
        this.verifyExpiresAt = verifyExpiresAt;
    }

    public String getUnsubscribeToken() {
        return unsubscribeToken;
    }

    public void setUnsubscribeToken(String unsubscribeToken) {
        this.unsubscribeToken = unsubscribeToken;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(OffsetDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public OffsetDateTime getUnsubscribedAt() {
        return unsubscribedAt;
    }

    public void setUnsubscribedAt(OffsetDateTime unsubscribedAt) {
        this.unsubscribedAt = unsubscribedAt;
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

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
