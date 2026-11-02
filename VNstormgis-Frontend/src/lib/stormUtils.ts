/**
 * @file stormUtils.ts
 * @description Cổng chuyển Feature bão -> dữ liệu render Leaflet, định dạng số liệu trung thực
 * (null → "--"), và helper dựng DivIcon. Mọi chuyển tọa độ đi qua geoUtils.geojsonToLatLng.
 * Tách khỏi .tsx theo tiền lệ rainfallUtils.ts/windUtils.ts (react-refresh/only-export-components).
 *
 * @author VNstormgis Team - Frontend & GIS Engineer
 * @version 1.0.0
 */

import { CoordinateTuple } from "@/types/map";
import { geojsonToLatLng } from "@/lib/geoUtils";
import {
  StormFeature,
  StormForecastFeature,
  StormHistoryFeature,
  StormImpactFeature,
  StormProperties,
  StormTrackCollection,
  StormTrackKind,
  StormTrackPointFeature,
  shortStormLabel,
} from "@/types/storm";

/** Một tâm bão đã tách tọa độ, sẵn sàng cho <Marker position={latLng}>. */
export interface StormCenter {
  id: string;
  /** [lat, lng] — Leaflet (đã đảo từ GeoJSON [lng, lat]). */
  latLng: CoordinateTuple;
  /** Nhãn ngắn, ví dụ "CHAMPI-26". */
  label: string;
  properties: StormProperties;
}

/**
 * Chuyển 1 Feature thành StormCenter. Trả null (KÈM log warn) khi geometry/coordinates rác —
 * tuyệt đối không âm thầm vẽ tại [0,0] và không được làm sập cả lớp vì một feature hỏng.
 */
export function toStormCenter(feature: StormFeature): StormCenter | null {
  if (feature.geometry?.type !== "Point") {
    console.warn(
      `[stormLayer] Feature ${feature.id} không phải Point — bỏ qua (hợp đồng W3-01)`,
    );
    return null;
  }
  try {
    const latLng = geojsonToLatLng(feature.geometry.coordinates);
    return {
      id: feature.id ?? feature.properties.id,
      latLng,
      label: shortStormLabel(feature.properties.name),
      properties: feature.properties,
    };
  } catch (error) {
    console.warn(
      `[stormLayer] Tọa độ không hợp lệ cho bão ${feature.id}:`,
      error,
    );
    return null;
  }
}

export function toStormCenters(features: StormFeature[]): StormCenter[] {
  return features
    .map(toStormCenter)
    .filter((center): center is StormCenter => center !== null);
}

/** km/h: "296.3 km/h" (backend đã làm tròn 1 chữ số). */
export function formatKmh(value: number): string {
  return `${value.toFixed(1)} km/h`;
}

/** m/s: "82.3 m/s". */
export function formatMs(value: number): string {
  return `${value.toFixed(1)} m/s`;
}

/** Trung thực tuyệt đối: null/NaN → "--", không nội suy, không bấp cấp (SYS-RULE-004). */
export function formatPressure(value: number | null): string {
  return value === null || Number.isNaN(value)
    ? "--"
    : `${Math.round(value)} hPa`;
}

/**
 * Giờ đo theo mốc dữ liệu (không dùng giờ browser) — "HH:mm dd/MM" theo múi giờ hiển thị
 * của người dùng. Định dạng tay thay vì toLocaleString để không phụ thuộc ICU hyphen/slash.
 */
export function formatSyncTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "--";
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const month = String(date.getMonth() + 1).padStart(2, "0");
  return `${hours}:${minutes} ${day}/${month}`;
}

/** max(updated_at) trong payload — mốc ĐO thật cho lastStormSyncedAt (W2-02 quyết định #6). */
export function latestStormUpdatedAt(features: StormFeature[]): string | null {
  let latest: string | null = null;
  let maxTs = Number.NEGATIVE_INFINITY;
  for (const feature of features) {
    const ts = Date.parse(feature.properties.updated_at);
    if (!Number.isNaN(ts) && ts > maxTs) {
      maxTs = ts;
      latest = feature.properties.updated_at;
    }
  }
  return latest;
}

