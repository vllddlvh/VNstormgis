/**
 * @file WindLayer.tsx
 * @description Lớp dữ liệu Trường Gió thời gian thực (SR-MAP-003) — mũi tên SVG
 * xoay theo chiều dòng gió (phương vị FROM + 180°), mã màu và kích thước theo bậc
 * Beaufort, Tooltip hover tốc độ m/s. Chỉ render trạm CÓ bản ghi thời tiết thật
 * từ OpenWeatherMap; hướng khuyết / gió lặng hiển thị chấm tròn (SR-MAP-003 Exceptions).
 *
 * @author VNstormgis Team - Frontend & GIS Engineer
 * @version 1.0.0
 */

import React, { useMemo } from "react";
import { Marker, Tooltip } from "react-leaflet";
import { StationMarkerData } from "@/types/map";
import {
  WIND_BEAUFORT_CONFIG,
  WIND_CALM_THRESHOLD_MS,
  calculateWindArrowRotation,
  createWindArrowIcon,
  createWindCalmIcon,
  getWindBeaufortBand,
} from "@/lib/windUtils";

export interface WindLayerProps {
  /** Danh sách trạm đã lọc theo vùng miền và đã enrich weather (sole owner: UnifiedAppLayout). */
  stations: StationMarkerData[];
  /** Độ mờ lớp gió — tiêu thụ layerOpacity.wind có sẵn (quyết định #12). */
  opacity: number;
}

const WindLayerComponent: React.FC<WindLayerProps> = ({
  stations,
  opacity,
}) => {
  // Icon phải ổn định theo [stations, opacity]: BaseMap re-render mỗi mousemove (telemetry
  // con trỏ). Nếu tạo L.DivIcon mới mỗi render, react-leaflet gọi setIcon → thay DOM marker
  // đang hover; Leaflet mất mouseout trên element cũ nên tooltip gió bị kẹt mở.
  const markers = useMemo(
    () =>
      stations.map((station) => {
        const weather = station.weather;
        // Không có bản ghi / thiếu wind_speed → không ký hiệu gió (quyết định #11)
        if (!weather || weather.wind_speed === null) return null;

        const band = getWindBeaufortBand(weather.wind_speed);
        const config = WIND_BEAUFORT_CONFIG[band];
        const speedText = `${weather.wind_speed.toFixed(1)} m/s`;
        const gustText =
          weather.wind_gust !== null ? ` (giật ${weather.wind_gust.toFixed(1)})` : "";

        // Hướng khuyết hoặc gió lặng → chấm tròn "hướng chưa xác định" (quyết định #10)
        const isCalmOrUnknownDir =
          weather.wind_direction === null || weather.wind_speed < WIND_CALM_THRESHOLD_MS;

        const icon =
          weather.wind_direction === null || weather.wind_speed < WIND_CALM_THRESHOLD_MS
            ? createWindCalmIcon(config.color, config.arrowSizePx, opacity)
            : createWindArrowIcon(
                config.color,
                config.arrowSizePx,
                calculateWindArrowRotation(weather.wind_direction),
                opacity,
              );

        return (
          <Marker
            key={`wind-${station.code}`}
            position={station.coordinates}
            icon={icon}
            // Nâng trên icon trạm để đầu mũi tên (chiều gió) không bị che; thân icon
            // trạm vẫn chiếm phần lớn vùng click nên popup mở bình thường.
            zIndexOffset={1000}
          >
            <Tooltip
              direction="top"
              offset={[0, -6]}
              className="san-rita-tooltip san-rita-tooltip-station"
            >
              <span className="font-mono text-[10px]">
                {isCalmOrUnknownDir
                  ? `[${station.code}] ${speedText} — hướng chưa xác định`
                  : `[${station.code}] ${speedText} — ${config.label}${gustText}`}
                <br />
                <span className="text-[#84907f]">{station.name}</span>
                <span className="text-[#e2ffcc]">
                  {" "}
                  · NHẤP ICON TRẠM ĐỂ XEM CHI TIẾT
                </span>
              </span>
            </Tooltip>
          </Marker>
        );
      }),
    [stations, opacity],
  );

  // Render SAU RainfallLayer (04B), TRƯỚC lớp marker trạm (05) — quyết định #5
  return <>{markers}</>;
};

export const WindLayer = React.memo(WindLayerComponent);

export default WindLayer;
