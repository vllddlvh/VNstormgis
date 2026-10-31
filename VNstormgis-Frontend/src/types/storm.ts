/**
 * @file storm.ts
 * @description Kiểu dữ liệu bão nhiệt đới — tiêu thụ hợp đồng W3-01/W3-02/W3-03
 * (GET /api/v1/storms GeoJSON RFC 7946, /storms/{id} envelope, /storms/{id}/track).
 * Đối chiếu 1-1 với StormServiceImpl#properties() — 11 khóa, snake_case,
 * min_pressure/end_date tường minh null qua NullNode (PROGRESS.md §5.1 D-1).
 *
 * @author VNstormgis Team - Frontend Engineer
 * @version 1.0.0
 */

import { GeoJsonFeature, GeoJsonFeatureCollection } from "@/types/location";

/** Cấp cảnh báo GDACS — giá trị THẬT duy nhất backend ghi vào storms.category (Mục 2 #2/#3). */
export type StormCategory = "Red" | "Orange" | "Green";

/** Trạng thái bão theo SYS-RULE-001. */
export type StormStatus = "active" | "dissipated";

/** Nhãn phân nhánh render của W3-02 (LineString/Polygon); Point mốc KHÔNG có khóa này. */
export type StormTrackKind = "history" | "forecast" | "impact";

/** properties của mỗi Feature trong FeatureCollection GET /storms?status=active — 11 khóa. */
export interface StormProperties {
  /** Mã sự kiện GDACS — trùng Feature.id, khóa chính bảng storms. */
  id: string;
  /** Tên quốc tế, ví dụ "Tropical Cyclone CHAMPI-26" (không bịa tên ngắn). */
  name: string;
  year: number;
  /** Cấp cảnh báo GDACS Green/Orange/Red (quyết định #14 W2-05) — chuỗi gốc, KHÔNG phải số. */
  category: string;
  /** Sức gió tối đa duy trì, m/s (đơn vị lưu trữ nguyên bản). */
  max_wind_speed: number;
  /** Sức gió quy đổi km/h, làm tròn 1 chữ số — backend tính (StormServiceImpl#toKmh). */
  max_wind_speed_kmh: number;
  /** Áp suất tâm hPa — NULL khi GDACS không công bố (SYS-RULE-004) → hiển thị "--". */
  min_pressure: number | null;
  status: StormStatus;
  /** Thời điểm hình thành (ISO-8601). */
  start_date: string;
  /** Thời điểm tan — null khi còn active. */
  end_date: string | null;
  /** Thời điểm GDACS cập nhật gần nhất (ISO-8601) — nguồn của lastStormSyncedAt. */
  updated_at: string;
}

/** Feature tâm bão — geometry.coordinates là [KINH ĐỘ, VĨ ĐỘ] (RFC 7946). */
export type StormFeature = GeoJsonFeature<StormProperties>;

/** FeatureCollection của GET /storms?status=active (mặc định geojson). */
export type StormGeoJsonCollection = GeoJsonFeatureCollection<StormProperties>;

/** properties của LineString history/forecast (W3-02). */
export interface StormLineProperties {
  kind: Exclude<StormTrackKind, "impact">;
  storm_id: string;
  /** Số mốc thật nối đường (đỉnh nối Junction không tính — quyết định #14 W3-02). */
  points: number;
}

/** properties của Polygon impact-zone (W3-02) — chỉ tồn tại khi CSDL có polygon THẬT. */
export interface StormImpactProperties {
  kind: "impact";
  storm_id: string;
  radius_km: number | null;
}

/** properties của Point mốc quỹ đạo — 9 khóa, NULL in ra null qua NullNode. */
export interface StormTrackPointProperties {
  track_id: number;
  storm_id: string;
  track_type: "historical" | "forecast";
  forecast_time: string;
  wind_speed: number | null;
  wind_gust: number | null;
  pressure: number | null;
  category: string | null;
  impact_radius_km: number;
}