// ============================ W3-06: STORM TRACK LAYER ============================

/** Màu & kiểu dáng quỹ đạo — token San Rita có sẵn trong tailwind.config.js (quyết định #3/#4/#5). */
export const TRACK_HISTORY_COLOR = "#dde2e4"; // Smoke — đường lịch sử liền nét
export const TRACK_FORECAST_COLOR = "#f59e0b"; // Orange — đường dự báo đứt đoạn
export const TRACK_IMPACT_COLOR = "#dc2626"; // Red — viền vùng ảnh hưởng nguy hiểm
export const TRACK_FORECAST_DASH_ARRAY = "6, 6";
export const TRACK_IMPACT_FILL_OPACITY = 0.15;
/** Ngưỡng zoom hiển thị nhãn permanent của mốc (quyết định #6 — README "khi zoom gần"). */
export const TRACK_TOOLTIP_ZOOM_THRESHOLD = 8;

/** Một mốc quỹ đạo đã tách tọa độ — sẵn sàng cho <CircleMarker center={latLng}>. */
export interface StormTrackPointDatum {
  trackId: number;
  /** [lat, lng] — Leaflet (đã đảo từ GeoJSON [lng, lat]). */
  latLng: CoordinateTuple;
  trackType: "historical" | "forecast";
  forecastTime: string;
  windSpeed: number | null;
  windGust: number | null;
  pressure: number | null;
  category: string | null;
}

/** Mô hình render quỹ đạo 1 cơn bão — bàn giao W3-07 dựng legend. */
export interface StormTrackData {
  stormId: string;
  /** Đỉnh đã đảo [lat, lng] của LineString kind=history (rỗng khi bão ≤ 1 mốc — W3-02 #14). */
  historyLatLngs: CoordinateTuple[];
  /** Đỉnh đã đảo của LineString kind=forecast — ĐÃ GẮN SẴN đỉnh nối từ backend (W3-02 #4). */
  forecastLatLngs: CoordinateTuple[];
  /** Ring đã đảo của Polygon kind=impact — null khi payload không có vùng ảnh hưởng thật. */
  impactRings: CoordinateTuple[][] | null;
  impactRadiusKm: number | null;
  points: StormTrackPointDatum[];
}

/** Union 4 loại feature của hợp đồng /track (W3-02) — bridge cục bộ cho collection bên dưới. */
type StormTrackFeature =
  | StormHistoryFeature
  | StormForecastFeature
  | StormImpactFeature
  | StormTrackPointFeature;

/**
 * Cửa sổ đọc chung cho 3 union properties của /track. TypeScript không narrow
 * `feature.properties` theo `feature.geometry.type` lồng nhau, nên khai báo tường minh
 * các khóa hợp đồng ở dạng optional — geometry.type đã phân nhánh trước khi đọc.
 */
interface StormTrackPropertiesWindow {
  kind?: StormTrackKind;
  radius_km?: number | null;
  track_id?: number;
  track_type?: "historical" | "forecast";
  forecast_time?: string;
  wind_speed?: number | null;
  wind_gust?: number | null;
  pressure?: number | null;
  category?: string | null;
}

/** Đảo một mảng đỉnh GeoJSON [lng, lat] → [lat, lng]. Đỉnh rác bị BỎ kèm warn — không sập cả đường. */
function toLatLngs(coords: number[][], context: string): CoordinateTuple[] {
  const out: CoordinateTuple[] = [];
  for (const coord of coords) {
    try {
      out.push(geojsonToLatLng(coord));
    } catch (error) {
      console.warn(
        `[stormTrack] Đỉnh không hợp lệ trong ${context} — bỏ qua 1 đỉnh:`,
        error,
      );
    }
  }
  return out;
}

