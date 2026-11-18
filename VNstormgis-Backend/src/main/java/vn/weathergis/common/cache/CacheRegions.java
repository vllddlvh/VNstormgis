package vn.weathergis.common.cache;

/** Tên 3 vùng cache phân tán — W3-04 import hằng số này để eviction, không hardcode chuỗi. */
public final class CacheRegions {

    // V11 đổi danh mục trạm. Dùng vùng cache mới để các mục Redis cũ tự hết hạn.
    /** Danh mục trạm quan trắc tĩnh — TTL 24 giờ. */
    public static final String LOCATIONS = "locations_v2";

    /** Thời tiết hiện tại của các trạm đang hoạt động — TTL 15 phút, khớp chu kỳ sync OWM. */
    public static final String WEATHER_CURRENT = "weather_current_v2";

    /** Bão đang hoạt động — TTL 30 phút, khớp chu kỳ sync GDACS (chỉ status=active). */
    public static final String ACTIVE_STORMS = "active_storms";

    private CacheRegions() {}
}
