/**
 * @file rainfallUtils.ts
 * @description Cấu hình phân cấp màu và công thức bán kính vòng tròn lượng mưa (SR-MAP-002).
 * Tách khỏi RainfallLayer.tsx để tuân thủ react-refresh/only-export-components.
 *
 * @author VNstormgis Team - Frontend & GIS Engineer
 * @version 1.0.0
 */

import { RainSeverityLevel } from "@/types/weather";

/**
 * Bảng màu 4 bậc theo README Tuần 2 Mục 3.5 (quyết định #4) — tách bạch với
 * SEVERITY_CONFIG 6 bậc của icon trạm (markerFactory).
 */
export const RAINFALL_SEVERITY_CONFIG: Record<
  RainSeverityLevel,
  { label: string; color: string }
> = {
  NONE: { label: "Không mưa", color: "#84907f" }, // Sage Stone
  MODERATE: { label: "Mưa nhỏ / vừa", color: "#68d391" }, // Xanh lục nhạt
  WARNING: { label: "Mưa to", color: "#ecc94b" }, // Vàng cam rực
  DANGER: { label: "Mưa rất to / Nguy hiểm", color: "#e53e3e" }, // Đỏ cờ báo động
};

const RAIN_BASE_RADIUS_PX = 6;
const RAIN_MAX_RADIUS_PX = 18;
const RAIN_SATURATION_MM = 50;

/** Bán kính px: 6 (không mưa) → 18 (≥ 50mm) — nội suy tuyến tính, bão hòa tại 50 (quyết định #6). */
export function calculateRainfallRadius(rain1h: number | null): number {
  const rain = rain1h ?? 0;
  if (rain <= 0) return RAIN_BASE_RADIUS_PX;
  const ratio = Math.min(rain, RAIN_SATURATION_MM) / RAIN_SATURATION_MM;
  return RAIN_BASE_RADIUS_PX + ratio * (RAIN_MAX_RADIUS_PX - RAIN_BASE_RADIUS_PX);
}