/**
 * Chuyển StormTrackCollection (hợp đồng W3-02) → StormTrackData.
 * Phân nhánh theo properties.kind (LineString/Polygon) và geometry.type === "Point" (mốc KHÔNG có kind — quyết định #8);
 * KHÔNG phụ thuộc trật tự feature trong mảng (id track-point không tiên đoán được — W3-02 D-3).
 * Trả object (có thể rỗng toàn phần) — null CHỈ khi không có feature nào parse được.
 */
export function toStormTrackData(
  stormId: string,
  collection: StormTrackCollection,
): StormTrackData | null {
  const data: StormTrackData = {
    stormId,
    historyLatLngs: [],
    forecastLatLngs: [],
    impactRings: null,
    impactRadiusKm: null,
    points: [],
  };
  // GeoJsonFeatureCollection chỉ tham số hóa properties nên features kế thừa mặc định
  // geometry Point của GeoJsonFeature; /track thật chứa 4 loại geometry (W3-02) — bridge cục bộ,
  // không sửa types/storm.ts (quyết định #2).
  const features = collection.features as unknown as StormTrackFeature[];
  let recognized = false;
  for (const feature of features) {
    if (!feature?.geometry) {
      console.warn(
        `[stormTrack] Feature ${feature?.id} thiếu geometry — bỏ qua`,
      );
      continue;
    }
    const featureId = feature.id;
    const geometryType: string = feature.geometry.type;
    const props = feature.properties as StormTrackPropertiesWindow;
    if (feature.geometry.type === "LineString") {
      const latLngs = toLatLngs(
        feature.geometry.coordinates,
        `LineString ${featureId ?? "?"}`,
      );
      if (props.kind === "history") {
        data.historyLatLngs = latLngs;
        recognized = true;
      } else if (props.kind === "forecast") {
        data.forecastLatLngs = latLngs; // đỉnh nối đã có sẵn — KHÔNG ghép thêm (quyết định #4)
        recognized = true;
      } else {
        console.warn(
          `[stormTrack] LineString ${featureId} kind lạ (${String(props.kind)}) — bỏ qua`,
        );
      }
    } else if (feature.geometry.type === "Polygon") {
      if (props.kind === "impact") {
        const rings = feature.geometry.coordinates
          .map((ring) => toLatLngs(ring, `Polygon ${featureId ?? "?"}`))
          .filter((ring) => ring.length >= 3);
        if (rings.length > 0) {
          data.impactRings = rings;
          data.impactRadiusKm = props.radius_km ?? null;
          recognized = true;
        }
      } else {
        console.warn(
          `[stormTrack] Polygon ${featureId} không phải kind=impact — bỏ qua`,
        );
      }
    } else if (feature.geometry.type === "Point") {
      try {
        data.points.push({
          trackId: props.track_id ?? -1,
          latLng: geojsonToLatLng(feature.geometry.coordinates),
          trackType:
            props.track_type === "forecast" ? "forecast" : "historical",
          forecastTime: props.forecast_time ?? "",
          windSpeed: props.wind_speed ?? null,
          windGust: props.wind_gust ?? null,
          pressure: props.pressure ?? null,
          category: props.category ?? null,
        });
        recognized = true;
      } catch (error) {
        console.warn(
          `[stormTrack] Mốc ${featureId} tọa độ rác — bỏ qua:`,
          error,
        );
      }
    } else {
      console.warn(
        `[stormTrack] Geometry lạ ${geometryType} (${featureId}) — bỏ qua`,
      );
    }
  }
  return recognized ? data : null;
}

/** m/s nullable: null/NaN → "--" (SYS-RULE-004 — GDACS không công bố gió theo mốc trên live). */
export function formatNullableMs(value: number | null): string {
  return value === null || Number.isNaN(value)
    ? "--"
    : `${value.toFixed(1)} m/s`;
}
