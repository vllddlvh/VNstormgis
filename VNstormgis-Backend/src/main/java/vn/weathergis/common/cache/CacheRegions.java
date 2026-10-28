package vn.weathergis.common.cache;

/** Tên 3 vùng cache phân tán — W3-04 import hằng số này để eviction, không hardcode chuỗi. */
public final class CacheRegions {

    /** Danh mục trạm quan trắc tĩnh — TTL 24 giờ. */
    public static final String LOCATIONS = "locations";

    /** Thời tiết hiện tại 68 trạm — TTL 15 phút, khớp chu kỳ sync OWM. */
    public static final String WEATHER_CURRENT = "weather_current";

    /** Bão đang hoạt động — TTL 30 phút, khớp chu kỳ sync GDACS (chỉ status=active). */
    public static final String ACTIVE_STORMS = "active_storms";

    private CacheRegions() {}
}
