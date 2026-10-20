/**
 * @file markerFactory.ts
 * @description Factory kiến tạo các biểu tượng trạm quan trắc và cảnh báo khí tượng chuyên dụng.
 * Tích hợp bảng màu thời tiết và ngôn ngữ thiết kế San Rita (alpine field guide).
 *
 * @author VNstormgis Team - Frontend & Design Specialist
 * @version 1.0.0
 */

import L from "leaflet";
import { WeatherSeverityLevel } from "@/types/map";

/**
 * Cấu hình bảng màu và nhãn hiển thị cho từng cấp độ rủi ro khí tượng.
 */
export const SEVERITY_CONFIG: Record<
  WeatherSeverityLevel,
  {
    name: string;
    bgHex: string;
    textHex: string;
    borderHex: string;
    pulseClass: string;
  }
> = {
  SAFE: {
    name: "Cấp 1 - An Toàn",
    bgHex: "#e2ffcc", // Highlighter Mint (San Rita primary accent)
    textHex: "#161b13", // Carbon Ink
    borderHex: "#2d3329", // Forest Charcoal
    pulseClass: "",
  },
  ADVISORY: {
    name: "Cấp 2 - Theo Dõi",
    bgHex: "#84907f", // Sage Stone (San Rita secondary accent)
    textHex: "#ffffff",
    borderHex: "#161b13",
    pulseClass: "",
  },
  WARNING: {
    name: "Cấp 3 - Cảnh Báo",
    bgHex: "#f59e0b", // Amber Warning
    textHex: "#161b13",
    borderHex: "#78350f",
    pulseClass: "animate-ping-slow",
  },
  SEVERE: {
    name: "Cấp 4 - Nguy Hiểm",
    bgHex: "#ea580c", // Dark Orange Severe
    textHex: "#ffffff",
    borderHex: "#7c2d12",
    pulseClass: "animate-ping-slow",
  },
  EXTREME: {
    name: "Cấp 5 - Rất Nguy Hiểm",
    bgHex: "#dc2626", // Crimson Extreme
    textHex: "#ffffff",
    borderHex: "#450a0a",
    pulseClass: "animate-ping-fast",
  },
  CATASTROPHIC: {
    name: "Thảm Họa Bão Lũ",
    bgHex: "#7c3aed", // Deep Purple Catastrophic
    textHex: "#ffffff",
    borderHex: "#2e1065",
    pulseClass: "animate-ping-fast",
  },
};

/**
 * Tạo một biểu tượng L.DivIcon chuyên dụng cho trạm quan trắc khí tượng.
 * Thiết kế phẳng góc cạnh (border-radius: 0px), viền nét đơn, typography utilitarian mono.
 *
 * @param {string} stationCode Mã trạm (ví dụ "HAN", "DNG", "SGN")
 * @param {WeatherSeverityLevel} severity Mức độ rủi ro khí tượng
 * @param {boolean} isSelected Trạng thái trạm đang được người dùng chọn
 * @returns {L.DivIcon} Đối tượng DivIcon của Leaflet
 */
export function createWeatherStationIcon(
  stationCode: string,
  severity: WeatherSeverityLevel = "SAFE",
  isSelected = false,
): L.DivIcon {
  const config = SEVERITY_CONFIG[severity];
  const pulseHtml = config.pulseClass
    ? `<span class="absolute inset-0 w-full h-full border opacity-75 ${config.pulseClass} pointer-events-none" style="border-color: ${config.bgHex};"></span>`
    : "";

  const selectedBorder = isSelected ? "outline outline-2 outline-offset-2 outline-[#e2ffcc]" : "";

  const html = `
    <div class="relative group flex items-center justify-center select-none ${selectedBorder}" style="width: 32px; height: 32px;">
      ${pulseHtml}
      <div 
        class="w-full h-full flex flex-col items-center justify-center border font-mono text-[10px] font-bold tracking-tight shadow-none transition-transform duration-150 group-hover:scale-110"
        style="
          background-color: ${config.bgHex};
          color: ${config.textHex};
          border-color: ${config.borderHex};
          border-radius: 0px;
        "
      >
        <span class="leading-none">${stationCode}</span>
      </div>
      <div 
        class="w-0 h-0 border-l-[4px] border-l-transparent border-r-[4px] border-r-transparent border-t-[5px] absolute -bottom-[5px] left-1/2 -translate-x-1/2"
        style="border-top-color: ${config.borderHex};"
      ></div>
    </div>
  `;

  return L.divIcon({
    html,
    className: "weather-station-marker-container",
    iconSize: [32, 37],
    iconAnchor: [16, 37], // Đỉnh nhọn phía dưới chạm đúng vị trí trạm
    popupAnchor: [0, -38],
  });
}

/**
 * Tạo biểu tượng mốc tâm bão (Storm Center Vortex Icon) với hiệu ứng xoay lốc xoáy.
 *
 * @param {string} stormName Tên cơn bão (ví dụ "YAGI")
 * @param {number} category Cấp bão Beaufort (ví dụ 11, 12, 16)
 * @returns {L.DivIcon} Biểu tượng lốc xoáy
 */
export function createStormVortexIcon(stormName: string, category: number): L.DivIcon {
  const html = `
    <div class="relative flex items-center justify-center select-none" title="${stormName}" style="width: 44px; height: 44px;">
      <span class="absolute inset-0 w-full h-full border-2 border-red-500 rounded-none animate-ping opacity-60"></span>
      <div class="w-full h-full bg-[#161b13] border-2 border-red-500 flex flex-col items-center justify-center font-mono text-white p-0.5">
        <svg class="w-4 h-4 text-red-500 animate-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <circle cx="12" cy="12" r="3" />
          <path d="M12 2a10 10 0 0 1 10 10c0 5.5-4.5 10-10 10S2 17.5 2 12" />
        </svg>
        <span class="text-[8px] font-bold text-[#e2ffcc] uppercase leading-none mt-0.5">C${category}</span>
      </div>
    </div>
  `;

  return L.divIcon({
    html,
    className: "storm-vortex-marker-container",
    iconSize: [44, 44],
    iconAnchor: [22, 22],
    popupAnchor: [0, -24],
  });
}
