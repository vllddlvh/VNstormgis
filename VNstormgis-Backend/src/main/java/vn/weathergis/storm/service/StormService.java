package vn.weathergis.storm.service;

import java.util.List;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.storm.dto.StormDetailResponse;
import vn.weathergis.storm.dto.StormSummaryResponse;

/** Tầng đọc bão nhiệt đới (bounded context storm) — tiêu thụ bởi StormController (W3-01). */
public interface StormService {

    /** GeoJSON FeatureCollection RFC 7946 — danh sách bão theo filter status (đã validate). */
    GeoJsonFeatureCollection getStormsAsGeoJson(String status);

    /** Danh sách DTO — cho format=json của GET /storms. */
    List<StormSummaryResponse> getStorms(String status);

    /**
     * Chi tiết 1 cơn bão theo mã sự kiện GDACS; không tồn tại → ResourceNotFoundException(STM_404).
     */
    StormDetailResponse getStormDetail(String id);
}
