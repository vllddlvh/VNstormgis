package vn.weathergis.storm.service.impl;

import com.fasterxml.jackson.databind.node.NullNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.weathergis.common.api.GeoJsonFeature;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.ResourceNotFoundException;
import vn.weathergis.storm.domain.StormEntity;
import vn.weathergis.storm.dto.StormDetailResponse;
import vn.weathergis.storm.dto.StormSummaryResponse;
import vn.weathergis.storm.repository.StormRepository;
import vn.weathergis.storm.repository.StormTrackRepository;
import vn.weathergis.storm.service.StormService;

/** Dựng GeoJSON RFC 7946 từ bảng storms, quy đổi gió m/s → km/h và đếm mốc quỹ đạo cho detail. */
@Service
public class StormServiceImpl implements StormService {

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
