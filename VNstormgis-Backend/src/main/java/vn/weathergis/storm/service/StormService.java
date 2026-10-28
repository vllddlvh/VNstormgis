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

    /**
     * GeoJSON FeatureCollection RFC 7946 — quỹ đạo (lịch sử + dự báo) và vùng ảnh hưởng của một cơn
     * bão, theo trật tự cố định: LineString lịch sử, LineString dự báo (có đỉnh nối từ vị trí mới
     * nhất), Polygon vùng ảnh hưởng, rồi Point cho từng mốc theo {@code forecast_time} tăng dần.
     *
     * <p>Mã sự kiện không tồn tại → {@code ResourceNotFoundException(STM_404_NOT_FOUND)}. Bão hợp
     * lệ chưa có mốc nào → collection rỗng với HTTP 200 (quyết định #13). Feature vùng ảnh hưởng
     * chỉ có khi CSDL mang {@code impact_polygon} thật — không bao giờ suy ra từ bán kính mặc định
     * (quyết định #9/#10, SYS-RULE-004).
     */
    GeoJsonFeatureCollection getStormTrack(String id);
}
