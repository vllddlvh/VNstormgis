package vn.weathergis.storm.service.impl;

import com.fasterxml.jackson.databind.node.NullNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.weathergis.common.api.GeoJsonFeature;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.ResourceNotFoundException;
import vn.weathergis.storm.domain.StormEntity;
import vn.weathergis.storm.domain.StormTrackEntity;
import vn.weathergis.storm.dto.StormDetailResponse;
import vn.weathergis.storm.dto.StormSummaryResponse;
import vn.weathergis.storm.repository.StormRepository;
import vn.weathergis.storm.repository.StormTrackRepository;
import vn.weathergis.storm.service.StormService;

/**
 * Tầng đọc bounded context storm: dựng GeoJSON RFC 7946 từ bảng storms (danh sách, chi tiết) và từ
 * storm_tracks (quỹ đạo lịch sử + dự báo, vùng ảnh hưởng), quy đổi gió m/s → km/h.
 */
@Service
public class StormServiceImpl implements StormService {

    private static final String TYPE_HISTORICAL = "historical";
    private static final String TYPE_FORECAST = "forecast";

    /**
     * SRID 4326 bắt buộc — factory gán SRID cho mọi LineString dựng ở tầng đọc (quyết định #15).
     */
    private static final GeometryFactory GEO_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private final StormRepository stormRepository;
    private final StormTrackRepository stormTrackRepository;

