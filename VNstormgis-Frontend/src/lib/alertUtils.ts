import type { AlertFeature, AlertSeverity } from "@/types/alert";
import type { CoordinateTuple } from "@/types/map";

export const ALERT_FOCUS_ZOOM = 11;

export const ALERT_SEVERITY_CONFIG: Record<AlertSeverity, { label: string; className: string }> = {
  INFO: { label: "Thông tin", className: "border-mint text-mint" },
  WARNING: { label: "Cảnh báo", className: "border-weather-warning text-weather-warning" },
  DANGER: { label: "Nguy hiểm", className: "border-weather-severe text-weather-severe" },
  CRITICAL: { label: "Khẩn cấp", className: "border-weather-extreme text-weather-extreme" },
};
const UNKNOWN_SEVERITY = { label: "Chưa xác định", className: "border-sage text-topo" };

export function getAlertSeverityConfig(severity: string) {
  return Object.prototype.hasOwnProperty.call(ALERT_SEVERITY_CONFIG, severity)
    ? ALERT_SEVERITY_CONFIG[severity as AlertSeverity]
    : UNKNOWN_SEVERITY;
}

/** The single GeoJSON [lng, lat] → Leaflet [lat, lng] conversion boundary. */
export function getAlertCoordinates(feature: AlertFeature): CoordinateTuple | null {
  const geometry = feature.geometry;
  if (!geometry || typeof geometry !== "object" || !("type" in geometry) ||
      geometry.type !== "Point" || !("coordinates" in geometry)) return null;
  const coordinates = geometry.coordinates;
  if (!Array.isArray(coordinates) || coordinates.length < 2) return null;
  const [lng, lat] = coordinates;
  if (typeof lng !== "number" || typeof lat !== "number" ||
      !Number.isFinite(lng) || !Number.isFinite(lat) ||
      lng < -180 || lng > 180 || lat < -90 || lat > 90) return null;
  return [lat, lng];
}

const alertTimeFormatter = new Intl.DateTimeFormat("vi-VN", {
  timeZone: "Asia/Ho_Chi_Minh",
  year: "numeric", month: "2-digit", day: "2-digit",
  hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false,
});

export function formatAlertTime(value: string | null | undefined): string {
  if (!value || !Number.isFinite(Date.parse(value))) return "Chưa có thời điểm";
  return `${alertTimeFormatter.format(new Date(value))} (UTC+7)`;
}
