/**
 * @file RainfallLayer.tsx
 * @description Lớp dữ liệu Lượng Mưa thời gian thực (SR-MAP-002) — CircleMarker
 * phân cấp màu San Rita theo severity_rain (QĐ 18/2021/QĐ-TTg), bán kính co giãn
 * theo rain_1h. Chỉ render trạm CÓ bản ghi thời tiết thật từ OpenWeatherMap.
 *
 * @author VNstormgis Team - Frontend & GIS Engineer
 * @version 1.0.0
 */

import React from "react";
import { CircleMarker, Tooltip } from "react-leaflet";
import { StationMarkerData } from "@/types/map";
import { RAINFALL_SEVERITY_CONFIG, calculateRainfallRadius } from "@/lib/rainfallUtils";

export interface RainfallLayerProps {
  /** Danh sách trạm đã lọc theo vùng miền và đã enrich weather (sole owner: UnifiedAppLayout). */
  stations: StationMarkerData[];
}

export const RainfallLayer: React.FC<RainfallLayerProps> = ({ stations }) => {
  return (
    <>
      {/* Render TRƯỚC lớp marker trạm trong MapContainer → icon trạm nằm TRÊN vòng tròn */}
      {stations.map((station) => {
        const weather = station.weather;
        if (!weather) return null;
        const config = RAINFALL_SEVERITY_CONFIG[weather.severity_rain];
        const rain1h = weather.rain_1h ?? 0;

        return (
          <CircleMarker
            key={`rainfall-${station.code}`}
            center={station.coordinates}
            radius={calculateRainfallRadius(weather.rain_1h)}
            pathOptions={{
              color: config.color,
              weight: 1,
              fillColor: config.color,
              fillOpacity: 0.55,
            }}
          >
            <Tooltip direction="top" offset={[0, -6]} className="san-rita-tooltip">
              <span className="font-mono text-[10px]">
                [{station.code}] {rain1h.toFixed(1)} mm — {config.label}
              </span>
            </Tooltip>
          </CircleMarker>
        );
      })}
    </>
  );
};

export default RainfallLayer;