    public StormServiceImpl(
            StormRepository stormRepository, StormTrackRepository stormTrackRepository) {
        this.stormRepository = stormRepository;
        this.stormTrackRepository = stormTrackRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public GeoJsonFeatureCollection getStormsAsGeoJson(String status) {
        GeoJsonFeatureCollection collection = new GeoJsonFeatureCollection();
        for (StormEntity storm : listStorms(status)) {
            collection.addFeature(
                    new GeoJsonFeature(storm.getId(), storm.getGeomCurrent(), properties(storm)));
        }
        return collection;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StormSummaryResponse> getStorms(String status) {
        return listStorms(status).stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StormDetailResponse getStormDetail(String id) {
        StormEntity storm =
                stormRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                ErrorCode.STORM_NOT_FOUND,
                                                "Không tìm thấy bão: " + id));
        return new StormDetailResponse(
                storm.getId(),
                storm.getName(),
                storm.getYear(),
                storm.getCategory(),
                storm.getMaxWindSpeed(),
                toKmh(storm.getMaxWindSpeed()),
                storm.getMinPressure(),
                storm.getStatus(),
                storm.getGeomCurrent().getX(), // x = KINH ĐỘ (AGENTS.md mục 1)
                storm.getGeomCurrent().getY(), // y = VĨ ĐỘ
                stormTrackRepository.countByStormId(storm.getId()),
                storm.getStartDate(),
                storm.getEndDate(),
                storm.getUpdatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public GeoJsonFeatureCollection getStormTrack(String id) {
        StormEntity storm =
                stormRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                ErrorCode.STORM_NOT_FOUND,
                                                "Không tìm thấy bão: " + id));

        List<StormTrackEntity> tracks =
                stormTrackRepository.findByStormIdOrderByForecastTimeAsc(id);
        List<StormTrackEntity> historical = filterByType(tracks, TYPE_HISTORICAL);
        List<StormTrackEntity> forecast = filterByType(tracks, TYPE_FORECAST);

        GeoJsonFeatureCollection collection = new GeoJsonFeatureCollection();
        addLineFeature(collection, storm.getId(), "track-history", "history", historical, null);
        addLineFeature(
                collection,
                storm.getId(),
                "track-forecast",
                "forecast",
                forecast,
                junctionPoint(storm, historical));
        addImpactFeature(collection, storm.getId(), tracks);
        historical.forEach(t -> collection.addFeature(pointFeature(storm.getId(), t)));
        forecast.forEach(t -> collection.addFeature(pointFeature(storm.getId(), t)));
        return collection;
    }

    private static List<StormTrackEntity> filterByType(
            List<StormTrackEntity> tracks, String trackType) {
        return tracks.stream().filter(t -> trackType.equalsIgnoreCase(t.getTrackType())).toList();
    }

    /**
     * LineString nối các mốc theo trật tự đã có ({@code forecast_time} ASC). Dưới 2 đỉnh thì bỏ
     * feature — JTS không dựng được LineString 1 đỉnh và RFC 7946 cũng cấm (quyết định #14).
     */
    private void addLineFeature(
            GeoJsonFeatureCollection collection,
            String stormId,
            String featureId,
            String kind,
            List<StormTrackEntity> points,
            Point junction) {
        List<Coordinate> coords = new ArrayList<>();
        if (junction != null) {
            coords.add(junction.getCoordinate());
        }
        points.forEach(t -> coords.add(t.getGeom().getCoordinate()));
        if (coords.size() < 2) {
            return;
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("kind", kind);
        props.put("storm_id", stormId);
        props.put("points", points.size()); // đỉnh nối là chi tiết render, không phải mốc (#4)
        collection.addFeature(
                new GeoJsonFeature(
                        featureId,
                        GEO_FACTORY.createLineString(coords.toArray(new Coordinate[0])),
                        props));
    }

    /** Đỉnh nối LineString dự báo: mốc historical cuối nếu có, ngược lại tâm bão hiện tại (#4). */
    private Point junctionPoint(StormEntity storm, List<StormTrackEntity> historical) {
        if (historical.isEmpty()) {
            return storm.getGeomCurrent();
        }
        return historical.get(historical.size() - 1).getGeom();
    }

    /**
     * Feature {@code impact-zone} từ polygon của mốc có {@code forecast_time} lớn nhất trong số các
     * mốc mang {@code impact_polygon} — vùng nguy hiểm hiểu là vùng tại thời điểm mới nhất (#11).
     * Danh sách đã sắp ASC nên duyệt ngược và dừng ở kết quả đầu tiên.
     *
     * <p>Không có polygon thật trong CSDL thì KHÔNG sinh feature: {@code impact_radius_km} của dữ
     * liệu GDACS là hằng số 100.0 do ingestion đặt, nên buffer từ nó là vùng bịa — vi phạm
     * SYS-RULE-004 (quyết định #9/#10).
     */
    private void addImpactFeature(
            GeoJsonFeatureCollection collection, String stormId, List<StormTrackEntity> tracks) {
        for (int i = tracks.size() - 1; i >= 0; i--) {
            StormTrackEntity track = tracks.get(i);
            if (track.getImpactPolygon() != null) {
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("kind", "impact");
                props.put("storm_id", stormId);
                props.put("radius_km", track.getImpactRadiusKm());
                collection.addFeature(
                        new GeoJsonFeature("impact-zone", track.getImpactPolygon(), props));
                return;
            }
        }
    }

    /** Point feature cho một mốc — 9 khóa hợp đồng (#7), cột NULL giữ null qua NullNode (#8). */
    private GeoJsonFeature pointFeature(String stormId, StormTrackEntity track) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("track_id", track.getId());
        props.put("storm_id", stormId);
        props.put("track_type", track.getTrackType());
        props.put("forecast_time", track.getForecastTime());
        props.put("wind_speed", explicitNull(track.getWindSpeed()));
        props.put("wind_gust", explicitNull(track.getWindGust()));
        props.put("pressure", explicitNull(track.getPressure()));
        props.put("category", track.getCategory());
        props.put("impact_radius_km", track.getImpactRadiusKm());
        return new GeoJsonFeature("track-point-" + track.getId(), track.getGeom(), props);
    }

    private List<StormEntity> listStorms(String status) {
        if ("all".equalsIgnoreCase(status)) {
            return stormRepository.findAllByOrderByUpdatedAtDesc();
        }
        return stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc(status);
    }

    /** Trật tự khóa ổn định cho hợp đồng properties; min_pressure null giữ nguyên null. */
    private Map<String, Object> properties(StormEntity storm) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", storm.getId());
        props.put("name", storm.getName());
        props.put("year", storm.getYear());
        props.put("category", storm.getCategory());
        props.put("max_wind_speed", storm.getMaxWindSpeed()); // m/s — nguyên bản storage
        props.put("max_wind_speed_kmh", toKmh(storm.getMaxWindSpeed()));
        props.put("min_pressure", explicitNull(storm.getMinPressure())); // SYS-RULE-004
        props.put("status", storm.getStatus());
        props.put("start_date", storm.getStartDate());
        props.put("end_date", explicitNull(storm.getEndDate()));
        props.put("updated_at", storm.getUpdatedAt());
        return props;
    }

    /**
     * application.yml đặt {@code spring.jackson.default-property-inclusion=non_null} (và
     * GeoJsonFeature có {@code @JsonInclude(NON_NULL)}), nên một giá trị Java {@code null} trong
     * Map bị bỏ luôn khóa khi serialize — hợp đồng 11 khóa và TC-13 sẽ thấy {@code end_date}/
     * {@code min_pressure} vắng mặt thay vì {@code null}. NullNode là đối tượng non-null nên khóa ở
     * lại và in ra đúng {@code null}.
     */
    private static Object explicitNull(Object value) {
        return value == null ? NullNode.instance : value;
    }

    /** Quy đổi m/s → km/h, làm tròn 1 chữ số thập phân — hàm thuần cho unit test. */
    public static double toKmh(double windSpeedMs) {
        return Math.round(windSpeedMs * 36.0) / 10.0;
    }

    private StormSummaryResponse toSummary(StormEntity storm) {
        return new StormSummaryResponse(
                storm.getId(),
                storm.getName(),
                storm.getYear(),
                storm.getCategory(),
                storm.getMaxWindSpeed(),
                toKmh(storm.getMaxWindSpeed()),
                storm.getMinPressure(),
                storm.getStatus(),
                storm.getStartDate(),
                storm.getEndDate(),
                storm.getUpdatedAt());
    }
}
