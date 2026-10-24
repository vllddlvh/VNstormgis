/**
 * @file windUtils.ts
 * @description Cấu hình phân cấp màu Beaufort, công thức xoay mũi tên theo phương vị gió
 * và nhà máy L.divIcon SVG cho lớp trường gió (SR-MAP-003). Tách khỏi WindLayer.tsx
 * để tuân thủ react-refresh/only-export-components (khuôn mẫu rainfallUtils.ts, W2-08).
 *
 * @author VNstormgis Team - Frontend & GIS Engineer
 * @version 1.0.0
 */

import L from "leaflet";

/** Bậc gió hiển thị trên bản đồ (gộp theo nhóm Beaufort của README Tuần 2 Mục 3.5). */
export type WindBeaufortBand = "LIGHT" | "MODERATE" | "STRONG" | "VIOLENT";

/**
 * Bảng màu 4 bậc theo README Tuần 2 Mục 3.5 — hex Tailwind chuẩn, cùng phiên pháp
 * với RAINFALL_SEVERITY_CONFIG của lớp mưa (W2-08). Kích thước mũi tên rời rạc theo
 * bậc (16/20/24/28px) để tránh phình DOM khi gió giật (RSK-W2-04).
 */
export const WIND_BEAUFORT_CONFIG: Record<
  WindBeaufortBand,
  { label: string; color: string; arrowSizePx: number }
> = {
  LIGHT: { label: "Cấp 1-3 — gió nhẹ", color: "#63b3ed", arrowSizePx: 16 },
  MODERATE: { label: "Cấp 4-6 — gió vừa", color: "#f6e05e", arrowSizePx: 20 },
  STRONG: { label: "Cấp 7-8 — gió mạnh", color: "#ed8936", arrowSizePx: 24 },
  VIOLENT: { label: "≥ Cấp 9 — gió rất mạnh / nguy hiểm", color: "#e53e3e", arrowSizePx: 28 },
};

/** Gió lặng Beaufort 0 — hướng gió vô nghĩa, hiển thị chấm tròn (SR-MAP-003 Exceptions). */
export const WIND_CALM_THRESHOLD_MS = 0.5;

/** Phân bậc Beaufort theo wind_speed (m/s) — biên dưới bao trùm, đúng ngưỡng README/WMO. */
export function getWindBeaufortBand(windSpeed: number): WindBeaufortBand {
  if (windSpeed < 5.5) return "LIGHT";
  if (windSpeed < 13.9) return "MODERATE";
  if (windSpeed < 20.8) return "STRONG";
  return "VIOLENT";
}

/**
 * Góc xoay CSS của mũi tên (độ, thuận chiều kim đồng hồ từ hướng Bắc).
 * OWM wind.deg là phương vị gió ĐẾN (FROM); mũi tên hiển thị chiều DÒNG (toward)
 * nên xoay thêm 180°.
 */
export function calculateWindArrowRotation(windDirection: number): number {
  return (windDirection + 180) % 360;
}

/** Mũi tên SVG gốc chỉ thẳng lên (Bắc = 0°) — xoay bằng CSS transform trên wrapper. */
export function createWindArrowIcon(
  color: string,
  arrowSizePx: number,
  rotationDeg: number,
  opacity: number,
): L.DivIcon {
  const half = arrowSizePx / 2;
  const html = `
    <div class="wind-arrow-icon" style="opacity: ${opacity}; transform: rotate(${rotationDeg}deg);">
      <svg width="${arrowSizePx}" height="${arrowSizePx}" viewBox="0 0 24 24"
           xmlns="http://www.w3.org/2000/svg" style="display: block;">
        <path d="M12 3 L12 21 M12 3 L7.5 9.5 M12 3 L16.5 9.5"
              stroke="${color}" stroke-width="2" fill="none" stroke-linecap="square"/>
      </svg>
    </div>`;
  return L.divIcon({
    html,
    className: "wind-arrow-marker",
    iconSize: [arrowSizePx, arrowSizePx],
    iconAnchor: [half, half],
  });
}

/** Ký hiệu chấm tròn cho hướng khuyết / gió lặng (SR-MAP-003 Exceptions). */
export function createWindCalmIcon(color: string, sizePx: number, opacity: number): L.DivIcon {
  const half = sizePx / 2;
  const html = `
    <div class="wind-calm-icon"
         style="opacity: ${opacity}; width: ${sizePx}px; height: ${sizePx}px;
                display: flex; align-items: center; justify-content: center;">
      <svg width="12" height="12" viewBox="0 0 12 12" xmlns="http://www.w3.org/2000/svg">
        <circle cx="6" cy="6" r="3" fill="${color}"/>
        <circle cx="6" cy="6" r="5.5" fill="none" stroke="${color}" stroke-width="1" opacity="0.5"/>
      </svg>
    </div>`;
  return L.divIcon({
    html,
    className: "wind-calm-marker",
    iconSize: [sizePx, sizePx],
    iconAnchor: [half, half],
  });
}
