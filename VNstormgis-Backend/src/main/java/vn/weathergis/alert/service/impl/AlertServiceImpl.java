package vn.weathergis.alert.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.weathergis.alert.domain.AlertEntity;
import vn.weathergis.alert.domain.AlertThresholdEntity;
import vn.weathergis.alert.dto.AlertDetailResponse;
import vn.weathergis.alert.dto.AlertDetailResponse.AlertLocationSummary;
import vn.weathergis.alert.dto.AlertDetailResponse.AlertStormSummary;
import vn.weathergis.alert.dto.AlertDetailResponse.AlertThresholdSummary;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.repository.AlertRepository;
import vn.weathergis.alert.repository.AlertThresholdRepository;
import vn.weathergis.alert.service.AlertService;
import vn.weathergis.common.api.GeoJsonFeature;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.ResourceNotFoundException;
import vn.weathergis.location.domain.LocationEntity;
import vn.weathergis.location.repository.LocationRepository;
import vn.weathergis.storm.domain.StormEntity;
import vn.weathergis.storm.repository.StormRepository;

@Service
@Transactional(readOnly = true)
public class AlertServiceImpl implements AlertService {

    private static final String STATUS_ACTIVE = "active";

    private final AlertRepository alertRepository;
    private final AlertThresholdRepository thresholdRepository;
    private final LocationRepository locationRepository;
    private final StormRepository stormRepository;
    private final ObjectMapper objectMapper;

    public AlertServiceImpl(
            AlertRepository alertRepository,
            AlertThresholdRepository thresholdRepository,
            LocationRepository locationRepository,
            StormRepository stormRepository,
            ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.thresholdRepository = thresholdRepository;
        this.locationRepository = locationRepository;
        this.stormRepository = stormRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public GeoJsonFeatureCollection getActiveAlertsAsGeoJson() {
        List<AlertEntity> activeAlerts =
                alertRepository.findByStatusOrderByTriggeredAtDescIdAsc(STATUS_ACTIVE);
        GeoJsonFeatureCollection collection = new GeoJsonFeatureCollection();
        for (AlertEntity alert : activeAlerts) {
            AlertFactualSnapshot snapshot = parseSnapshotOrNull(alert.getFactualSnapshot());
            String locationCode = snapshot != null ? snapshot.locationCode() : null;
            String locationName = snapshot != null ? snapshot.locationName() : null;
            if ((locationCode == null || locationName == null) && alert.getLocationId() != null) {
                LocationEntity location =
                        locationRepository.findById(alert.getLocationId()).orElse(null);
                if (location != null) {
                    if (locationCode == null) {
                        locationCode = location.getCode();
                    }
                    if (locationName == null) {
                        locationName = location.getName();
                    }
                }
            }

            Geometry geometry = normalizeGeometry(alert.getAffectedGeom());
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("alert_id", alert.getId().toString());
            props.put("location_code", explicitNull(locationCode));
            props.put("location_name", explicitNull(locationName));
            props.put("severity_level", alert.getSeverityLevel());
            props.put("title", alert.getTitle());
            props.put("trigger_reason", alert.getTriggerReason());
            props.put(
                    "triggered_at",
                    alert.getTriggeredAt() == null
                            ? NullNode.instance
                            : alert.getTriggeredAt().toInstant());
            props.put("is_fallback", Boolean.TRUE.equals(alert.getIsFallback()));

            collection.addFeature(new GeoJsonFeature(alert.getId().toString(), geometry, props));
        }
        return collection;
    }

    @Override
    public AlertDetailResponse getAlertById(UUID id) {
        AlertEntity alert =
                alertRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                ErrorCode.ALERT_NOT_FOUND,
                                                "Không tìm thấy cảnh báo: " + id));

        AlertLocationSummary locationSummary =
                alert.getLocationId() == null
                        ? null
                        : locationRepository
                                .findById(alert.getLocationId())
                                .map(AlertServiceImpl::toLocationSummary)
                                .orElse(null);

        AlertThresholdSummary thresholdSummary =
                alert.getThresholdId() == null
                        ? null
                        : thresholdRepository
                                .findById(alert.getThresholdId())
                                .map(AlertServiceImpl::toThresholdSummary)
                                .orElse(null);

        AlertStormSummary stormSummary =
                alert.getStormId() == null
                        ? null
                        : stormRepository
                                .findById(alert.getStormId())
                                .map(AlertServiceImpl::toStormSummary)
                                .orElse(null);

        AlertFactualSnapshot snapshot = parseSnapshotOrNull(alert.getFactualSnapshot());

        return new AlertDetailResponse(
                alert.getId(),
                alert.getThresholdId(),
                alert.getLocationId(),
                alert.getStormId(),
                alert.getTitle(),
                alert.getSeverityLevel(),
                alert.getTriggerReason(),
                alert.getAiExplanation(),
                Boolean.TRUE.equals(alert.getIsFallback()),
                alert.getStatus(),
                alert.getTriggeredAt() == null ? null : alert.getTriggeredAt().toInstant(),
                alert.getResolvedAt() == null ? null : alert.getResolvedAt().toInstant(),
                alert.getCreatedAt() == null ? null : alert.getCreatedAt().toInstant(),
                locationSummary,
                thresholdSummary,
                stormSummary,
                snapshot);
    }

    private AlertFactualSnapshot parseSnapshotOrNull(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawJson, AlertFactualSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể đọc factual_snapshot từ CSDL", ex);
        }
    }

    private static Geometry normalizeGeometry(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        return geometry;
    }

    private static AlertLocationSummary toLocationSummary(LocationEntity entity) {
        Point geom = entity.getGeom();
        List<Double> coordinates =
                geom == null
                                || geom.isEmpty()
                                || !Double.isFinite(geom.getX())
                                || !Double.isFinite(geom.getY())
                        ? null
                        : List.of(geom.getX(), geom.getY());
        return new AlertLocationSummary(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getRegion(),
                coordinates);
    }

    private static AlertThresholdSummary toThresholdSummary(AlertThresholdEntity entity) {
        return new AlertThresholdSummary(
                entity.getId(),
                entity.getName(),
                entity.getParameter(),
                entity.getOperator(),
                entity.getThresholdValue(),
                entity.getSeverityLevel(),
                entity.getDescription(),
                entity.getIsEnabled(),
                entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toInstant());
    }

    private static AlertStormSummary toStormSummary(StormEntity entity) {
        Point geom = entity.getGeomCurrent();
        List<Double> center =
                geom == null
                                || geom.isEmpty()
                                || !Double.isFinite(geom.getX())
                                || !Double.isFinite(geom.getY())
                        ? null
                        : List.of(geom.getX(), geom.getY());
        return new AlertStormSummary(
                entity.getId(),
                entity.getName(),
                entity.getCategory(),
                entity.getStatus(),
                entity.getMaxWindSpeed(),
                entity.getMinPressure(),
                center,
                entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toInstant());
    }

    private static Object explicitNull(Object value) {
        return value == null ? NullNode.instance : value;
    }
}
