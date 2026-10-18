package vn.weathergis.storm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/** Thực thể ánh xạ bảng storm_tracks: quỹ đạo lịch sử và dự báo tâm bão kèm vùng ảnh hưởng. */
@Entity
@Table(name = "storm_tracks")
public class StormTrackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "storm_id", nullable = false)
    private StormEntity storm;

    @Column(name = "track_type", nullable = false, length = 32)
    private String trackType;

    @Column(name = "forecast_time", nullable = false)
    private OffsetDateTime forecastTime;

    @Column(name = "geom", nullable = false, columnDefinition = "geometry(Point, 4326)")
    private Point geom;

    @Column(name = "wind_speed", nullable = false)
    private Double windSpeed;

    @Column(name = "wind_gust", nullable = false)
    private Double windGust = 0.0;

    @Column(name = "pressure", nullable = false)
    private Double pressure;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "impact_radius_km", nullable = false)
    private Double impactRadiusKm = 100.0;

    @Column(name = "impact_polygon", columnDefinition = "geometry(Polygon, 4326)")
    private Polygon impactPolygon;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public StormTrackEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public StormEntity getStorm() {
        return storm;
    }

    public void setStorm(StormEntity storm) {
        this.storm = storm;
    }

    public String getTrackType() {
        return trackType;
    }

    public void setTrackType(String trackType) {
        this.trackType = trackType;
    }

    public OffsetDateTime getForecastTime() {
        return forecastTime;
    }

    public void setForecastTime(OffsetDateTime forecastTime) {
        this.forecastTime = forecastTime;
    }

    public Point getGeom() {
        return geom;
    }

    public void setGeom(Point geom) {
        this.geom = geom;
    }

    public Double getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(Double windSpeed) {
        this.windSpeed = windSpeed;
    }

    public Double getWindGust() {
        return windGust;
    }

    public void setWindGust(Double windGust) {
        this.windGust = windGust;
    }

    public Double getPressure() {
        return pressure;
    }

    public void setPressure(Double pressure) {
        this.pressure = pressure;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getImpactRadiusKm() {
        return impactRadiusKm;
    }

    public void setImpactRadiusKm(Double impactRadiusKm) {
        this.impactRadiusKm = impactRadiusKm;
    }

    public Polygon getImpactPolygon() {
        return impactPolygon;
    }

    public void setImpactPolygon(Polygon impactPolygon) {
        this.impactPolygon = impactPolygon;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
