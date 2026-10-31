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
import { StormFeature, StormProperties, shortStormLabel } from "@/types/storm";

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
