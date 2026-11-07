import { apiClient } from "@/services/apiClient";
import { ALERT_SEVERITY_CONFIG } from "@/lib/alertUtils";
import type { AlertDetail, AlertFeatureCollection } from "@/types/alert";

type Check = (value: unknown) => boolean;
const isString: Check = (value) => typeof value === "string";
const isNumber: Check = (value) => typeof value === "number" && Number.isFinite(value);
const isBoolean: Check = (value) => typeof value === "boolean";
const nullable = (check: Check): Check => (value) => value === null || check(value);
const numberArray: Check = (value) => Array.isArray(value) && value.every(isNumber);
const uuid: Check = (value) => typeof value === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
const object = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);
const fields = (schema: Record<string, Check>): Check => (value) => object(value) &&
  Object.entries(schema).every(([key, check]) => Object.prototype.hasOwnProperty.call(value, key) && check(value[key]));

const representativeFields = {
  threshold_id: nullable(isNumber), parameter: nullable(isString), operator: nullable(isString),
  threshold_value: isNumber, severity_level: nullable(isString), unit: nullable(isString),
  violation_key: nullable(isString),
};
const violation = fields({
  ...representativeFields, measured_value: isNumber,
  source_timestamp: nullable(isString), evaluation_window: nullable(isString),
  storm_id: nullable(isString), storm_name: nullable(isString), distance_km: nullable(isNumber),
});
const snapshot = fields({
  location_id: nullable(uuid), location_code: nullable(isString), location_name: nullable(isString),
  coordinates: nullable(numberArray), evaluation_cycle_id: nullable(uuid), evaluated_at: nullable(isString),
  representative_threshold: nullable(fields(representativeFields)),
  violations: (value) => Array.isArray(value) && value.every(violation),
});
const properties = fields({
  alert_id: uuid, location_code: nullable(isString), location_name: nullable(isString),
  severity_level: isString, title: nullable(isString), trigger_reason: nullable(isString),
  triggered_at: nullable(isString), is_fallback: isBoolean,
});
const detailShape = fields({
  id: uuid, threshold_id: nullable(isNumber), location_id: nullable(uuid), storm_id: nullable(isString),
  title: nullable(isString), severity_level: isString, trigger_reason: nullable(isString),
  ai_explanation: nullable(isString), is_fallback: isBoolean,
  status: (value) => value === "active" || value === "resolved" || value === "dismissed",
  triggered_at: nullable(isString), resolved_at: nullable(isString), created_at: nullable(isString),
  location: nullable(fields({
    id: uuid, code: nullable(isString), name: nullable(isString), region: nullable(isString),
    coordinates: nullable(numberArray),
  })),
  threshold: nullable(fields({
    id: isNumber, name: nullable(isString), parameter: nullable(isString), operator: nullable(isString),
    threshold_value: nullable(isNumber), severity_level: nullable(isString), description: nullable(isString),
    is_enabled: nullable(isBoolean), updated_at: nullable(isString),
  })),
  storm: nullable(fields({
    id: isString, name: nullable(isString), category: nullable(isString), status: nullable(isString),
    max_wind_speed: nullable(isNumber), min_pressure: nullable(isNumber),
    current_center: nullable(numberArray), updated_at: nullable(isString),
  })),
  factual_snapshot: nullable(snapshot),
});

function recordUnknownSeverity(severity: string) {
  if (!Object.prototype.hasOwnProperty.call(ALERT_SEVERITY_CONFIG, severity)) {
    console.warn("[alerts] API trả về severity_level chưa được hỗ trợ");
  }
}

export const alertService = {
  async getActiveAlerts(signal?: AbortSignal): Promise<AlertFeatureCollection> {
    const response = await apiClient.get<unknown>("/v1/alerts/active", { signal });
    const value = response.data;
    if (!object(value) || value.type !== "FeatureCollection" || !Array.isArray(value.features) ||
        !value.features.every((feature) => object(feature) && feature.type === "Feature" && uuid(feature.id) &&
          Object.prototype.hasOwnProperty.call(feature, "geometry") && properties(feature.properties) &&
          object(feature.properties) && feature.id === feature.properties.alert_id) ||
        new Set(value.features.map((feature) => feature.id)).size !== value.features.length) {
      throw new Error("Phản hồi danh sách cảnh báo không đúng cấu trúc");
    }
    const collection = value as unknown as AlertFeatureCollection;
    collection.features.forEach((feature) => recordUnknownSeverity(feature.properties.severity_level));
    return collection;
  },

  async getAlertDetail(id: string, signal?: AbortSignal): Promise<AlertDetail> {
    const response = await apiClient.get<unknown>(`/v1/alerts/${encodeURIComponent(id)}`, { signal });
    const value = response.data;
    if (!object(value) || value.success !== true || !detailShape(value.data) ||
        !object(value.data) || value.data.id !== id) {
      throw new Error("Phản hồi chi tiết cảnh báo không đúng cấu trúc");
    }
    const detail = value.data as unknown as AlertDetail;
    recordUnknownSeverity(detail.severity_level);
    return detail;
  },
};
