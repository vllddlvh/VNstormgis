/** Public alert DTOs keep the API's snake_case and historical values unchanged. */
export type AlertSeverity = "INFO" | "WARNING" | "DANGER" | "CRITICAL";
export type AlertStatus = "active" | "resolved" | "dismissed";

export const ALERT_REFRESH_INTERVAL_MS = 60_000;

export interface AlertProperties {
  alert_id: string;
  location_code: string | null;
  location_name: string | null;
  /** Unknown API values remain visible with a neutral badge. */
  severity_level: string;
  title: string | null;
  trigger_reason: string | null;
  triggered_at: string | null;
  is_fallback: boolean;
}

export interface AlertFeature {
  type: "Feature";
  id: string;
  /** Validated only when locating: malformed geometry must not hide an alert. */
  geometry: unknown;
  properties: AlertProperties;
}

export interface AlertFeatureCollection {
  type: "FeatureCollection";
  features: AlertFeature[];
}

export interface AlertRepresentativeThreshold {
  threshold_id: number | null;
  parameter: string | null;
  operator: string | null;
  threshold_value: number;
  severity_level: string | null;
  unit: string | null;
  violation_key: string | null;
}

export interface AlertViolation extends AlertRepresentativeThreshold {
  measured_value: number;
  source_timestamp: string | null;
  evaluation_window: string | null;
  storm_id: string | null;
  storm_name: string | null;
  distance_km: number | null;
}

export interface AlertFactualSnapshot {
  location_id: string | null;
  location_code: string | null;
  location_name: string | null;
  coordinates: number[] | null;
  evaluation_cycle_id: string | null;
  evaluated_at: string | null;
  representative_threshold: AlertRepresentativeThreshold | null;
  violations: AlertViolation[];
}

export interface AlertDetail {
  id: string;
  threshold_id: number | null;
  location_id: string | null;
  storm_id: string | null;
  title: string | null;
  severity_level: string;
  trigger_reason: string | null;
  ai_explanation: string | null;
  is_fallback: boolean;
  status: AlertStatus;
  triggered_at: string | null;
  resolved_at: string | null;
  created_at: string | null;
  location: {
    id: string;
    code: string | null;
    name: string | null;
    region: string | null;
    coordinates: number[] | null;
  } | null;
  threshold: {
    id: number;
    name: string | null;
    parameter: string | null;
    operator: string | null;
    threshold_value: number | null;
    severity_level: string | null;
    description: string | null;
    is_enabled: boolean | null;
    updated_at: string | null;
  } | null;
  storm: {
    id: string;
    name: string | null;
    category: string | null;
    status: string | null;
    max_wind_speed: number | null;
    min_pressure: number | null;
    current_center: number[] | null;
    updated_at: string | null;
  } | null;
  factual_snapshot: AlertFactualSnapshot | null;
}