export type StormHistoryFeature = GeoJsonFeature<
  StormLineProperties,
  "LineString",
  [number, number][] | number[][]
>;
export type StormForecastFeature = GeoJsonFeature<
  StormLineProperties,
  "LineString",
  [number, number][] | number[][]
>;
export type StormImpactFeature = GeoJsonFeature<
  StormImpactProperties,
  "Polygon",
  number[][][]
>;
export type StormTrackPointFeature = GeoJsonFeature<StormTrackPointProperties>;

/** FeatureCollection của GET /storms/{id}/track — trộn 4 loại feature (W3-06 render). */
export type StormTrackCollection = GeoJsonFeatureCollection<
  StormLineProperties | StormImpactProperties | StormTrackPointProperties
>;

/** data của ApiResponse từ GET /storms?format=json (camelCase theo StormSummaryResponse). */
export interface StormSummary {
  id: string;
  name: string;
  year: number;
  category: string;
  maxWindSpeed: number;
  maxWindSpeedKmh: number;
  minPressure: number | null;
  status: StormStatus;
  startDate: string;
  endDate: string | null;
  updatedAt: string;
}

/** data của ApiResponse từ GET /storms/{id} (StormDetailResponse). */
export interface StormDetail extends StormSummary {
  /** Kinh độ tâm hiện tại — x của geom_current (AGENTS.md mục 1). */
  longitude: number;
  /** Vĩ độ tâm hiện tại — y của geom_current. */
  latitude: number;
  totalTrackPoints: number;
}

/** Văn bản empty state — hằng số DUY NHẤT dùng cho lớp bão (Mục 2 #8; SR-STM-002 Exceptions). */
export const STORM_EMPTY_MESSAGE =
  "Hiện không có bão hoạt động trong khu vực theo dõi";

/** Nhịp nạp lại lớp bão (ms) — khớp chu kỳ sync GDACS 30 phút (W2-05) và TTL active_storms (W3-03). */
export const STORM_REFRESH_INTERVAL_MS = 30 * 60 * 1000;

/**
 * Hộp theo dõi bão tác chiến — cùng ngưỡng README Mục 3.5 dùng cho verify script W3-09.
 * CHỈ dùng để gắn phù hiệu "NGOÀI VÙNG THEO DÕI", KHÔNG dùng để ẩn tâm bão (Mục 2 #4).
 */
export const STORM_WATCH_BBOX = {
  minLng: 100,
  maxLng: 128,
  minLat: 0,
  maxLat: 28,
} as const;

/** Nhãn hộp theo dõi để in ra UI — dẫn xuất từ hằng trên, KHÔNG hardcode cặp số thứ hai. */
export const STORM_WATCH_BBOX_LABEL = `lng ${STORM_WATCH_BBOX.minLng}–${STORM_WATCH_BBOX.maxLng}, lat ${STORM_WATCH_BBOX.minLat}–${STORM_WATCH_BBOX.maxLat}`;

/** Gán nhãn ngắn cho popup/danh sách: "Tropical Cyclone CHAMPI-26" -> "CHAMPI-26". */
export function shortStormLabel(name: string): string {
  const withoutPrefix = name
    .replace(/^(Tropical\s+)?(Cyclone|Storm|Depression|Typhoon)\s+/i, "")
    .trim();
  const firstToken = withoutPrefix.split(/\s+/)[0] ?? name;
  return firstToken.slice(0, 10).toUpperCase() || "BÃO";
}

/** Tâm có nằm trong hộp theo dõi? Dùng cho phù hiệu, không dùng để lọc. */
export function isWithinStormWatchBox(lat: number, lng: number): boolean {
  return (
    lng >= STORM_WATCH_BBOX.minLng &&
    lng <= STORM_WATCH_BBOX.maxLng &&
    lat >= STORM_WATCH_BBOX.minLat &&
    lat <= STORM_WATCH_BBOX.maxLat
  );
}
