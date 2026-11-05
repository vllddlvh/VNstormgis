/**
 * @file RainfallLayer.tsx
 * @description Lớp dữ liệu Lượng Mưa thời gian thực (SR-MAP-002) — CircleMarker
 * phân cấp màu San Rita theo severity_rain (QĐ 18/2021/QĐ-TTg), bán kính co giãn
 * theo rain_1h. Chỉ render trạm CÓ bản ghi thời tiết thật từ OpenWeatherMap.
 *
 * @author VNstormgis Team - Frontend & GIS Engineer
 * @version 1.0.0
 */

import React, { useMemo } from "react";
import { CircleMarker, Tooltip } from "react-leaflet";
import { StationMarkerData } from "@/types/map";
import { RAINFALL_SEVERITY_CONFIG, calculateRainfallRadius } from "@/lib/rainfallUtils";

export interface RainfallLayerProps {
  /** Danh sách trạm đã lọc theo vùng miền và đã enrich weather (sole owner: UnifiedAppLayout). */
  stations: StationMarkerData[];
}

const RainfallLayerComponent: React.FC<RainfallLayerProps> = ({
  stations,
}) => {
  // Giữ element ổn định khi BaseMap re-render vì telemetry con trỏ (mỗi mousemove) —
  // tránh cập nhật path/tooltip vô ích và giữ nguyên listener hover của Leaflet.
  const markers = useMemo(
    () =>
      stations.map((station) => {
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
            <Tooltip
              direction="top"
              offset={[0, -6]}
              className="san-rita-tooltip san-rita-tooltip-station"
            >
              <span className="font-mono text-[10px]">
                [{station.code}] {rain1h.toFixed(1)} mm — {config.label}
                <br />
                <span className="text-[#84907f]">{station.name}</span>
                <span className="text-[#e2ffcc]">
                  {" "}
                  · NHẤP ICON TRẠM ĐỂ XEM CHI TIẾT
                </span>
              </span>
            </Tooltip>
          </CircleMarker>
        );
      }),
    [stations],
  );

  // Render TRƯỚC lớp marker trạm trong MapContainer → icon trạm nằm TRÊN vòng tròn
  return <>{markers}</>;
};

export const RainfallLayer = React.memo(RainfallLayerComponent);

export default RainfallLayer;
